package com.openticker.app

import com.openticker.app.data.model.*
import com.openticker.app.data.repository.StockMarketUiState
import com.openticker.app.settings.*
import com.openticker.app.slideshow.DashboardSlide
import com.openticker.app.util.*
import com.openticker.app.viewmodel.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class FeedHealthTest {
    private val now=Instant.parse("2026-11-07T17:00:00Z")
    private val weather=Weather(60.0,null,null,null,null,null,null,true,"America/New_York",emptyList(),emptyList())
    private fun dashboard()=DashboardState(settings=AppSettings(stocks=StockSettings(watchlist=emptyList(),indices=emptySet())),
        online=true,weather=weather,weatherUpdated=now,
        scores=League.entries.associateWith { LeagueState(updated=now) })
    private fun market()=StockMarketUiState(configured=true,fetched=now,
        quotes=mapOf("HD" to StockQuote("HD",price=300.0,updated=now.minusSeconds(900),freshness=QuoteFreshness.DELAYED,delayMinutes=15)))
    @Test fun `receiving feed does not mislabel delayed quote as realtime or an error`() {
        val feeds=dashboardFeedHealth(dashboard(),market(),now)
        assertTrue(feeds.all { it.condition==FeedCondition.CURRENT })
        assertEquals("Feeds OK",feedHealthCaption(feeds))
        assertTrue(feeds.last().detail.contains("delayed"))
    }
    @Test fun `network online does not hide provider failures or unconfigured markets`() {
        val s=dashboard().copy(weatherError="Weather service returned HTTP 500")
        val feeds=dashboardFeedHealth(s,StockMarketUiState(),now)
        assertEquals(FeedCondition.ERROR,feeds.first().condition)
        assertEquals(FeedCondition.UNCONFIGURED,feeds.last().condition)
        assertEquals("2 feed notices",feedHealthCaption(feeds))
    }
    @Test fun `offline with no saved data is not described as retained content`() {
        val feeds=dashboardFeedHealth(DashboardState(),StockMarketUiState(configured=true,online=false),now)
        assertEquals(FeedCondition.OFFLINE,feeds.first().condition)
        assertTrue(feeds.first().detail.contains("No saved"))
        assertTrue(feeds.last().detail.contains("No saved"))
        assertTrue(feeds.drop(1).dropLast(1).all { it.updated==null })
    }
    @Test fun `old scoreboard date and restored snapshots remain notices`() {
        val s=dashboard().copy(weatherRestored=true,scores=dashboard().scores+
            (League.NFL to LeagueState(updated=now.minusSeconds(86_400))))
        val feeds=dashboardFeedHealth(s,market(),now)
        assertEquals(FeedCondition.SAVED,feeds.first().condition)
        assertEquals(FeedCondition.OVERDUE,feeds.first { it.name=="NFL" }.condition)
    }
    @Test fun `weather and sports overdue thresholds match dashboard warnings`() {
        val s=dashboard().copy(weatherUpdated=now.minusSeconds(899),scores=League.entries.associateWith { LeagueState(updated=now.minusSeconds(300)) })
        assertTrue(dashboardFeedHealth(s,market(),now).all { it.condition==FeedCondition.CURRENT })
        val later=dashboardFeedHealth(s,market(),now.plusSeconds(2))
        assertEquals(FeedCondition.OVERDUE,later.first().condition)
        assertEquals(FeedCondition.OVERDUE,later.first { it.name=="NFL" }.condition)
    }
    @Test fun `missing stale and failed chart data create partial market notice`() {
        val s=dashboard()
        assertEquals(FeedCondition.PARTIAL,dashboardFeedHealth(s,market().copy(staleSymbols=setOf("HD")),now).last().condition)
        assertEquals(FeedCondition.PARTIAL,dashboardFeedHealth(s,market().copy(historyErrors=mapOf(("HD" to ChartRange.DAY) to "Unavailable")),now).last().condition)
        assertEquals(FeedCondition.WAITING,dashboardFeedHealth(s,market().copy(quotes=emptyMap()),now).last().condition)
    }
    @Test fun `disabled market display does not demand setup and disabled sports are excluded`() {
        val s=dashboard().copy(settings=dashboard().settings.copy(leagues=setOf(League.NFL),
            enabledScreens=listOf(DashboardSlide.WEATHER,DashboardSlide.SPORTS),persistentMarketTicker=false))
        assertEquals(listOf("Weather","NFL"),dashboardFeedHealth(s,StockMarketUiState(),now).map { it.name })
        assertEquals("Markets",dashboardFeedHealth(s,StockMarketUiState(),now,DashboardSlide.STOCKS).last().name)
    }
    @Test fun `old closed market response eventually becomes overdue without altering quote labels`() {
        val m=market().copy(session=MarketSession.CLOSED,fetched=now.minusSeconds(1831))
        assertEquals(FeedCondition.OVERDUE,dashboardFeedHealth(dashboard(),m,now).last().condition)
        assertEquals(QuoteFreshness.DELAYED,m.quotes["HD"]?.freshness)
    }
    @Test fun `selected indices participate in missing and stale feed health`() {
        val s=dashboard().copy(settings=dashboard().settings.copy(stocks=dashboard().settings.stocks.copy(indices=setOf(MarketIndex.SP500))))
        assertEquals(FeedCondition.PARTIAL,dashboardFeedHealth(s,market(),now).last().condition)
        val withIndex=market().copy(quotes=market().quotes+("I:SPX" to StockQuote("I:SPX",price=5000.0,updated=now,freshness=QuoteFreshness.REALTIME)))
        assertEquals(FeedCondition.CURRENT,dashboardFeedHealth(s,withIndex,now).last().condition)
        assertEquals(FeedCondition.PARTIAL,dashboardFeedHealth(s,withIndex.copy(staleSymbols=setOf("I:SPX")),now).last().condition)
    }

}
