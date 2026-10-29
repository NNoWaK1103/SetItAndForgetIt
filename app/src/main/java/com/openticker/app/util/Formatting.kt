package com.openticker.app.util

import com.openticker.app.data.model.*
import java.time.ZoneId
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

fun fahrenheitToCelsius(f: Double) = (f - 32.0) * 5.0 / 9.0
fun celsiusToFahrenheit(c: Double) = c * 9.0 / 5.0 + 32.0
fun temperature(f: Double?, celsius: Boolean) = f?.let { "${(if (celsius) fahrenheitToCelsius(it) else it).roundToInt()}°" } ?: "—"
fun windDirection(degrees: Double?): String = degrees?.let { listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[((it / 45).roundToInt() % 8 + 8) % 8] } ?: "—"
fun filterLeagues(games: List<Game>, enabled: Set<League>) = games.filter { it.league in enabled }
fun sortedGames(games: List<Game>) = games.sortedWith(compareBy<Game> {
    when (it.status) { GameStatus.LIVE -> 0; GameStatus.UPCOMING -> 1; GameStatus.FINAL -> 2; else -> 3 }
}.thenComparator { a,b ->
    when {
        a.start==null && b.start==null -> 0
        a.start==null -> 1
        b.start==null -> -1
        a.status==GameStatus.FINAL -> b.start.compareTo(a.start)
        else -> a.start.compareTo(b.start)
    }
})
fun startTime(game: Game, zone: ZoneId = ZoneId.systemDefault()): String = game.start?.atZone(zone)?.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US)) ?: "Time TBD"
fun gameStatusText(game: Game): String = when (game.status) {
    GameStatus.LIVE -> game.detail.ifBlank {
        val period = game.period?.let { p -> when (game.league) { League.MLB -> "Inning $p"; League.NHL -> "P$p"; else -> "Q$p" } }.orEmpty()
        "$period ${game.clock.orEmpty()}".trim().ifBlank { "LIVE" }
    }
    GameStatus.FINAL -> "FINAL"
    GameStatus.UPCOMING -> startTime(game)
    GameStatus.POSTPONED -> "POSTPONED / DELAYED"
    GameStatus.CANCELED -> "CANCELED"
    GameStatus.UNKNOWN -> "Status unavailable"
}
fun tickerText(game: Game): String {
    val matchup = if (game.status == GameStatus.LIVE || game.status == GameStatus.FINAL)
        "${game.away.abbreviation} ${game.awayScore ?: "—"}  –  ${game.home.abbreviation} ${game.homeScore ?: "—"}"
    else "${game.away.abbreviation} @ ${game.home.abbreviation}"
    val live = if (game.status == GameStatus.LIVE) "LIVE · " else ""
    return "${game.league.name}  |  $matchup  |  $live${gameStatusText(game)}"
}

fun weatherTickerText(weather: Weather?, location: Location, celsius: Boolean, now: LocalDateTime): String {
    if(weather==null) return "${location.label}  |  Weather unavailable · waiting for an update"
    val day=weather.days.find { it.date==now.toLocalDate() }
    fun temp(value: Double?) = temperature(value,celsius)+(if(value!=null) if(celsius) "C" else "F" else "")
    val details=listOf(location.label,"CURRENT: ${temp(weather.temperatureF)}",
        weatherCondition(weather.code,weather.isDay).description.uppercase(Locale.US),
        "FEELS LIKE: ${temp(weather.feelsLikeF)}","HIGH: ${temp(day?.highF)}","LOW: ${temp(day?.lowF)}",
        "WIND: ${weather.windMph?.let { "${it.roundToInt()} mph ${windDirection(weather.windDirection)}" } ?: "—"}",
        "HUMIDITY: ${weather.humidity?.let { "$it%" } ?: "—"}","RAIN: ${day?.rain?.let { "$it%" } ?: "—"}")
    val hourly=weather.hours.filter { !it.time.isBefore(now.withMinute(0).withSecond(0).withNano(0)) }.take(12).map {
        "${it.time.format(DateTimeFormatter.ofPattern("ha",Locale.US))}: ${temp(it.temperatureF)} ${weatherCondition(it.code).description}"
    }
    return (details+hourly).joinToString("  |  ")
}
