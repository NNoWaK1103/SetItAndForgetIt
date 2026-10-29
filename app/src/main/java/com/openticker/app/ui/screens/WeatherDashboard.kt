package com.openticker.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openticker.app.ui.components.*
import com.openticker.app.ui.theme.*
import com.openticker.app.viewmodel.DashboardState
import kotlinx.coroutines.delay
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable fun WeatherDashboard(state: DashboardState, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    val weather=state.weather
    val zone=remember(weather?.timezone) { runCatching { ZoneId.of(weather?.timezone ?: "America/New_York") }.getOrDefault(ZoneId.of("UTC")) }
    val now by produceState(ZonedDateTime.now(zone),zone) {
        while(true) { value=ZonedDateTime.now(zone); delay(30_000) }
    }
    val day=weather?.days?.find { it.date==now.toLocalDate() }
    BoxWithConstraints(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(WeatherBackground,Color(0xFF183457))))) {
        val compact=maxHeight<550.dp
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=22.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(if(compact) 8.dp else 14.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SectionLabel("YOUR WEATHER, AT A GLANCE",state.weatherUpdated?.atZone(zone)?.format(DateTimeFormatter.ofPattern("'Updated' h:mm a"))) }
                TextButton(onClick=onRefresh,enabled=state.online && !state.refreshing,modifier=Modifier.height(30.dp),contentPadding=PaddingValues(horizontal=12.dp)) { Text("Refresh",fontSize=11.sp) }
            }
            val warning=if(!state.online) "Offline · weather may be out of date" else state.weatherError
            warning?.let { Text(it,color=Color(0xFFFFCA82),fontSize=11.sp) }
            if(weather==null) {
                Text(state.settings.location.label,fontSize=26.sp)
                EmptyPanel(if(state.online) "Waiting for weather" else "Waiting for a connection","Current conditions and forecasts will appear here.")
            } else {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(22.dp)) {
                    Column(Modifier.weight(1.25f),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                        CurrentWeatherCard(weather,day,state.settings.location,state.settings.celsius,large=!compact)
                        Text(now.format(DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a z",Locale.US)),color=Muted,fontSize=11.sp)
                    }
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                        SectionLabel("THE DETAILS")
                        WeatherDetails(weather,day,columns=2,compact=compact)
                    }
                    Column(Modifier.weight(1.1f),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                        SectionLabel("7-DAY OUTLOOK")
                        WeeklyForecast(weather.days.filter { !it.date.isBefore(now.toLocalDate()) }.take(7),state.settings.celsius,now.toLocalDate(),compact)
                    }
                }
                SectionLabel("NEXT 12 HOURS", "TEMP / RAIN CHANCE")
                HourlyForecast(weather.hours.filter { !it.time.isBefore(now.toLocalDateTime().withMinute(0).withSecond(0).withNano(0)) }.take(12),state.settings.celsius,compact)
            }
            Text("Weather: Open-Meteo · CC BY 4.0",color=Muted,fontSize=9.sp)
        }
    }
}
