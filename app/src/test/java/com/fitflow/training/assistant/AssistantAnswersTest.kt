package com.fitflow.training.assistant

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime

class AssistantAnswersTest {
    private val city=WeatherCity("1","北京","中国",39.9,116.4)
    private class Weather(val cities:List<WeatherCity>):WeatherRepository {
        var calls=0
        override suspend fun searchCities(query:String)=cities
        override suspend fun weather(city:WeatherCity):WeatherAnswer { calls++; return WeatherAnswer(city,"晴",25.0,18.0,28.0) }
    }
    @Test fun timeComesFromDeviceClock()=runBlocking {
        val a=AssistantAnswers({null},{null},{"动作"},Weather(emptyList())) { ZonedDateTime.parse("2026-09-24T08:05:00+08:00") }
        assertEquals("现在是8点5分",a.answer(AssistantIntent.Query(LocalQuestion.TIME)))
    }
    @Test fun weatherRequiresCityAndDoesNotGuessAmbiguousPlace()=runBlocking {
        val w=Weather(listOf(city,city.copy(id="2",region="其他地区")))
        val a=AssistantAnswers({null},{null},{"动作"},w)
        assertTrue(a.answer(AssistantIntent.Weather(null)).contains("设置"))
        assertTrue(a.answer(AssistantIntent.Weather("北京")).contains("多个"))
        assertEquals(0,w.calls)
    }
    @Test fun savedCityIsUsedAndFailuresAreExplicit()=runBlocking {
        val w=Weather(emptyList()); val a=AssistantAnswers({null},{city},{"动作"},w)
        assertTrue(a.answer(AssistantIntent.Weather(null)).contains("北京"))
        assertEquals(1,w.calls)
    }
}
