package com.openticker.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import com.openticker.app.ui.theme.DisplayFont
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.runtime.saveable.rememberSaveable
import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
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

private val StockBackground = Color(0xFF061313)
private val StockPanel = Color(0xFF0B1B1C)
private val StockMuted = Color(0xFF8CA3A3)
private val Gain = Color(0xFF20C8B5)
private val Loss = Color(0xFFFF7D8D)
private val Accent = Color(0xFFF58A22)
private val StockBorder = Color(0xFF203536)
private val StockDisplay = DisplayFont
private fun direction(quote: StockQuote?) = when {
    quote?.change == null -> StockMuted
    quote.change!! > 0 -> Gain
    quote.change!! < 0 -> Loss
    else -> StockMuted
}

@Composable fun StockDashboard(state: StockMarketUiState, settings: StockSettings, appearance: Int, onRange: (ChartRange) -> Unit,
    paused: Boolean = false, interactionHeld: Boolean = false, onDetailsVisibilityChange: (Boolean) -> Unit = {}) {
    val now by produceState(Instant.now()) { while (true) { value = Instant.now(); delay(15_000) } }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var featuredPage by rememberSaveable { mutableIntStateOf(0) }
    var remaining by rememberSaveable { mutableLongStateOf(STOCK_BROWSE_MILLIS) }
    var gridCapacity by remember { mutableIntStateOf(2) }
    var locallyPaused by rememberSaveable { mutableStateOf(false) }
    var selectedSymbol by remember { mutableStateOf<String?>(null) }
    val detailsCallback by rememberUpdatedState(onDetailsVisibilityChange)
    DisposableEffect(selectedSymbol != null) {
        val wasVisible=selectedSymbol != null
        if(wasVisible) detailsCallback(true)
        onDispose { if(wasVisible) detailsCallback(false) }
    }
    fun openDetails(symbol: String) { selectedSymbol = symbol }
    fun closeDetails() { selectedSymbol = null }
    BoxWithConstraints(Modifier.fillMaxSize().background(StockBackground)) {
    val roomy = maxHeight >= 440.dp && maxWidth >= 700.dp && LocalDensity.current.fontScale <= 1.2f
    Column(Modifier.fillMaxSize().padding(horizontal=24.dp, vertical=12.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.SpaceBetween) {
            Column {
                Text("●  PHILADELPHIA MARKET CENTER", color=Gain, fontSize=9.sp,lineHeight=13.sp,letterSpacing=1.5.sp,fontFamily=StockDisplay,fontWeight=FontWeight.Bold)
                Text("Market Dashboard", fontSize=if(roomy) 44.sp else 30.sp, lineHeight=if(roomy) 49.sp else 35.sp, fontWeight=FontWeight.Bold, fontFamily=StockDisplay)
            }
            Text("${state.sessionAt(now).name.replace('_', ' ')} · NYSE", color=StockMuted, fontSize=10.sp, lineHeight=13.sp)
        }

        Row(Modifier.weight(1f), horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            FeaturedStockCard(settings.featured, state, settings.range, { remaining=STOCK_BROWSE_MILLIS; onRange(it) }, Modifier.weight(.67f).fillMaxHeight(), now,
                featuredPage = featuredPage, onDetails = { openDetails(settings.featured) })
            Column(Modifier.weight(.33f).fillMaxHeight(), verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Surface(Modifier.weight(1f).fillMaxWidth(), color=StockPanel, shape=RoundedCornerShape(17.dp),border=BorderStroke(1.dp,StockBorder)) {
                Column {
                Column(Modifier.padding(horizontal=16.dp,vertical=12.dp)) {
                    Text("Watchlist", fontSize=24.sp,lineHeight=28.sp,fontFamily=StockDisplay, fontWeight=FontWeight.Bold)
                    Text("MARKET PRICES", color=StockMuted, fontSize=9.sp,lineHeight=13.sp,letterSpacing=1.sp)
                }
                HorizontalDivider(color=StockBorder)
                BoxWithConstraints(Modifier.weight(1f)) {
                    // Each row includes the original quote timestamp and freshness label.
                    val capacity = (maxHeight.value / (66f * LocalDensity.current.fontScale)).toInt().coerceIn(1, 6)
                    SideEffect { gridCapacity = capacity }
                    val (symbols, pages) = stockBrowsePage(settings, page, capacity)
                    val held = paused || locallyPaused || interactionHeld || selectedSymbol != null
                    LaunchedEffect(pages, capacity, held, settings.rotate) {
                        if (!held && settings.rotate) {
                            var last = SystemClock.elapsedRealtime()
                            while (true) {
                                delay(250)
                                val current = SystemClock.elapsedRealtime()
                                val next = advanceStockBrowse(StockBrowseClock(page, featuredPage, remaining), current-last, pages)
                                page=next.page; featuredPage=next.featuredPage; remaining=next.remainingMillis
                                last=current
                            }
                        }
                    }
                    StockGrid(symbols, state.quotes, Modifier.fillMaxSize(),
                        if (state.online) state.staleSymbols else state.quotes.keys, onSelect = ::openDetails, compactRows = true)
                }
                val pageCount = stockBrowsePage(settings, page, gridCapacity).second
                Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.SpaceBetween) {
                    TextButton(contentPadding=PaddingValues(horizontal=4.dp), modifier=Modifier.defaultMinSize(minWidth=1.dp), onClick={ val next=moveStockBrowse(StockBrowseClock(page,featuredPage,remaining),-1,pageCount); page=next.page; featuredPage=next.featuredPage; remaining=next.remainingMillis }) { Text("Previous",fontSize=11.sp,lineHeight=14.sp) }
                    Text("${Math.floorMod(page,pageCount)+1}/$pageCount · ${if(paused || interactionHeld || selectedSymbol != null) "Held" else if(locallyPaused || !settings.rotate) "Paused" else "${(remaining+999)/1000}s"}",color=StockMuted,fontSize=11.sp,lineHeight=14.sp)
                    TextButton(contentPadding=PaddingValues(horizontal=4.dp), modifier=Modifier.defaultMinSize(minWidth=1.dp), onClick={ locallyPaused=!locallyPaused }, enabled=settings.rotate) { Text(if(locallyPaused) "Resume" else "Pause",fontSize=11.sp,lineHeight=14.sp) }
                    TextButton(contentPadding=PaddingValues(horizontal=4.dp), modifier=Modifier.defaultMinSize(minWidth=1.dp), onClick={ val next=moveStockBrowse(StockBrowseClock(page,featuredPage,remaining),1,pageCount); page=next.page; featuredPage=next.featuredPage; remaining=next.remainingMillis }) { Text("Next",fontSize=11.sp,lineHeight=14.sp) }
                }
                }
                }
                if(roomy && settings.indices.isNotEmpty()) MarketIndexHeader(state, settings.indices, now)
            }
        }
        if(!roomy && settings.indices.isNotEmpty()) MarketIndexHeader(state, settings.indices, now)
        Text(if(!state.configured) "Stock data not configured. Add your HTTPS market-data proxy in Settings." else if (!state.online) "Offline · ${if (state.quotes.isEmpty()) "no saved market quotes" else "showing saved market data · may be stale"}" else state.error ?: "${state.provider} · quotes carry their own data timestamps · checked ${stockTime(state.fetched)}",
            color=if(!state.online || state.error!=null || !state.configured) Accent else StockMuted, fontSize=11.sp,lineHeight=14.sp, maxLines=2, overflow=TextOverflow.Ellipsis)
    }
    }
    selectedSymbol?.let { symbol -> StockQuoteDetails(symbol, state, now, ::closeDetails) }
}

@Composable fun MarketIndexHeader(state: StockMarketUiState, indices: Set<MarketIndex>, now: Instant) {
    Surface(Modifier.fillMaxWidth(), color=StockPanel, shape=RoundedCornerShape(17.dp), border=BorderStroke(1.dp,StockBorder)) {
        BoxWithConstraints {
        if(maxWidth >= 500.dp) {
            Row(Modifier.padding(horizontal=12.dp,vertical=6.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("Market pulse",fontFamily=StockDisplay,fontSize=20.sp,lineHeight=24.sp,fontWeight=FontWeight.Bold)
                indices.forEach { index -> Box(Modifier.weight(1f)) { MarketIndexCard(state,index) } }
            }
        } else Column(Modifier.padding(horizontal=16.dp,vertical=12.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Text("Market pulse",fontSize=23.sp,lineHeight=27.sp,fontFamily=StockDisplay,fontWeight=FontWeight.Bold)
                Text("US INDICES",color=StockMuted,fontSize=8.sp,lineHeight=11.sp)
            }
            Spacer(Modifier.height(6.dp))
            indices.forEach { index ->
                HorizontalDivider(color=StockBorder)
                MarketIndexCard(state,index)
            }
        }
        }
    }
}

@Composable private fun MarketIndexCard(state: StockMarketUiState, index: MarketIndex) {
    val quote = state.quotes[index.symbol]
    Row(Modifier.fillMaxWidth().padding(vertical=5.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(index.label.uppercase(),color=StockMuted,fontSize=9.sp,lineHeight=11.sp)
            Text(stockPrice(quote?.price,false),fontFamily=StockDisplay,fontWeight=FontWeight.Bold,fontSize=15.sp,lineHeight=18.sp)
        }
        Column(horizontalAlignment=Alignment.End) {
            Text(stockSigned(quote?.changePercent,"%"),color=direction(quote),fontFamily=StockDisplay,fontWeight=FontWeight.Bold,fontSize=12.sp,lineHeight=15.sp)
            Text("${stockFreshness(quote)}${if(index.symbol in state.staleSymbols || (!state.online && quote != null)) " · stale" else ""} · ${stockTime(quote?.updated)}",color=StockMuted,fontSize=8.sp,lineHeight=10.sp,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.widthIn(max=170.dp))
        }
    }
}

@Composable fun FeaturedStockCard(symbol: String, state: StockMarketUiState, range: ChartRange, onRange: (ChartRange) -> Unit,
    modifier: Modifier = Modifier, now: Instant = Instant.now(), featuredPage: Int = 0, onDetails: () -> Unit = {}) {
    val quote = state.quotes[symbol]
    val company = stockCompany(symbol)
    val key = symbol to range
    Surface(modifier, color=StockPanel, shape=RoundedCornerShape(17.dp), border=BorderStroke(1.dp,StockBorder)) {
        BoxWithConstraints(Modifier.padding(20.dp)) {
            val compact = maxHeight < 320.dp || LocalDensity.current.fontScale > 1.2f
            val closed = quote?.freshness == QuoteFreshness.PREVIOUS_CLOSE
            Column(Modifier.fillMaxSize().then(if(compact) Modifier.verticalScroll(rememberScrollState()) else Modifier), verticalArrangement=Arrangement.spacedBy(if(compact) 3.dp else 7.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(10.dp),modifier=Modifier.fillMaxWidth().clickable(onClick=onDetails)) {
                    Surface(color=Accent, shape=RoundedCornerShape(10.dp)) {
                        Box(Modifier.defaultMinSize(minWidth=46.dp,minHeight=42.dp).padding(8.dp),contentAlignment=Alignment.Center) {
                            Text(symbol, color=Color.White, fontSize=22.sp,lineHeight=26.sp,fontFamily=StockDisplay,fontWeight=FontWeight.ExtraBold)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(quote?.name ?: company.name, fontFamily=StockDisplay, fontSize=if(compact) 17.sp else 24.sp,lineHeight=if(compact) 21.sp else 29.sp, fontWeight=FontWeight.Bold, maxLines=1, overflow=TextOverflow.Ellipsis)
                        Text("${quote?.exchange ?: company.exchange} · ${state.sessionAt(now).name.replace('_', ' ')}", color=StockMuted,fontSize=10.sp,lineHeight=13.sp)
                    }
                }
                Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    Text(stockPrice(quote?.price),fontFamily=StockDisplay,fontSize=if(compact) 40.sp else 54.sp,lineHeight=if(compact) 46.sp else 60.sp,fontWeight=FontWeight.Bold,letterSpacing=(-1).sp)
                    Text("${stockDollarChange(quote?.change)}  (${stockSigned(quote?.changePercent,"%")})",color=direction(quote),fontSize=12.sp,lineHeight=16.sp,fontWeight=FontWeight.SemiBold)
                }
                Text("${stockFreshness(quote)}${if(symbol in state.staleSymbols || (!state.online && quote != null)) " · stale" else ""} · ${stockTime(quote?.updated)}",color=StockMuted,fontSize=10.sp,lineHeight=13.sp,maxLines=2)
                if(!compact || featuredPage != 0) {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        ChartRange.entries.forEach { item ->
                            TextButton(onClick={onRange(item)},contentPadding=PaddingValues(horizontal=10.dp,vertical=0.dp),modifier=Modifier.height(32.dp).defaultMinSize(minWidth=1.dp), colors=ButtonDefaults.textButtonColors(containerColor=if(item==range) Accent.copy(alpha=.14f) else Color.Transparent)) {
                                Text(item.label,color=if(item==range) Accent else StockMuted,fontSize=11.sp,lineHeight=14.sp,fontWeight=FontWeight.Bold)
                            }
                        }
                    }
                    StockChart(state.histories[key], range, state.historyErrors[key] ?: if (!state.online || key in state.staleHistories) "Saved chart · may be stale" else null, Accent, Modifier.fillMaxWidth().then(if(compact) Modifier.height(150.dp) else Modifier.weight(1f)))
                }
                if(!compact || featuredPage == 0) {
                    HorizontalDivider(color=StockBorder)
                    if(!compact) {
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            StockMetricTile("Previous close",stockPrice(quote?.previousClose),Modifier.weight(1f))
                            StockMetricTile(if(closed) "Session high" else "Day high",stockPrice(quote?.high),Modifier.weight(1f))
                            StockMetricTile(if(closed) "Session low" else "Day low",stockPrice(quote?.low),Modifier.weight(1f))
                            StockMetricTile(if(closed) "Session volume" else "Volume",quote?.volume?.let { String.format(java.util.Locale.US,"%,d",it) } ?: "Unavailable",Modifier.weight(1f))
                        }
                    } else {
                        StockMetric("Previous close",stockPrice(quote?.previousClose))
                        StockMetric(if(closed) "Session high / low" else "Day high / low","${stockPrice(quote?.high)} / ${stockPrice(quote?.low)}")
                        StockMetric(if(closed) "Session volume" else "Volume",quote?.volume?.let { String.format(java.util.Locale.US,"%,d",it) } ?: "Unavailable")
                    }
                    Text(quote?.feedLabel ?: "Feed label unavailable",color=StockMuted,fontSize=9.sp,lineHeight=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
                if(compact) Text(if(featuredPage==0) "OVERVIEW · price history on next page" else "PRICE HISTORY · metrics on next page",color=StockMuted,fontSize=9.sp,lineHeight=12.sp)
            }
        }
    }
}

@Composable private fun StockMetricTile(label: String, value: String, modifier: Modifier) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Text(label,color=StockMuted,fontSize=10.sp,lineHeight=13.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
        Text(value,fontSize=15.sp,lineHeight=19.sp,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
    }
}

@Composable private fun StockQuoteDetails(symbol: String, state: StockMarketUiState, now: Instant, onDismiss: () -> Unit) {
    val quote = state.quotes[symbol]
    val closed = quote?.freshness == QuoteFreshness.PREVIOUS_CLOSE
    AlertDialog(onDismissRequest=onDismiss, title={ Text("$symbol · ${quote?.name ?: stockCompany(symbol).name}") },
        text={ Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(stockPrice(quote?.price),fontSize=30.sp,lineHeight=36.sp,fontWeight=FontWeight.Bold)
            Text("${stockDollarChange(quote?.change)} (${stockSigned(quote?.changePercent,"%")})",color=direction(quote))
            Text("Market ${state.sessionAt(now).name} · ${quote?.exchange ?: stockCompany(symbol).exchange}",color=StockMuted)
            Text("${stockFreshness(quote)}${if(symbol in state.staleSymbols || (!state.online && quote != null)) " · stale" else ""}",color=StockMuted)
            Text("Quote timestamp: ${stockTime(quote?.updated)}",color=StockMuted)
            StockMetric("Previous close",stockPrice(quote?.previousClose))
            StockMetric(if(closed) "Session high / low" else "Day high / low","${stockPrice(quote?.high)} / ${stockPrice(quote?.low)}")
            StockMetric(if(closed) "Session volume" else "Volume",quote?.volume?.let { String.format(java.util.Locale.US,"%,d",it) } ?: "Unavailable")
            Text(quote?.feedLabel ?: "Feed label unavailable",color=StockMuted)
            Text("${state.provider} · cached quote · no additional request",fontSize=11.sp,lineHeight=14.sp,color=StockMuted)
        } }, confirmButton={ TextButton(onClick=onDismiss) { Text("Close") } })
}
@Composable private fun StockMetric(label: String, value: String) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(label,color=StockMuted,fontSize=11.sp,lineHeight=14.sp,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f))
        Text(value,fontSize=11.sp,lineHeight=14.sp,maxLines=1)
    }
}
@Composable fun StockChart(history: StockHistory?, range: ChartRange, error: String?, color: Color, modifier: Modifier = Modifier) {
    val points = history?.points.orEmpty()
    if(points.size < 2) {
        Box(modifier,contentAlignment=Alignment.Center) {
            Text(error ?: history?.message ?: if(range==ChartRange.DAY) "Intraday data unavailable for this feed." else "Historical data unavailable.",color=StockMuted,fontSize=12.sp,lineHeight=15.sp)
        }
        return
    }
    Column(modifier) {
        Text(listOfNotNull(error ?: history?.message,history?.feedLabel,"${history?.freshness?.label} · ${stockTime(points.first().time)} – ${stockTime(points.last().time)}").joinToString(" · "),color=StockMuted,fontSize=9.sp,lineHeight=12.sp,maxLines=2)
        val pathPoints = remember(points) {
            val min = points.minOf { it.close }; val max = points.maxOf { it.close }
            val span = (max-min).takeIf { it > 0 } ?: (min*.01).coerceAtLeast(1.0)
            val start=points.first().time.toEpochMilli(); val duration=(points.last().time.toEpochMilli()-start).coerceAtLeast(1)
            points.map { ((it.time.toEpochMilli()-start).toFloat()/duration) to (if(max==min) .5f else ((it.close-min)/span).toFloat()) }
        }
        Canvas(Modifier.fillMaxWidth().weight(1f).semantics { contentDescription="${range.label} price chart, ${points.size} historical prices" }) {
            repeat(3) { i -> val y=size.height*(i+1)/4;drawLine(Color(0xFF203536),Offset(0f,y),Offset(size.width,y)) }
            val path=Path()
            pathPoints.forEachIndexed { i,(x,y) ->
                val px=4+x*(size.width-8); val py=4+(1-y)*(size.height-8)
                if(i==0) path.moveTo(px,py) else path.lineTo(px,py)
            }
            val area = Path().apply {
                addPath(path)
                lineTo(size.width-4, size.height)
                lineTo(4f, size.height)
                close()
            }
            drawPath(area,Brush.verticalGradient(listOf(color.copy(alpha=.26f),color.copy(alpha=.015f))))
            drawPath(path,color,style=androidx.compose.ui.graphics.drawscope.Stroke(width=2.dp.toPx(),cap=StrokeCap.Round,join=StrokeJoin.Round))
        }
    }
}
@Composable fun StockGrid(symbols: List<String>, quotes: Map<String, StockQuote>, modifier: Modifier = Modifier, staleSymbols: Set<String> = emptySet(), onSelect: ((String) -> Unit)? = null, compactRows: Boolean = false) {
    BoxWithConstraints(modifier) {
        val columns=if(compactRows) 1 else if(maxWidth>=500.dp) 3 else 2
        val rows=symbols.chunked(columns)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(if(compactRows) 0.dp else 8.dp)) {
            if(symbols.isEmpty()) Text("Select companies and categories in Settings.",color=StockMuted,fontSize=14.sp,lineHeight=18.sp)
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    row.forEach { symbol ->
                        val q=quotes[symbol]
                        Surface(Modifier.weight(1f).then(if(onSelect != null) Modifier.clickable { onSelect(symbol) } else Modifier),color=StockPanel,shape=RoundedCornerShape(if(compactRows) 0.dp else 12.dp),border=if(compactRows) null else BorderStroke(1.dp,StockBorder)) {
                            // Normal font scale: 109dp of text (including two freshness lines),
                            // 15dp gaps and 20dp padding = 144dp maximum card height.
                            if(compactRows) {
                                Column {
                                    Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                        Surface(color=Color(0xFF203334),shape=RoundedCornerShape(6.dp)) {
                                            Box(Modifier.size(30.dp),contentAlignment=Alignment.Center) {
                                                Text(symbol.take(1),fontFamily=StockDisplay,fontSize=18.sp,lineHeight=22.sp,fontWeight=FontWeight.Bold)
                                            }
                                        }
                                        Column(Modifier.weight(1f)) {
                                            Text(symbol,fontFamily=StockDisplay,fontSize=14.sp,lineHeight=17.sp,fontWeight=FontWeight.Bold)
                                            Text(q?.name ?: stockCompany(symbol).name,color=StockMuted,fontSize=9.sp,lineHeight=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                                        }
                                        Column(horizontalAlignment=Alignment.End) {
                                            Text(stockPrice(q?.price),fontFamily=StockDisplay,fontSize=17.sp,lineHeight=20.sp,fontWeight=FontWeight.Bold)
                                            Text(stockSigned(q?.changePercent,"%"),color=direction(q),fontSize=9.sp,lineHeight=11.sp)
                                        }
                                        Text("›",color=StockMuted,fontSize=20.sp)
                                    }
                                    Text("${stockFreshness(q)}${if(symbol in staleSymbols) " · stale" else ""} · ${stockTime(q?.updated)}",Modifier.padding(start=54.dp,end=14.dp,bottom=5.dp),color=StockMuted,fontSize=8.sp,lineHeight=10.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                                    HorizontalDivider(color=StockBorder)
                                }
                            } else Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                                Row {
                                    Text(symbol,fontSize=16.sp,lineHeight=20.sp,maxLines=1,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                                    Text(if(q?.change==null) "—" else if(q.change!!>0) "▲" else if(q.change!!<0) "▼" else "•",color=direction(q),fontSize=12.sp,lineHeight=15.sp)
                                }
                                Text(q?.name ?: stockCompany(symbol).name,color=StockMuted,fontSize=10.sp,lineHeight=13.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                                Text(stockPrice(q?.price),fontSize=21.sp,lineHeight=26.sp,fontWeight=FontWeight.SemiBold,maxLines=1)
                                Text("${stockDollarChange(q?.change)}  ${stockSigned(q?.changePercent,"%")}",color=direction(q),fontSize=11.sp,lineHeight=14.sp,maxLines=1)
                                Text(stockFreshness(q)+if(symbol in staleSymbols) " · stale" else "",color=StockMuted,fontSize=9.sp,lineHeight=12.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
                                Text(stockTime(q?.updated),color=StockMuted,fontSize=9.sp,lineHeight=12.sp,maxLines=1)
                            }
                        }
                    }
                    repeat(columns-row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
