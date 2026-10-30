package com.openticker.app.util

import com.openticker.app.data.model.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun stockPrice(price: Double?, dollar: Boolean = true): String = price?.let {
    (if (dollar) "$" else "") + String.format(Locale.US, "%,.2f", it)
} ?: "Unavailable"
fun stockSigned(value: Double?, suffix: String = ""): String = value?.let {
    String.format(Locale.US, "%+.2f%s", if (it == 0.0) 0.0 else it, suffix)
} ?: "—"
fun stockDollarChange(value: Double?): String = value?.let {
    (if(it < 0) "-" else "+") + stockPrice(kotlin.math.abs(it))
} ?: "—"
fun stockTime(time: Instant?): String = time?.atZone(ZoneId.of("America/New_York"))
    ?.format(DateTimeFormatter.ofPattern("MMM d, h:mm a 'ET'", Locale.US)) ?: "Time unavailable"
fun stockFreshness(quote: StockQuote?): String {
    val freshness = when (quote?.freshness) {
        QuoteFreshness.DELAYED -> quote.delayMinutes?.let { "Delayed ${it}m" } ?: "Delayed"
        null -> "Unavailable"
        else -> quote.freshness.label
    }
    return listOfNotNull(freshness,quote?.feedLabel?.takeIf { it.isNotBlank() }).joinToString(" · ")
}
fun stockTickerText(symbol: String, quote: StockQuote?, stale: Boolean = false): String =
    if (quote?.price == null) "$symbol  Unavailable" else
        "$symbol  ${stockPrice(quote.price)}  ${stockSigned(quote.changePercent, "%")}  ${stockFreshness(quote)} · ${stockTime(quote.updated)}${if (stale) " · STALE" else ""}"
