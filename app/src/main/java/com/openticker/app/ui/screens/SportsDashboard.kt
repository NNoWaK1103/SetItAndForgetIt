package com.openticker.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openticker.app.data.model.*
import com.openticker.app.settings.*
import com.openticker.app.ui.components.*
import com.openticker.app.ui.theme.*
import com.openticker.app.util.sortedGames
import com.openticker.app.viewmodel.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun SportsDashboard(state: DashboardState, onSelectGroup: (SportsGroup) -> Unit, modifier: Modifier = Modifier) {
    val settings=state.settings
    val leagues=settings.visibleGroupLeagues()
    val groupStates=rememberSaveableStateHolder()
    Column(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(SportsBackground,Color(0xFF19212D)))).padding(horizontal=18.dp,vertical=12.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)) {
        SectionLabel("AROUND THE LEAGUES", "TODAY · US EASTERN")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            SportsGroup.entries.forEach { group ->
                FilterChip(selected=settings.activeSportsGroup==group,onClick={onSelectGroup(group)},
                    label={Text(settings.groupLabel(group),fontSize=12.sp,fontWeight=FontWeight.SemiBold)})
                if(group==SportsGroup.A) Text("|",color=Muted,fontSize=12.sp)
            }
        }
        if(!state.online) Text("Offline · showing last received scores",color=Color(0xFFFFCA82),fontSize=11.sp)
        if(leagues.isEmpty()) EmptyPanel("This sports group is empty","Choose leagues for Group ${settings.activeSportsGroup.name} in Settings, or select the other group.")
        groupStates.SaveableStateProvider(settings.activeSportsGroup.name) {
            if(leagues.size<=3) {
                Row(Modifier.weight(1f).fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    leagues.forEach { league -> key(league) {
                        LeagueColumn(league,state.scores[league],state.online,Modifier.weight(1f).fillMaxHeight())
                    } }
                }
            } else {
                // Custom groups can contain all five leagues; keep readable, equal-width panels.
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    LazyVerticalGrid(columns=GridCells.Fixed(if(maxWidth>=1000.dp) 3 else 2),
                        horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        gridItems(leagues,key={it.name}) { LeagueColumn(it,state.scores[it],state.online,Modifier.height(340.dp)) }
                    }
                }
            }
        }
    }
}

@Composable private fun LeagueColumn(league: League, feed: LeagueState?, online: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha=.025f)).padding(10.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)) {
            LeagueIcon(league)
            Column {
                Text(league.name,color=Mint,fontSize=19.sp,fontWeight=FontWeight.Bold)
                Text(when(league) {
                    League.NFL -> "PRO FOOTBALL"
                    League.NCAAF -> "COLLEGE FOOTBALL"
                    League.NHL -> "HOCKEY"
                    League.NBA -> "BASKETBALL"
                    League.MLB -> "BASEBALL"
                },color=Muted,fontSize=8.sp,letterSpacing=.7.sp)
            }
        }
        Text(feed?.updated?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("'Updated' h:mm:ss a")) ?: "Awaiting feed",color=Muted,fontSize=10.sp)
        feed?.error?.let { Text(it,color=Color(0xFFFFCA82),fontSize=10.sp) }
        val games=remember(feed?.games) { sortedGames(feed?.games.orEmpty()) }
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(9.dp),contentPadding=PaddingValues(bottom=8.dp)) {
            if(games.isEmpty()) item {
                Text(when {
                    feed?.updated!=null -> "No ${league.name} games scheduled today."
                    feed?.error!=null -> "Scores unavailable. Retrying automatically."
                    !online -> "Waiting for a connection."
                    else -> "Loading ${league.name} scores…"
                },color=Muted,fontSize=13.sp,modifier=Modifier.padding(vertical=12.dp))
            }
            items(games,key={it.id}) { GameCard(it) }
        }
    }
}
