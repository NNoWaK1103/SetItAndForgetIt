package com.openticker.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openticker.app.data.model.*
import com.openticker.app.ui.components.*
import com.openticker.app.ui.theme.*
import com.openticker.app.util.sortedGames
import com.openticker.app.viewmodel.DashboardState
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun SportsDashboard(state: DashboardState, modifier: Modifier = Modifier) {
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    val enabled = state.settings.leagues
    val selected = enabled.find { it.name == selectedName }
    val leagues = if (selected != null) listOf(selected) else enabled.sortedWith(
        compareByDescending<League> { state.scores[it]?.games?.any { game -> game.status == GameStatus.LIVE } == true }.thenBy { it.ordinal })
    Column(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(SportsBackground, Color(0xFF19212D)))).padding(horizontal=22.dp, vertical=12.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)) {
        SectionLabel("AROUND THE LEAGUES", "TODAY · US EASTERN")
        Row(verticalAlignment=Alignment.CenterVertically) {
            FilterChip(selected=selected==null, onClick={selectedName=null}, label={Text("ALL",fontSize=11.sp)})
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) { LeagueTabs(enabled, selected) { selectedName=it.name } }
        }
        if (!state.online) Text("Offline · showing last received scores",color=Color(0xFFFFCA82),fontSize=11.sp)
        if (enabled.isEmpty()) EmptyPanel("Your leagues, your dashboard", "Enable a league in Settings to see scores.")
        LazyVerticalGrid(columns=GridCells.Adaptive(270.dp), horizontalArrangement=Arrangement.spacedBy(12.dp),
            verticalArrangement=Arrangement.spacedBy(10.dp), contentPadding=PaddingValues(bottom=12.dp)) {
            leagues.forEach { league ->
                val feed=state.scores[league]
                item(key="heading-${league.name}",span={GridItemSpan(maxLineSpan)}) {
                    Row(Modifier.fillMaxWidth().padding(top=5.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text(league.name,fontSize=17.sp,fontWeight=FontWeight.Bold,color=Mint,modifier=Modifier.weight(1f))
                        Text(feed?.updated?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("'Updated' h:mm:ss a")) ?: "Awaiting feed",color=Muted,fontSize=10.sp)
                    }
                }
                feed?.error?.let { warning -> item(key="error-${league.name}",span={GridItemSpan(maxLineSpan)}) {
                    Text(warning,color=Color(0xFFFFCA82),fontSize=11.sp)
                } }
                if (feed?.games.isNullOrEmpty()) {
                    item(key="empty-${league.name}",span={GridItemSpan(maxLineSpan)}) {
                        Text(when {
                            feed?.updated!=null -> "No ${league.name} games scheduled today."
                            feed?.error!=null -> "Scores unavailable. The feed will retry automatically."
                            !state.online -> "Waiting for a connection."
                            else -> "Loading ${league.name} scores…"
                        },color=Muted,fontSize=13.sp,modifier=Modifier.padding(bottom=8.dp))
                    }
                } else items(sortedGames(feed!!.games),key={"${league.name}-${it.id}"}) { GameCard(it) }
            }
        }
    }
}
