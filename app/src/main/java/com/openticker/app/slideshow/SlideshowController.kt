package com.openticker.app.slideshow

import com.openticker.app.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class DashboardSlide(val label: String) { WEATHER("Weather"), SPORTS("Sports"), STOCKS("Stocks");
    fun next(enabled: List<DashboardSlide> = entries.toList()): DashboardSlide =
        if (enabled.isEmpty()) this else enabled[(enabled.indexOf(this) + 1).mod(enabled.size)]
}
enum class TickerContent { SPORTS, WEATHER, STOCKS }
fun tickerContent(slide: DashboardSlide) = when (slide) {
    DashboardSlide.WEATHER -> TickerContent.SPORTS
    DashboardSlide.SPORTS -> TickerContent.WEATHER
    DashboardSlide.STOCKS -> TickerContent.STOCKS
}
data class SlideshowState(val slide: DashboardSlide = DashboardSlide.WEATHER, val playing: Boolean = false,
    val enabledScreens: List<DashboardSlide> = DashboardSlide.entries.toList())

/** One timer job, independent of data polling. Pauses retain the unexpired display time. */
class SlideshowController(scope: CoroutineScope, private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private sealed interface Event {
        data class Configure(val settings: AppSettings) : Event
        data class Foreground(val visible: Boolean) : Event
        data class Select(val slide: DashboardSlide) : Event
    }
    private val events = Channel<Event>(Channel.UNLIMITED)
    private val mutable = MutableStateFlow(SlideshowState())
    val state = mutable.asStateFlow()

    init {
        scope.launch {
            var configured = false
            var foreground = false
            var paused = false
            var intervalMillis = 10_000L
            var remainingMillis = intervalMillis
            var slide = DashboardSlide.WEATHER
            var enabled = DashboardSlide.entries.toList()
            while (isActive) {
                val running = configured && foreground && !paused && enabled.size > 1
                val started = nowMillis()
                val event = if (running) withTimeoutOrNull(remainingMillis) { events.receive() } else events.receive()
                if (running) remainingMillis = (remainingMillis - (nowMillis() - started).coerceAtLeast(0)).coerceAtLeast(1)
                when (event) {
                    null -> { slide = slide.next(enabled); remainingMillis = intervalMillis }
                    is Event.Configure -> {
                        val newInterval = event.settings.slideshowIntervalSeconds.coerceIn(5, 60) * 1_000L
                        if (!configured) slide = event.settings.startSlide
                        enabled = event.settings.enabledScreens.distinct()
                        if (enabled.isNotEmpty() && slide !in enabled) { slide = enabled.first(); remainingMillis = newInterval }
                        if (!configured || intervalMillis != newInterval) remainingMillis = newInterval
                        intervalMillis = newInterval
                        paused = event.settings.slideshowPaused
                        configured = true
                    }
                    is Event.Foreground -> foreground = event.visible
                    is Event.Select -> if (event.slide in enabled) { slide = event.slide; remainingMillis = intervalMillis }
                }
                mutable.value = SlideshowState(slide, configured && foreground && !paused && enabled.size > 1, enabled)
            }
        }
    }
    fun configure(settings: AppSettings) { events.trySend(Event.Configure(settings)) }
    fun setForeground(visible: Boolean) { events.trySend(Event.Foreground(visible)) }
    /** A manual selection gets a full interval before the next automatic switch. */
    fun select(slide: DashboardSlide) { events.trySend(Event.Select(slide)) }
}
