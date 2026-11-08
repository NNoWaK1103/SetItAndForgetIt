package com.openticker.app.util

import com.openticker.app.data.model.Game
import com.openticker.app.data.model.GameStatus
import com.openticker.app.data.model.League

/** Status is from the last successful scoreboard, not necessarily the current game. */
fun sportsStatusCaption(game: Game, stale: Boolean = false): String = when {
    stale && game.status == GameStatus.LIVE -> "SAVED • WAS LIVE"
    stale -> "SAVED • ${game.status.name}"
    game.status == GameStatus.LIVE -> "● LIVE"
    else -> game.status.name
}

/** At most two short rows; absent provider fields never produce inferred game facts. */
fun sportsGameBrief(game: Game): List<String> {
    val situation = sportsSituationText(game).compact()
    val play = if (game.status == GameStatus.LIVE) game.situation?.lastPlay.compact() else null
    val coverage = game.broadcasts.mapNotNull { it.compact() }.distinct().joinToString(" / ").compact()
    val venue = game.venue.compact()
    return buildList {
        situation?.let { add(it) }
        play?.let { add("Last play: $it") }
        coverage?.let { add("Coverage: $it") }
        venue?.let { add("Venue: $it") }
    }.take(2)
}

private fun String?.compact(): String? = this?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() }

fun sportsSituationText(game: Game): String {
    if (game.status != GameStatus.LIVE) return ""
    val s = game.situation ?: return ""
    return if (game.league == League.MLB) {
        buildList {
            if (s.balls != null && s.strikes != null) add("${s.balls}-${s.strikes} count")
            s.outs?.let { add("$it ${if (it == 1) "out" else "outs"}") }
            val bases = listOfNotNull(if (s.onFirst == true) "1st" else null, if (s.onSecond == true) "2nd" else null, if (s.onThird == true) "3rd" else null)
            if (bases.isNotEmpty()) add("On ${bases.joinToString(", ")}")
        }.joinToString(" • ")
    } else {
        val possessing = listOf(game.away, game.home).firstOrNull { it.id != null && it.id == s.possessionTeamId }
        listOfNotNull(s.downDistance.compact(), possessing?.let { "${it.abbreviation} ball" }).joinToString(" • ")
    }
}
