package com.openticker.app.data.model

import java.time.Instant
import java.util.Locale

enum class QuoteFreshness(val label: String) {
    REALTIME("Real-time"), DELAYED("Delayed"), PREVIOUS_CLOSE("Previous close"), UNAVAILABLE("Unavailable")
}
enum class MarketSession { OPEN, CLOSED, UNKNOWN }
enum class ChartRange(val label: String) { DAY("1D"), WEEK("1W"), MONTH("1M"), QUARTER("3M"), YEAR("1Y") }
enum class StockCategory(val label: String) {
    TECHNOLOGY("Technology"), RETAIL("Retail"), FINANCIAL("Financial"), HEALTHCARE("Healthcare"), ENERGY("Energy"), INDUSTRIAL("Industrial"), OTHER("Other")
}
enum class MarketIndex(val symbol: String, val label: String) {
    SP500("I:SPX", "S&P 500"), DOW("I:DJI", "Dow Jones"), NASDAQ("I:COMP", "Nasdaq Composite")
}
data class StockCompany(val symbol: String, val name: String, val category: StockCategory, val exchange: String = "")
// A configurable company directory, NOT a claim about membership or rank in any year's Fortune 500.
val STOCK_COMPANIES = listOf(
    StockCompany("HD", "The Home Depot", StockCategory.RETAIL, "NYSE"),
    StockCompany("AAPL", "Apple", StockCategory.TECHNOLOGY), StockCompany("MSFT", "Microsoft", StockCategory.TECHNOLOGY),
    StockCompany("GOOGL", "Alphabet", StockCategory.TECHNOLOGY), StockCompany("AMZN", "Amazon", StockCategory.TECHNOLOGY),
    StockCompany("NVDA", "Nvidia", StockCategory.TECHNOLOGY), StockCompany("WMT", "Walmart", StockCategory.RETAIL),
    StockCompany("COST", "Costco", StockCategory.RETAIL), StockCompany("TGT", "Target", StockCategory.RETAIL),
    StockCompany("JPM", "JPMorgan Chase", StockCategory.FINANCIAL), StockCompany("BAC", "Bank of America", StockCategory.FINANCIAL),
    StockCompany("BRK.B", "Berkshire Hathaway", StockCategory.FINANCIAL), StockCompany("UNH", "UnitedHealth Group", StockCategory.HEALTHCARE),
    StockCompany("JNJ", "Johnson & Johnson", StockCategory.HEALTHCARE), StockCompany("LLY", "Eli Lilly", StockCategory.HEALTHCARE),
    StockCompany("XOM", "Exxon Mobil", StockCategory.ENERGY), StockCompany("CVX", "Chevron", StockCategory.ENERGY),
    StockCompany("CAT", "Caterpillar", StockCategory.INDUSTRIAL), StockCompany("GE", "GE Aerospace", StockCategory.INDUSTRIAL),
    StockCompany("BA", "Boeing", StockCategory.INDUSTRIAL)
)
fun stockCompany(symbol: String) = STOCK_COMPANIES.find { it.symbol == symbol }
    ?: StockCompany(symbol, symbol, StockCategory.OTHER)
fun normalizeStockSymbol(value: String): String? = value.trim().uppercase(Locale.US)
    .takeIf { it.matches(Regex("[A-Z][A-Z0-9]{0,9}(\\.[A-Z])?")) }

data class StockQuote(
    val symbol: String, val price: Double? = null, val previousClose: Double? = null,
    val high: Double? = null, val low: Double? = null, val volume: Long? = null,
    val updated: Instant? = null, val freshness: QuoteFreshness = QuoteFreshness.UNAVAILABLE,
    val delayMinutes: Int? = null, val name: String? = null, val exchange: String? = null, val feedLabel: String? = null
) {
    val change: Double? get() = price?.let { p -> previousClose?.let { p - it } }
    val changePercent: Double? get() = change?.let { delta -> previousClose?.takeIf { it > 0 }?.let { delta / it * 100 } }
}
data class StockHistoricalPoint(val time: Instant, val close: Double)
data class StockHistory(val points: List<StockHistoricalPoint> = emptyList(), val message: String? = null,
    val freshness: QuoteFreshness = QuoteFreshness.UNAVAILABLE, val updated: Instant? = null, val pending: Boolean = false,
    val feedLabel: String? = null)
data class StockSnapshot(val quotes: Map<String, StockQuote>, val session: MarketSession,
    val statusUpdated: Instant?, val received: Instant, val refreshAfterSeconds: Int = 60,
    val warning: String? = null, val provider: String = "Market data provider", val pending: Boolean = false,
    val staleSymbols: Set<String> = emptySet())
