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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openticker.app.slideshow.*
import com.openticker.app.settings.*
import com.openticker.app.ui.components.*
import com.openticker.app.ui.theme.*
import com.openticker.app.viewmodel.*
import com.openticker.app.data.repository.StockMarketUiState
import com.openticker.app.data.model.ChartRange
import com.openticker.app.util.*
import kotlinx.coroutines.delay
import java.time.Instant

@Composable fun OpenTickerApp(model: DashboardViewModel, stockModel: StockMarketViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val playback by model.slideshow.collectAsStateWithLifecycle()
    val stocks by stockModel.state.collectAsStateWithLifecycle()
    val appearance by stockModel.appearance.collectAsStateWithLifecycle()
    LaunchedEffect(playback.slide,playback.showDashboard) {
        if(playback.slide==DashboardSlide.STOCKS && playback.showDashboard) stockModel.onStockAppearance()
    }
    val owner=LocalLifecycleOwner.current
    var visible by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var sportsTabName by rememberSaveable { mutableStateOf<String?>(null) }
    var sportsModeInitialized by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.settingsLoaded) {
        if(state.settingsLoaded && !sportsModeInitialized) {
            sportsTabName=if(usePhillyTabsInitially(state.settings)) PhillySportsTab.FOOTBALL.name else null
            sportsModeInitialized=true
        }
    }
    val sportsTab=sportsTabName?.let { PhillySportsTab.valueOf(it) }
    var detailsOpen by remember { mutableStateOf(false) }
    var feedStatusOpen by rememberSaveable { mutableStateOf(false) }
    val interactionHeld = settingsOpen || detailsOpen || feedStatusOpen
    LaunchedEffect(interactionHeld) { model.setPlaybackInteraction(interactionHeld) }
    DisposableEffect(model) { onDispose { model.setPlaybackInteraction(false) } }
    val slideStates=rememberSaveableStateHolder()
    DisposableEffect(owner) {
        val observer=LifecycleEventObserver { _, _ -> visible=owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    // Keep the editor composed across background/foreground transitions so its draft is retained.
    if(settingsOpen) SettingsDialog(state,model::search,{ settings ->
        model.saveSettings(settings) { settingsOpen=false;model.clearSearch() }
    }) { settingsOpen=false;model.clearSearch() }
    // Remove animation and clock producers from composition while backgrounded.
    if(!visible) { Box(Modifier.fillMaxSize().background(SportsBackground)); return }
    val now by produceState(Instant.now()) { while(true) { value=Instant.now();delay(15_000) } }
    val visibleSportsLeagues=state.settings.leagues.filterNot { shouldHideSportToday(state.scores[it],state.online,now) }.toSet()
    val availableSportsTabs=PhillySportsTab.entries.filter { tab -> tab.leagues.any { it in visibleSportsLeagues } }
    LaunchedEffect(availableSportsTabs,sportsTabName,playback.slide) {
        if(sportsTab!=null && sportsTab !in availableSportsTabs && availableSportsTabs.isNotEmpty()) sportsTabName=availableSportsTabs.first().name
        if(availableSportsTabs.isEmpty() && playback.slide==DashboardSlide.SPORTS) {
            state.settings.enabledScreens.firstOrNull { it!=DashboardSlide.SPORTS }?.let(model::selectSlide)
        }
    }
    val feedHealth=remember(state,stocks,now,playback.slide) { dashboardFeedHealth(state,stocks,now,playback.slide) }
    if(feedStatusOpen) FeedHealthDialog(feedHealth) { feedStatusOpen=false }
    Surface {
        Column(Modifier.fillMaxSize()) {
            DashboardHeader(state.online,playback.slide,state.settings.slideshowPaused,model::selectSlide,model::toggleSlideshow,state.settingsLoaded,feedHealthCaption(feedHealth),feedHealth.any { it.needsAttention },{ feedStatusOpen=true },
                sportsTab=sportsTab,onSportsTab={ sportsModeInitialized=true;sportsTabName=it.name;model.selectSlide(DashboardSlide.SPORTS) },
                visibleSportsLeagues=visibleSportsLeagues,onRefresh=model::manualRefresh,refreshing=state.refreshing) {
                model.clearSearch();settingsOpen=true
            }
            state.settingsError?.let { Text(it,color=MaterialTheme.colorScheme.error) }
            if(!shouldShowScheduleAwareDashboard(playback,state.settings,state.scores,state.online,now)) {
                Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=androidx.compose.ui.Alignment.Center) { Text(if(state.settings.enabledScreens.isNotEmpty()) "No games scheduled today. Schedules keep refreshing; Weather and Stocks are available above." else "All screens are disabled. Enable a screen in Settings.") }
            } else {
                DashboardSlideshow(playback.slide,state,stocks,appearance,stockModel::selectRange,state.settings.animationsEnabled,slideStates,model::manualRefresh,model::selectSportsGroup,interactionHeld,{ detailsOpen=it },sportsTab,{ sportsTabName=null },{ sportsTabName=nextPhillySportsTab(sportsTab,availableSportsTabs)?.name },Modifier.weight(1f))
                BottomTicker(playback.slide,state,stocks)
            }
        }
    }
}
@Composable private fun DashboardSlideshow(slide: DashboardSlide, state: DashboardState, stocks: StockMarketUiState,
    appearance: Int, onRange: (ChartRange) -> Unit, animate: Boolean,
    savedState: androidx.compose.runtime.saveable.SaveableStateHolder, onRefresh: () -> Unit, onSelectGroup: (SportsGroup) -> Unit, interactionHeld: Boolean, onDetailsVisibilityChange: (Boolean) -> Unit, sportsTab: PhillySportsTab?, onUseGroups: () -> Unit, onAdvanceSportTab: () -> Unit, modifier: Modifier = Modifier) {
    val content: @Composable (DashboardSlide) -> Unit = { current ->
        savedState.SaveableStateProvider(current.name) {
            Box(Modifier.fillMaxSize().semantics { contentDescription="${current.label} dashboard" }) { when(current) {
                DashboardSlide.WEATHER -> WeatherDashboard(state,onRefresh,interactionHeld=interactionHeld)
                DashboardSlide.SPORTS -> SportsDashboard(state,onSelectGroup,interactionHeld=interactionHeld,onDetailsVisibilityChange=onDetailsVisibilityChange,sportsTab=sportsTab,onUseGroups=onUseGroups,onAdvanceSportTab=onAdvanceSportTab)
                DashboardSlide.STOCKS -> StockDashboard(stocks,state.settings.stocks,appearance,onRange,paused=state.settings.slideshowPaused,interactionHeld=interactionHeld,onDetailsVisibilityChange=onDetailsVisibilityChange)
            } }
        }
    }
    Box(modifier.fillMaxWidth()) {
        if(animate) Crossfade(slide,modifier=Modifier.fillMaxSize(),animationSpec=tween(500),label="dashboard-slide") { content(it) }
        else content(slide)
    }
}
