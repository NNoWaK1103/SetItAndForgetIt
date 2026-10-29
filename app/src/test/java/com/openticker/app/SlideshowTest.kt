package com.openticker.app

import com.openticker.app.settings.AppSettings
import com.openticker.app.settings.SportsGroup
import com.openticker.app.data.model.League
import com.openticker.app.slideshow.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SlideshowTest {
    private fun TestScope.controller(settings: AppSettings = AppSettings()): SlideshowController =
        SlideshowController(backgroundScope) { testScheduler.currentTime }.also {
            it.configure(settings);it.setForeground(true);runCurrent()
        }
    @Test fun `starts with weather then switches every ten seconds`() = runTest {
        val controller=controller()
        assertEquals(DashboardSlide.WEATHER,controller.state.value.slide)
        advanceTimeBy(9_999);runCurrent()
        assertEquals(DashboardSlide.WEATHER,controller.state.value.slide)
        advanceTimeBy(1);runCurrent()
        assertEquals(DashboardSlide.SPORTS,controller.state.value.slide)
        advanceTimeBy(10_000);runCurrent()
        assertEquals(DashboardSlide.WEATHER,controller.state.value.slide)
        advanceTimeBy(10_000);runCurrent()
        assertEquals(DashboardSlide.SPORTS,controller.state.value.slide)
    }
    @Test fun `background pauses remaining interval and resume continues it`() = runTest {
        val c=controller()
        advanceTimeBy(4_000);c.setForeground(false);runCurrent()
        assertFalse(c.state.value.playing)
        advanceTimeBy(120_000);runCurrent()
        assertEquals(DashboardSlide.WEATHER,c.state.value.slide)
        c.setForeground(true);runCurrent()
        advanceTimeBy(5_999);runCurrent();assertEquals(DashboardSlide.WEATHER,c.state.value.slide)
        advanceTimeBy(1);runCurrent();assertEquals(DashboardSlide.SPORTS,c.state.value.slide)
    }
    @Test fun `user pause and resume retain remaining interval`() = runTest {
        val c=controller()
        advanceTimeBy(7_000);c.configure(AppSettings(slideshowPaused=true));runCurrent()
        advanceTimeBy(60_000);runCurrent();assertEquals(DashboardSlide.WEATHER,c.state.value.slide)
        c.configure(AppSettings());runCurrent()
        advanceTimeBy(3_000);runCurrent();assertEquals(DashboardSlide.SPORTS,c.state.value.slide)
    }
    @Test fun `manual selection resets dwell time without disabling automatic playback`() = runTest {
        val c=controller()
        advanceTimeBy(9_000);c.select(DashboardSlide.SPORTS);runCurrent()
        assertEquals(DashboardSlide.SPORTS,c.state.value.slide);assertTrue(c.state.value.playing)
        advanceTimeBy(9_999);runCurrent();assertEquals(DashboardSlide.SPORTS,c.state.value.slide)
        advanceTimeBy(1);runCurrent();assertEquals(DashboardSlide.WEATHER,c.state.value.slide)
    }
    @Test fun `manual selection works while paused`() = runTest {
        val c=controller(AppSettings(slideshowPaused=true))
        c.select(DashboardSlide.SPORTS);runCurrent()
        advanceTimeBy(120_000);runCurrent()
        assertEquals(DashboardSlide.SPORTS,c.state.value.slide);assertFalse(c.state.value.playing)
    }
    @Test fun `configured interval replaces the current dwell time`() = runTest {
        val c=controller()
        advanceTimeBy(4_000);c.configure(AppSettings(slideshowIntervalSeconds=5));runCurrent()
        advanceTimeBy(4_999);runCurrent();assertEquals(DashboardSlide.WEATHER,c.state.value.slide)
        advanceTimeBy(1);runCurrent();assertEquals(DashboardSlide.SPORTS,c.state.value.slide)
    }
    @Test fun `start preference applies at startup without resetting active slides on other settings updates`() = runTest {
        val c=controller(AppSettings(startSlide=DashboardSlide.SPORTS))
        assertEquals(DashboardSlide.SPORTS,c.state.value.slide)
        advanceTimeBy(5_000);c.configure(AppSettings(startSlide=DashboardSlide.WEATHER,celsius=true));runCurrent()
        assertEquals(DashboardSlide.SPORTS,c.state.value.slide)
        advanceTimeBy(5_000);runCurrent();assertEquals(DashboardSlide.WEATHER,c.state.value.slide)
    }
    @Test fun `repeated foreground events do not create duplicate timers`() = runTest {
        val c=controller()
        repeat(12) { c.setForeground(true) };runCurrent()
        advanceTimeBy(10_000);runCurrent();assertEquals(DashboardSlide.SPORTS,c.state.value.slide)
        advanceTimeBy(10_000);runCurrent();assertEquals(DashboardSlide.WEATHER,c.state.value.slide)
    }
    @Test fun `timer waits for persisted settings to load`() = runTest {
        val c=SlideshowController(backgroundScope) { testScheduler.currentTime }
        c.setForeground(true);runCurrent();advanceTimeBy(30_000);runCurrent()
        assertFalse(c.state.value.playing)
        c.configure(AppSettings(startSlide=DashboardSlide.SPORTS));runCurrent()
        assertEquals(DashboardSlide.SPORTS,c.state.value.slide)
        advanceTimeBy(10_000);runCurrent();assertEquals(DashboardSlide.WEATHER,c.state.value.slide)
    }
    @Test fun `ticker always shows the other dashboard content`() {
        assertEquals(TickerContent.SPORTS,tickerContent(DashboardSlide.WEATHER))
        assertEquals(TickerContent.WEATHER,tickerContent(DashboardSlide.SPORTS))
    }
    @Test fun `group switches and membership edits never reset the slideshow deadline`() = runTest {
        val c=controller()
        advanceTimeBy(4_000)
        c.configure(AppSettings(activeSportsGroup=SportsGroup.B));runCurrent()
        advanceTimeBy(5_999);runCurrent();assertEquals(DashboardSlide.WEATHER,c.state.value.slide)
        advanceTimeBy(1);runCurrent();assertEquals(DashboardSlide.SPORTS,c.state.value.slide)
        advanceTimeBy(3_000)
        c.configure(AppSettings(sportsGroupA=setOf(League.NHL),tickerAllLeagues=true));runCurrent()
        advanceTimeBy(6_999);runCurrent();assertEquals(DashboardSlide.SPORTS,c.state.value.slide)
        advanceTimeBy(1);runCurrent();assertEquals(DashboardSlide.WEATHER,c.state.value.slide)
    }
}
