package com.openticker.app.util

import com.openticker.app.data.model.*
import java.time.ZoneId
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
}.thenBy { it.start })
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
