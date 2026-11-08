package com.openticker.app

import com.openticker.app.data.model.League
import com.openticker.app.settings.*
import org.junit.Assert.*
import org.junit.Test

class SportsBrowsingTest {
    @Test fun `all games pages precede next nonempty group`() {
        val settings=AppSettings(sportsGroupA=setOf(League.NFL),sportsGroupB=setOf(League.NBA))
        val counts=mapOf(League.NFL to 5,League.NBA to 1)
        assertEquals(3,sportsPageCount(counts,settings.visibleGroupLeagues(),2))
        var page=SportsBrowsePage(SportsGroup.A,0)
        page=nextSportsPage(page,settings,counts,2); assertEquals(SportsBrowsePage(SportsGroup.A,1),page)
        page=nextSportsPage(page,settings,counts,2); assertEquals(SportsBrowsePage(SportsGroup.A,2),page)
        page=nextSportsPage(page,settings,counts,2); assertEquals(SportsBrowsePage(SportsGroup.B,0),page)
        assertEquals(SportsBrowsePage(SportsGroup.A,0),nextSportsPage(page,settings,counts,2))
    }
    @Test fun `empty and duplicate groups are skipped without modifying settings`() {
        val s=AppSettings(sportsGroupA=setOf(League.NFL),sportsGroupB=setOf(League.NFL))
        assertEquals(SportsBrowsePage(SportsGroup.A,0),nextSportsPage(SportsBrowsePage(SportsGroup.A,0),s,emptyMap(),1))
        val empty=s.copy(leagues=emptySet())
        assertEquals(SportsBrowsePage(empty.activeSportsGroup,0),nextSportsPage(SportsBrowsePage(SportsGroup.B,8),empty,emptyMap(),1))
        assertEquals(SportsGroup.A,s.activeSportsGroup)
    }
    @Test fun `custom five league groups cycle column sets and game pages`() {
        val s=AppSettings(sportsGroupA=League.entries.toSet(),sportsGroupB=emptySet())
        val counts=mapOf(League.NFL to 3)
        assertEquals(4,sportsPageCount(counts,s.visibleGroupLeagues(),2))
        assertEquals(SportsBrowsePage(SportsGroup.A,0),nextSportsPage(SportsBrowsePage(SportsGroup.A,3),s,counts,2))
    }
    @Test fun `short leagues wrap and empty or shrunk feeds remain safe`() {
        assertTrue(sportsPageIndices(0,2,2).isEmpty())
        assertEquals(0..1,sportsPageIndices(2,7,2))
        assertEquals(4..4,sportsPageIndices(5,2,2))
        assertEquals(0..0,sportsPageIndices(1,99,0))
    }
    @Test fun `short visible visits preserve countdown across holds and pages`() {
        for(visitSeconds in listOf(5,10,15)) {
            val s=AppSettings(sportsGroupA=setOf(League.NFL),sportsGroupB=setOf(League.NBA))
            val counts=mapOf(League.NFL to 4,League.NBA to 1)
            var clock=SportsBrowseClock(SportsBrowsePage(SportsGroup.A,0))
            val seen=mutableSetOf(clock.position)
            repeat(12) {
                repeat(visitSeconds) {
                    clock=advanceSportsBrowse(clock,1_000,s,counts,1)
                    seen+=clock.position
                }
                val paused=advanceSportsBrowse(clock,60_000,s,counts,1,paused=true)
                assertEquals(clock,paused) // Another screen, background, settings or details.
            }
            assertTrue(seen.containsAll((0..3).map { SportsBrowsePage(SportsGroup.A,it) }+SportsBrowsePage(SportsGroup.B,0)))
        }
    }

}
