package com.openticker.app

import com.openticker.app.data.model.*
import com.openticker.app.settings.*
import com.openticker.app.ui.components.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class PhillyPresentationTest {
    private fun game(id:String,away:String,home:String,league:League=League.NFL)=Game(
        id,league,Team(away,away),Team(home,home),null,null,GameStatus.UPCOMING,
        Instant.parse("2026-11-08T18:00:00Z"),null,null,"")
    @Test fun `football tab pairs NFL and college and honors enabled leagues`() {
        assertEquals(listOf(League.NFL,League.NCAAF),PhillySportsTab.FOOTBALL.enabledLeagues(AppSettings()))
        assertEquals(listOf(League.NFL),PhillySportsTab.FOOTBALL.enabledLeagues(AppSettings(leagues=setOf(League.NFL))))
        assertTrue(PhillySportsTab.NHL.enabledLeagues(AppSettings(leagues=setOf(League.NFL))).isEmpty())
    }
    @Test fun `Philadelphia emphasis reorders actual games without dropping or adding any`() {
        val other=game("other","DAL","NYG");val philly=game("philly","PHI","WAS")
        val ordered=philadelphiaFirstGames(listOf(other,philly))
        assertEquals(listOf(philly,other),ordered)
        assertEquals(setOf("other","philly"),ordered.map { it.id }.toSet())
        assertTrue(isPhiladelphiaGame(philly));assertFalse(isPhiladelphiaGame(other))
    }
    @Test fun `Philly team labels are facts not invented college affiliations`() {
        assertEquals("Eagles",phillyTeamName(League.NFL));assertEquals("Flyers",phillyTeamName(League.NHL))
        assertEquals("Phillies",phillyTeamName(League.MLB));assertEquals("76ers",phillyTeamName(League.NBA))
        assertNull(phillyTeamName(League.NCAAF))
    }
    @Test fun `Philadelphia clock uses Eastern date rather than device UTC`() {
        assertEquals(LocalDate.of(2026,11,7),Instant.parse("2026-11-08T02:00:00Z").atZone(PhiladelphiaZone).toLocalDate())
    }
    @Test fun `saved custom sports groups keep their browsing mode`() {
        assertTrue(usePhillyTabsInitially(AppSettings()))
        assertFalse(usePhillyTabsInitially(AppSettings(activeSportsGroup=SportsGroup.B)))
        assertFalse(usePhillyTabsInitially(AppSettings(sportsGroupA=setOf(League.NHL))))
    }
    @Test fun `tab browsing cycles available sports and skips absent ones`() {
        val tabs=listOf(PhillySportsTab.FOOTBALL,PhillySportsTab.NBA)
        assertEquals(PhillySportsTab.NBA,nextPhillySportsTab(PhillySportsTab.FOOTBALL,tabs))
        assertEquals(PhillySportsTab.FOOTBALL,nextPhillySportsTab(PhillySportsTab.NBA,tabs))
        assertNull(nextPhillySportsTab(PhillySportsTab.FOOTBALL,emptyList()))
    }
}
