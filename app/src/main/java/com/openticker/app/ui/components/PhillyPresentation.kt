package com.openticker.app.ui.components

import com.openticker.app.data.model.Game
import com.openticker.app.data.model.League
import com.openticker.app.settings.*
import com.openticker.app.util.sortedGames
import java.time.ZoneId

val PhiladelphiaZone: ZoneId = ZoneId.of("America/New_York")

enum class PhillySportsTab(val label: String, val title: String, val leagues: List<League>) {
    FOOTBALL("NFL / College", "Football Scoreboard", listOf(League.NFL, League.NCAAF)),
    NHL("NHL", "NHL Scoreboard", listOf(League.NHL)),
    MLB("MLB", "MLB Scoreboard", listOf(League.MLB)),
    NBA("NBA", "NBA Scoreboard", listOf(League.NBA))
}

fun PhillySportsTab.enabledLeagues(settings: AppSettings): List<League> = leagues.filter { it in settings.leagues }

fun phillyTeamName(league: League): String? = when(league) {
    League.NFL -> "Eagles"
    League.NHL -> "Flyers"
    League.MLB -> "Phillies"
    League.NBA -> "76ers"
    League.NCAAF -> null
}

fun isPhiladelphiaGame(game: Game): Boolean = game.league != League.NCAAF &&
    listOf(game.away, game.home).any { it.abbreviation.equals("PHI",ignoreCase=true) || it.name.startsWith("Philadelphia ",ignoreCase=true) }

/** Keep every actual provider game; put hometown games first, without inventing a fixture. */
fun philadelphiaFirstGames(games: List<Game>): List<Game> = sortedGames(games).sortedBy { if(isPhiladelphiaGame(it)) 0 else 1 }

/** Fresh default groups use the Figma tabs; explicit custom group choices retain group browsing. */
fun usePhillyTabsInitially(settings: AppSettings): Boolean = settings.activeSportsGroup==SportsGroup.A &&
    settings.sportsGroupA==DEFAULT_GROUP_A && settings.sportsGroupB==DEFAULT_GROUP_B

fun nextPhillySportsTab(current: PhillySportsTab?, available: List<PhillySportsTab>): PhillySportsTab? =
    if(available.isEmpty()) null else available[(available.indexOf(current)+1).mod(available.size)]
