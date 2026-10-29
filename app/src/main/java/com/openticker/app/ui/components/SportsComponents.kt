package com.openticker.app.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.openticker.app.data.model.*
import com.openticker.app.ui.theme.*
import com.openticker.app.util.*
import com.openticker.app.viewmodel.LeagueState
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun SectionLabel(text: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, modifier = Modifier.weight(1f))
        trailing?.let { Text(it, color = Muted, fontSize = 10.sp) }
    }
}
@Composable fun LeagueTabs(enabled: Set<League>, selected: League?, onSelect: (League) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        enabled.sortedBy { it.ordinal }.forEach { league ->
            val active = selected == league
            TextButton(onClick = { onSelect(league) }, colors = ButtonDefaults.textButtonColors(contentColor = if (active) Mint else Muted),
                shape = RoundedCornerShape(10.dp), modifier = Modifier.background(if (active) Mint.copy(alpha=.1f) else Color.Transparent, RoundedCornerShape(10.dp))) {
                Text(league.name, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
@Composable fun SportsPanel(enabled: Set<League>, scores: Map<League, LeagueState>, online: Boolean, modifier: Modifier = Modifier) {
    var selectedName by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(League.NFL.name) }
    val selected = enabled.find { it.name == selectedName } ?: enabled.minByOrNull { it.ordinal }
    val feed = selected?.let { scores[it] }
    Column(modifier.background(Brush.verticalGradient(listOf(SportsBackground, Color(0xFF151B25)))).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("THE SCOREBOARD", "TODAY · EASTERN")
        LeagueTabs(enabled, selected) { selectedName = it.name }
        val warning = if (!online) "Offline · showing last received scores" else feed?.error
        warning?.let { Text(it, color = Color(0xFFFFCA82), fontSize = 11.sp) }
        feed?.updated?.let { Text("Updated ${it.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm:ss a"))}", color = Muted, fontSize = 10.sp) }
        if (selected == null) {
            EmptyPanel("Your leagues, your dashboard", "Enable a league in Settings to see scores.")
        } else if (feed == null || (feed.updated == null && feed.error == null)) {
            EmptyPanel(if (online) "Loading ${selected.name} scores" else "Waiting for a connection", "Live scores will appear when the sports feed is available.")
        } else if (feed.games.isEmpty()) {
            EmptyPanel(if (feed.updated == null) "Scores unavailable" else "No ${selected.name} games today", if (feed.updated == null) "The feed will retry automatically." else "Choose another league. The scoreboard checks for new games automatically.")
        } else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 10.dp)) {
            items(sortedGames(feed.games), key = { it.id }) { GameCard(it) }
        }
    }
}
@Composable fun EmptyPanel(title: String, detail: String) {
    Column(Modifier.fillMaxWidth().padding(vertical=32.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text(title, fontSize=18.sp, fontWeight=FontWeight.SemiBold)
        Text(detail, color=Muted, fontSize=13.sp)
    }
}
@Composable fun GameCard(game: Game) {
    val live = game.status == GameStatus.LIVE
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
        .background(Color(0xFF1D2430)).border(1.dp, if (live) Mint.copy(alpha=.38f) else Color.White.copy(alpha=.05f), RoundedCornerShape(14.dp))
        .padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
            Text(if (live) "● LIVE" else when(game.status) { GameStatus.UPCOMING -> "UPCOMING"; GameStatus.POSTPONED -> "POSTPONED"; else -> game.status.name },
                color=if(live) Mint else Muted, fontSize=10.sp, fontWeight=FontWeight.Bold, modifier=Modifier.weight(1f))
            Text(gameStatusText(game), fontSize=10.sp, color=if(live) Mint else Muted, maxLines=1, overflow=TextOverflow.Ellipsis, modifier=Modifier.widthIn(max=200.dp))
        }
        TeamRow(game.away, game.awayScore, game.status)
        TeamRow(game.home, game.homeScore, game.status)
    }
}
@Composable private fun TeamRow(team: Team, score: Int?, status: GameStatus) {
    Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha=.05f)), contentAlignment=Alignment.Center) {
            Text(team.abbreviation.take(3), fontSize=9.sp, color=Muted)
            team.logo?.let { AsyncImage(model=it, contentDescription="${team.name} logo", modifier=Modifier.size(28.dp)) }
        }
        Column(Modifier.weight(1f)) {
            Text(team.name, maxLines=1, overflow=TextOverflow.Ellipsis, fontSize=14.sp, fontWeight=FontWeight.Medium)
            Text(team.abbreviation, color=Muted, fontSize=10.sp)
        }
        Text(if(status == GameStatus.LIVE || status == GameStatus.FINAL) score?.toString() ?: "—" else "—", fontSize=25.sp, fontWeight=FontWeight.SemiBold)
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable fun SportsTicker(games: List<Game>, enabled: Set<League>) {
    val filtered = remember(games, enabled) { sortedGames(filterLeagues(games, enabled)) }
    val text = remember(filtered) { if (filtered.isEmpty()) "OPENTICKER   •   Waiting for scheduled games and score updates" else filtered.joinToString("     ◆     ") { tickerText(it) } }
    Row(Modifier.fillMaxWidth().height(42.dp).background(Color(0xFF080C13)), verticalAlignment=Alignment.CenterVertically) {
        Text("  SCORE WIRE  ", color=SportsBackground, fontSize=10.sp, fontWeight=FontWeight.ExtraBold,
            modifier=Modifier.fillMaxHeight().background(Mint).wrapContentHeight(Alignment.CenterVertically))
        Text(List(3) { text }.joinToString("     ◆     "), maxLines=1, softWrap=false, color=Color(0xFFDCE5F3), fontSize=12.sp,
            modifier=Modifier.weight(1f).padding(horizontal=12.dp).basicMarquee(iterations=Int.MAX_VALUE, repeatDelayMillis=0, initialDelayMillis=0, spacing=MarqueeSpacing(48.dp), velocity=38.dp))
    }
}
