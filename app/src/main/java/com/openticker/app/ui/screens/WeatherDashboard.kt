package com.openticker.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import android.os.SystemClock
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openticker.app.ui.components.*
import com.openticker.app.ui.theme.*
import com.openticker.app.viewmodel.DashboardState
import com.openticker.app.util.*
import kotlinx.coroutines.delay
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable fun WeatherDashboard(state: DashboardState, onRefresh: () -> Unit, modifier: Modifier = Modifier, interactionHeld: Boolean = false) {
    val weather=state.weather
    val zone=remember(weather?.timezone) { runCatching { ZoneId.of(weather?.timezone ?: "America/New_York") }.getOrDefault(ZoneId.of("UTC")) }
    val now by produceState(ZonedDateTime.now(zone),zone) {
        while(true) { value=ZonedDateTime.now(zone); delay(30_000) }
    }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var remaining by rememberSaveable { mutableLongStateOf(10_000L) }
    var browsePaused by rememberSaveable { mutableStateOf(false) }
    val automatic=!state.settings.slideshowPaused && !interactionHeld && !browsePaused
    val day=weather?.days?.find { it.date==now.toLocalDate() }
    BoxWithConstraints(modifier.fillMaxSize().background(WeatherBackground)) {
        val compact=maxHeight<480.dp || maxWidth<800.dp
        LaunchedEffect(compact, automatic, weather != null) {
            if(compact && automatic && weather != null) {
                var previous=SystemClock.elapsedRealtime()
                while(true) {
                    delay(250)
                    val current=SystemClock.elapsedRealtime()
                    val next=advanceWeatherPage(WeatherPageClock(page,remaining),current-previous)
                    page=next.page; remaining=next.remainingMillis; previous=current
                }
            }
        }
        if(compact) {
            CompactWeatherDashboard(state,now,page,automatic,onRefresh,
                onPage={page=it;remaining=10_000L},onTogglePause={browsePaused=!browsePaused})
            return@BoxWithConstraints
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=32.dp,vertical=16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    Text("●  LOCAL WEATHER CENTER",color=Mint,fontFamily=DisplayFont,fontSize=10.sp,letterSpacing=1.8.sp)
                    Text("${state.settings.location.name} Weather",fontFamily=DisplayFont,fontWeight=androidx.compose.ui.text.font.FontWeight.Bold,
                        fontSize=42.sp,lineHeight=46.sp,color=TextStrong,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
                Text(now.format(DateTimeFormatter.ofPattern("EEE, MMM d",Locale.US)),color=Muted,fontSize=12.sp)
                TextButton(onClick=onRefresh,enabled=state.online && !state.refreshing) { Text("Refresh",fontSize=11.sp) }
            }
            val warning=when {
                !state.online -> if(weather == null) "Network offline · no saved weather" else "Network offline · showing saved weather"
                state.weatherError != null -> state.weatherError
                state.weatherRestored -> "Saved weather · awaiting refresh"
                state.weatherUpdated?.let { now.toEpochSecond() - it.epochSecond >= 900 } == true -> "Weather update overdue · showing last received data"
                weather == null -> "Weather feed · awaiting first response"
                else -> null
            }
            warning?.let { Text(it,color=Color(0xFFFFCA82),fontSize=11.sp,lineHeight=14.sp) }
            if(weather==null) {
                EmptyPanel(if(state.online) "Waiting for weather" else "Waiting for a connection","Current conditions and forecasts will appear here.")
            } else {
                Row(Modifier.fillMaxWidth().height(300.dp),horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                    WeatherPanel(Modifier.weight(2.2f).fillMaxHeight(),compact=true) {
                        CurrentWeatherCard(weather,day,state.settings.location,state.settings.celsius,large=true)
                        Text(state.weatherUpdated?.atZone(zone)?.format(DateTimeFormatter.ofPattern("'Updated' MMM d, h:mm a z",Locale.US)) ?: "Update time unavailable",color=Muted,fontSize=9.sp,lineHeight=12.sp)
                        Spacer(Modifier.weight(1f))
                        WeatherReferenceMetrics(weather,day,state.settings.celsius)
                    }
                    WeatherPanel(Modifier.weight(1f).fillMaxHeight(),compact=true) {
                        WeatherOutlook(weather,day,now,state.settings.celsius)
                    }
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                    WeatherPanel(Modifier.weight(2.2f)) {
                        WeatherHeading("Hourly forecast","NEXT 12 HOURS", "${state.settings.location.name.uppercase(Locale.US)} LOCAL TIME")
                        HourlyForecast(forecastHours(weather,now.toInstant()),state.settings.celsius)
                    }
                    WeatherPanel(Modifier.weight(1f)) {
                        WeatherHeading("7-day forecast", trailing=state.settings.location.name.uppercase(Locale.US))
                        WeeklyForecast(weather.days.filter { !it.date.isBefore(now.toLocalDate()) }.take(7),state.settings.celsius,now.toLocalDate())
                    }
                }
            }
            Text("Weather: Open-Meteo model estimates & forecasts · CC BY 4.0 · Times local to selected city",color=Muted,fontSize=9.sp,lineHeight=12.sp)
        }
    }
}


/** Compact tablets retain automatic two-page browsing; short viewports can also scroll. */
@Composable private fun CompactWeatherDashboard(
    state: DashboardState, now: ZonedDateTime, page: Int, automatic: Boolean,
    onRefresh: () -> Unit, onPage: (Int) -> Unit, onTogglePause: () -> Unit
) {
    val weather=state.weather
    val day=weather?.days?.find { it.date==now.toLocalDate() }
    val status=when {
        !state.online -> if(weather==null) "Offline · no saved weather" else "Offline · showing saved weather"
        state.weatherError != null -> if(weather==null) "Weather update failed · no saved weather" else "Weather update failed · showing last received data"
        state.weatherRestored -> "Saved weather · awaiting refresh"
        state.weatherUpdated?.let { now.toEpochSecond()-it.epochSecond>=900 }==true -> "Weather update overdue · showing last received data"
        weather==null -> "Awaiting weather forecast"
        else -> state.weatherUpdated?.atZone(now.zone)?.format(DateTimeFormatter.ofPattern("'Updated' MMM d, h:mm a z",Locale.US)) ?: "Update time unavailable"
    }
    Column(Modifier.fillMaxSize().padding(horizontal=16.dp,vertical=6.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth().height(32.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("Now & outlook", "Hourly & 7-day").forEachIndexed { index,label ->
                TextButton(onClick={onPage(index)},modifier=Modifier.height(32.dp),contentPadding=PaddingValues(horizontal=10.dp),
                    colors=ButtonDefaults.textButtonColors(containerColor=if(page==index) Mint.copy(alpha=.12f) else Color.Transparent)) {
                    Text(label,color=if(page==index) Mint else Muted,fontSize=12.sp, lineHeight=15.sp)
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick=onTogglePause,modifier=Modifier.height(32.dp),contentPadding=PaddingValues(horizontal=8.dp)) { Text(if(automatic) "Auto pages on" else "Pages paused",fontSize=11.sp, lineHeight=14.sp) }
            TextButton(onClick=onRefresh,enabled=state.online && !state.refreshing,modifier=Modifier.height(32.dp),contentPadding=PaddingValues(horizontal=8.dp)) { Text("Refresh",fontSize=11.sp, lineHeight=14.sp) }
        }
        Text(status,color=if(!state.online || state.weatherError!=null || state.weatherRestored || state.weatherUpdated?.let { now.toEpochSecond()-it.epochSecond>=900 }==true) Color(0xFFFFCA82) else Muted,
            fontSize=10.sp,lineHeight=13.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            if(weather==null) EmptyPanel("Waiting for weather","Current estimates and forecasts will appear here.")
            else if(page==0) {
                Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                    WeatherPanel(Modifier.weight(1.35f),compact=true) {
                        CurrentWeatherCard(weather,day,state.settings.location,state.settings.celsius)
                        WeatherQuickDetails(weather,day,state.settings.celsius)
                    }
                    Box(Modifier.weight(1f)) { WeatherInsightStack(weatherInsights(weather,now.toInstant(),state.settings.celsius)) }
                }
            } else {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    WeatherPanel(Modifier.weight(1.5f),compact=true) {
                        SectionLabel("NEXT 12 HOURS", "TEMP / PRECIP · ${if(state.settings.celsius) "°C" else "°F"}")
                        HourlyForecast(forecastHours(weather,now.toInstant()),state.settings.celsius,compact=true)
                    }
                    WeatherPanel(Modifier.weight(1f),compact=true) {
                        SectionLabel("7-DAY OUTLOOK")
                        WeeklyForecast(weather.days.filter { !it.date.isBefore(now.toLocalDate()) }.take(7),state.settings.celsius,now.toLocalDate(),compact=true)
                    }
                }
            }
        }
        Text("Open-Meteo model estimates & forecasts · CC BY 4.0 · City local time",color=Muted,fontSize=9.sp,lineHeight=11.sp,maxLines=1)
    }
}
