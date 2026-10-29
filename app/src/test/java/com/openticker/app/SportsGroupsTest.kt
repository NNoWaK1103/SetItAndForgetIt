package com.openticker.app

import com.openticker.app.data.model.*
import com.openticker.app.settings.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class SportsGroupsTest {
    private fun game(league: League,status: GameStatus=GameStatus.LIVE,id: String=league.name,start: String="2026-10-29T20:00:00Z") =
        Game(id,league,Team("Away","AWY"),Team("Home","HME"),1,2,status,Instant.parse(start),1,"2:31","1st 2:31")

    @Test fun `defaults show NFL NCAAF NHL with NBA MLB retained in second group`() {
        val s=AppSettings()
        assertEquals(SportsGroup.A,s.activeSportsGroup)
        assertEquals(listOf(League.NFL,League.NCAAF,League.NHL),s.visibleGroupLeagues())
        assertEquals(listOf(League.NBA,League.MLB),s.visibleGroupLeagues(SportsGroup.B))
        assertEquals("NFL / NCAAF / NHL",s.groupLabel(SportsGroup.A))
        assertEquals("NBA / MLB",s.groupLabel(SportsGroup.B))
        assertEquals(League.entries.toSet(),s.polledLeagues())
    }
    @Test fun `active group controls ticker while all five option overrides group and disabled feeds`() {
        val games=League.entries.map { game(it) }
        assertEquals(setOf(League.NFL,League.NCAAF,League.NHL),sportsTickerGames(games,AppSettings()).map { it.league }.toSet())
        val b=AppSettings(activeSportsGroup=SportsGroup.B)
        assertEquals(setOf(League.NBA,League.MLB),sportsTickerGames(games,b).map { it.league }.toSet())
        val all=b.copy(leagues=setOf(League.NBA),tickerAllLeagues=true)
        assertEquals(League.entries.toSet(),sportsTickerGames(games,all).map { it.league }.toSet())
        assertEquals(League.entries.toSet(),all.polledLeagues())
    }
    @Test fun `switching groups changes neither polled leagues nor refresh interval`() {
        val a=AppSettings(sportsRefreshSeconds=60)
        val b=a.copy(activeSportsGroup=SportsGroup.B,sportsGroupB=setOf(League.NBA,League.NHL))
        assertEquals(a.polledLeagues(),b.polledLeagues())
        assertEquals(a.sportsRefreshSeconds,b.sportsRefreshSeconds)
        assertEquals(a.polledLeagues(),a.copy(sportsGroupA=emptySet()).polledLeagues())
    }
    @Test fun `custom membership uses stable columns and honors disabled feeds`() {
        val s=AppSettings(sportsGroupA=setOf(League.NHL,League.NBA,League.NFL),leagues=setOf(League.NBA,League.NHL))
        assertEquals(listOf(League.NBA,League.NHL),s.visibleGroupLeagues())
        assertEquals("NBA / NHL",s.groupLabel(SportsGroup.A))
        assertEquals(setOf(League.NBA,League.NHL),s.sportsTickerLeagues())
    }
    @Test fun `empty group never silently falls back to a different set of leagues`() {
        val s=AppSettings(sportsGroupA=emptySet())
        assertTrue(s.visibleGroupLeagues().isEmpty())
        assertTrue(sportsTickerGames(League.entries.map { game(it) },s).isEmpty())
        assertEquals("Group A (empty)",s.groupLabel(SportsGroup.A))
        assertEquals(listOf(League.NBA,League.MLB),s.visibleGroupLeagues(SportsGroup.B))
    }
    @Test fun `ticker prioritizes live then upcoming then newest finals`() {
        val live=game(League.NFL)
        val upcoming=game(League.NCAAF,GameStatus.UPCOMING)
        val recent=game(League.NHL,GameStatus.FINAL,"recent","2026-10-29T19:00:00Z")
        val older=game(League.NHL,GameStatus.FINAL,"older","2026-10-29T16:00:00Z")
        val list=sportsTickerGames(listOf(older,recent,game(League.NBA),upcoming,live),AppSettings())
        assertEquals(listOf(live,upcoming,recent,older),list)
    }
    @Test fun `all five prefetch stops when its ticker is disabled`() {
        val s=AppSettings(leagues=setOf(League.NFL),tickerAllLeagues=true,tickerEnabled=false)
        assertEquals(setOf(League.NFL),s.polledLeagues())
    }
    @Test fun `custom groups can overlap or include all five leagues`() {
        val s=AppSettings(sportsGroupA=League.entries.toSet(),sportsGroupB=setOf(League.NHL))
        assertEquals(5,s.visibleGroupLeagues().size)
        assertEquals(listOf(League.NHL),s.copy(activeSportsGroup=SportsGroup.B).visibleGroupLeagues())
    }
}
