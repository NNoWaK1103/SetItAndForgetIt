package com.openticker.app.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openticker.app.slideshow.*
import com.openticker.app.settings.SportsGroup
import com.openticker.app.ui.components.*
import com.openticker.app.ui.theme.*
import com.openticker.app.viewmodel.*

@Composable fun OpenTickerApp(model: DashboardViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val playback by model.slideshow.collectAsStateWithLifecycle()
    val owner=LocalLifecycleOwner.current
    var visible by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val slideStates=rememberSaveableStateHolder()
    DisposableEffect(owner) {
        val observer=LifecycleEventObserver { _, _ -> visible=owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    // Remove animation and clock producers from composition while backgrounded.
    if(!visible) { Box(Modifier.fillMaxSize().background(SportsBackground)); return }
    Surface {
        Column(Modifier.fillMaxSize()) {
            val healthy=state.weatherUpdated!=null && state.weatherError==null && state.settings.leagues.all { state.scores[it]?.updated!=null && state.scores[it]?.error==null }
            DashboardHeader(state.online,healthy,playback.slide,state.settings.slideshowPaused,model::selectSlide,model::toggleSlideshow) {
                model.clearSearch();settingsOpen=true
            }
            state.settingsError?.let { Text(it,color=MaterialTheme.colorScheme.error) }
            DashboardSlideshow(playback.slide,state,state.settings.animationsEnabled,slideStates,model::manualRefresh,model::selectSportsGroup,Modifier.weight(1f))
            BottomTicker(playback.slide,state)
        }
        if(settingsOpen) SettingsDialog(state,model::search,model::saveSettings) { settingsOpen=false;model.clearSearch() }
    }
}
@Composable private fun DashboardSlideshow(slide: DashboardSlide, state: DashboardState, animate: Boolean,
    savedState: androidx.compose.runtime.saveable.SaveableStateHolder, onRefresh: () -> Unit, onSelectGroup: (SportsGroup) -> Unit, modifier: Modifier = Modifier) {
    val content: @Composable (DashboardSlide) -> Unit = { current ->
        savedState.SaveableStateProvider(current.name) {
            when(current) {
                DashboardSlide.WEATHER -> WeatherDashboard(state,onRefresh)
                DashboardSlide.SPORTS -> SportsDashboard(state,onSelectGroup)
            }
        }
    }
    Box(modifier.fillMaxWidth()) {
        if(animate) Crossfade(slide,modifier=Modifier.fillMaxSize(),animationSpec=tween(500),label="dashboard-slide") { content(it) }
        else content(slide)
    }
}
