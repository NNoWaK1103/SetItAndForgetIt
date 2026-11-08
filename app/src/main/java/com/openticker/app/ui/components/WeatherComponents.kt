package com.openticker.app.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openticker.app.data.model.*
import com.openticker.app.ui.theme.*
import com.openticker.app.util.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Shared quiet panel treatment; content and unavailable states remain provider driven. */
@Composable fun WeatherPanel(modifier: Modifier = Modifier, compact: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.background(Panel, RoundedCornerShape(16.dp))
        .border(1.dp, Border, RoundedCornerShape(16.dp))
        .padding(if(compact) 12.dp else 20.dp),
        verticalArrangement=Arrangement.spacedBy(if(compact) 6.dp else 14.dp), content=content)
}

@Composable fun CurrentWeatherCard(weather: Weather, day: DayWeather?, location: Location, celsius: Boolean, large: Boolean = false) {
    if(large) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(36.dp).background(Mint.copy(alpha=.22f),RoundedCornerShape(50)),contentAlignment=Alignment.Center) {
                Text(if(location.name.equals("Philadelphia",ignoreCase=true)) "PHL" else location.name.take(3).uppercase(Locale.US),fontFamily=DisplayFont,fontSize=11.sp,fontWeight=FontWeight.Bold)
            }
            Text(location.label,fontFamily=DisplayFont,fontSize=23.sp,lineHeight=27.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f),maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text("OPEN-METEO",color=Muted,fontSize=7.sp,modifier=Modifier.border(1.dp,Border,RoundedCornerShape(5.dp)).padding(7.dp))
        }
        Row(Modifier.fillMaxWidth().padding(top=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            WeatherIcon(weather.code,104.dp,weather.isDay)
            Text(temperature(weather.temperatureF,celsius),fontFamily=DisplayFont,fontSize=100.sp,lineHeight=106.sp,fontWeight=FontWeight.Bold,color=TextStrong,letterSpacing=(-3).sp)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text(weatherCondition(weather.code,weather.isDay).description,fontFamily=DisplayFont,fontSize=23.sp,lineHeight=26.sp,fontWeight=FontWeight.Bold)
                Text("Feels like ${temperature(weather.feelsLikeF,celsius)}",color=Muted,fontSize=11.sp)
                Text("H ${temperature(day?.highF,celsius)} / L ${temperature(day?.lowF,celsius)} · ${if(celsius) "°C" else "°F"}",color=Muted,fontSize=10.sp)
            }
        }
        return
    }
    Column(verticalArrangement=Arrangement.spacedBy(if(large) 8.dp else 3.dp)) {
        Text("CURRENT CONDITIONS", fontSize=9.sp, lineHeight=12.sp, letterSpacing=1.5.sp, color=Mint)
        Text(location.label, fontFamily=DisplayFont, fontSize=if(large) 28.sp else 20.sp, lineHeight=if(large) 34.sp else 25.sp,
            fontWeight=FontWeight.SemiBold, color=TextStrong,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
            Text(temperature(weather.temperatureF,celsius), fontFamily=DisplayFont,
                fontSize=if(large) 110.sp else 66.sp, lineHeight=if(large) 114.sp else 68.sp,
                fontWeight=FontWeight.Medium, color=TextStrong, letterSpacing=(-3).sp)
            Text(if(celsius) "C" else "F", fontSize=18.sp, lineHeight=23.sp, color=Muted, modifier=Modifier.padding(bottom=24.dp,start=3.dp))
            Spacer(Modifier.weight(1f))
            WeatherIcon(weather.code, if(large) 96.dp else 58.dp, weather.isDay)
        }
        Text(weatherCondition(weather.code,weather.isDay).description, color=TextStrong, fontSize=if(large) 20.sp else 15.sp, lineHeight=if(large) 25.sp else 19.sp)
        Text("Feels like ${temperature(weather.feelsLikeF,celsius)}   ·   H ${temperature(day?.highF,celsius)} / L ${temperature(day?.lowF,celsius)}", color=Muted, fontSize=12.sp, lineHeight=15.sp)
    }
}
@Composable fun WeatherInsightStack(insights: List<WeatherInsight>) {
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        insights.forEach { insight ->
            Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(14.dp)).border(1.dp,Border,RoundedCornerShape(14.dp)).padding(10.dp), verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Text(insight.title,color=Mint,fontSize=9.sp,lineHeight=11.sp)
                Text(insight.value,fontFamily=DisplayFont,fontSize=16.sp,lineHeight=19.sp,fontWeight=FontWeight.Bold,maxLines=1)
                Text(insight.detail,color=Muted,fontSize=10.sp,lineHeight=12.sp,maxLines=2)
            }
        }
    }
}

@Composable fun WeatherQuickDetails(weather: Weather, day: DayWeather?, celsius: Boolean) {
    val wind=weather.windMph?.let { "${if(celsius) (it*1.609344).toInt() else it.toInt()} ${if(celsius) "km/h" else "mph"}" } ?: "—"
    HorizontalDivider(color=Border)
    Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        val metrics=listOf(
            Triple("WIND", wind, windDescription(weather.windMph)),
            Triple("UV MAX", day?.uv?.let { String.format(Locale.US, "%.1f", it) } ?: "—", uvDescription(day?.uv)),
            Triple("HUMIDITY", weather.humidity?.let { "$it%" } ?: "—", "Precip ${day?.rain?.let { "$it%" } ?: "—"}")
        )
        metrics.forEach { (label,value,detail) ->
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Text(label,color=Muted,fontSize=8.sp,lineHeight=10.sp,letterSpacing=.8.sp)
                Text(value,color=TextStrong,fontSize=13.sp,lineHeight=16.sp,fontWeight=FontWeight.SemiBold)
                Text(detail,color=Muted,fontSize=9.sp,lineHeight=11.sp,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable fun WeatherInsightCards(insights: List<WeatherInsight>, compact: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        insights.forEach { insight ->
            Column(Modifier.weight(1f).background(Panel, RoundedCornerShape(14.dp)).border(1.dp,Border,RoundedCornerShape(14.dp)).padding(if(compact) 9.dp else 12.dp), verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(insight.title, color=Mint, fontSize=9.sp, lineHeight=12.sp, letterSpacing=.8.sp)
                Text(insight.value, fontSize=if(compact) 14.sp else 16.sp, lineHeight=if(compact) 18.sp else 20.sp, fontWeight=FontWeight.SemiBold)
                Text(insight.detail, color=Muted, fontSize=10.sp, lineHeight=13.sp)
            }
        }
    }
}

@Composable fun WeatherHeading(title: String, subtitle: String? = null, trailing: String? = null) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(title,fontFamily=DisplayFont,fontSize=23.sp,lineHeight=26.sp,fontWeight=FontWeight.Bold)
            subtitle?.let { Text(it,color=Muted,fontSize=8.sp,letterSpacing=.8.sp) }
        }
        trailing?.let { Text(it,color=Muted,fontSize=7.sp,maxLines=1) }
    }
}

@Composable fun WeatherReferenceMetrics(weather: Weather, day: DayWeather?, celsius: Boolean) {
    HorizontalDivider(color=Border)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        val values=listOf("Wind" to (weather.windMph?.let { "${windDirection(weather.windDirection)} ${if(celsius) (it*1.609344).toInt() else it.toInt()} ${if(celsius) "km/h" else "mph"}" } ?: "—"),
            "Humidity" to (weather.humidity?.let { "$it%" } ?: "—"),
            "UV max" to (day?.uv?.let { String.format(Locale.US,"%.1f",it) } ?: "—"),
            "Rain chance" to (day?.rain?.let { "$it%" } ?: "—"))
        values.forEach { (label,value) ->
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(label,color=Muted,fontSize=9.sp)
                Text(value,fontSize=12.sp,fontWeight=FontWeight.SemiBold,maxLines=1)
            }
        }
    }
}

@Composable fun ColumnScope.WeatherOutlook(weather: Weather, day: DayWeather?, now: ZonedDateTime, celsius: Boolean) {
    val insights=weatherInsights(weather,now.toInstant(),celsius)
    val outlook=insights.first()
    Column(Modifier.fillMaxWidth().weight(1f).background(Mint.copy(alpha=.045f),RoundedCornerShape(12.dp)).border(1.dp,Border,RoundedCornerShape(12.dp)).padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("TODAY’S OUTLOOK",color=Mint,fontSize=9.sp,letterSpacing=1.sp)
        Text(outlook.value,fontFamily=DisplayFont,fontSize=23.sp,lineHeight=26.sp,fontWeight=FontWeight.Bold)
        Text(outlook.detail,color=Muted,fontSize=10.sp,lineHeight=14.sp)
        Spacer(Modifier.weight(1f))
        HorizontalDivider(color=Border)
        Text("${insights[1].value} · ${insights[1].detail}",color=Muted,fontSize=9.sp,lineHeight=12.sp)
    }
    Row(Modifier.fillMaxWidth().border(1.dp,Border,RoundedCornerShape(12.dp)).padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
        val format=DateTimeFormatter.ofPattern("h:mm a",Locale.US)
        Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text("SUNRISE",color=Muted,fontSize=7.sp)
            Text(day?.sunrise?.format(format) ?: "—",fontSize=11.sp,fontWeight=FontWeight.SemiBold)
        }
        Text("●",color=androidx.compose.ui.graphics.Color(0xFFE6CC82),fontSize=12.sp)
        Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text("SUNSET",color=Muted,fontSize=7.sp)
            Text(day?.sunset?.format(format) ?: "—",fontSize=11.sp,fontWeight=FontWeight.SemiBold)
        }
    }
}

@Composable fun HourlyForecast(hours: List<HourWeather>, celsius: Boolean, compact: Boolean = false) {
    if(hours.isEmpty()) {
        Text("Hourly forecast unavailable · waiting for an updated forecast",color=Muted,fontSize=12.sp,lineHeight=15.sp)
        return
    }
    if(compact) {
        Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
            hours.chunked(6).forEach { row ->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    row.forEach { h ->
                        Column(Modifier.weight(1f).background(PanelRaised,RoundedCornerShape(8.dp)).padding(vertical=5.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(2.dp)) {
                            Text(h.time.format(DateTimeFormatter.ofPattern("ha",Locale.US)),color=Muted,fontSize=9.sp)
                            WeatherIcon(h.code,23.dp)
                            Text(temperature(h.temperatureF,celsius),fontFamily=DisplayFont,fontSize=18.sp,lineHeight=20.sp)
                            Text(h.rain?.let { "$it%" } ?: "—",color=Muted,fontSize=9.sp)
                        }
                    }
                    repeat(6-row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        return
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width=(maxWidth/6).coerceAtLeast(60.dp)
        // Keep every supplied hour reachable, without pretending two-hour samples are hourly data.
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            hours.forEachIndexed { index,h ->
                Column(Modifier.width(width).background(if(index==0) Mint.copy(alpha=.05f) else androidx.compose.ui.graphics.Color.Transparent,RoundedCornerShape(10.dp))
                    .padding(vertical=10.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(h.time.format(DateTimeFormatter.ofPattern("ha",Locale.US)),color=Muted,fontSize=9.sp,lineHeight=12.sp)
                    WeatherIcon(h.code,32.dp)
                    Text(temperature(h.temperatureF,celsius),fontFamily=DisplayFont,fontSize=24.sp,lineHeight=26.sp,fontWeight=FontWeight.Bold)
                    Text(h.rain?.let { "$it% rain" } ?: "—",color=Muted,fontSize=9.sp,lineHeight=12.sp)
                }
            }
        }
    }
    if(hours.size>6) Text("Swipe for all ${hours.size} hours →",color=Muted,fontSize=8.sp,lineHeight=10.sp)
}
@Composable fun WeeklyForecast(days: List<DayWeather>, celsius: Boolean, today: LocalDate, compact: Boolean = false) {
    Column(Modifier.fillMaxWidth()) {
        if(days.isEmpty()) Text("Daily forecast unavailable",color=Muted,fontSize=12.sp,lineHeight=15.sp)
        days.forEach { d ->
            HorizontalDivider(color=Border.copy(alpha=.65f))
            Row(Modifier.fillMaxWidth().height(if(compact) 26.dp else 31.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(if(d.date==today) "Today" else if(d.date==today.plusDays(1)) "Tomorrow" else d.date.format(DateTimeFormatter.ofPattern("EEEE",Locale.US)),fontSize=10.sp,lineHeight=12.sp,fontWeight=FontWeight.Medium,modifier=Modifier.weight(1.15f),maxLines=1)
                WeatherIcon(d.code,20.dp)
                Text(d.rain?.let { "$it% rain" } ?: "—",color=Muted,fontSize=8.sp,lineHeight=10.sp,modifier=Modifier.weight(1f))
                Text(temperature(d.highF,celsius),fontSize=11.sp,lineHeight=13.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.width(27.dp))
                Text(temperature(d.lowF,celsius),color=Muted,fontSize=11.sp,lineHeight=13.sp,modifier=Modifier.width(27.dp))
            }
        }
    }
}
@Composable fun WeatherDetails(weather: Weather, day: DayWeather?, columns: Int = 3, compact: Boolean = false, celsius: Boolean = false) {
    val formatter = DateTimeFormatter.ofPattern("h:mm a",Locale.US)
    val cells = listOf("HUMIDITY" to (weather.humidity?.let { "$it%" } ?: "—"),
        "WIND" to (weather.windMph?.let { "${if(celsius) (it * 1.609344).toInt() else it.toInt()} ${if(celsius) "km/h" else "mph"} ${windDirection(weather.windDirection)}" } ?: "—"),
        "UV MAX" to (day?.uv?.let { String.format(Locale.US,"%.1f",it) } ?: "—"),
        "MODEL PRECIP" to (weather.precipitationInches?.let { if(celsius) String.format(Locale.US,"%.1f mm",it * 25.4) else String.format(Locale.US,"%.2f in",it) } ?: "—"),
        "SUNRISE" to (day?.sunrise?.format(formatter) ?: "—"), "SUNSET" to (day?.sunset?.format(formatter) ?: "—"),
        "TODAY PRECIP CHANCE" to (day?.rain?.let { "$it%" } ?: "—"))
    Column(verticalArrangement=Arrangement.spacedBy(7.dp)) {
        cells.chunked(columns).forEach { row ->
            Row(horizontalArrangement=Arrangement.spacedBy(7.dp)) { row.forEach { (label,value) ->
                Column(Modifier.weight(1f).background(PanelRaised,RoundedCornerShape(10.dp)).padding(if(compact) 7.dp else 10.dp), verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(label,color=Muted,fontSize=8.sp, lineHeight=10.sp,letterSpacing=.7.sp)
                    Text(value,fontSize=12.sp, lineHeight=15.sp,fontWeight=FontWeight.Medium,maxLines=1)
                    if(label=="WIND") Text(windDescription(weather.windMph),color=Muted,fontSize=9.sp, lineHeight=12.sp)
                    if(label=="UV MAX") Text(uvDescription(day?.uv),color=Muted,fontSize=9.sp, lineHeight=12.sp)
                }
            }; repeat(columns-row.size) { Spacer(Modifier.weight(1f)) } }
        }
    }
}
