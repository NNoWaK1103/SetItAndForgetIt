package com.openticker.app.util

import com.openticker.app.viewmodel.LeagueState
import com.openticker.app.data.model.League
import com.openticker.app.settings.AppSettings
import com.openticker.app.slideshow.DashboardSlide
import com.openticker.app.slideshow.SlideshowState
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val scheduleZone = ZoneId.of("America/New_York")
private val emptyScheduleFreshness = Duration.ofSeconds(300)

fun sportsScheduleDate(now: Instant): LocalDate = now.atZone(scheduleZone).toLocalDate()

/** Hide only an explicitly confirmed, current-day empty schedule, never an unknown feed. */
fun shouldHideSportToday(feed: LeagueState?, online: Boolean, now: Instant): Boolean {
    if (!online || feed == null || feed.loading || feed.restored || feed.error != null || feed.games.isNotEmpty()) return false
    if (feed.scheduleDate != sportsScheduleDate(now)) return false
    val updated = feed.updated ?: return false
    val age = Duration.between(updated, now)
    return !age.isNegative && age <= emptyScheduleFreshness
}

internal data class SportsScheduleAttempt(val date: LocalDate, val time: Instant)

/** Date-scoped backoff keeps hidden leagues polling and never reuses yesterday at midnight. */
internal fun isSportsScheduleRefreshDue(
    feed: LeagueState?, attempt: SportsScheduleAttempt?, now: Instant, intervalSeconds: Int
): Boolean {
    val today = sportsScheduleDate(now)
    fun recent(time: Instant): Boolean {
        val age = Duration.between(time, now)
        return !age.isNegative && age < Duration.ofSeconds(intervalSeconds.toLong())
    }
    if (attempt?.date == today && recent(attempt.time)) return false
    if (feed?.scheduleDate == today && !feed.restored && feed.updated?.let(::recent) == true) return false
    return true
}

/** A playback-only copy: never changes persisted preferences or the polling league set. */
fun sportsPlaybackSettings(
    settings: AppSettings, scores: Map<League, LeagueState>, online: Boolean, now: Instant
): AppSettings {
    val leagues = settings.leagues
    return if (leagues.isNotEmpty() && leagues.all { shouldHideSportToday(scores[it], online, now) }) {
        settings.copy(enabledScreens = settings.enabledScreens.filterNot { it == DashboardSlide.SPORTS })
    } else settings
}

/** Manual navigation must not resurrect a sport once every enabled schedule is confirmed empty. */
fun shouldShowScheduleAwareDashboard(playback: SlideshowState, settings: AppSettings,
    scores: Map<League, LeagueState>, online: Boolean, now: Instant): Boolean =
    playback.showDashboard && !(playback.slide==DashboardSlide.SPORTS && settings.leagues.isNotEmpty() &&
        settings.leagues.all { shouldHideSportToday(scores[it],online,now) })
