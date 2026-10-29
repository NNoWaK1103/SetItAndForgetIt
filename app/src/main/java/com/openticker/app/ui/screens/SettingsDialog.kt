package com.openticker.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openticker.app.data.model.*
import com.openticker.app.settings.AppSettings
import com.openticker.app.settings.SLIDESHOW_INTERVALS
import com.openticker.app.slideshow.DashboardSlide
import com.openticker.app.ui.theme.*
import com.openticker.app.viewmodel.DashboardState

@Composable fun SettingsDialog(state: DashboardState, onSearch: (String) -> Unit, onSave: (AppSettings) -> Unit, onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf(state.settings) }
    var query by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest=onDismiss, title={ Text("Make it yours") },
        text={ Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("Automatic slideshow",color=Mint,fontSize=13.sp)
            SettingToggle("Pause automatic switching",draft.slideshowPaused) {draft=draft.copy(slideshowPaused=it)}
            Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) { SLIDESHOW_INTERVALS.forEach { seconds ->
                FilterChip(selected=draft.slideshowIntervalSeconds==seconds,onClick={draft=draft.copy(slideshowIntervalSeconds=seconds)},label={Text("${seconds}s",fontSize=10.sp)})
            } }
            Text("Start on",color=Mint,fontSize=13.sp)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { DashboardSlide.entries.forEach { slide ->
                FilterChip(selected=draft.startSlide==slide,onClick={draft=draft.copy(startSlide=slide)},label={Text(if(slide==DashboardSlide.WEATHER) "Weather" else "Sports")})
            } }
            Text("The start screen applies next time the app launches. Manual switches get a full display interval.",color=Muted,fontSize=11.sp)
            SettingToggle("Animations and ticker scrolling",draft.animationsEnabled) {draft=draft.copy(animationsEnabled=it)}
            Text("Weather location",color=Mint,fontSize=13.sp)
            Text(draft.location.label,fontSize=13.sp)
            OutlinedTextField(value=query,onValueChange={query=it; onSearch(it)},label={Text("City or ZIP code")},singleLine=true,modifier=Modifier.fillMaxWidth())
            if(state.searching) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.searchError?.let { Text(it,color=MaterialTheme.colorScheme.error,fontSize=12.sp) }
            state.searchResults.forEach { location -> TextButton(onClick={draft=draft.copy(location=location)}) { Text(location.label,fontSize=12.sp) } }
            SettingToggle("Celsius (°C)",draft.celsius) {draft=draft.copy(celsius=it)}
            Text("Sports leagues",color=Mint,fontSize=13.sp)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { League.entries.forEach { league ->
                Column(horizontalAlignment=Alignment.CenterHorizontally) {
                    Text(league.name,fontSize=10.sp)
                    Checkbox(checked=league in draft.leagues,onCheckedChange={ checked -> draft=draft.copy(leagues=if(checked) draft.leagues+league else draft.leagues-league) })
                }
            } }
            Text("Live sports refresh",color=Mint,fontSize=13.sp)
            Row(horizontalArrangement=Arrangement.spacedBy(7.dp)) { listOf(30,60,120,300).forEach { seconds ->
                FilterChip(selected=draft.sportsRefreshSeconds==seconds,onClick={draft=draft.copy(sportsRefreshSeconds=seconds)},label={Text(if(seconds<60) "${seconds}s" else "${seconds/60}m",fontSize=11.sp)})
            } }
            Text("Quiet scoreboards refresh at least every 3 minutes. Weather refreshes every 15 minutes.",color=Muted,fontSize=11.sp)
            SettingToggle("Sports ticker on weather screen",draft.tickerEnabled) {draft=draft.copy(tickerEnabled=it)}
            SettingToggle("Weather ticker on sports screen",draft.weatherTickerEnabled) {draft=draft.copy(weatherTickerEnabled=it)}
            SettingToggle("Keep screen awake",draft.keepAwake) {draft=draft.copy(keepAwake=it)}
        } },
        confirmButton={TextButton(onClick={onSave(draft);onDismiss()}) {Text("Save settings")}},
        dismissButton={TextButton(onClick=onDismiss) {Text("Cancel")}})
}
@Composable private fun SettingToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text(label,fontSize=13.sp,modifier=Modifier.weight(1f)); Switch(checked=checked,onCheckedChange=onChange)
    }
}
