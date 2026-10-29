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

@Composable fun CurrentWeatherCard(weather: Weather, day: DayWeather?, location: Location, celsius: Boolean, large: Boolean = false) {
    Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Text(location.label, fontSize=20.sp, fontWeight=FontWeight.SemiBold, color=Color(0xFFE1ECFF))
        Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
            Text(temperature(weather.temperatureF,celsius), fontSize=if(large) 100.sp else 70.sp, fontWeight=FontWeight.Light, letterSpacing=(-3).sp)
            Text(if(celsius) "C" else "F", fontSize=18.sp, color=Muted, modifier=Modifier.padding(bottom=24.dp,start=3.dp))
            Spacer(Modifier.weight(1f))
            WeatherIcon(weather.code, if(large) 88.dp else 58.dp, weather.isDay)
        }
        Text(weatherCondition(weather.code,weather.isDay).description, color=Ice, fontSize=17.sp)
        Text("Feels like ${temperature(weather.feelsLikeF,celsius)}   ·   H ${temperature(day?.highF,celsius)} / L ${temperature(day?.lowF,celsius)}", color=Muted, fontSize=12.sp)
    }
}
@Composable fun HourlyForecast(hours: List<HourWeather>, celsius: Boolean, compact: Boolean = false) {
    if(hours.isEmpty()) Text("Hourly forecast unavailable", color=Muted, fontSize=12.sp)
    LazyRow(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
        items(hours, key={it.time.toString()}) { h ->
            Column(Modifier.width(68.dp).background(Color.White.copy(alpha=.045f),RoundedCornerShape(12.dp)).padding(vertical=if(compact) 6.dp else 10.dp),
                horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(if(compact) 3.dp else 5.dp)) {
                Text(h.time.format(DateTimeFormatter.ofPattern("ha",Locale.US)), color=Muted, fontSize=10.sp)
                WeatherIcon(h.code,if(compact) 26.dp else 32.dp)
                Text(temperature(h.temperatureF,celsius), fontSize=15.sp,fontWeight=FontWeight.SemiBold)
                Text(h.rain?.let { "$it%" } ?: "—", color=Ice, fontSize=10.sp)
            }
        }
    }
}
@Composable fun WeeklyForecast(days: List<DayWeather>, celsius: Boolean, today: LocalDate, compact: Boolean = false) {
    Column(Modifier.background(Color.White.copy(alpha=.035f),RoundedCornerShape(12.dp)).padding(horizontal=12.dp,vertical=5.dp)) {
        if(days.isEmpty()) Text("Daily forecast unavailable", color=Muted, fontSize=12.sp)
        days.forEach { d ->
            Row(Modifier.fillMaxWidth().height(if(compact) 28.dp else 37.dp), verticalAlignment=Alignment.CenterVertically) {
                Text(if(d.date==today) "Today" else d.date.format(DateTimeFormatter.ofPattern("EEE",Locale.US)), fontSize=12.sp, modifier=Modifier.weight(1f))
                WeatherIcon(d.code, 27.dp)
                Text(d.rain?.let { "$it%" } ?: "—", color=Ice, fontSize=10.sp, modifier=Modifier.width(44.dp).padding(start=8.dp))
                Text(temperature(d.highF,celsius),fontSize=13.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.width(38.dp))
                Text(temperature(d.lowF,celsius),color=Muted,fontSize=13.sp,modifier=Modifier.width(30.dp))
            }
        }
    }
}
@Composable fun WeatherDetails(weather: Weather, day: DayWeather?, columns: Int = 3, compact: Boolean = false) {
    val formatter = DateTimeFormatter.ofPattern("h:mm a",Locale.US)
    val cells = listOf("HUMIDITY" to (weather.humidity?.let { "$it%" } ?: "—"),
        "WIND" to (weather.windMph?.let { "${it.toInt()} mph ${windDirection(weather.windDirection)}" } ?: "—"),
        "UV MAX" to (day?.uv?.let { String.format(Locale.US,"%.1f",it) } ?: "—"),
        "PRECIPITATION" to (weather.precipitationInches?.let { String.format(Locale.US,"%.2f in",it) } ?: "—"),
        "SUNRISE" to (day?.sunrise?.format(formatter) ?: "—"), "SUNSET" to (day?.sunset?.format(formatter) ?: "—"),
        "RAIN CHANCE" to (day?.rain?.let { "$it%" } ?: "—"))
    Column(verticalArrangement=Arrangement.spacedBy(7.dp)) {
        cells.chunked(columns).forEach { row ->
            Row(horizontalArrangement=Arrangement.spacedBy(7.dp)) { row.forEach { (label,value) ->
                Column(Modifier.weight(1f).background(Color.White.copy(alpha=.045f),RoundedCornerShape(10.dp)).padding(if(compact) 7.dp else 10.dp), verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(label,color=Muted,fontSize=8.sp,letterSpacing=.7.sp)
                    Text(value,fontSize=12.sp,fontWeight=FontWeight.Medium,maxLines=1)
                }
            }; repeat(columns-row.size) { Spacer(Modifier.weight(1f)) } }
        }
    }
}
