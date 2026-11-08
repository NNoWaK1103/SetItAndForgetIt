package com.openticker.app

import com.openticker.app.data.model.*
import com.openticker.app.util.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class WeatherInsightsTest {
    private val now = Instant.parse("2026-11-08T04:20:00Z") // Nov 7, 11:20 PM in New York
    private val local = LocalDateTime.parse("2026-11-07T23:00")
    private fun weather(hours: List<HourWeather> = (0L..11L).map { HourWeather(local.plusHours(it), 50.0 + it, 0, 5) }) =
        Weather(50.0, 49.0, 60, 10.0, 90.0, 0, 0.0, false, "America/New_York", hours, emptyList())

    @Test fun `uses location timezone and includes next day within actual twelve hour window`() {
        val data=weather()
        assertEquals(12,forecastHours(data,now).size)
        val text=weatherInsights(data.copy(hours=data.hours.map { if(it.time==local.plusHours(2)) it.copy(rain=70) else it }),now,false)[0]
        assertEquals("70% at Tomorrow 1 AM",text.value)
    }
    @Test fun `expired data does not masquerade as a dry outlook or temperature trend`() {
        val data=weather()
        val insights=weatherInsights(data,now.plusSeconds(86400),false)
        assertTrue(forecastHours(data,now.plusSeconds(86400)).isEmpty())
        assertEquals("Forecast unavailable",insights[0].value)
        assertEquals("Trend unavailable",insights[1].value)
    }
    @Test fun `missing probabilities or gaps do not yield dry assurance`() {
        val data=weather()
        assertEquals("Incomplete forecast",weatherInsights(data.copy(hours=data.hours.drop(1)),now,false)[0].value)
        assertEquals("Incomplete forecast",weatherInsights(data.copy(hours=data.hours.map { it.copy(rain=null) }),now,false)[0].value)
        assertEquals("Incomplete forecast",weatherInsights(data.copy(hours=data.hours.map { it.copy(rain=101) }),now,false)[0].value)
        assertEquals("Lower chance next 12h",weatherInsights(data,now,false)[0].value)
    }
    @Test fun `trend converts temperature difference rather than absolute temperature`() {
        assertEquals("Warming 6°",weatherInsights(weather(),now,false)[1].value)
        assertEquals("Warming 3°",weatherInsights(weather(),now,true)[1].value)
        assertTrue(weatherInsights(weather(),now,true)[1].detail.contains("C"))
    }
    @Test fun `daylight does not reuse yesterday sun times at midnight`() {
        val day=DayWeather(local.toLocalDate(),0,60.0,40.0,5,2.0,local.withHour(7),local.withHour(17))
        val data=weather().copy(days=listOf(day))
        assertEquals("Sunset has passed",weatherInsights(data,now,false)[2].value)
        assertEquals("Times unavailable",weatherInsights(data,now.plusSeconds(3600),false)[2].value)
    }
    @Test fun `hour ordering duplicates and out of window entries are handled`() {
        val data=weather()
        val dirty=data.copy(hours=data.hours.reversed()+data.hours.first()+HourWeather(local.plusDays(1),70.0,0,80))
        assertEquals(data.hours,forecastHours(dirty,now))
    }
    @Test fun `missing and nonfinite wind or UV are not given reassuring labels`() {
        assertEquals("Unavailable",uvDescription(null))
        assertEquals("Unavailable",uvDescription(Double.NaN))
        assertEquals("Unavailable",windDescription(-1.0))
        assertEquals("Unavailable",windDescription(null))
        assertEquals("Light breeze",windDescription(5.0))
        assertTrue(uvDescription(7.0).startsWith("High"))
    }
    @Test fun `short visits accumulate instead of resetting the weather page countdown`() {
        val firstVisit=advanceWeatherPage(WeatherPageClock(),5_000)
        assertEquals(0,firstVisit.page)
        assertEquals(5_000L,firstVisit.remainingMillis)
        val secondVisit=advanceWeatherPage(firstVisit,5_000)
        assertEquals(1,secondVisit.page)
        assertEquals(10_000L,secondVisit.remainingMillis)
        assertEquals(0,advanceWeatherPage(secondVisit,10_000).page)
    }
    @Test fun `paused settings or background time never consumes page countdown`() {
        val clock=WeatherPageClock(1,2_500)
        assertEquals(clock,advanceWeatherPage(clock,60_000,paused=true))
        assertEquals(clock,advanceWeatherPage(clock,-100))
        assertEquals(WeatherPageClock(0,9_500),advanceWeatherPage(clock,3_000))
    }
    @Test fun `elapsed tick overflow carries remainder across multiple page changes`() {
        assertEquals(WeatherPageClock(0,5_000),advanceWeatherPage(WeatherPageClock(),25_000))
        assertEquals(WeatherPageClock(1,10_000),advanceWeatherPage(WeatherPageClock(),30_000))
    }
}
