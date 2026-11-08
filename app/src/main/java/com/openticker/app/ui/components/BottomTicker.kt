package com.openticker.app.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openticker.app.slideshow.*
import com.openticker.app.settings.sportsTickerGames
import com.openticker.app.ui.theme.*
import com.openticker.app.util.*
import com.openticker.app.viewmodel.DashboardState
import com.openticker.app.data.repository.StockMarketUiState
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.LocalDateTime
import java.time.ZoneId

/** Stationary container outside DashboardSlideshow. Only its contents crossfade. */
@Composable fun BottomTicker(slide: DashboardSlide, state: DashboardState, stocks: StockMarketUiState) {
    val sportsPosition = remember { mutableFloatStateOf(0f) }
    val weatherPosition = remember { mutableFloatStateOf(0f) }
    val stockPosition = remember { mutableFloatStateOf(0f) }
    val type = tickerContent(slide)
    val content: @Composable (TickerContent) -> Unit = { selected ->
        val enabled = when(selected) { TickerContent.SPORTS -> state.settings.tickerEnabled; TickerContent.WEATHER -> state.settings.weatherTickerEnabled; TickerContent.STOCKS -> state.settings.stocks.tickerEnabled }
        Row(Modifier.fillMaxSize(),verticalAlignment=Alignment.CenterVertically) {
            Text("●  " + when(selected) { TickerContent.SPORTS -> "SCORE WIRE"; TickerContent.WEATHER -> "WEATHER WIRE"; TickerContent.STOCKS -> "MARKET WIRE" },color=TextStrong,fontSize=10.sp,fontWeight=FontWeight.ExtraBold,
                modifier=Modifier.width(132.dp).fillMaxHeight().background(Color(0xFF125659),GenericShape { size, _ ->
                    moveTo(0f,0f);lineTo(size.width-16f,0f);lineTo(size.width,size.height/2);lineTo(size.width-16f,size.height);lineTo(0f,size.height);close()
                }).wrapContentSize(Alignment.Center))
            Box(Modifier.weight(1f).fillMaxHeight().padding(horizontal=16.dp),contentAlignment=Alignment.CenterStart) {
                if(!enabled) Text("${selected.name.lowercase().replaceFirstChar { it.uppercase() }} ticker disabled",color=Muted,fontSize=14.sp)
                else if(selected==TickerContent.SPORTS) SportsTicker(state,state.settings.animationsEnabled,sportsPosition)
                else if(selected==TickerContent.WEATHER) WeatherTicker(state,state.settings.animationsEnabled,weatherPosition)
                else StockTicker(stocks,state.settings.stocks,state.settings.animationsEnabled,stockPosition)
            }
        }
    }
    Column(Modifier.fillMaxWidth().background(SportsBackground).border(1.dp,Border)) {
        Box(Modifier.fillMaxWidth().height(50.dp)) {
            if(state.settings.animationsEnabled) Crossfade(type,animationSpec=tween(500),label="ticker-content") { content(it) }
            else content(type)
        }
        if(state.settings.persistentMarketTicker && state.settings.stocks.tickerEnabled && slide != DashboardSlide.STOCKS) {
            Row(Modifier.fillMaxWidth().height(32.dp).background(Panel),verticalAlignment=Alignment.CenterVertically) {
                Text("MARKET WIRE",color=Ice,fontSize=10.sp,fontWeight=FontWeight.Bold,
                    modifier=Modifier.width(126.dp).padding(start=15.dp))
                Box(Modifier.weight(1f).fillMaxHeight().padding(horizontal=16.dp),contentAlignment=Alignment.CenterStart) {
                    StockTicker(stocks,state.settings.stocks,state.settings.animationsEnabled,stockPosition)
                }
            }
        }
    }
}
@Composable private fun SportsTicker(state: DashboardState, animate: Boolean, position: MutableFloatState) {
    val games = remember(state.scores,state.settings.leagues,state.settings.sportsGroupA,state.settings.sportsGroupB,state.settings.activeSportsGroup,state.settings.tickerAllLeagues) {
        sportsTickerGames(state.scores.values.flatMap { it.games },state.settings)
    }
    val now by produceState(Instant.now()) { while(true) { value=Instant.now(); delay(30_000) } }
    val text=remember(games,state.scores,state.online,now) {
        if(games.isEmpty()) "OPENTICKER   •   Waiting for scheduled games and score updates"
        else games.joinToString("     ◆     ") { game ->
            val feed=state.scores[game.league]
            val stale=!state.online || feed?.restored==true || feed?.error!=null ||
                feed?.updated?.let { now.epochSecond-it.epochSecond>300 }==true
            tickerText(game) + if(stale) " · SAVED ${feed?.updated?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("MMM d, h:mm a")) ?: "time unavailable"}" else ""
        }
    }
    TickerLine(AnnotatedString(text),animate,position)
}
@Composable private fun WeatherTicker(state: DashboardState, animate: Boolean, position: MutableFloatState) {
    val now by produceState(Instant.now()) { while(true) { value=Instant.now(); delay(30_000) } }
    val text=remember(state.weather,state.settings.location,state.settings.celsius,state.weatherUpdated,state.weatherRestored,state.weatherError,state.online,now) {
        val zone=runCatching { ZoneId.of(state.weather?.timezone ?: "UTC") }.getOrDefault(ZoneId.of("UTC"))
        val stale=!state.online || state.weatherRestored || state.weatherError!=null ||
            state.weatherUpdated?.let { now.epochSecond-it.epochSecond>=900 }==true
        val prefix=if(stale && state.weather!=null) "SAVED WEATHER · ${state.weatherUpdated?.atZone(zone)?.format(DateTimeFormatter.ofPattern("MMM d, h:mm a")) ?: "time unavailable"}     ◆     " else ""
        prefix + weatherTickerText(state.weather,state.settings.location,state.settings.celsius,LocalDateTime.ofInstant(now,zone))
    }
    TickerLine(AnnotatedString(text),animate,position)
}

/** Measured once per update; frame changes invalidate drawing, not composition or text layout. */
@Composable internal fun TickerLine(message: AnnotatedString, animate: Boolean, position: MutableFloatState) {
    val style=TextStyle(fontSize=16.sp,color=TextStrong,fontWeight=FontWeight.Medium)
    if(!animate) {
        Text(message,style=style,maxLines=1,softWrap=false,modifier=Modifier.horizontalScroll(rememberScrollState()))
        return
    }
    val measurer=rememberTextMeasurer()
    val layout=remember(message,measurer) { measurer.measure(message,style=style,softWrap=false,maxLines=1) }
    val gap=with(LocalDensity.current) { 48.dp.toPx() }
    val speed=with(LocalDensity.current) { 38.dp.toPx() }
    val cycle by rememberUpdatedState((layout.size.width+gap).coerceAtLeast(1f))
    LaunchedEffect(speed) {
        var previous=withFrameNanos { it }
        while(isActive) withFrameNanos { frame ->
            val seconds=((frame-previous)/1_000_000_000f).coerceIn(0f,.05f)
            position.floatValue=(position.floatValue+speed*seconds)%cycle
            previous=frame
        }
    }
    Canvas(Modifier.fillMaxSize().clipToBounds().semantics { contentDescription=message.text }) {
        var x=-(position.floatValue%cycle)
        val y=(size.height-layout.size.height)/2f
        while(x<size.width) { drawText(layout,topLeft=Offset(x,y)); x+=cycle }
    }
}
