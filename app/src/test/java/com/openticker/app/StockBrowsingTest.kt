package com.openticker.app

import com.openticker.app.data.model.StockCategory
import com.openticker.app.settings.*
import org.junit.Assert.*
import org.junit.Test

class StockBrowsingTest {
    @Test fun allFiveHundredSymbolsAreReachableAtCompactCapacity() {
        val symbols = (1..500).map { "S$it" }
        val settings = StockSettings(watchlist=symbols)
        val count = stockBrowsePage(settings,0,2).second
        assertEquals(symbols.toSet(), (0 until count).flatMap { stockBrowsePage(settings,it,2).first }.toSet())
        (0 until count).forEach { assertTrue(stockBrowsePage(settings,it,2).first.size <= 2) }
    }
    @Test fun homeDepotIsPinnedAndCategoriesAreRespected() {
        val settings = StockSettings(featured="MSFT",watchlist=listOf("AAPL","WMT","HD","JPM"),categories=setOf(StockCategory.RETAIL))
        val count = stockBrowsePage(settings,0,2).second
        (0 until count).forEach { assertTrue("HD" in stockBrowsePage(settings,it,2).first) }
        assertEquals(listOf("HD","WMT"),stockBrowsePage(settings,0,2).first)
    }
    @Test fun emptyAndFilteredWatchlistsAreSafe() {
        assertEquals(emptyList<String>() to 1,stockBrowsePage(StockSettings(watchlist=emptyList()),0,2))
        assertEquals(emptyList<String>() to 1,stockBrowsePage(StockSettings(categories=emptySet()),0,2))
        assertEquals(listOf("HD") to 1,stockBrowsePage(StockSettings(featured="MSFT",categories=emptySet()),0,2))
    }
    @Test fun capacityFitsCompactAndWideViewports() {
        assertEquals(2,stockBrowseCapacity(400f,250f))
        assertEquals(4,stockBrowseCapacity(400f,360f))
        assertEquals(6,stockBrowseCapacity(600f,360f))
        assertEquals(2,stockBrowseCapacity(0f,0f))
    }
    @Test fun capacityBudgetsWrappedFreshnessAndRowGaps() {
        assertEquals(2,stockBrowseCapacity(400f,153f)) // 144dp card fits.
        assertEquals(2,stockBrowseCapacity(400f,295f)) // Two cards need 296dp.
        assertEquals(4,stockBrowseCapacity(400f,296f))
        assertEquals(4,stockBrowseCapacity(400f,447f)) // Three cards need 448dp.
        assertEquals(6,stockBrowseCapacity(400f,448f))
        assertEquals(2,stockBrowseCapacity(400f,296f,1.3f)) // Respect larger text.
    }
    @Test fun twoShortVisitsCompleteOneVisibleCountdown() {
        val first = advanceStockBrowse(StockBrowseClock(),5000,10)
        assertEquals(5000L,first.remainingMillis)
        val nextVisit = advanceStockBrowse(first,5000,10)
        assertEquals(1,nextVisit.page)
        assertEquals(1,nextVisit.featuredPage)
        assertEquals(STOCK_BROWSE_MILLIS,nextVisit.remainingMillis)
    }
    @Test fun allPauseSourcesPreserveTimeAndPage() {
        val clock=StockBrowseClock(2,1,3000)
        assertEquals(clock,advanceStockBrowse(clock,5000,10,paused=true))
        assertEquals(clock,advanceStockBrowse(clock,5000,10,interactionHeld=true))
        assertEquals(clock,advanceStockBrowse(clock,5000,10,autoRotate=false))
        assertEquals(clock,advanceStockBrowse(clock,0,10))
    }
    @Test fun manualNavigationWrapsAndResetsCountdownWhenRotationIsOff() {
        val settings=StockSettings(watchlist=listOf("AAPL","MSFT","WMT"),rotate=false)
        assertEquals(listOf("WMT"),stockBrowsePage(settings,1,2).first)
        assertEquals(StockBrowseClock(2,1),moveStockBrowse(StockBrowseClock(),-1,3))
        assertEquals(StockBrowseClock(0,0),moveStockBrowse(StockBrowseClock(2,1,1000),1,3))
    }
    @Test fun featuredOverviewAndChartRotateEvenWithOneWatchlistPage() {
        assertEquals(StockBrowseClock(0,1),advanceStockBrowse(StockBrowseClock(),10_000,1))
        assertEquals(StockBrowseClock(0,0,5000),advanceStockBrowse(StockBrowseClock(),25_000,1))
    }
}
