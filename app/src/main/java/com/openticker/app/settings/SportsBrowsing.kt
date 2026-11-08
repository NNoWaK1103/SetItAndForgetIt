package com.openticker.app.settings

import com.openticker.app.data.model.League

/** Automatic browsing is presentation-only: no preference writes or network requests. */
data class SportsBrowsePage(val group: SportsGroup, val page: Int)

fun sportsPageCount(gameCounts: Map<League, Int>, leagues: List<League>, pageSize: Int): Int =
    leagues.maxOfOrNull { ((gameCounts[it] ?: 0).coerceAtLeast(0) + pageSize.coerceAtLeast(1) - 1) / pageSize.coerceAtLeast(1) }
        ?.coerceAtLeast(1)?.times(((leagues.size + 2) / 3).coerceAtLeast(1)) ?: 1

fun nextSportsPage(current: SportsBrowsePage, settings: AppSettings, gameCounts: Map<League, Int>, pageSize: Int): SportsBrowsePage {
    val groups = SportsGroup.entries.filter { settings.visibleGroupLeagues(it).isNotEmpty() }
        .distinctBy { settings.visibleGroupLeagues(it).toSet() }
    if (groups.isEmpty()) return SportsBrowsePage(settings.activeSportsGroup, 0)
    val group = current.group.takeIf { it in groups } ?: groups.first()
    val count = sportsPageCount(gameCounts, settings.visibleGroupLeagues(group), pageSize)
    if (current.page + 1 < count) return SportsBrowsePage(group, current.page + 1)
    return SportsBrowsePage(groups[(groups.indexOf(group) + 1) % groups.size], 0)
}

fun sportsPageIndices(gameCount: Int, page: Int, pageSize: Int): IntRange {
    if (gameCount <= 0) return IntRange.EMPTY
    val size = pageSize.coerceAtLeast(1)
    val pages = (gameCount + size - 1) / size
    val start = Math.floorMod(page, pages) * size
    return start until minOf(start + size, gameCount)
}

/** Save the remaining visible time, not a new deadline on each slideshow visit. */
data class SportsBrowseClock(val position: SportsBrowsePage, val remainingMillis: Long = 10_000)
fun advanceSportsBrowse(clock: SportsBrowseClock, elapsedMillis: Long, settings: AppSettings,
    gameCounts: Map<League, Int>, pageSize: Int, paused: Boolean = false): SportsBrowseClock {
    if(paused || elapsedMillis <= 0) return clock
    val remaining=clock.remainingMillis.coerceIn(1,10_000)-elapsedMillis
    return if(remaining>0) clock.copy(remainingMillis=remaining)
    else SportsBrowseClock(nextSportsPage(clock.position,settings,gameCounts,pageSize))
}
