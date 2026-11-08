package com.openticker.app.util

import com.openticker.app.data.model.League
import com.openticker.app.data.repository.StockMarketUiState
import com.openticker.app.settings.polledLeagues
import com.openticker.app.slideshow.DashboardSlide
import com.openticker.app.viewmodel.DashboardState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class FeedCondition(val label: String) {
    CURRENT("Receiving data"), WAITING("Awaiting data"), SAVED("Saved data"), ERROR("Update failed"),
    OVERDUE("Update overdue"), PARTIAL("Partial data"), UNCONFIGURED("Setup needed"), OFFLINE("Network offline")
}
data class FeedHealth(val name: String, val condition: FeedCondition, val detail: String, val updated: Instant? = null) {
    val needsAttention: Boolean get() = condition != FeedCondition.CURRENT
}
fun feedHealthCaption(feeds: List<FeedHealth>): String {
    val count=feeds.count { it.needsAttention }
    return if(feeds.isEmpty()) "Feeds loading" else if(count==0) "Feeds OK" else "$count feed ${if(count==1) "notice" else "notices"}"
}
fun feedHealthTime(time: Instant?): String = time?.atZone(ZoneId.systemDefault())
    ?.format(DateTimeFormatter.ofPattern("MMM d, h:mm:ss a z",Locale.US)) ?: "No successful update"

/** Feed health is distinct from whether an individual quote is real-time, delayed or previous-close. */
fun dashboardFeedHealth(state: DashboardState, stocks: StockMarketUiState, now: Instant, activeSlide: DashboardSlide? = null): List<FeedHealth> = buildList {
    val weatherCondition=when {
        !state.online -> FeedCondition.OFFLINE
        state.weatherError!=null -> FeedCondition.ERROR
        state.weatherRestored -> FeedCondition.SAVED
        state.weather==null || state.weatherUpdated==null -> FeedCondition.WAITING
        now.epochSecond-state.weatherUpdated.epochSecond>=900 -> FeedCondition.OVERDUE
        else -> FeedCondition.CURRENT
    }
    add(FeedHealth("Weather",weatherCondition,when(weatherCondition) {
        FeedCondition.ERROR -> state.weatherError.orEmpty()
        FeedCondition.CURRENT -> "Open-Meteo model estimates and forecasts. Refreshes every 15 minutes."
        FeedCondition.WAITING -> "Waiting for the first valid forecast."
        else -> if(state.weather==null) "No saved weather is available." else "Showing last received weather with its original update time."
    },state.weatherUpdated))
    state.settings.polledLeagues().sortedBy(League::ordinal).forEach { league ->
        val feed=state.scores[league]
        val dated=feed?.updated?.atZone(ZoneId.of("America/New_York"))?.toLocalDate()
        val condition=when {
            !state.online -> FeedCondition.OFFLINE
            feed?.error!=null -> FeedCondition.ERROR
            feed?.restored==true -> FeedCondition.SAVED
            feed?.updated==null -> FeedCondition.WAITING
            dated!=now.atZone(ZoneId.of("America/New_York")).toLocalDate() -> FeedCondition.OVERDUE
            now.epochSecond-feed.updated.epochSecond>300 -> FeedCondition.OVERDUE
            else -> FeedCondition.CURRENT
        }
        add(FeedHealth(league.name,condition,when(condition) {
            FeedCondition.ERROR -> feed?.error.orEmpty()
            FeedCondition.CURRENT -> "ESPN scoreboard · ${feed?.games?.size ?: 0} games in the received feed. Times and scores are provider-supplied."
            FeedCondition.WAITING -> "Waiting for the first valid scoreboard."
            else -> if(feed?.updated==null) "No saved scores are available." else "Showing saved scores. A saved live status is not confirmation the game is still live."
        },feed?.updated))
    }
    val showStocks=activeSlide==DashboardSlide.STOCKS || DashboardSlide.STOCKS in state.settings.enabledScreens ||
        (state.settings.persistentMarketTicker && state.settings.stocks.tickerEnabled)
    if(showStocks) {
        val wanted=(state.settings.stocks.quoteSymbols()+state.settings.stocks.indices.map { it.symbol }).toSet()
        val available=stocks.quotes.filter { it.key in wanted && it.value.price!=null }
        val missing=wanted-available.keys
        val retained=wanted.intersect(stocks.staleSymbols)
        val chartKey=state.settings.stocks.featured to state.settings.stocks.range
        val chartIssue=chartKey in stocks.historyErrors || chartKey in stocks.staleHistories
        val condition=when {
            !stocks.configured -> FeedCondition.UNCONFIGURED
            !state.online || !stocks.online -> FeedCondition.OFFLINE
            stocks.error!=null -> if(available.isEmpty()) FeedCondition.ERROR else FeedCondition.PARTIAL
            retained.isNotEmpty() || missing.isNotEmpty() || chartIssue -> if(available.isEmpty()) FeedCondition.WAITING else FeedCondition.PARTIAL
            stocks.fetched==null -> FeedCondition.WAITING
            now.epochSecond-stocks.fetched.epochSecond>maxOf(1830,state.settings.stocks.refreshSeconds+30) -> FeedCondition.OVERDUE
            else -> FeedCondition.CURRENT
        }
        add(FeedHealth("Markets",condition,when(condition) {
            FeedCondition.UNCONFIGURED -> "Add your HTTPS market-data proxy in Settings. No stock service or provider credentials are bundled."
            FeedCondition.OFFLINE -> if(available.isEmpty()) "No saved market quotes are available." else "Showing saved market quotes; each retains its original timestamp."
            FeedCondition.PARTIAL -> listOfNotNull(stocks.error, "${available.size} of ${wanted.size} requested quotes/index values available", "${retained.size} retained/stale quotes".takeIf { retained.isNotEmpty() }, "Chart may be stale or unavailable".takeIf { chartIssue }, "Missing values may require provider/index entitlements".takeIf { missing.isNotEmpty() }).joinToString(" · ")
            FeedCondition.ERROR -> stocks.error ?: "Market feed unavailable. Retrying with backoff."
            FeedCondition.CURRENT -> "${stocks.provider}. Real-time, delayed and previous-close labels describe each quote separately."
            FeedCondition.OVERDUE -> "No recent successful market response. Retained quotes may be stale."
            else -> "Waiting for usable quotes from your configured provider."
        },stocks.fetched))
    }
}
