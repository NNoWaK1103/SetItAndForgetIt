package com.openticker.app.settings

import com.openticker.app.data.model.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

val STOCK_REFRESH_INTERVALS = listOf(60, 120, 300, 900, 1800)
data class StockSettings(
    val featured: String = "HD",
    val watchlist: List<String> = STOCK_COMPANIES.drop(1).map { it.symbol },
    val categories: Set<StockCategory> = StockCategory.entries.toSet(),
    val range: ChartRange = ChartRange.DAY,
    val tickerEnabled: Boolean = true,
    val refreshSeconds: Int = 60,
    val rotate: Boolean = true,
    val indices: Set<MarketIndex> = MarketIndex.entries.toSet(),
    val proxyUrl: String = ""
) {
    fun selectedSymbols(): List<String> = (listOf(featured) + watchlist.filter { stockCompany(it).category in categories }).distinct()
    // HD stays visible even if the user features a different stock.
    fun quoteSymbols(): List<String> = (listOf("HD") + selectedSymbols()).distinct()
    fun normalized() = copy(featured = normalizeStockSymbol(featured) ?: "HD",
        watchlist = watchlist.mapNotNull(::normalizeStockSymbol).distinct().take(50),
        refreshSeconds = refreshSeconds.takeIf { it in STOCK_REFRESH_INTERVALS } ?: 60,
        proxyUrl = proxyUrl.trim().takeIf { it.isEmpty() || validStockProxy(it) } ?: "")
}
fun validStockProxy(value: String): Boolean = value.toHttpUrlOrNull()?.let {
    it.isHttps && it.username.isEmpty() && it.password.isEmpty() && it.query == null && it.fragment == null &&
        it.host !in setOf("api.alpaca.markets", "paper-api.alpaca.markets", "data.alpaca.markets", "api.massive.com")
} == true

fun stockGridPage(settings: StockSettings, appearance: Int, size: Int = 8): Pair<List<String>, Int> {
    val pinned = listOf("HD").filter { it != settings.featured }
    val remaining = settings.selectedSymbols().filter { it != settings.featured && it !in pinned }
    val capacity = (size - pinned.size).coerceAtLeast(1)
    val pages = maxOf(1, (remaining.size + capacity - 1) / capacity)
    val page = if (settings.rotate) Math.floorMod(appearance, pages) else 0
    return (pinned + remaining.drop(page * capacity).take(capacity)) to pages
}
