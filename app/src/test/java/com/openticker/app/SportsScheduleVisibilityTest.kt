package com.openticker.app

import com.openticker.app.data.model.*
import com.openticker.app.data.repository.parseReliableScoreboard
import com.openticker.app.util.*
import com.openticker.app.settings.AppSettings
import com.openticker.app.settings.polledLeagues
import com.openticker.app.slideshow.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import com.openticker.app.viewmodel.LeagueState
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class SportsScheduleVisibilityTest {
    private val now = Instant.parse("2026-11-08T18:00:00Z")
    private val empty = LeagueState(updated = now, scheduleDate = sportsScheduleDate(now))
    private fun game(status: GameStatus) = Game("1", League.NFL, Team("Away", "A"), Team("Home", "H"),
        null, null, status, now, null, null, "")

    @Test fun successfulEmptyTodayIsHidden() {
        assertTrue(shouldHideSportToday(empty, true, now))
    }
    @Test fun everyNonemptyGameStatusStaysVisible() {
        GameStatus.entries.forEach { status ->
            assertFalse(shouldHideSportToday(empty.copy(games = listOf(game(status))), true, now))
        }
    }
    @Test fun missingLoadingErrorAndRestoredStayVisible() {
        listOf(null, LeagueState(), empty.copy(updated = null), empty.copy(scheduleDate = null),
            empty.copy(loading = true), empty.copy(error = "Unavailable"), empty.copy(restored = true)).forEach {
            assertFalse(shouldHideSportToday(it, true, now))
        }
    }
    @Test fun offlineStaysVisible() {
        assertFalse(shouldHideSportToday(empty, false, now))
    }
    @Test fun freshnessIncludesLongestRefreshIntervalButNotOverdueOrFuture() {
        assertTrue(shouldHideSportToday(empty.copy(updated = now.minusSeconds(300)), true, now))
        assertFalse(shouldHideSportToday(empty.copy(updated = now.minusSeconds(300).minusNanos(1)), true, now))
        assertFalse(shouldHideSportToday(empty.copy(updated = now.plusNanos(1)), true, now))
    }
    @Test fun easternMidnightInvalidatesEmptyScheduleImmediately() {
        val before = Instant.parse("2026-11-09T04:59:59Z")
        val after = before.plusSeconds(1)
        val yesterday = empty.copy(updated = before, scheduleDate = sportsScheduleDate(before))
        assertTrue(shouldHideSportToday(yesterday, true, before))
        assertFalse(shouldHideSportToday(yesterday, true, after))
        assertTrue(isSportsScheduleRefreshDue(yesterday, SportsScheduleAttempt(sportsScheduleDate(before), before), after, 300))
    }
    @Test fun responseCompletingAfterMidnightKeepsOriginalRequestDate() {
        val request = Instant.parse("2026-11-09T04:59:59Z")
        val completed = request.plusSeconds(3)
        val response = empty.copy(updated = completed, scheduleDate = sportsScheduleDate(request))
        assertFalse(shouldHideSportToday(response, true, completed))
        assertTrue(isSportsScheduleRefreshDue(response, SportsScheduleAttempt(sportsScheduleDate(request), request), completed, 300))
    }
    @Test fun daylightSavingUsesEasternDateNotUtcDate() {
        val summer = Instant.parse("2026-07-02T03:59:59Z")
        assertEquals("2026-07-01", sportsScheduleDate(summer).toString())
        assertEquals("2026-07-02", sportsScheduleDate(summer.plusSeconds(1)).toString())
        assertFalse(shouldHideSportToday(empty.copy(scheduleDate = sportsScheduleDate(now).plusDays(1)), true, now))
    }
    @Test fun hiddenLeaguesContinuePollingAndReappearWithGames() {
        val attempt = SportsScheduleAttempt(sportsScheduleDate(now), now)
        assertFalse(isSportsScheduleRefreshDue(empty, attempt, now.plusSeconds(299), 300))
        assertTrue(isSportsScheduleRefreshDue(empty, attempt, now.plusSeconds(300), 300))
        val refreshed = empty.copy(games = listOf(game(GameStatus.UPCOMING)), updated = now.plusSeconds(300))
        assertFalse(shouldHideSportToday(refreshed, true, now.plusSeconds(300)))
        assertTrue(shouldHideSportToday(refreshed.copy(games = emptyList()), true, now.plusSeconds(300)))
    }
    @Test fun restoredUnknownAndFutureTimestampsDoNotSuppressRefresh() {
        assertTrue(isSportsScheduleRefreshDue(empty.copy(restored = true), null, now, 300))
        assertTrue(isSportsScheduleRefreshDue(empty.copy(scheduleDate = null), null, now, 300))
        assertTrue(isSportsScheduleRefreshDue(empty.copy(updated = now.plusSeconds(1)),
            SportsScheduleAttempt(sportsScheduleDate(now), now.plusSeconds(1)), now, 300))
    }
    @Test fun currentDayFailuresStillRespectRetryBackoff() {
        val failed = empty.copy(error = "Unavailable", scheduleDate = sportsScheduleDate(now).minusDays(1))
        val attempt = SportsScheduleAttempt(sportsScheduleDate(now), now)
        assertFalse(isSportsScheduleRefreshDue(failed, attempt, now.plusSeconds(29), 30))
        assertTrue(isSportsScheduleRefreshDue(failed, attempt, now.plusSeconds(30), 30))
    }
    @Test fun genuineEmptyEventsAreAccepted() {
        assertTrue(parseReliableScoreboard("""{"events":[]}""", League.NFL).isEmpty())
    }
    @Test(expected = IllegalArgumentException::class) fun unreadableEventsDoNotBecomeEmptySchedule() {
        parseReliableScoreboard("""{"events":[{"id":"broken"}]}""", League.NFL)
    }
    @Test(expected = IllegalArgumentException::class) fun missingEventsDoNotBecomeEmptySchedule() {
        parseReliableScoreboard("""{}""", League.NFL)
    }
    @Test fun playbackOmitsOnlySportsWithoutMutatingPreferencesOrPolling() {
        val settings = AppSettings(leagues = setOf(League.NFL, League.NHL),
            enabledScreens = listOf(DashboardSlide.STOCKS, DashboardSlide.SPORTS, DashboardSlide.WEATHER))
        val feeds = settings.leagues.associateWith { empty }
        val effective = sportsPlaybackSettings(settings, feeds, true, now)
        assertEquals(listOf(DashboardSlide.STOCKS, DashboardSlide.WEATHER), effective.enabledScreens)
        assertEquals(settings, effective.copy(enabledScreens = settings.enabledScreens))
        assertTrue(DashboardSlide.SPORTS in settings.enabledScreens)
        assertEquals(settings.polledLeagues(), effective.polledLeagues())
    }
    @Test fun unknownOfflineEmptySelectionAndNonemptyKeepSportsPlayback() {
        val settings = AppSettings(leagues = setOf(League.NFL, League.NHL))
        assertEquals(settings, sportsPlaybackSettings(settings, mapOf(League.NFL to empty), true, now))
        assertEquals(settings, sportsPlaybackSettings(settings, settings.leagues.associateWith { empty }, false, now))
        assertEquals(settings, sportsPlaybackSettings(settings, mapOf(League.NFL to empty,
            League.NHL to empty.copy(games = listOf(game(GameStatus.FINAL)))), true, now))
        val noLeagues = settings.copy(leagues = emptySet())
        assertEquals(noLeagues, sportsPlaybackSettings(noLeagues, emptyMap(), true, now))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun manualSportsSelectionCannotResurrectAnEmptyDay() = runTest {
        val settings=AppSettings(leagues=setOf(League.NFL),enabledScreens=listOf(DashboardSlide.SPORTS),startSlide=DashboardSlide.SPORTS)
        val scores=mapOf(League.NFL to empty)
        val controller=SlideshowController(backgroundScope) { testScheduler.currentTime }
        controller.configure(settings);controller.setForeground(true);runCurrent()
        controller.select(DashboardSlide.SPORTS);runCurrent()
        controller.configure(sportsPlaybackSettings(settings,scores,true,now));runCurrent()
        assertTrue(controller.state.value.showDashboard) // Existing explicit-manual semantics are retained.
        assertFalse(shouldShowScheduleAwareDashboard(controller.state.value,settings,scores,true,now))
        assertTrue(shouldShowScheduleAwareDashboard(controller.state.value,settings,emptyMap(),true,now))
        controller.select(DashboardSlide.WEATHER);runCurrent()
        assertTrue(shouldShowScheduleAwareDashboard(controller.state.value,settings,scores,true,now))
    }
}
