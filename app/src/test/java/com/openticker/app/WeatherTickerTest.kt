package com.openticker.app

import com.openticker.app.data.model.Location
import com.openticker.app.data.remote.WeatherParser
import com.openticker.app.util.weatherTickerText
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class WeatherTickerTest {
    private val now=LocalDateTime.parse("2026-10-29T13:15")
    private fun weather()=WeatherParser.parse(requireNotNull(javaClass.classLoader?.getResource("weather.json")).readText())
    @Test fun `weather ticker uses forecast data for all current statistics and hourly entries`() {
        val text=weatherTickerText(weather(),Location.DEFAULT,false,now)
        listOf("Hatboro","CURRENT: 73°F","PARTLY CLOUDY","FEELS LIKE: 71°F","HIGH: 76°F","LOW: 52°F",
            "WIND: 9 mph SW","HUMIDITY: 65%","RAIN: 20%","1PM: 73°F").forEach { assertTrue("Missing $it",text.contains(it)) }
    }
    @Test fun `weather ticker respects Celsius and represents missing values without inventing data`() {
        val text=weatherTickerText(weather(),Location.DEFAULT,true,now)
        assertTrue(text.contains("CURRENT: 23°C"));assertTrue(text.contains("2PM: — Rain"));assertFalse(text.contains("°F"))
    }
    @Test fun `unavailable weather has an informative message`() {
        val text=weatherTickerText(null,Location.DEFAULT,false,now)
        assertTrue(text.contains("Weather unavailable"));assertFalse(text.contains("CURRENT:"))
    }
    @Test fun `expired hourly entries and previous day highs are not reused`() {
        val text=weatherTickerText(weather(),Location.DEFAULT,false,now.plusDays(2))
        assertFalse(text.contains("1PM:"));assertTrue(text.contains("HIGH: —"));assertTrue(text.contains("RAIN: —"))
    }
}
