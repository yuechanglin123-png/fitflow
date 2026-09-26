package com.fitflow.training.assistant

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class WeatherRepositoryTest {
    private val city = WeatherCity("1", "北京", "中国", 39.9, 116.4)
    @Test fun readsTemperaturesAndUnknownCodeWithoutInventingWeather() = runBlocking {
        val repo = OpenMeteoWeatherRepository { """{"current":{"temperature_2m":20,"weather_code":999},"daily":{"temperature_2m_min":[10],"temperature_2m_max":[24]}}""" }
        val answer = repo.weather(city)
        assertEquals(20.0, answer.temperature, 0.0)
        assertEquals("天气描述暂不可用", answer.description)
    }
    @Test fun missingTemperatureFailsInsteadOfReportingZero() = runBlocking {
        val repo = OpenMeteoWeatherRepository { """{"current":{"weather_code":0},"daily":{"temperature_2m_min":[10],"temperature_2m_max":[24]}}""" }
        try { repo.weather(city); fail("Missing temperature must fail") } catch (_: IllegalArgumentException) { }
    }
    @Test fun keepsAmbiguousCitiesAndEncodesOnlyQuery() = runBlocking {
        var requested = ""
        val repo = OpenMeteoWeatherRepository { url -> requested=url; """{"results":[{"id":1,"name":"朝阳","latitude":1,"longitude":2,"admin1":"辽宁"},{"id":2,"name":"朝阳","latitude":3,"longitude":4,"admin1":"北京"}]}""" }
        assertEquals(2, repo.searchCities("朝阳").size)
        assertTrue(requested.contains("%E6%9C%9D%E9%98%B3"))
        assertFalse(requested.contains("训练"))
    }
    @Test fun emptyResultsStayEmpty() = runBlocking {
        assertTrue(OpenMeteoWeatherRepository { "{}" }.searchCities("不存在").isEmpty())
    }
}
