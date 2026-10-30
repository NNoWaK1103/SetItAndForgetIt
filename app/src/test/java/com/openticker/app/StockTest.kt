package com.openticker.app

import com.openticker.app.data.model.*
import com.openticker.app.data.remote.*
import com.openticker.app.data.repository.*
import com.openticker.app.settings.*
import com.openticker.app.util.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class StockTest {
    private fun fixture(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()
    private val now = Instant.parse("2026-10-29T14:00:00Z")
    @Test fun `quotes map actual fields and compute daily changes`() {
        val s=StockParser.snapshot(fixture("stocks.json"));val q=s.quotes.getValue("HD")
        assertEquals(410.25,q.price!!,.001); assertEquals(10.25,q.change!!,.001); assertEquals(2.5625,q.changePercent!!,.0001)
        assertEquals(412.0,q.high!!,.001);assertEquals(398.0,q.low!!,.001);assertEquals(1234567L,q.volume)
        assertEquals(QuoteFreshness.DELAYED,q.freshness);assertEquals(15,q.delayMinutes)
        assertEquals(-2.5,s.quotes.getValue("AAPL").changePercent!!,.001)
        assertEquals(0.0,s.quotes.getValue("BRK.B").change!!,.001)
        assertNull(s.quotes.getValue("MSFT").price)
    }
    @Test fun `missing invalid or untimestamped quotes stay unavailable`() {
        val s=StockParser.snapshot("""{"quotes":[{"symbol":"HD","price":100,"freshness":"REALTIME"},{"symbol":"AAPL","price":-5,"updated":"2026-10-29T14:00:00Z","freshness":"REALTIME"}]}""")
        assertNull(s.quotes.getValue("HD").price);assertNull(s.quotes.getValue("AAPL").price)
        assertEquals(MarketSession.UNKNOWN,s.session)
        assertNull(StockQuote("HD",price=100.0,previousClose=0.0).changePercent)
        assertNull(StockQuote("HD",price=100.0).change)
    }
    @Test(expected=IllegalArgumentException::class) fun `error envelopes are rejected`() {StockParser.snapshot("""{"error":"rate limited"}""")}
    @Test fun `history maps real points in order without synthesizing missing points`() {
        val h=StockParser.history(fixture("stock-history.json"))
        assertEquals(2,h.points.size);assertEquals(400.0,h.points.first().close,.001)
        assertEquals(Instant.parse("2026-10-29T13:40:00Z"),h.points.last().time)
        assertTrue(StockParser.history("""{"points":[],"message":"Intraday unavailable"}""").points.isEmpty())
    }
    @Test fun `ticker labels freshness timestamp signs and missing data`() {
        val q=StockParser.snapshot(fixture("stocks.json")).quotes
        assertTrue(stockTickerText("HD",q["HD"]).contains("HD  $410.25  +2.56%  Delayed 15m"))
        assertTrue(stockTickerText("AAPL",q["AAPL"]).contains("-2.50%  Previous close"))
        assertEquals("MSFT  Unavailable",stockTickerText("MSFT",q["MSFT"]))
        assertTrue(stockTickerText("HD",q["HD"],true).endsWith("STALE"))
        assertTrue(stockTime(now).endsWith("10:00 AM ET"))
        assertTrue(stockTime(Instant.parse("2026-12-01T14:00:00Z")).endsWith("9:00 AM ET"))
        assertEquals("+$10.25",stockDollarChange(q["HD"]?.change))
        assertEquals("-$5.00",stockDollarChange(q["AAPL"]?.change))
    }
    @Test fun `market status must be provider supplied and fresh even on holidays`() {
        val s=StockMarketUiState(session=MarketSession.OPEN,statusUpdated=now)
        assertEquals(MarketSession.OPEN,s.sessionAt(now.plusSeconds(120)))
        assertEquals(MarketSession.UNKNOWN,s.sessionAt(now.plusSeconds(121)))
        assertEquals(MarketSession.UNKNOWN,s.sessionAt(now.minusSeconds(1)))
        assertEquals(MarketSession.CLOSED,s.copy(session=MarketSession.CLOSED).sessionAt(now))
        assertEquals(MarketSession.UNKNOWN,s.copy(statusUpdated=null).sessionAt(now))
    }
    @Test fun `pages rotate in groups of eight while Home Depot stays pinned`() {
        val s=StockSettings()
        val pages=(0..2).map { stockGridPage(s,it).first }
        assertTrue(pages.all { it.size<=8 });assertEquals(s.watchlist.toSet(),pages.flatten().toSet())
        assertEquals(pages[0],stockGridPage(s,3).first)
        assertEquals(pages[0],stockGridPage(s.copy(rotate=false),2).first)
        assertTrue(stockGridPage(s.copy(featured="AAPL"),1).first.contains("HD"))
        assertEquals(listOf("HD"),s.copy(categories=emptySet()).quoteSymbols())
    }
    @Test fun `config validates symbols and secure endpoints`() {
        assertEquals("BRK.B",normalizeStockSymbol(" brk.b "));assertNull(normalizeStockSymbol("HD&apikey=bad"))
        assertTrue(validStockProxy("https://example.com/market"));assertFalse(validStockProxy("http://example.com"))
        assertFalse(validStockProxy("https://user:secret@example.com"));assertFalse(validStockProxy("https://example.com?key=secret"))
        assertFalse(validStockProxy("https://api.alpaca.markets"));assertFalse(validStockProxy("https://data.alpaca.markets"))
        assertFalse(validStockProxy("https://paper-api.alpaca.markets"))
    }
    @Test fun `Alpaca feed coverage survives quote and chart parsing and ticker formatting`() {
        val quote=StockParser.snapshot("""{"quotes":[{"symbol":"HD","price":410.25,"previousClose":400,"updated":"2026-10-29T13:59:59Z","freshness":"REALTIME","feedLabel":"IEX only"}]}""").quotes.getValue("HD")
        assertEquals("Real-time · IEX only",stockFreshness(quote))
        assertTrue(stockTickerText("HD",quote).contains("IEX only"))
        assertEquals("Delayed 15m · SIP",stockFreshness(quote.copy(freshness=QuoteFreshness.DELAYED,delayMinutes=15,feedLabel="SIP")))
        val chart=StockParser.history("""{"points":[{"time":"2026-10-29T13:30:00Z","close":400}],"freshness":"REALTIME","feedLabel":"IEX only"}""")
        assertEquals("IEX only",chart.feedLabel)
    }
    private inner class Source : StockDataSource {
        var snapshotCalls=0;var historyCalls=0;var fail=false;var missing=false;var session=MarketSession.OPEN;var cancel=false;var quota=60;var historyPending=false
        override suspend fun snapshot(baseUrl: String,symbols: List<String>,indices: Set<MarketIndex>): StockSnapshot {
            snapshotCalls++;if(cancel) throw CancellationException();if(fail) error("offline")
            return StockParser.snapshot(fixture("stocks.json")).copy(session=session,refreshAfterSeconds=quota,
                quotes=if(missing) emptyMap() else StockParser.snapshot(fixture("stocks.json")).quotes)
        }
        override suspend fun history(baseUrl: String,symbol: String,range: ChartRange): StockHistory {
            historyCalls++;if(fail) error("offline");return if(historyPending) StockHistory(pending=true,message="History loading") else StockParser.history(fixture("stock-history.json"))
        }
    }
    private val settings=StockSettings(proxyUrl="https://example.com",watchlist=listOf("AAPL"),indices=emptySet())
    @Test fun `cache survives failures and retries with exponential backoff`() = runTest {
        val source=Source();val r=CachedStockRepository(source)
        val first=r.refresh(settings,now);source.fail=true
        val failed=r.refresh(settings,now.plusSeconds(60))
        assertEquals(first.quotes,failed.quotes);assertEquals(first.fetched,failed.fetched);assertNotNull(failed.error)
        assertEquals(first.quotes.keys,failed.staleSymbols)
        r.refresh(settings,now.plusSeconds(119));assertEquals(2,source.snapshotCalls)
        r.refresh(settings,now.plusSeconds(120));assertEquals(3,source.snapshotCalls)
        r.refresh(settings,now.plusSeconds(239));assertEquals(3,source.snapshotCalls)
        source.fail=false;r.refresh(settings,now.plusSeconds(240));assertEquals(4,source.snapshotCalls)
    }
    @Test fun `charts cache per range and repeated polls do not fetch per slide`() = runTest {
        val source=Source();val r=CachedStockRepository(source)
        r.refresh(settings,now);repeat(10) {r.refresh(settings,now.plusSeconds(it.toLong()))}
        assertEquals(1,source.snapshotCalls);assertEquals(1,source.historyCalls)
        r.refresh(settings.copy(range=ChartRange.YEAR),now.plusSeconds(10));assertEquals(2,source.historyCalls)
        r.refresh(settings,now.plusSeconds(20));assertEquals(2,source.historyCalls)
        assertEquals(1,source.snapshotCalls)
    }
    @Test fun `closed and unknown sessions and provider quotas lengthen polling`() = runTest {
        for(session in listOf(MarketSession.CLOSED,MarketSession.UNKNOWN)) {
            val source=Source().also {it.session=session};val r=CachedStockRepository(source)
            r.refresh(settings,now);r.refresh(settings,now.plusSeconds(899));assertEquals(1,source.snapshotCalls)
            r.refresh(settings,now.plusSeconds(900));assertEquals(2,source.snapshotCalls)
        }
        val source=Source().also {it.quota=1800};val r=CachedStockRepository(source)
        r.refresh(settings,now);r.refresh(settings,now.plusSeconds(1799));assertEquals(1,source.snapshotCalls)
    }
    @Test fun `partial responses retain last valid prices with original timestamps`() = runTest {
        val source=Source();val r=CachedStockRepository(source);val first=r.refresh(settings,now)
        source.missing=true;val result=r.refresh(settings,now.plusSeconds(60))
        assertEquals(first.quotes,result.quotes);assertNotNull(result.error)
        assertTrue(result.staleSymbols.contains("HD"))
    }
    @Test fun `unconfigured feed never calls source and changed proxy clears cache`() = runTest {
        val source=Source();val r=CachedStockRepository(source)
        val unconfigured=r.refresh(StockSettings(),now)
        assertFalse(unconfigured.configured);assertEquals(0,source.snapshotCalls)
        assertTrue(unconfigured.error!!.contains("Stock data not configured"));assertTrue(unconfigured.quotes.isEmpty())
        r.refresh(settings,now);val cleared=r.refresh(StockSettings(),now.plusSeconds(1))
        assertTrue(cleared.quotes.isEmpty());assertTrue(cleared.histories.isEmpty())
    }
    @Test fun `cancellation does not masquerade as a provider failure`() = runTest {
        val source=Source().also {it.cancel=true};val r=CachedStockRepository(source)
        try {r.refresh(settings,now);fail("Expected cancellation")} catch(_: CancellationException) { }
        source.cancel=false
        assertNull(r.refresh(settings,now).error)
        assertEquals(2,source.snapshotCalls)
    }
    @Test fun `pending proxy reload retains a valid chart but never relabels yesterday as today`() = runTest {
        val source=Source();val r=CachedStockRepository(source);val key=settings.featured to settings.range
        val original=r.refresh(settings,now).histories.getValue(key);source.historyPending=true
        val pending=r.refresh(settings,now.plusSeconds(300)).histories.getValue(key)
        assertEquals(original.points,pending.points);assertTrue(pending.pending)
        val nextDay=r.refresh(settings,now.plusSeconds(86400)).histories.getValue(key)
        assertTrue(nextDay.points.isEmpty());assertTrue(nextDay.pending)
    }
    @Test fun `backoff stays bounded`() { assertEquals(60L,stockBackoffSeconds(1));assertEquals(120L,stockBackoffSeconds(2));assertEquals(3600L,stockBackoffSeconds(20)) }
    @Test fun `full company watchlist is preserved and proxy requests batch fifty symbols without losing warnings`() = runTest {
        val symbols=(1..500).map { "S$it" }
        val settings=StockSettings(watchlist=symbols).normalized()
        assertEquals(500,settings.watchlist.size)
        val requests=mutableListOf<okhttp3.HttpUrl>()
        val source=ProxyStockDataSource { url ->
            requests+=url
            val batch=url.queryParameter("symbols")!!.split(',')
            val quotes=batch.joinToString(",") { """{"symbol":"$it","price":100,"updated":"2026-10-29T14:00:00Z","freshness":"REALTIME"}""" }
            """{"quotes":[$quotes],"warning":"Provider warning","refreshAfterSeconds":120,"staleSymbols":["${batch.first()}"]}"""
        }
        val result=source.snapshot("https://proxy.example/market",settings.quoteSymbols(),setOf(MarketIndex.SP500))
        assertEquals(11,requests.size);assertEquals(501,result.quotes.size)
        assertTrue(requests.all { it.queryParameter("symbols")!!.split(',').size<=50 })
        assertEquals("I:SPX",requests.first().queryParameter("indices"))
        assertTrue(requests.drop(1).all { it.queryParameter("indices")=="" })
        assertTrue(requests.all { it.encodedPath=="/market/v1/quotes" })
        assertEquals("Provider warning",result.warning);assertEquals(120,result.refreshAfterSeconds)
        assertEquals(11,result.staleSymbols.size)
    }
}
