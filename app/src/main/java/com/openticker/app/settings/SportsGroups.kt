package com.openticker.app.settings

import com.openticker.app.data.model.Game
import com.openticker.app.data.model.League
import com.openticker.app.util.filterLeagues
import com.openticker.app.util.sortedGames

enum class SportsGroup { A, B }
val DEFAULT_GROUP_A = setOf(League.NFL, League.NCAAF, League.NHL)
val DEFAULT_GROUP_B = setOf(League.NBA, League.MLB)

fun AppSettings.groupMembers(group: SportsGroup): Set<League> = when(group) {
    SportsGroup.A -> sportsGroupA
    SportsGroup.B -> sportsGroupB
}
fun AppSettings.visibleGroupLeagues(group: SportsGroup = activeSportsGroup): List<League> =
    League.entries.filter { it in groupMembers(group) && it in leagues }

fun AppSettings.groupLabel(group: SportsGroup): String = visibleGroupLeagues(group)
    .joinToString(" / ") { it.name }.ifEmpty { "Group ${group.name} (empty)" }

fun AppSettings.sportsTickerLeagues(): Set<League> =
    if(tickerAllLeagues) League.entries.toSet() else visibleGroupLeagues().toSet()

/** Independent of the active group: switching columns must never restart polling. */
fun AppSettings.polledLeagues(): Set<League> =
    if(tickerEnabled && tickerAllLeagues) League.entries.toSet() else leagues

fun sportsTickerGames(games: List<Game>, settings: AppSettings): List<Game> =
    sortedGames(filterLeagues(games,settings.sportsTickerLeagues()))
