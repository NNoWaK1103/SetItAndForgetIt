package com.openticker.app.data.repository

import com.openticker.app.data.model.*
import com.openticker.app.data.remote.StockDataSource
import com.openticker.app.settings.StockSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId

data class StockMarketUiState(
    val quotes: Map<String, StockQuote> = emptyMap(), val session: MarketSession = MarketSession.UNKNOWN,
    val statusUpdated: Instant? = null, val fetched: Instant? = null, val error: String? = null,
    val histories: Map<Pair<String, ChartRange>, StockHistory> = emptyMap(),
    val historyErrors: Map<Pair<String, ChartRange>, String> = emptyMap(),
    val provider: String = "Market data", val configured: Boolean = false, val refreshing: Boolean = false,
    val staleSymbols: Set<String> = emptySet()
) {
    fun sessionAt(now: Instant): MarketSession = if (statusUpdated?.let {
        now.epochSecond - it.epochSecond in 0..120 } == true) session else MarketSession.UNKNOWN
}
interface StockRepository {
    suspend fun refresh(settings: StockSettings, now: Instant): StockMarketUiState
}

/** Independent quote/chart caches. Slide transitions never call this repository. */
class CachedStockRepository(private val source: StockDataSource) : StockRepository {
    private val mutex = Mutex()
    private var state = StockMarketUiState()
    private var baseUrl = ""
    private var quoteConfig: Pair<List<String>, Set<MarketIndex>>? = null
    private var quoteDue = Instant.MIN
    private var quoteFailures = 0
    private val historyDue = mutableMapOf<Pair<String, ChartRange>, Instant>()
    private val historyFailures = mutableMapOf<Pair<String, ChartRange>, Int>()

    override suspend fun refresh(settings: StockSettings, now: Instant): StockMarketUiState = mutex.withLock {
        if (baseUrl != settings.proxyUrl) {
            baseUrl = settings.proxyUrl; state = StockMarketUiState(); quoteDue = Instant.MIN
            quoteConfig = null; quoteFailures = 0; historyDue.clear(); historyFailures.clear()
        }
        if (baseUrl.isBlank()) return@withLock state.copy(error = "Stock data not configured. Add your HTTPS market-data proxy in Settings.")
        state = state.copy(configured = true)
        val config = settings.quoteSymbols() to settings.indices
        // Configuration changes also respect a failure's backoff.
        if (now >= quoteDue || (config != quoteConfig && quoteFailures == 0)) {
            try {
                val result = source.snapshot(baseUrl, config.first, config.second)
                val missing = (config.first + config.second.map { it.symbol }).filter { result.quotes[it]?.price == null }
                val valid = result.quotes.filterValues { it.price != null }
                val retained = missing.filter { state.quotes[it]?.price != null }.toSet()
                val missingStocks = missing.filter { it in config.first }
                state = state.copy(quotes = state.quotes + valid, session = result.session,
                    statusUpdated = result.statusUpdated, fetched = now, provider = result.provider,
                    staleSymbols = retained + result.staleSymbols,
                    error = result.warning ?: if (missingStocks.isNotEmpty()) "Some quotes unavailable; retained values show their original timestamps." else null)
                val seconds = if (result.pending || result.session == MarketSession.OPEN) settings.refreshSeconds else maxOf(900, settings.refreshSeconds)
                quoteDue = now.plusSeconds(maxOf(seconds, result.refreshAfterSeconds).toLong())
                quoteConfig = config; quoteFailures = 0
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                quoteFailures++
                quoteDue = now.plusSeconds(stockBackoffSeconds(quoteFailures, settings.refreshSeconds))
                state = state.copy(error = "Market feed unavailable · retained data may be stale · retrying",staleSymbols=state.quotes.keys)
            }
        }
        val key = settings.featured to settings.range
        if (now >= (historyDue[key] ?: Instant.MIN)) {
            try {
                val history = source.history(baseUrl, key.first, key.second)
                val previous = state.histories[key]
                val sameDay = settings.range != ChartRange.DAY || previous?.points?.lastOrNull()?.time
                    ?.atZone(ZoneId.of("America/New_York"))?.toLocalDate() == now.atZone(ZoneId.of("America/New_York")).toLocalDate()
                val displayed = if(history.pending && history.points.isEmpty() && previous!=null && sameDay)
                    previous.copy(message=history.message ?: "Chart refresh pending · showing retained history",pending=true) else history
                state = state.copy(histories = state.histories + (key to displayed), historyErrors = state.historyErrors - key)
                historyFailures.remove(key)
                historyDue[key] = now.plusSeconds(if(history.pending) 60 else if (settings.range == ChartRange.DAY) maxOf(300, settings.refreshSeconds).toLong() else 21600)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                val failures = (historyFailures[key] ?: 0) + 1; historyFailures[key] = failures
                historyDue[key] = now.plusSeconds(stockBackoffSeconds(failures, maxOf(300, settings.refreshSeconds)))
                state = state.copy(historyErrors = state.historyErrors + (key to "Chart unavailable · retained history may be stale"))
            }
        }
        state
    }
}
fun stockBackoffSeconds(failures: Int, minimum: Int = 60): Long =
    (minimum.toLong() * (1L shl (failures - 1).coerceIn(0, 10))).coerceAtMost(3600)
