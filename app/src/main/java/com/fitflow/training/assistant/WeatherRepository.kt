package com.fitflow.training.assistant

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Serializable
data class WeatherCity(val id: String, val name: String, val region: String, val latitude: Double, val longitude: Double)
data class WeatherAnswer(val city: WeatherCity, val description: String, val temperature: Double, val low: Double, val high: Double)
interface WeatherRepository {
    suspend fun searchCities(query: String): List<WeatherCity>
    suspend fun weather(city: WeatherCity): WeatherAnswer
}

class OpenMeteoWeatherRepository(private val get: suspend (String) -> String = WeatherHttp::get) : WeatherRepository {
    override suspend fun searchCities(query: String): List<WeatherCity> = withTimeout(10_000) {
        require(query.isNotBlank() && query.length <= 40)
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(query.trim(), "UTF-8")}&count=10&language=zh&format=json"
        val root = Json.parseToJsonElement(get(url)).jsonObject
        (root["results"] as? JsonArray).orEmpty().map { value ->
            val item = value.jsonObject
            fun field(key: String) = item[key]?.jsonPrimitive?.contentOrNull.orEmpty()
            WeatherCity(field("id"), field("name"), listOf(field("admin1"), field("country")).filter { it.isNotBlank() }.distinct().joinToString(" · "),
                number(item, "latitude").also { require(it in -90.0..90.0) }, number(item, "longitude").also { require(it in -180.0..180.0) })
        }.filter { it.id.isNotBlank() && it.name.isNotBlank() }.distinctBy { it.id }
    }

    override suspend fun weather(city: WeatherCity): WeatherAnswer = withTimeout(10_000) {
        require(city.latitude in -90.0..90.0 && city.longitude in -180.0..180.0)
        val root = Json.parseToJsonElement(get("https://api.open-meteo.com/v1/forecast?latitude=${city.latitude}&longitude=${city.longitude}&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1")).jsonObject
        val current = root["current"] as? JsonObject ?: throw IllegalArgumentException("Missing current weather")
        val daily = root["daily"] as? JsonObject ?: throw IllegalArgumentException("Missing daily weather")
        fun first(key: String): Double = (daily[key] as? JsonArray)?.firstOrNull()?.jsonPrimitive?.doubleOrNull?.takeIf { it.isFinite() }
            ?: throw IllegalArgumentException("Missing $key")
        val low = first("temperature_2m_min")
        val high = first("temperature_2m_max")
        require(low <= high)
        WeatherAnswer(city, describe(current["weather_code"]?.jsonPrimitive?.intOrNull), number(current,"temperature_2m"), low, high)
    }

    private fun number(item: JsonObject, key: String) = item[key]?.jsonPrimitive?.doubleOrNull?.takeIf { it.isFinite() }
        ?: throw IllegalArgumentException("Missing $key")
    private fun describe(code: Int?) = when(code) {
        0 -> "晴"; 1,2 -> "多云"; 3 -> "阴"; 45,48 -> "有雾"
        51,53,55,56,57 -> "毛毛雨"; 61,63,65,66,67,80,81,82 -> "有雨"
        71,73,75,77,85,86 -> "有雪"; 95,96,99 -> "雷雨"; else -> "天气描述暂不可用"
    }
}

private object WeatherHttp {
    private val executor = Executors.newFixedThreadPool(2) { runnable -> Thread(runnable, "assistant-weather").apply { isDaemon=true } }
    suspend fun get(url: String): String = suspendCancellableCoroutine { continuation ->
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.instanceFollowRedirects = false
        val future = executor.submit {
            try {
                require(connection.responseCode == 200) { "Weather service unavailable" }
                val text = connection.inputStream.bufferedReader().use { reader ->
                    val buffer = CharArray(262145)
                    var count = 0
                    while (count < buffer.size) {
                        val read = reader.read(buffer, count, buffer.size-count)
                        if (read < 0) break
                        count += read
                    }
                    require(count <= 262144) { "Weather response too large" }
                    String(buffer, 0, count)
                }
                if (continuation.isActive) continuation.resume(text)
            } catch (error: Exception) {
                if (continuation.isActive) continuation.resumeWithException(error)
            } finally { connection.disconnect() }
        }
        continuation.invokeOnCancellation { connection.disconnect(); future.cancel(true) }
    }
}
