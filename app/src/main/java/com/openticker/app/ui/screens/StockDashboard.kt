package com.openticker.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openticker.app.data.model.*
import com.openticker.app.data.repository.StockMarketUiState
import com.openticker.app.settings.*
import com.openticker.app.util.*
import kotlinx.coroutines.delay
import java.time.Instant

private val StockBackground = Color(0xFF090F1B)
private val StockPanel = Color(0xFF141E2D)
private val StockMuted = Color(0xFF9DAEC5)
private val Gain = Color(0xFF52E0AD)
private val Loss = Color(0xFFFF7D8D)
private val Accent = Color(0xFFF7A45B)
private fun direction(quote: StockQuote?) = when {
    quote?.change == null -> StockMuted
    quote.change!! > 0 -> Gain
    quote.change!! < 0 -> Loss
    else -> StockMuted
}

@Composable fun StockDashboard(state: StockMarketUiState, settings: StockSettings, appearance: Int, onRange: (ChartRange) -> Unit) {
    val now by produceState(Instant.now()) { while (true) { value = Instant.now(); delay(15_000) } }
    val (symbols, pages) = stockGridPage(settings, appearance)
    Column(Modifier.fillMaxSize().background(StockBackground).padding(horizontal=18.dp, vertical=12.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
        MarketIndexHeader(state, settings.indices, now)
        Row(Modifier.weight(1f), horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            FeaturedStockCard(settings.featured, state, settings.range, onRange, Modifier.weight(.38f).fillMaxHeight(), now)
            Column(Modifier.weight(.62f).fillMaxHeight(), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("COMPANY WATCHLIST", fontSize=14.sp, fontWeight=FontWeight.Bold, letterSpacing=1.sp, modifier=Modifier.weight(1f))
                    Text("${if(settings.rotate) Math.floorMod(appearance,pages)+1 else 1} / $pages", color=StockMuted, fontSize=12.sp)
                }
                Text("Fortune 500 is a revenue ranking, not a stock index. Membership varies by year.", color=StockMuted, fontSize=10.sp)
                StockGrid(symbols, state.quotes, Modifier.weight(1f),state.staleSymbols)
            }
        }
        Text(state.error ?: "${state.provider} · quotes carry their own data timestamps · checked ${stockTime(state.fetched)}",
            color=if(state.error!=null) Accent else StockMuted, fontSize=11.sp, maxLines=2, overflow=TextOverflow.Ellipsis)
    }
}

@Composable fun MarketIndexHeader(state: StockMarketUiState, indices: Set<MarketIndex>, now: Instant) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(12.dp), verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.widthIn(min=115.dp)) {
            Text("US MARKETS", fontSize=15.sp, fontWeight=FontWeight.Bold, letterSpacing=1.sp)
            Text("${state.sessionAt(now).let { if(it==MarketSession.UNKNOWN) "STATUS UNKNOWN" else it.name }} · NYSE", color=StockMuted, fontSize=10.sp)
            Text(stockTime(state.statusUpdated), color=StockMuted, fontSize=9.sp)
        }
        indices.forEach { index ->
            val quote = state.quotes[index.symbol]
            Surface(Modifier.weight(1f), color=StockPanel, shape=RoundedCornerShape(10.dp)) {
                Column(Modifier.padding(horizontal=12.dp,vertical=8.dp)) {
                    Text(index.label, color=StockMuted, fontSize=11.sp, maxLines=1)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp), verticalAlignment=Alignment.CenterVertically) {
                        Text(stockPrice(quote?.price,false), fontSize=17.sp, fontWeight=FontWeight.SemiBold, maxLines=1)
                        Text(stockSigned(quote?.changePercent,"%"),color=direction(quote),fontSize=12.sp,maxLines=1)
                    }
                    Text("${stockFreshness(quote)}${if(index.symbol in state.staleSymbols) " · stale" else ""} · ${stockTime(quote?.updated)}", color=StockMuted, fontSize=9.sp, maxLines=1)
                }
            }
        }
    }
}

@Composable fun FeaturedStockCard(symbol: String, state: StockMarketUiState, range: ChartRange, onRange: (ChartRange) -> Unit,
    modifier: Modifier = Modifier, now: Instant = Instant.now()) {
    val quote = state.quotes[symbol]
    val company = stockCompany(symbol)
    val key = symbol to range
    Surface(modifier, color=StockPanel, shape=RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(symbol, color=Accent, fontSize=24.sp, fontWeight=FontWeight.ExtraBold, modifier=Modifier.weight(1f))
                Text(quote?.exchange ?: company.exchange, color=StockMuted, fontSize=11.sp)
            }
            Text(quote?.name ?: company.name, fontSize=19.sp, fontWeight=FontWeight.SemiBold, maxLines=1, overflow=TextOverflow.Ellipsis)
            Text("MARKET ${state.sessionAt(now).name}",color=StockMuted,fontSize=10.sp)
            Text(stockPrice(quote?.price), fontSize=36.sp, fontWeight=FontWeight.Bold, letterSpacing=(-1).sp)
            Text("${stockDollarChange(quote?.change)}  (${stockSigned(quote?.changePercent,"%")})",color=direction(quote),fontSize=17.sp,fontWeight=FontWeight.SemiBold)
            Text("${stockFreshness(quote)}${if(symbol in state.staleSymbols) " · stale" else ""} · ${stockTime(quote?.updated)}",color=StockMuted,fontSize=10.sp)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                ChartRange.entries.forEach { item ->
                    TextButton(onClick={onRange(item)},contentPadding=PaddingValues(horizontal=5.dp),modifier=Modifier.defaultMinSize(minWidth=1.dp)) {
                        Text(item.label,color=if(item==range) Accent else StockMuted,fontSize=12.sp,fontWeight=FontWeight.Bold)
                    }
                }
            }
            StockChart(state.histories[key], range, state.historyErrors[key], direction(quote), Modifier.fillMaxWidth().height(122.dp))
            HorizontalDivider(color=Color(0xFF2A3546))
            val closed = quote?.freshness == QuoteFreshness.PREVIOUS_CLOSE
            StockMetric("Previous close",stockPrice(quote?.previousClose))
            StockMetric(if(closed) "Session high / low" else "Day high / low", "${stockPrice(quote?.high)} / ${stockPrice(quote?.low)}")
            StockMetric(if(closed) "Session volume" else "Volume",quote?.volume?.let { String.format(java.util.Locale.US,"%,d",it) } ?: "Unavailable")
        }
    }
}
@Composable private fun StockMetric(label: String, value: String) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(label,color=StockMuted,fontSize=11.sp,modifier=Modifier.weight(1f))
        Text(value,fontSize=11.sp,maxLines=1)
    }
}
@Composable fun StockChart(history: StockHistory?, range: ChartRange, error: String?, color: Color, modifier: Modifier = Modifier) {
    val points = history?.points.orEmpty()
    if(points.size < 2) {
        Box(modifier,contentAlignment=Alignment.Center) {
            Text(error ?: history?.message ?: if(range==ChartRange.DAY) "Intraday data unavailable for this feed." else "Historical data unavailable.",color=StockMuted,fontSize=12.sp)
        }
        return
    }
    Column(modifier) {
        Text(listOfNotNull(error ?: history?.message,"${history?.freshness?.label} · ${stockTime(points.first().time)} – ${stockTime(points.last().time)}").joinToString(" · "),color=StockMuted,fontSize=9.sp,maxLines=2)
        val pathPoints = remember(points) {
            val min = points.minOf { it.close }; val max = points.maxOf { it.close }
            val span = (max-min).takeIf { it > 0 } ?: (min*.01).coerceAtLeast(1.0)
            val start=points.first().time.toEpochMilli(); val duration=(points.last().time.toEpochMilli()-start).coerceAtLeast(1)
            points.map { ((it.time.toEpochMilli()-start).toFloat()/duration) to (if(max==min) .5f else ((it.close-min)/span).toFloat()) }
        }
        Canvas(Modifier.fillMaxWidth().weight(1f).semantics { contentDescription="${range.label} price chart, ${points.size} historical prices" }) {
            repeat(3) { i -> val y=size.height*(i+1)/4;drawLine(Color(0xFF2A3546),Offset(0f,y),Offset(size.width,y)) }
            val path=Path()
            pathPoints.forEachIndexed { i,(x,y) ->
                val px=4+x*(size.width-8); val py=4+(1-y)*(size.height-8)
                if(i==0) path.moveTo(px,py) else path.lineTo(px,py)
            }
            drawPath(path,color,style=androidx.compose.ui.graphics.drawscope.Stroke(width=2.dp.toPx(),cap=StrokeCap.Round,join=StrokeJoin.Round))
        }
    }
}
@Composable fun StockGrid(symbols: List<String>, quotes: Map<String, StockQuote>, modifier: Modifier = Modifier, staleSymbols: Set<String> = emptySet()) {
    BoxWithConstraints(modifier) {
        val columns=if(maxWidth>=500.dp) 3 else 2
        val rows=symbols.chunked(columns)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(symbols.isEmpty()) Text("Select companies and categories in Settings.",color=StockMuted,fontSize=14.sp)
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    row.forEach { symbol ->
                        val q=quotes[symbol]
                        Surface(Modifier.weight(1f),color=StockPanel,shape=RoundedCornerShape(10.dp)) {
                            Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                                Row {
                                    Text(symbol,fontSize=16.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                                    Text(if(q?.change==null) "—" else if(q.change!!>0) "▲" else if(q.change!!<0) "▼" else "•",color=direction(q),fontSize=12.sp)
                                }
                                Text(q?.name ?: stockCompany(symbol).name,color=StockMuted,fontSize=10.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                                Text(stockPrice(q?.price),fontSize=21.sp,fontWeight=FontWeight.SemiBold,maxLines=1)
                                Text("${stockDollarChange(q?.change)}  ${stockSigned(q?.changePercent,"%")}",color=direction(q),fontSize=11.sp,maxLines=1)
                                Text(stockFreshness(q)+if(symbol in staleSymbols) " · stale" else "",color=StockMuted,fontSize=9.sp)
                                Text(stockTime(q?.updated),color=StockMuted,fontSize=9.sp,maxLines=1)
                            }
                        }
                    }
                    repeat(columns-row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
