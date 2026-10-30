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
    private fun TestScope.controller(settings: AppSettings = AppSettings(slideshowIntervalSeconds=10)): SlideshowController =
        SlideshowController(backgroundScope) { testScheduler.currentTime }.also {
            it.configure(settings);it.setForeground(true);runCurrent()
        }
    @Test fun `default playback gives all three dashboards fifteen seconds over repeated cycles`() = runTest {
        val c=controller(AppSettings())
        repeat(9) { index ->
            val expected=DashboardSlide.entries[index%3]
            assertEquals(expected,c.state.value.slide)
            advanceTimeBy(14_999);runCurrent();assertEquals(expected,c.state.value.slide)
            advanceTimeBy(1);runCurrent();assertEquals(expected.next(),c.state.value.slide)
        }
    }
    @Test fun `starts with weather then switches every ten seconds`() = runTest {
        val controller=controller()
        assertEquals(DashboardSlide.WEATHER,controller.state.value.slide)
        advanceTimeBy(9_999);runCurrent()
        assertEquals(DashboardSlide.WEATHER,controller.state.value.slide)
        advanceTimeBy(1);runCurrent()
        assertEquals(DashboardSlide.SPORTS,controller.state.value.slide)
        advanceTimeBy(10_000);runCurrent()
        assertEquals(DashboardSlide.STOCKS,controller.state.value.slide)
        advanceTimeBy(10_000);runCurrent()
        assertEquals(DashboardSlide.WEATHER,controller.state.value.slide)
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
        advanceTimeBy(7_000);c.configure(AppSettings(slideshowIntervalSeconds=10,slideshowPaused=true));runCurrent()
        advanceTimeBy(60_000);runCurrent();assertEquals(DashboardSlide.WEATHER,c.state.value.slide)
        c.configure(AppSettings(slideshowIntervalSeconds=10));runCurrent()
        advanceTimeBy(3_000);runCurrent();assertEquals(DashboardSlide.SPORTS,c.state.value.slide)
    }
    @Test fun `manual selection resets dwell time without disabling automatic playback`() = runTest {
        val c=controller()
        advanceTimeBy(9_000);c.select(DashboardSlide.SPORTS);runCurrent()
        assertEquals(DashboardSlide.SPORTS,c.state.value.slide);assertTrue(c.state.value.playing)
        advanceTimeBy(9_999);runCurrent();assertEquals(DashboardSlide.SPORTS,c.state.value.slide)
        advanceTimeBy(1);runCurrent();assertEquals(DashboardSlide.STOCKS,c.state.value.slide)
    }
    @Test fun `manual selection works while paused`() = runTest {
        val c=controller(AppSettings(slideshowIntervalSeconds=10,slideshowPaused=true))
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
        val c=controller(AppSettings(slideshowIntervalSeconds=10,startSlide=DashboardSlide.SPORTS))
        assertEquals(DashboardSlide.SPORTS,c.state.value.slide)
        advanceTimeBy(5_000);c.configure(AppSettings(slideshowIntervalSeconds=10,startSlide=DashboardSlide.WEATHER,celsius=true));runCurrent()
        assertEquals(DashboardSlide.SPORTS,c.state.value.slide)
        advanceTimeBy(5_000);runCurrent();assertEquals(DashboardSlide.STOCKS,c.state.value.slide)
    }
    @Test fun `repeated foreground events do not create duplicate timers`() = runTest {
        val c=controller()
        repeat(12) { c.setForeground(true) };runCurrent()
        advanceTimeBy(10_000);runCurrent();assertEquals(DashboardSlide.SPORTS,c.state.value.slide)
        advanceTimeBy(10_000);runCurrent();assertEquals(DashboardSlide.STOCKS,c.state.value.slide)
    }
    @Test fun `timer waits for persisted settings to load`() = runTest {
        val c=SlideshowController(backgroundScope) { testScheduler.currentTime }
        c.setForeground(true);runCurrent();advanceTimeBy(30_000);runCurrent()
        assertFalse(c.state.value.playing)
        c.configure(AppSettings(slideshowIntervalSeconds=10,startSlide=DashboardSlide.SPORTS));runCurrent()
        assertEquals(DashboardSlide.SPORTS,c.state.value.slide)
        advanceTimeBy(10_000);runCurrent();assertEquals(DashboardSlide.STOCKS,c.state.value.slide)
    }
    @Test fun `ticker always shows the other dashboard content`() {
        assertEquals(TickerContent.SPORTS,tickerContent(DashboardSlide.WEATHER))
        assertEquals(TickerContent.WEATHER,tickerContent(DashboardSlide.SPORTS))
        assertEquals(TickerContent.STOCKS,tickerContent(DashboardSlide.STOCKS))
    }
    @Test fun `group switches and membership edits never reset the slideshow deadline`() = runTest {
        val c=controller()
        advanceTimeBy(4_000)
        c.configure(AppSettings(slideshowIntervalSeconds=10,activeSportsGroup=SportsGroup.B));runCurrent()
        advanceTimeBy(5_999);runCurrent();assertEquals(DashboardSlide.WEATHER,c.state.value.slide)
        advanceTimeBy(1);runCurrent();assertEquals(DashboardSlide.SPORTS,c.state.value.slide)
        advanceTimeBy(3_000)
        c.configure(AppSettings(slideshowIntervalSeconds=10,sportsGroupA=setOf(League.NHL),tickerAllLeagues=true));runCurrent()
        advanceTimeBy(6_999);runCurrent();assertEquals(DashboardSlide.SPORTS,c.state.value.slide)
        advanceTimeBy(1);runCurrent();assertEquals(DashboardSlide.STOCKS,c.state.value.slide)
    }
    @Test fun `configured ordered list repeats and disabled screens are skipped`() = runTest {
        val screens=listOf(DashboardSlide.STOCKS,DashboardSlide.WEATHER)
        val c=controller(AppSettings(slideshowIntervalSeconds=10,enabledScreens=screens,startSlide=DashboardSlide.STOCKS))
        repeat(4) {
            advanceTimeBy(9_999);runCurrent();assertEquals(screens[it%2],c.state.value.slide)
            advanceTimeBy(1);runCurrent();assertEquals(screens[(it+1)%2],c.state.value.slide)
        }
        c.select(DashboardSlide.SPORTS);runCurrent();assertEquals(DashboardSlide.SPORTS,c.state.value.slide)
        advanceTimeBy(10_000);runCurrent();assertEquals(DashboardSlide.STOCKS,c.state.value.slide)
    }
    @Test fun `disabling current screen selects first enabled and empty slideshow stops`() = runTest {
        val c=controller()
        c.configure(AppSettings(slideshowIntervalSeconds=10,enabledScreens=listOf(DashboardSlide.STOCKS)));runCurrent()
        assertEquals(DashboardSlide.STOCKS,c.state.value.slide);assertFalse(c.state.value.playing)
        advanceTimeBy(60_000);runCurrent();assertEquals(DashboardSlide.STOCKS,c.state.value.slide)
        c.configure(AppSettings(slideshowIntervalSeconds=10,enabledScreens=emptyList()));runCurrent();assertFalse(c.state.value.playing)
        c.configure(AppSettings(slideshowIntervalSeconds=10));runCurrent();advanceTimeBy(10_000);runCurrent()
        assertEquals(DashboardSlide.WEATHER,c.state.value.slide)
    }
    @Test fun `each individual screen can be disabled`() = runTest {
        DashboardSlide.entries.forEach { disabled ->
            val screens=DashboardSlide.entries.filter {it!=disabled}
            val c=controller(AppSettings(slideshowIntervalSeconds=10,enabledScreens=screens))
            repeat(6) {assertTrue(c.state.value.slide in screens);advanceTimeBy(10_000);runCurrent()}
        }
    }
    @Test fun `ten fifteen and twenty second settings drive full three screen cycles`() = runTest {
        for(seconds in listOf(10,15,20)) {
            val c=controller(AppSettings(slideshowIntervalSeconds=seconds))
            val screens=DashboardSlide.entries
            repeat(6) { index ->
                advanceTimeBy(seconds*1_000L-1);runCurrent();assertEquals(screens[index%3],c.state.value.slide)
                advanceTimeBy(1);runCurrent();assertEquals(screens[(index+1)%3],c.state.value.slide)
            }
        }
    }
    @Test fun `changing between ten fifteen and twenty starts a full new interval`() = runTest {
        val c=controller()
        for(seconds in listOf(15,20,10,20,15,10)) {
            advanceTimeBy(3_000)
            c.configure(AppSettings(slideshowIntervalSeconds=seconds));runCurrent()
            val current=c.state.value.slide
            advanceTimeBy(seconds*1_000L-1);runCurrent();assertEquals(current,c.state.value.slide)
            advanceTimeBy(1);runCurrent();assertEquals(current.next(),c.state.value.slide)
        }
    }
    @Test fun `every tab selects its own screen immediately during playing and paused states`() = runTest {
        for(paused in listOf(false,true)) {
            val c=controller(AppSettings(slideshowPaused=paused,slideshowIntervalSeconds=20))
            for(slide in listOf(DashboardSlide.STOCKS,DashboardSlide.WEATHER,DashboardSlide.SPORTS,DashboardSlide.STOCKS,DashboardSlide.SPORTS,DashboardSlide.WEATHER)) {
                c.select(slide);runCurrent();assertEquals(slide,c.state.value.slide)
                advanceTimeBy(19_999);runCurrent();assertEquals(slide,c.state.value.slide)
            }
        }
    }
    @Test fun `tabs work when every automatic screen is disabled`() = runTest {
        val c=controller(AppSettings(slideshowIntervalSeconds=10,enabledScreens=emptyList()))
        assertFalse(c.state.value.showDashboard)
        for(slide in DashboardSlide.entries) {
            c.select(slide);runCurrent();assertEquals(slide,c.state.value.slide);assertTrue(c.state.value.showDashboard)
            advanceTimeBy(60_000);runCurrent();assertEquals(slide,c.state.value.slide);assertFalse(c.state.value.playing)
        }
    }
    @Test fun `manual navigation with one automatic screen returns after a full interval`() = runTest {
        val c=controller(AppSettings(enabledScreens=listOf(DashboardSlide.WEATHER),slideshowIntervalSeconds=15))
        c.select(DashboardSlide.STOCKS);runCurrent();assertEquals(DashboardSlide.STOCKS,c.state.value.slide)
        advanceTimeBy(14_999);runCurrent();assertEquals(DashboardSlide.STOCKS,c.state.value.slide)
        advanceTimeBy(1);runCurrent();assertEquals(DashboardSlide.WEATHER,c.state.value.slide);assertFalse(c.state.value.playing)
    }
    @Test fun `tab input before settings load survives startup configuration`() = runTest {
        val c=SlideshowController(backgroundScope) {testScheduler.currentTime}
        c.select(DashboardSlide.STOCKS);runCurrent()
        c.configure(AppSettings(slideshowIntervalSeconds=10));c.setForeground(true);runCurrent()
        assertEquals(DashboardSlide.STOCKS,c.state.value.slide)
        advanceTimeBy(10_000);runCurrent();assertEquals(DashboardSlide.WEATHER,c.state.value.slide)
    }
    @Test fun `tab presses at the automatic deadline are not swallowed`() = runTest {
        val c=controller()
        repeat(6) {
            advanceTimeBy(10_000);c.select(DashboardSlide.STOCKS);runCurrent()
            assertEquals(DashboardSlide.STOCKS,c.state.value.slide)
            advanceTimeBy(9_999);runCurrent();assertEquals(DashboardSlide.STOCKS,c.state.value.slide)
            c.select(DashboardSlide.WEATHER);runCurrent()
        }
    }
}
