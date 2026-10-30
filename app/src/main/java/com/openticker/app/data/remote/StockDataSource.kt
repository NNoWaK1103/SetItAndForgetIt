package com.openticker.app.data.remote

import com.openticker.app.data.model.*
import com.openticker.app.settings.validStockProxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.Instant

interface StockDataSource {
    suspend fun snapshot(baseUrl: String, symbols: List<String>, indices: Set<MarketIndex>): StockSnapshot
    suspend fun history(baseUrl: String, symbol: String, range: ChartRange): StockHistory
}

/** Only calls an operator-configured HTTPS proxy. Provider credentials never enter the app. */
class ProxyStockDataSource(private val get: suspend (okhttp3.HttpUrl) -> String) : StockDataSource {
    constructor(http: HttpClient) : this(http::get)
    private fun url(base: String, path: String) = base.also { require(validStockProxy(it)) }
        .toHttpUrl().newBuilder().addPathSegments(path)
    override suspend fun snapshot(baseUrl: String, symbols: List<String>, indices: Set<MarketIndex>) = withContext(Dispatchers.IO) {
        require(symbols.isNotEmpty())
        // Bound each proxy request even when the user configures the full company watchlist.
        val snapshots = symbols.distinct().chunked(50).mapIndexed { index, batch ->
            StockParser.snapshot(get(url(baseUrl, "v1/quotes")
                .addQueryParameter("symbols", batch.joinToString(","))
                .addQueryParameter("indices", if(index==0) indices.joinToString(",") { it.symbol } else "").build()))
        }
        snapshots.maxBy { it.statusUpdated ?: Instant.MIN }.copy(
            quotes = snapshots.flatMap { it.quotes.entries }.associate { it.key to it.value },
            received = snapshots.maxOf { it.received },
            refreshAfterSeconds = snapshots.maxOf { it.refreshAfterSeconds },
            warning = snapshots.mapNotNull { it.warning }.distinct().joinToString(" ").takeIf { it.isNotBlank() },
            pending = snapshots.any { it.pending },
            staleSymbols = snapshots.flatMap { it.staleSymbols }.toSet())
    }
    override suspend fun history(baseUrl: String, symbol: String, range: ChartRange) = withContext(Dispatchers.IO) {
        StockParser.history(get(url(baseUrl, "v1/history").addQueryParameter("symbol", symbol)
            .addQueryParameter("range", range.label).build()))
    }
}

object StockParser {
    private fun JsonObject.time(key: String) = str(key)?.let { runCatching { Instant.parse(it) }.getOrNull() }
    private fun JsonObject.positive(key: String) = num(key)?.takeIf { it.isFinite() && it > 0 }
    private fun JsonObject.freshness() = QuoteFreshness.entries.find { it.name == str("freshness") } ?: QuoteFreshness.UNAVAILABLE
    fun snapshot(body: String): StockSnapshot {
        val root = Json.parseToJsonElement(body).jsonObject
        require(root["quotes"] is JsonArray) { "Missing quotes" }
        val quotes = root.arr("quotes").filterIsInstance<JsonObject>().mapNotNull { q ->
            val symbol = q.str("symbol")?.takeIf { normalizeStockSymbol(it) != null || MarketIndex.entries.any { index -> index.symbol == it } }
                ?: return@mapNotNull null
            val time = q.time("updated")
            val freshness = q.freshness()
            val price = q.positive("price").takeIf { time != null && freshness != QuoteFreshness.UNAVAILABLE }
            StockQuote(symbol, price, q.positive("previousClose"), q.positive("high"), q.positive("low"),
                q.num("volume")?.takeIf { it.isFinite() && it >= 0 }?.toLong(), time,
                if (price == null) QuoteFreshness.UNAVAILABLE else freshness,
                q.int("delayMinutes")?.coerceAtLeast(0), q.str("name"), q.str("exchange"), q.str("feedLabel"))
        }.associateBy { it.symbol }
        return StockSnapshot(quotes, MarketSession.entries.find { it.name == root.str("session") } ?: MarketSession.UNKNOWN,
            root.time("statusUpdated"), Instant.now(), (root.int("refreshAfterSeconds") ?: 60).coerceIn(60, 86400),
            root.str("warning"), root.str("provider") ?: "Market data provider", (root["pending"] as? JsonPrimitive)?.booleanOrNull == true,
            root.arr("staleSymbols").mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.toSet())
    }
    fun history(body: String): StockHistory {
        val root = Json.parseToJsonElement(body).jsonObject
        require(root["points"] is JsonArray) { "Missing historical points" }
        val points = root.arr("points").filterIsInstance<JsonObject>().mapNotNull { p ->
            val time = p.time("time") ?: return@mapNotNull null
            p.positive("close")?.let { StockHistoricalPoint(time, it) }
        }.distinctBy { it.time }.sortedBy { it.time }
        return StockHistory(points, root.str("message"), root.freshness(), root.time("updated"), (root["pending"] as? JsonPrimitive)?.booleanOrNull == true, root.str("feedLabel"))
    }
}
