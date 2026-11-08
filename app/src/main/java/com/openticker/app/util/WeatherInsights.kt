package com.openticker.app.util

import com.openticker.app.data.model.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class WeatherInsight(val title: String, val value: String, val detail: String)

/** Forecast timestamps are local wall-clock times supplied in the location's timezone. */
fun forecastHours(weather: Weather, now: Instant): List<HourWeather> {
    val zone = runCatching { ZoneId.of(weather.timezone) }.getOrDefault(ZoneOffset.UTC)
    val start = now.atZone(zone).toLocalDateTime().withMinute(0).withSecond(0).withNano(0)
    return weather.hours.filter { !it.time.isBefore(start) && it.time.isBefore(start.plusHours(12)) }
        .distinctBy { it.time }.sortedBy { it.time }
}

fun weatherInsights(weather: Weather, now: Instant, celsius: Boolean): List<WeatherInsight> {
    val zone = runCatching { ZoneId.of(weather.timezone) }.getOrDefault(ZoneOffset.UTC)
    val local = now.atZone(zone).toLocalDateTime()
    val start = local.withMinute(0).withSecond(0).withNano(0)
    val hours = forecastHours(weather, now)
    val clock = DateTimeFormatter.ofPattern("h a", Locale.US)
    fun label(time: LocalDateTime) = (if (time.toLocalDate() != local.toLocalDate()) "Tomorrow " else "") + time.format(clock)
    val complete = (0L..11L).all { offset -> hours.any { it.time == start.plusHours(offset) && (it.rain ?: -1) in 0..100 } }
    val wet = hours.firstOrNull { (it.rain ?: -1) in 40..100 }
    val rain = when {
        hours.isEmpty() -> WeatherInsight("PRECIP OUTLOOK", "Forecast unavailable", "No upcoming hourly data")
        wet != null -> WeatherInsight("PRECIP OUTLOOK", "${wet.rain}% at ${label(wet.time)}", "First supplied hour at 40%+${if (!complete) " · partial forecast" else ""}")
        !complete -> WeatherInsight("PRECIP OUTLOOK", "Incomplete forecast", "Not enough data for a 12-hour outlook")
        else -> WeatherInsight("PRECIP OUTLOOK", "Lower chance next 12h", "Peak ${hours.maxOf { it.rain!! }}% · precipitation still possible")
    }
    val first = hours.firstOrNull { it.time == start }?.takeIf { it.temperatureF?.isFinite() == true }
    val end = hours.firstOrNull { it.time == start.plusHours(6) }?.takeIf { it.temperatureF?.isFinite() == true }
    val trend = if (first != null && end != null) {
        val delta = (end.temperatureF!! - first.temperatureF!!) * if (celsius) 5.0 / 9 else 1.0
        val change = when { abs(delta) < 1 -> "Holding steady"; delta > 0 -> "Warming ${abs(delta).roundToInt()}°"; else -> "Cooling ${abs(delta).roundToInt()}°" }
        WeatherInsight("NEXT 6 HOURS", change, "Forecast ${temperature(first.temperatureF,celsius)} → ${temperature(end.temperatureF,celsius)}${if(celsius) "C" else "F"} by ${label(end.time)}")
    } else WeatherInsight("NEXT 6 HOURS", "Trend unavailable", "Hourly temperature data incomplete")
    val day = weather.days.find { it.date == local.toLocalDate() }
    val sunrise = day?.sunrise
    val sunset = day?.sunset
    val daylight = if (sunrise != null && sunset != null && sunrise.toLocalDate() == local.toLocalDate() && sunset.toLocalDate() == local.toLocalDate() && sunset.isAfter(sunrise)) {
        val event = if (local.isBefore(sunrise)) sunrise else sunset
        val remaining = Duration.between(local, event).toMinutes().coerceAtLeast(0)
        val detail = "Sunrise ${sunrise.format(DateTimeFormatter.ofPattern("h:mm a",Locale.US))} · sunset ${sunset.format(DateTimeFormatter.ofPattern("h:mm a",Locale.US))}"
        WeatherInsight("DAYLIGHT", when { local.isBefore(sunrise) -> "Sunrise in ${remaining / 60}h ${remaining % 60}m"; local.isBefore(sunset) -> "${remaining / 60}h ${remaining % 60}m until sunset"; else -> "Sunset has passed" }, detail)
    } else WeatherInsight("DAYLIGHT", "Times unavailable", "Sunrise and sunset data incomplete")
    return listOf(rain, trend, daylight)
}

fun uvDescription(uv: Double?): String = when {
    uv == null || !uv.isFinite() || uv < 0 -> "Unavailable"
    uv < 3 -> "Low"
    uv < 6 -> "Moderate · protection advised"
    uv < 8 -> "High · protection advised"
    uv < 11 -> "Very high · limit midday sun"
    else -> "Extreme · limit midday sun"
}

fun windDescription(mph: Double?): String = when {
    mph == null || !mph.isFinite() || mph < 0 -> "Unavailable"
    mph < 1 -> "Calm"
    mph < 8 -> "Light breeze"
    mph < 19 -> "Breezy"
    mph < 25 -> "Brisk wind"
    else -> "Strong wind"
}

/** Only visible, unpaused time counts; persist both fields across slideshow visits. */
data class WeatherPageClock(val page: Int = 0, val remainingMillis: Long = 10_000)
fun advanceWeatherPage(clock: WeatherPageClock, elapsedMillis: Long, paused: Boolean = false): WeatherPageClock {
    if (paused || elapsedMillis <= 0) return clock
    val remaining = clock.remainingMillis.coerceIn(1, 10_000)
    if (elapsedMillis < remaining) return clock.copy(remainingMillis = remaining - elapsedMillis)
    val afterFirst = elapsedMillis - remaining
    val switches = 1 + afterFirst / 10_000
    return WeatherPageClock(((clock.page + switches) % 2).toInt(), 10_000 - afterFirst % 10_000)
}
