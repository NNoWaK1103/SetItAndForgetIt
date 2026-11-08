package com.openticker.app.settings

/** Presentation-only clock. Persist remaining visible time across short slideshow visits. */
const val STOCK_BROWSE_MILLIS = 10_000L
data class StockBrowseClock(val page: Int = 0, val featuredPage: Int = 0, val remainingMillis: Long = STOCK_BROWSE_MILLIS)

// Card: 109sp text + 20dp padding + five 3dp gaps. Rows have an 8dp gap.
fun stockBrowseCapacity(widthDp: Float, heightDp: Float, fontScale: Float = 1f): Int {
    val columns = if (widthDp >= 500f) 3 else 2
    val cardHeight = 109f * fontScale.coerceAtLeast(1f) + 35f
    return columns * ((heightDp + 8f) / (cardHeight + 8f)).toInt().coerceIn(1, 3)
}

/** HD remains pinned when another stock is featured; manual paging works with auto rotation off. */
fun stockBrowsePage(settings: StockSettings, page: Int, capacity: Int): Pair<List<String>, Int> =
    stockGridPage(settings.copy(rotate = true), page, capacity.coerceAtLeast(2))

fun moveStockBrowse(clock: StockBrowseClock, direction: Int, pageCount: Int): StockBrowseClock =
    StockBrowseClock(Math.floorMod(clock.page + direction, pageCount.coerceAtLeast(1)),
        Math.floorMod(clock.featuredPage + direction, 2))

fun advanceStockBrowse(clock: StockBrowseClock, elapsedMillis: Long, pageCount: Int,
    paused: Boolean = false, interactionHeld: Boolean = false, autoRotate: Boolean = true): StockBrowseClock {
    if (paused || interactionHeld || !autoRotate || elapsedMillis <= 0) return clock
    val remaining = clock.remainingMillis.coerceIn(1, STOCK_BROWSE_MILLIS)
    if (elapsedMillis < remaining) return clock.copy(remainingMillis = remaining - elapsedMillis)
    val extra = elapsedMillis - remaining
    val steps = 1 + extra / STOCK_BROWSE_MILLIS
    return StockBrowseClock(
        Math.floorMod(clock.page.toLong() + steps, pageCount.coerceAtLeast(1).toLong()).toInt(),
        ((clock.featuredPage + steps) % 2).toInt(), STOCK_BROWSE_MILLIS - extra % STOCK_BROWSE_MILLIS)
}
