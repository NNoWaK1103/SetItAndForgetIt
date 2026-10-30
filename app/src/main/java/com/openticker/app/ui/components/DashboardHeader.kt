package com.openticker.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openticker.app.ui.theme.*
import com.openticker.app.slideshow.DashboardSlide
import kotlinx.coroutines.delay
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable fun DashboardHeader(online: Boolean, healthy: Boolean, slide: DashboardSlide, paused: Boolean,
    onSelect: (DashboardSlide) -> Unit, onTogglePlayback: () -> Unit,
    enabledScreens: List<DashboardSlide> = DashboardSlide.entries.toList(), onSettings: () -> Unit) {
    val now by produceState(ZonedDateTime.now()) { while(true) { value = ZonedDateTime.now(); delay(1000) } }
    Row(Modifier.fillMaxWidth().height(60.dp).background(Color(0xFF0B1019)).padding(horizontal=16.dp),
        verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(31.dp).background(Mint,RoundedCornerShape(8.dp)),contentAlignment=Alignment.Center) {
            Text("OT",color=SportsBackground,fontWeight=FontWeight.ExtraBold,fontSize=13.sp)
        }
        Text("OpenTicker",fontSize=19.sp,fontWeight=FontWeight.SemiBold,letterSpacing=(-.5).sp)
        Spacer(Modifier.weight(1f))
        enabledScreens.forEach { target ->
            TextButton(onClick={onSelect(target)},contentPadding=PaddingValues(horizontal=8.dp)) {
                Text(target.label,color=if(target==slide) Mint else Muted,fontSize=12.sp,fontWeight=FontWeight.SemiBold)
            }
        }
        TextButton(onClick=onTogglePlayback,contentPadding=PaddingValues(horizontal=8.dp)) { Text(if(paused) "Resume" else "Pause",fontSize=12.sp) }
        Column(horizontalAlignment=Alignment.End) {
            Text(now.format(DateTimeFormatter.ofPattern("h:mm:ss a",Locale.US)),fontSize=18.sp,fontWeight=FontWeight.Medium)
            Text(now.format(DateTimeFormatter.ofPattern("EEE, MMM d",Locale.US)),color=Muted,fontSize=10.sp)
        }
        Text(if(!online) "● OFFLINE" else if(!healthy) "● CONNECTING" else "● CONNECTED", color=if(online && healthy) Mint else Color(0xFFFFCA82),fontSize=9.sp)
        TextButton(onClick=onSettings) { Text("Settings",color=Ice,fontSize=12.sp) }
    }
}
