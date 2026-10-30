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
class ProxyStockDataSource(private val http: HttpClient) : StockDataSource {
    private fun url(base: String, path: String) = base.also { require(validStockProxy(it)) }
        .toHttpUrl().newBuilder().addPathSegments(path)
    override suspend fun snapshot(baseUrl: String, symbols: List<String>, indices: Set<MarketIndex>) = withContext(Dispatchers.IO) {
        StockParser.snapshot(http.get(url(baseUrl, "v1/quotes")
            .addQueryParameter("symbols", symbols.joinToString(","))
            .addQueryParameter("indices", indices.joinToString(",") { it.symbol }).build()))
    }
    override suspend fun history(baseUrl: String, symbol: String, range: ChartRange) = withContext(Dispatchers.IO) {
        StockParser.history(http.get(url(baseUrl, "v1/history").addQueryParameter("symbol", symbol)
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
                q.int("delayMinutes")?.coerceAtLeast(0), q.str("name"), q.str("exchange"))
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
        return StockHistory(points, root.str("message"), root.freshness(), root.time("updated"), (root["pending"] as? JsonPrimitive)?.booleanOrNull == true)
    }
}
