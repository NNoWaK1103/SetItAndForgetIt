package com.openticker.app.ui.components

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.openticker.app.data.repository.StockMarketUiState
import com.openticker.app.settings.StockSettings
import com.openticker.app.util.stockTickerText

@Composable fun StockTicker(state: StockMarketUiState, settings: StockSettings, animate: Boolean, position: MutableFloatState) {
    val message = remember(state.quotes, state.staleSymbols, settings) {
        buildAnnotatedString {
            settings.quoteSymbols().forEachIndexed { i, symbol ->
                if(i>0) append("     |     ")
                val quote = state.quotes[symbol]
                val color = when {
                    quote?.change == null -> Color(0xFFAFBED2)
                    quote.change!! > 0 -> Color(0xFF52E0AD)
                    quote.change!! < 0 -> Color(0xFFFF7D8D)
                    else -> Color(0xFFAFBED2)
                }
                withStyle(SpanStyle(color=color)) { append(stockTickerText(symbol,quote,symbol in state.staleSymbols)) }
            }
        }
    }
    TickerLine(message,animate,position)
}
