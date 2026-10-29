package com.openticker.app.data.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

enum class League(val path: String) {
    NFL("football/nfl"), NCAAF("football/college-football"), NBA("basketball/nba"),
    MLB("baseball/mlb"), NHL("hockey/nhl")
}
enum class GameStatus { LIVE, FINAL, UPCOMING, POSTPONED, CANCELED, UNKNOWN }
data class Team(val name: String, val abbreviation: String, val logo: String? = null)
data class Game(
    val id: String, val league: League, val away: Team, val home: Team,
    val awayScore: Int?, val homeScore: Int?, val status: GameStatus,
    val start: Instant?, val period: Int?, val clock: String?, val detail: String
)
data class Location(val name: String, val region: String, val latitude: Double, val longitude: Double) {
    val label: String get() = listOf(name, region).filter { it.isNotBlank() }.joinToString(", ")
    companion object { val DEFAULT = Location("Hatboro", "Pennsylvania", 40.1743, -75.1068) }
}
data class HourWeather(val time: LocalDateTime, val temperatureF: Double?, val code: Int?, val rain: Int?)
data class DayWeather(
    val date: LocalDate, val code: Int?, val highF: Double?, val lowF: Double?,
    val rain: Int?, val uv: Double?, val sunrise: LocalDateTime?, val sunset: LocalDateTime?
)
data class Weather(
    val temperatureF: Double, val feelsLikeF: Double?, val humidity: Int?, val windMph: Double?,
    val windDirection: Double?, val code: Int?, val precipitationInches: Double?, val isDay: Boolean,
    val timezone: String, val hours: List<HourWeather>, val days: List<DayWeather>
)
enum class WeatherGlyph { SUN, MOON, CLOUD_SUN, CLOUD, FOG, RAIN, SNOW, STORM, UNKNOWN }
data class WeatherCondition(val description: String, val glyph: WeatherGlyph)
fun weatherCondition(code: Int?, isDay: Boolean = true): WeatherCondition = when (code) {
    0 -> WeatherCondition("Clear sky", if (isDay) WeatherGlyph.SUN else WeatherGlyph.MOON)
    1 -> WeatherCondition("Mostly clear", WeatherGlyph.CLOUD_SUN)
    2 -> WeatherCondition("Partly cloudy", WeatherGlyph.CLOUD_SUN)
    3 -> WeatherCondition("Overcast", WeatherGlyph.CLOUD)
    45, 48 -> WeatherCondition("Fog", WeatherGlyph.FOG)
    51, 53, 55 -> WeatherCondition("Drizzle", WeatherGlyph.RAIN)
    56, 57 -> WeatherCondition("Freezing drizzle", WeatherGlyph.RAIN)
    61, 63, 65 -> WeatherCondition("Rain", WeatherGlyph.RAIN)
    66, 67 -> WeatherCondition("Freezing rain", WeatherGlyph.RAIN)
    71, 73, 75, 77 -> WeatherCondition("Snow", WeatherGlyph.SNOW)
    80, 81, 82 -> WeatherCondition("Rain showers", WeatherGlyph.RAIN)
    85, 86 -> WeatherCondition("Snow showers", WeatherGlyph.SNOW)
    95 -> WeatherCondition("Thunderstorm", WeatherGlyph.STORM)
    96, 99 -> WeatherCondition("Thunderstorm with hail", WeatherGlyph.STORM)
    else -> WeatherCondition("Conditions unavailable", WeatherGlyph.UNKNOWN)
}
