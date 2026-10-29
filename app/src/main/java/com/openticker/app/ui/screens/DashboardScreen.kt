package com.openticker.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.openticker.app.settings.AppSettings
import com.openticker.app.ui.components.*
import com.openticker.app.viewmodel.DashboardState

@Composable fun DashboardScreen(state: DashboardState, onRefresh: () -> Unit, onSearch: (String) -> Unit,
    onSave: (AppSettings) -> Unit, onClearSearch: () -> Unit) {
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    Surface {
        Column(Modifier.fillMaxSize()) {
            val healthy = state.weatherUpdated != null && state.weatherError == null && state.settings.leagues.all { state.scores[it]?.updated != null && state.scores[it]?.error == null }
            DashboardHeader(state.online,healthy) { onClearSearch(); settingsOpen=true }
            state.settingsError?.let { Text(it,color=MaterialTheme.colorScheme.error) }
            Row(Modifier.weight(1f).fillMaxWidth()) {
                SportsPanel(state.settings.leagues,state.scores,state.online,Modifier.weight(1f).fillMaxHeight())
                WeatherPanel(state,onRefresh,Modifier.weight(1f).fillMaxHeight())
            }
            if(state.settings.tickerEnabled) SportsTicker(state.scores.values.flatMap { it.games },state.settings.leagues)
        }
        if(settingsOpen) SettingsDialog(state,onSearch,onSave) {settingsOpen=false;onClearSearch()}
    }
}
