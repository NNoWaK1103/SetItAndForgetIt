package com.openticker.app.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.openticker.app.data.model.*
import com.openticker.app.ui.theme.*
import com.openticker.app.util.*

@Composable fun SectionLabel(text: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = Mint, fontSize = 10.sp, lineHeight=14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, modifier = Modifier.weight(1f))
        trailing?.let { Text(it, color = Muted, fontSize = 10.sp, lineHeight=13.sp) }
    }
}
@Composable fun LeagueTabs(enabled: Set<League>, selected: League?, onSelect: (League) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        enabled.sortedBy { it.ordinal }.forEach { league ->
            val active = selected == league
            TextButton(onClick = { onSelect(league) }, colors = ButtonDefaults.textButtonColors(contentColor = if (active) Mint else Muted),
                shape = RoundedCornerShape(10.dp), modifier = Modifier.background(if (active) Mint.copy(alpha=.1f) else Color.Transparent, RoundedCornerShape(10.dp))) {
                Text(league.name, fontSize = 12.sp, lineHeight=15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
@Composable fun EmptyPanel(title: String, detail: String) {
    Column(Modifier.fillMaxWidth().padding(vertical=32.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text(title, fontSize=18.sp, lineHeight=23.sp, fontWeight=FontWeight.SemiBold, fontFamily=DisplayFont)
        Text(detail, color=Muted, fontSize=13.sp, lineHeight=17.sp)
    }
}
@Composable fun GameCard(
    game: Game,
    onDetailsVisibilityChange: (Boolean) -> Unit = {},
    feedNote: String? = null,
    stale: Boolean = false
) {
    val live = game.status == GameStatus.LIVE
    val freshLive = live && !stale
    val statusColor = if (stale) Color(0xFFFFC36B) else if (live) Mint else Muted
    var showDetails by remember(game.id) { mutableStateOf(false) }
    val visibilityCallback by rememberUpdatedState(onDetailsVisibilityChange)
    DisposableEffect(game.id, showDetails) {
        val wasVisible = showDetails
        if (wasVisible) visibilityCallback(true)
        onDispose { if (wasVisible) visibilityCallback(false) }
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
        .background(PanelRaised.copy(alpha=.55f)).border(1.dp, if (stale) statusColor.copy(alpha=.38f) else if (freshLive) Mint.copy(alpha=.50f) else Border, RoundedCornerShape(14.dp))
        .clickable(role = Role.Button, onClickLabel = "Show game details") { showDetails = true }
        .padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            Text(sportsStatusCaption(game, stale),
                color=if(freshLive) SportsBackground else statusColor, fontSize=9.sp, lineHeight=12.sp, fontWeight=FontWeight.Bold,
                maxLines=1, overflow=TextOverflow.Ellipsis, modifier=Modifier.weight(1f)
                    .background(if(freshLive) Mint else statusColor.copy(alpha=.08f),RoundedCornerShape(4.dp)).padding(horizontal=6.dp,vertical=4.dp))
            Text(gameStatusText(game), fontSize=10.sp, lineHeight=13.sp, color=statusColor, maxLines=1,
                overflow=TextOverflow.Ellipsis, modifier=Modifier.weight(1f))
        }
        HorizontalDivider(color=Border.copy(alpha=.7f))
        TeamRow(game.away, game.awayScore, game.status, freshLive && game.situation?.possessionTeamId != null && game.situation.possessionTeamId == game.away.id, game.league)
        TeamRow(game.home, game.homeScore, game.status, freshLive && game.situation?.possessionTeamId != null && game.situation.possessionTeamId == game.home.id, game.league)
        HorizontalDivider(color=Border.copy(alpha=.7f))
        // Fixed two-row budget keeps the hands-free brief inside compact landscape cards.
        val brief = sportsGameBrief(game)
        Column(Modifier.fillMaxWidth().height(30.dp)) {
            brief.forEachIndexed { index, line ->
                Text(line, color=if (freshLive && index == 0 && sportsSituationText(game).isNotBlank()) Mint else Muted,
                    fontSize=10.sp, lineHeight=14.sp, maxLines=1, overflow=TextOverflow.Ellipsis)
            }
        }
    }
    if (showDetails) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            title = { Text("${game.away.abbreviation} at ${game.home.abbreviation}") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text("${game.league.name} • ${sportsStatusCaption(game, stale)} • ${gameStatusText(game)}", color=statusColor)
                    feedNote?.let { Text(it, color=Muted, fontSize=12.sp, lineHeight=15.sp) }
                    TeamRow(game.away, game.awayScore, game.status, league=game.league)
                    TeamRow(game.home, game.homeScore, game.status, league=game.league)
                    if (game.detail.isNotBlank()) Text(game.detail)
                    liveSituationText(game).takeIf(String::isNotBlank)?.let { Text(it, color=if (stale) Muted else Mint) }
                    game.venue?.let { DetailLine("Venue", it) }
                    if (game.broadcasts.isNotEmpty()) DetailLine("Coverage", game.broadcasts.joinToString(" / "))
                    if (live) game.situation?.lastPlay?.let { DetailLine(if (stale) "Last saved play" else "Latest play", it) }
                    Text("ESPN scoreboard • Coverage and live details appear when supplied by the provider.", color=Muted, fontSize=11.sp, lineHeight=14.sp)
                }
            },
            confirmButton = { TextButton(onClick = { showDetails = false }) { Text("Close") } }
        )
    }
}

@Composable private fun DetailLine(label: String, value: String) {
    Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
        Text(label, color=Muted, fontSize=11.sp, lineHeight=14.sp)
        Text(value, fontSize=14.sp, lineHeight=18.sp)
    }
}

internal fun liveSituationText(game: Game): String = sportsSituationText(game)

@Composable private fun TeamRow(team: Team, score: Int?, status: GameStatus, possession: Boolean = false, league: League = League.NFL) {
    val philly=team.abbreviation.equals("PHI",true) || team.name.startsWith("Philadelphia ",true)
    val badge=when {
        philly && league==League.NFL -> Color(0xFF087C81)
        philly && league==League.NHL -> Color(0xFFF47732)
        philly && league==League.MLB -> Color(0xFFC43148)
        philly && league==League.NBA -> Color(0xFF2974B9)
        team.abbreviation in setOf("PIT","BOS") -> Color(0xFFD6BB00)
        team.abbreviation in setOf("WAS","WSH") -> Color(0xFF852747)
        team.abbreviation in setOf("BAL") -> Color(0xFF503182)
        team.abbreviation in setOf("OSU","TEX","KC","NJ","CAR") -> Color(0xFFB72A43)
        team.abbreviation in setOf("TENN","CLEM","EDM") -> Color(0xFFEF7B36)
        else -> Color(0xFF285F79)
    }
    Row(Modifier.fillMaxWidth().heightIn(min=42.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(badge).border(1.dp,Color.White.copy(alpha=.24f),CircleShape),contentAlignment=Alignment.Center) {
            Text(team.abbreviation.take(4),fontFamily=DisplayFont,fontSize=11.sp,lineHeight=14.sp,color=Color.White)
        }
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(1.dp)) {
            Text(if(philly) "Philadelphia" else team.abbreviation,color=Muted,fontSize=8.sp,lineHeight=10.sp)
            Text(listOfNotNull(team.rank?.let { "#$it" },if(philly) phillyTeamName(league) ?: team.name else team.name).joinToString(" "),
                maxLines=1,overflow=TextOverflow.Ellipsis,fontFamily=DisplayFont,fontSize=19.sp,lineHeight=23.sp,color=TextStrong)
        }
        Text(team.record.orEmpty(),color=Muted,fontSize=9.sp,lineHeight=12.sp,maxLines=1)
        if(possession) Text("●",color=Mint,fontSize=10.sp,lineHeight=12.sp,modifier=Modifier.semantics { contentDescription="Possession" })
        Text(if(status==GameStatus.LIVE || status==GameStatus.FINAL) score?.toString() ?: "—" else "—",fontFamily=DisplayFont,fontSize=30.sp,lineHeight=36.sp,color=TextStrong,
            modifier=Modifier.widthIn(min=24.dp))
    }
}
