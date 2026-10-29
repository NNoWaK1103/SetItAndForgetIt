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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openticker.app.slideshow.*
import com.openticker.app.ui.theme.*
import com.openticker.app.util.*
import com.openticker.app.viewmodel.DashboardState
import kotlinx.coroutines.isActive
import java.time.LocalDateTime
import java.time.ZoneId

/** Stationary container outside DashboardSlideshow. Only its contents crossfade. */
@Composable fun BottomTicker(slide: DashboardSlide, state: DashboardState) {
    val sportsPosition = remember { mutableFloatStateOf(0f) }
    val weatherPosition = remember { mutableFloatStateOf(0f) }
    val type = tickerContent(slide)
    val content: @Composable (TickerContent) -> Unit = { selected ->
        val enabled = if(selected==TickerContent.SPORTS) state.settings.tickerEnabled else state.settings.weatherTickerEnabled
        Row(Modifier.fillMaxSize(),verticalAlignment=Alignment.CenterVertically) {
            Text(if(selected==TickerContent.SPORTS) "SCORE WIRE" else "WEATHER WIRE",color=SportsBackground,fontSize=11.sp,fontWeight=FontWeight.ExtraBold,
                modifier=Modifier.width(126.dp).fillMaxHeight().background(if(selected==TickerContent.SPORTS) Mint else Ice).wrapContentSize(Alignment.Center))
            Box(Modifier.weight(1f).fillMaxHeight().padding(horizontal=16.dp),contentAlignment=Alignment.CenterStart) {
                if(!enabled) Text("${if(selected==TickerContent.SPORTS) "Sports" else "Weather"} ticker disabled",color=Muted,fontSize=14.sp)
                else if(selected==TickerContent.SPORTS) SportsTicker(state,state.settings.animationsEnabled,sportsPosition)
                else WeatherTicker(state,state.settings.animationsEnabled,weatherPosition)
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(60.dp).background(Color(0xFF080C13))) {
        if(state.settings.animationsEnabled) Crossfade(type,animationSpec=tween(500),label="ticker-content") { content(it) }
        else content(type)
    }
}
@Composable private fun SportsTicker(state: DashboardState, animate: Boolean, position: MutableFloatState) {
    val games = remember(state.scores,state.settings.leagues) { sortedGames(filterLeagues(state.scores.values.flatMap { it.games },state.settings.leagues)) }
    val text=remember(games) { if(games.isEmpty()) "OPENTICKER   •   Waiting for scheduled games and score updates" else games.joinToString("     ◆     ") { tickerText(it) } }
    TickerLine(text,animate,position)
}
@Composable private fun WeatherTicker(state: DashboardState, animate: Boolean, position: MutableFloatState) {
    val text=remember(state.weather,state.settings.location,state.settings.celsius,state.weatherUpdated) {
        val zone=runCatching { ZoneId.of(state.weather?.timezone ?: "UTC") }.getOrDefault(ZoneId.of("UTC"))
        weatherTickerText(state.weather,state.settings.location,state.settings.celsius,LocalDateTime.now(zone))
    }
    TickerLine(text,animate,position)
}

/** Measured once per update; frame changes invalidate drawing, not composition or text layout. */
@Composable private fun TickerLine(message: String, animate: Boolean, position: MutableFloatState) {
    val style=TextStyle(fontSize=16.sp,color=Color(0xFFDCE5F3),fontWeight=FontWeight.Medium)
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
    Canvas(Modifier.fillMaxSize().clipToBounds().semantics { contentDescription=message }) {
        var x=-(position.floatValue%cycle)
        val y=(size.height-layout.size.height)/2f
        while(x<size.width) { drawText(layout,topLeft=Offset(x,y)); x+=cycle }
    }
}
