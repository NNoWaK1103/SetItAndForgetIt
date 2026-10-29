package com.openticker.app

import com.openticker.app.data.model.*
import com.openticker.app.data.remote.*
import com.openticker.app.util.*
import org.junit.Assert.*
import org.junit.Test

class ParsingTest {
    @Test fun `ESPN minute precision timestamps retain scheduled start times`() {
        val body=fixture("espn-scoreboard.json").replace("2026-10-29T23:00:00Z","2026-10-29T23:00Z")
        val game=EspnParser.parse(body,League.NFL).first()
        assertEquals(java.time.Instant.parse("2026-10-29T23:00:00Z"),game.start)
        assertEquals("7:00 PM",startTime(game,java.time.ZoneId.of("America/New_York")))
    }
    @Test fun `ESPN timezone offsets normalize to the correct instant`() {
        val body=fixture("espn-scoreboard.json").replace("2026-10-29T23:00:00Z","2026-10-30T01:00+02:00")
        assertEquals(java.time.Instant.parse("2026-10-29T23:00:00Z"),EspnParser.parse(body,League.NHL).first().start)
    }
    private fun fixture(name: String) = requireNotNull(javaClass.classLoader?.getResource(name)).readText()
    private fun games() = EspnParser.parse(fixture("espn-scoreboard.json"),League.NFL)
    @Test fun `scoreboard handles home and away independent of order`() {
        val g = games().first()
        assertEquals("PHI",g.away.abbreviation); assertEquals("DAL",g.home.abbreviation)
        assertEquals(24,g.awayScore); assertEquals(17,g.homeScore)
        assertEquals(GameStatus.LIVE,g.status); assertEquals(4,g.period); assertEquals("2:31",g.clock)
        assertEquals("https://example.com/dal.png",g.home.logo)
        assertNotNull(g.start)
    }
    @Test fun `missing fields stay unknown and malformed games are skipped`() {
        assertEquals(3,games().size)
        val g=games().last()
        assertEquals(GameStatus.UNKNOWN,g.status); assertNull(g.awayScore); assertNull(g.homeScore)
        assertNull(g.start); assertNull(g.period); assertNull(g.away.logo)
        assertEquals("Unknown team",g.home.name)
    }
    @Test fun `status falls back to event status`() { assertEquals(GameStatus.FINAL,games()[1].status) }
    @Test fun `status maps upcoming final postponed canceled and unknown`() {
        assertEquals(GameStatus.UPCOMING,EspnParser.status("STATUS_SCHEDULED","pre",false))
        assertEquals(GameStatus.FINAL,EspnParser.status("STATUS_FINAL",null,true))
        assertEquals(GameStatus.POSTPONED,EspnParser.status("STATUS_POSTPONED","pre",false))
        assertEquals(GameStatus.POSTPONED,EspnParser.status("STATUS_DELAYED","in",false))
        assertEquals(GameStatus.CANCELED,EspnParser.status("STATUS_CANCELED","post",false))
        assertEquals(GameStatus.UNKNOWN,EspnParser.status(null,null,null))
    }
    @Test fun `empty scoreboard is valid`() { assertTrue(EspnParser.parse("""{"events":[]}""",League.NHL).isEmpty()) }
    @Test(expected=IllegalArgumentException::class) fun `invalid scoreboard cannot replace cached scores`() { EspnParser.parse("{}",League.NBA) }
    @Test fun `all leagues use the same game mapping`() { League.entries.forEach { assertEquals(it,EspnParser.parse(fixture("espn-scoreboard.json"),it).first().league) } }
    @Test fun `weather parses requested US units and timezone`() {
        val w=WeatherParser.parse(fixture("weather.json"))
        assertEquals(72.5,w.temperatureF,.001); assertEquals(8.5,w.windMph!!,.001)
        assertEquals(65,w.humidity); assertEquals("America/New_York",w.timezone)
        assertEquals(0.02,w.precipitationInches!!,.001); assertTrue(w.isDay)
        assertEquals(4.2,w.days.first().uv!!,.001); assertEquals(7,w.days.first().sunrise?.hour)
    }
    @Test fun `weather tolerates null and mismatched forecast arrays`() {
        val w=WeatherParser.parse(fixture("weather.json"))
        assertEquals(2,w.hours.size); assertNull(w.hours[1].temperatureF); assertNull(w.hours[1].rain)
        assertEquals(2,w.days.size); assertNull(w.days[1].lowF); assertNull(w.days[1].sunset)
        val minimal=WeatherParser.parse("""{"current":{"temperature_2m":0}}""")
        assertTrue(minimal.hours.isEmpty()); assertNull(minimal.code)
    }
    @Test(expected=IllegalArgumentException::class) fun `weather without valid current data is rejected`() { WeatherParser.parse("""{"current":{"temperature_2m":null}}""") }
    @Test fun `weather codes include all WMO groups and unknown`() {
        assertEquals(WeatherGlyph.SUN,weatherCondition(0).glyph)
        assertEquals(WeatherGlyph.MOON,weatherCondition(0,false).glyph)
        assertEquals(WeatherGlyph.CLOUD_SUN,weatherCondition(2).glyph)
        assertEquals(WeatherGlyph.CLOUD,weatherCondition(3).glyph)
        assertEquals(WeatherGlyph.FOG,weatherCondition(48).glyph)
        listOf(51,56,61,66,80).forEach { assertEquals(WeatherGlyph.RAIN,weatherCondition(it).glyph) }
        listOf(71,77,85).forEach { assertEquals(WeatherGlyph.SNOW,weatherCondition(it).glyph) }
        listOf(95,96,99).forEach { assertEquals(WeatherGlyph.STORM,weatherCondition(it).glyph) }
        assertEquals(WeatherGlyph.UNKNOWN,weatherCondition(null).glyph)
        assertEquals(WeatherGlyph.UNKNOWN,weatherCondition(1234).glyph)
    }
    @Test fun `temperature converts in both directions`() {
        assertEquals(0.0,fahrenheitToCelsius(32.0),.0001); assertEquals(212.0,celsiusToFahrenheit(100.0),.0001)
        assertEquals(-40.0,fahrenheitToCelsius(-40.0),.0001)
        assertEquals("0°",temperature(32.0,true)); assertEquals("32°",temperature(32.0,false)); assertEquals("—",temperature(null,false))
    }
    @Test fun `league filtering honors empty and mixed selections`() {
        val mixed=games()+games().map { it.copy(league=League.NBA) }
        assertEquals(3,filterLeagues(mixed,setOf(League.NBA)).size)
        assertTrue(filterLeagues(mixed,emptySet()).isEmpty())
    }
    @Test fun `ticker includes real scores status and league`() {
        val text=tickerText(games().first())
        assertTrue(text.contains("NFL")); assertTrue(text.contains("PHI 24")); assertTrue(text.contains("DAL 17")); assertTrue(text.contains("LIVE")); assertTrue(text.contains("2:31"))
        assertTrue(tickerText(games()[1]).contains("FINAL"))
    }
    @Test fun `upcoming ticker does not present zero as an actual score`() {
        val text=tickerText(games().first().copy(status=GameStatus.UPCOMING,awayScore=0,homeScore=0))
        assertTrue(text.contains("PHI @ DAL")); assertFalse(text.contains("PHI 0"))
    }
    @Test fun `live games sort before scheduled and completed`() {
        val g=games().first()
        assertEquals(listOf(GameStatus.LIVE,GameStatus.UPCOMING,GameStatus.FINAL),sortedGames(listOf(g.copy(status=GameStatus.FINAL),g.copy(status=GameStatus.UPCOMING),g)).map { it.status })
    }
    @Test fun `geocoder handles missing results and absent coordinates`() {
        assertTrue(WeatherParser.locations("{}").isEmpty())
        val list=WeatherParser.locations("""{"results":[{"name":"Hatboro","latitude":40.17,"longitude":-75.1,"admin1":"Pennsylvania","country_code":"US"},{"name":"Incomplete"}]}""")
        assertEquals(1,list.size); assertEquals("Hatboro, Pennsylvania, US",list.first().label)
    }
    @Test fun `wind directions wrap at north`() { assertEquals("N",windDirection(360.0)); assertEquals("SW",windDirection(225.0)); assertEquals("—",windDirection(null)) }
}
