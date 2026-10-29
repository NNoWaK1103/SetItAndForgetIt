package com.openticker.app.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openticker.app.data.model.*
import com.openticker.app.ui.theme.*
import com.openticker.app.util.*
import com.openticker.app.viewmodel.DashboardState
import kotlinx.coroutines.delay
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable fun WeatherPanel(state: DashboardState, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    val w = state.weather
    val zone = remember(w?.timezone) { runCatching { ZoneId.of(w?.timezone ?: "UTC") }.getOrDefault(ZoneId.of("UTC")) }
    val now by produceState(LocalDateTime.now(zone), zone) { while (true) { value = LocalDateTime.now(zone); delay(60_000) } }
    val today = w?.days?.find { it.date == now.toLocalDate() }
    val celsius = state.settings.celsius
    Column(modifier.background(Brush.verticalGradient(listOf(WeatherBackground, Color(0xFF152944))))
        .verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement=Arrangement.spacedBy(15.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { SectionLabel("LOCAL WEATHER", state.weatherUpdated?.atZone(zone)?.format(DateTimeFormatter.ofPattern("h:mm a"))?.let { "Updated $it" }) }
            TextButton(onClick=onRefresh, enabled=state.online && !state.refreshing, contentPadding=PaddingValues(start=10.dp)) { Text("Refresh", fontSize=11.sp) }
        }
        state.weatherError?.let { Text(it, color=Color(0xFFFFCA82), fontSize=11.sp) }
        if (!state.online) Text("Offline · weather may be out of date", color=Color(0xFFFFCA82), fontSize=11.sp)
        if(w == null) {
            Text(state.settings.location.label, fontSize=22.sp, fontWeight=FontWeight.SemiBold)
            EmptyPanel(if(state.online) "Waiting for weather" else "Waiting for a connection", "Current conditions and forecasts will appear here.")
        } else {
            CurrentWeatherCard(w, today, state.settings.location, celsius)
            SectionLabel("NEXT 12 HOURS")
            HourlyForecast(w.hours.filter { !it.time.isBefore(now.withMinute(0).withSecond(0).withNano(0)) }.take(12), celsius)
            SectionLabel("7-DAY OUTLOOK")
            WeeklyForecast(w.days.filter { !it.date.isBefore(now.toLocalDate()) }.take(7), celsius, now.toLocalDate())
            SectionLabel("THE DETAILS")
            WeatherDetails(w, today)
        }
        Text("Weather: Open-Meteo · CC BY 4.0   /   Sports: ESPN (unofficial feed)", color=Muted, fontSize=9.sp)
    }
}
@Composable fun CurrentWeatherCard(weather: Weather, day: DayWeather?, location: Location, celsius: Boolean) {
    Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Text(location.label, fontSize=20.sp, fontWeight=FontWeight.SemiBold, color=Color(0xFFE1ECFF))
        Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
            Text(temperature(weather.temperatureF,celsius), fontSize=64.sp, fontWeight=FontWeight.Light, letterSpacing=(-3).sp)
            Text(if(celsius) "C" else "F", fontSize=18.sp, color=Muted, modifier=Modifier.padding(bottom=24.dp,start=3.dp))
            Spacer(Modifier.weight(1f))
            WeatherIcon(weather.code, 90.dp, weather.isDay)
        }
        Text(weatherCondition(weather.code,weather.isDay).description, color=Ice, fontSize=17.sp)
        Text("Feels like ${temperature(weather.feelsLikeF,celsius)}   ·   H ${temperature(day?.highF,celsius)} / L ${temperature(day?.lowF,celsius)}", color=Muted, fontSize=12.sp)
    }
}
@Composable fun HourlyForecast(hours: List<HourWeather>, celsius: Boolean) {
    if(hours.isEmpty()) Text("Hourly forecast unavailable", color=Muted, fontSize=12.sp)
    LazyRow(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
        items(hours, key={it.time.toString()}) { h ->
            Column(Modifier.width(62.dp).background(Color.White.copy(alpha=.045f),RoundedCornerShape(12.dp)).padding(vertical=10.dp),
                horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text(h.time.format(DateTimeFormatter.ofPattern("ha",Locale.US)), color=Muted, fontSize=10.sp)
                WeatherIcon(h.code)
                Text(temperature(h.temperatureF,celsius), fontSize=15.sp,fontWeight=FontWeight.SemiBold)
                Text(h.rain?.let { "$it%" } ?: "—", color=Ice, fontSize=10.sp)
            }
        }
    }
}
@Composable fun WeeklyForecast(days: List<DayWeather>, celsius: Boolean, today: LocalDate) {
    Column(Modifier.background(Color.White.copy(alpha=.035f),RoundedCornerShape(12.dp)).padding(horizontal=12.dp,vertical=5.dp)) {
        if(days.isEmpty()) Text("Daily forecast unavailable", color=Muted, fontSize=12.sp)
        days.forEach { d ->
            Row(Modifier.fillMaxWidth().height(37.dp), verticalAlignment=Alignment.CenterVertically) {
                Text(if(d.date==today) "Today" else d.date.format(DateTimeFormatter.ofPattern("EEE",Locale.US)), fontSize=12.sp, modifier=Modifier.weight(1f))
                WeatherIcon(d.code, 27.dp)
                Text(d.rain?.let { "$it%" } ?: "—", color=Ice, fontSize=10.sp, modifier=Modifier.width(44.dp).padding(start=8.dp))
                Text(temperature(d.highF,celsius),fontSize=13.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.width(38.dp))
                Text(temperature(d.lowF,celsius),color=Muted,fontSize=13.sp,modifier=Modifier.width(30.dp))
            }
        }
    }
}
@Composable fun WeatherDetails(weather: Weather, day: DayWeather?) {
    val formatter = DateTimeFormatter.ofPattern("h:mm a",Locale.US)
    val cells = listOf("HUMIDITY" to (weather.humidity?.let { "$it%" } ?: "—"),
        "WIND" to (weather.windMph?.let { "${it.toInt()} mph ${windDirection(weather.windDirection)}" } ?: "—"),
        "UV MAX" to (day?.uv?.let { String.format(Locale.US,"%.1f",it) } ?: "—"),
        "PRECIPITATION" to (weather.precipitationInches?.let { String.format(Locale.US,"%.2f in",it) } ?: "—"),
        "SUNRISE" to (day?.sunrise?.format(formatter) ?: "—"), "SUNSET" to (day?.sunset?.format(formatter) ?: "—"))
    Column(verticalArrangement=Arrangement.spacedBy(7.dp)) {
        cells.chunked(3).forEach { row ->
            Row(horizontalArrangement=Arrangement.spacedBy(7.dp)) { row.forEach { (label,value) ->
                Column(Modifier.weight(1f).background(Color.White.copy(alpha=.045f),RoundedCornerShape(10.dp)).padding(10.dp), verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(label,color=Muted,fontSize=8.sp,letterSpacing=.7.sp)
                    Text(value,fontSize=12.sp,fontWeight=FontWeight.Medium,maxLines=1)
                }
            } }
        }
    }
}
