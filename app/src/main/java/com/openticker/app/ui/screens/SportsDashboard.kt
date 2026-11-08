package com.openticker.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.openticker.app.util.shouldHideSportToday
import com.openticker.app.viewmodel.*
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun SportsDashboard(state: DashboardState, onSelectGroup: (SportsGroup) -> Unit,
    modifier: Modifier = Modifier, interactionHeld: Boolean = false, onDetailsVisibilityChange: (Boolean) -> Unit = {},
    sportsTab: PhillySportsTab? = PhillySportsTab.FOOTBALL, onUseGroups: () -> Unit = {}, onAdvanceSportTab: () -> Unit = {}) {
    val now by produceState(Instant.now()) { while(true) { value=Instant.now();delay(15_000) } }
    val settings=state.settings.copy(leagues=state.settings.leagues.filterNot { shouldHideSportToday(state.scores[it],state.online,now) }.toSet())
    var groupName by rememberSaveable(state.settings.activeSportsGroup,state.settings.sportsGroupA,state.settings.sportsGroupB) { mutableStateOf(state.settings.activeSportsGroup.name) }
    var page by rememberSaveable(sportsTab,state.settings.activeSportsGroup,state.settings.sportsGroupA,state.settings.sportsGroupB) { mutableIntStateOf(0) }
    var browsePaused by rememberSaveable { mutableStateOf(false) }
    var browseSecondsRemaining by rememberSaveable(sportsTab) { mutableIntStateOf(10) }
    val group=SportsGroup.valueOf(groupName)
    val leagues=sportsTab?.enabledLeagues(settings) ?: settings.visibleGroupLeagues(group)
    val counts=state.scores.mapValues { it.value.games.size }
    val latestTabAdvance by rememberUpdatedState(onAdvanceSportTab)
    val latestSettings by rememberUpdatedState(settings)
    val automatic=settings.sportsAutoBrowse && !settings.slideshowPaused && !browsePaused && !interactionHeld
    BoxWithConstraints(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(SportsBackground,Color(0xFF092021),SportsBackground)))) {
        val grid=leagues.size==1 && maxWidth>=850.dp
        val pageSize=(if(maxHeight>=580.dp) 2 else 1)*(if(grid) 2 else 1)
        val titleSize=if(maxHeight<450.dp) 32.sp else 44.sp
        val pageCount=sportsPageCount(counts,leagues,pageSize)
        val currentPage=Math.floorMod(page,pageCount)
        val leaguePages=((leagues.size+2)/3).coerceAtLeast(1)
        val visibleLeagues=leagues.chunked(3).getOrElse(currentPage%leaguePages) { emptyList() }
        val gamePage=currentPage/leaguePages
        val latestCounts by rememberUpdatedState(counts)
        val advance={
            if(sportsTab==null) {
                val next=nextSportsPage(SportsBrowsePage(group,currentPage),latestSettings,latestCounts,pageSize)
                groupName=next.group.name;page=next.page
            } else if(currentPage+1>=pageCount) { page=0;latestTabAdvance() } else page=currentPage+1
            browseSecondsRemaining=10
        }
        LaunchedEffect(automatic,sportsTab,group,currentPage,pageSize,pageCount,leagues) {
            if(automatic) while(true) {
                delay(1_000)
                if(browseSecondsRemaining<=1) advance() else browseSecondsRemaining--
            }
        }
        val freshLive=leagues.sumOf { league ->
            val feed=state.scores[league]
            if(state.online && feed!=null && feed.error==null && !feed.restored && feed.scheduleDate==now.atZone(PhiladelphiaZone).toLocalDate() && feed.updated?.let { now.epochSecond-it.epochSecond in 0..300 && it.atZone(PhiladelphiaZone).toLocalDate()==now.atZone(PhiladelphiaZone).toLocalDate() }==true)
                feed.games.count { it.status==GameStatus.LIVE } else 0
        }
        val totalGames=leagues.sumOf { counts[it] ?: 0 }
        Column(Modifier.fillMaxSize().padding(horizontal=if(maxWidth>=1000.dp) 28.dp else 18.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(if(freshLive>0) "●  LIVE SCORE CENTER" else "●  PHILADELPHIA SCORE CENTER",color=Mint,fontFamily=DisplayFont,fontSize=9.sp,lineHeight=12.sp,letterSpacing=1.7.sp)
                    Text(sportsTab?.title ?: "Sports Scoreboard",fontFamily=DisplayFont,fontSize=titleSize,lineHeight=titleSize*1.1f,maxLines=1)
                }
                Row(Modifier.border(1.dp,Border,RoundedCornerShape(11.dp)).padding(horizontal=18.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("Today",color=Muted,fontSize=11.sp,lineHeight=15.sp)
                    Text(now.atZone(PhiladelphiaZone).format(DateTimeFormatter.ofPattern("MMM d")),fontFamily=DisplayFont,fontSize=15.sp,lineHeight=19.sp)
                }
                Text("${if(freshLive>0) "● $freshLive LIVE   " else ""}$totalGames GAMES",color=if(freshLive>0) Mint else Muted,fontSize=10.sp,lineHeight=14.sp)
            }
            Row(Modifier.weight(1f).fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                if(visibleLeagues.isEmpty()) EmptyPanel("No games scheduled today", "Confirmed empty sports are hidden. Schedules keep refreshing; unknown or unavailable feeds stay visible.")
                visibleLeagues.forEach { league -> key(sportsTab,group,league) {
                    LeagueColumn(league,state.scores[league],state.online,gamePage,pageSize,onDetailsVisibilityChange,
                        Modifier.weight(1f).fillMaxHeight(),twoColumns=grid)
                } }
            }
            Row(Modifier.fillMaxWidth().heightIn(min=28.dp).horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("PHILADELPHIA FIRST",fontFamily=DisplayFont,color=Mint,fontSize=10.sp,lineHeight=13.sp,letterSpacing=.8.sp)
                if(sportsTab==null) SportsGroup.entries.forEach { item ->
                    TextButton(onClick={groupName=item.name;page=0;browseSecondsRemaining=10;onSelectGroup(item)},contentPadding=PaddingValues(horizontal=6.dp,vertical=0.dp)) {
                        Text(settings.groupLabel(item),color=if(group==item) Mint else Muted,fontSize=11.sp,lineHeight=14.sp)
                    }
                } else TextButton(onClick=onUseGroups,contentPadding=PaddingValues(horizontal=6.dp,vertical=0.dp)) { Text("Custom groups",color=Muted,fontSize=10.sp,lineHeight=13.sp) }
                Text("Page ${currentPage+1}/$pageCount",color=Muted,fontSize=10.sp,lineHeight=13.sp)
                TextButton(onClick={browsePaused=!browsePaused},enabled=settings.sportsAutoBrowse && !settings.slideshowPaused,contentPadding=PaddingValues(horizontal=6.dp,vertical=0.dp)) {
                    Text(if(automatic) "Auto · ${browseSecondsRemaining}s" else "Browse paused",fontSize=10.sp,lineHeight=13.sp)
                }
                TextButton(onClick=advance,contentPadding=PaddingValues(horizontal=6.dp,vertical=0.dp)) { Text("Next ›",fontSize=11.sp,lineHeight=14.sp) }
                Text("ESPN · today in Philadelphia · tap a game for details",color=Muted,fontSize=9.sp,lineHeight=12.sp)
            }
        }
    }
}

@Composable private fun LeagueColumn(league: League, feed: LeagueState?, online: Boolean, page: Int, pageSize: Int,
    onDetailsVisibilityChange: (Boolean) -> Unit, modifier: Modifier = Modifier, twoColumns: Boolean = false) {
    val now by produceState(Instant.now()) { while(true) { value=Instant.now(); delay(30_000) } }
    val oldDate=(feed?.scheduleDate ?: feed?.updated?.atZone(PhiladelphiaZone)?.toLocalDate())?.let {
        it!=now.atZone(PhiladelphiaZone).toLocalDate()
    }==true
    val warning = when {
        !online -> if(feed?.updated==null) "Offline · no saved scores" else "Offline · saved scores"
        feed?.error!=null -> feed.error
        feed?.loading==true -> "Refreshing scores…"
        feed?.restored==true -> "Saved scores · awaiting refresh"
        oldDate -> "Older scoreboard · awaiting today's feed"
        feed?.updated?.let { now.epochSecond-it.epochSecond>300 }==true -> "Scores update overdue"
        else -> null
    }
    val games=remember(feed?.games) { philadelphiaFirstGames(feed?.games.orEmpty()) }
    val indices=sportsPageIndices(games.size,page,pageSize)
    val updated=feed?.updated?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("'Updated' MMM d, h:mm:ss a")) ?: "Awaiting feed"
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Panel).border(1.dp,Border,RoundedCornerShape(16.dp)).padding(12.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)) {
            LeagueIcon(league)
            Column(Modifier.weight(1f)) {
                Text(leagueTitle(league),color=TextStrong,fontFamily=DisplayFont,fontSize=21.sp,lineHeight=25.sp,fontWeight=FontWeight.Bold,maxLines=2)
                Text(if(warning!=null) "Saved / pending scoreboard" else "${games.count { it.status==GameStatus.LIVE }} live · ${games.count { it.status==GameStatus.UPCOMING }} upcoming · ${games.count { it.status==GameStatus.FINAL }} final",color=Muted,fontSize=9.sp,lineHeight=12.sp)
            }
            Column(horizontalAlignment=Alignment.End) {
                phillyTeamName(league)?.let { Text("PHILADELPHIA $it".uppercase(),fontFamily=DisplayFont,color=Mint,fontSize=10.sp,lineHeight=13.sp) }
                Text(if(games.isEmpty()) "Awaiting schedule" else "${indices.first+1}–${indices.last+1} of ${games.size}",color=Muted,fontSize=9.sp,lineHeight=12.sp)
            }
        }
        Text(updated,color=Muted,fontSize=10.sp, lineHeight=13.sp)
        warning?.let { Text(it,color=Color(0xFFFFCA82),fontSize=10.sp, lineHeight=13.sp) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(9.dp)) {
            if(games.isEmpty()) Text(when {
                feed?.updated!=null -> if(oldDate || feed.restored || !online || feed.error!=null)
                    "No games in the saved scoreboard. Check the update date above." else "No ${league.name} games scheduled today."
                feed?.error!=null -> "Scores unavailable. Retrying automatically."
                !online -> "Waiting for a connection."
                else -> "Loading ${league.name} scores…"
            },color=Muted,fontSize=13.sp, lineHeight=17.sp,modifier=Modifier.padding(vertical=12.dp))
            indices.toList().chunked(if(twoColumns) 2 else 1).forEach { row ->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    row.forEach { index -> key(games[index].id) {
                        Box(Modifier.weight(1f)) { GameCard(games[index],stale=warning!=null,onDetailsVisibilityChange=onDetailsVisibilityChange,feedNote=listOfNotNull(warning,updated).joinToString(" · ")) }
                    } }
                    if(twoColumns && row.size==1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

private fun leagueTitle(league: League): String = when(league) {
    League.NFL -> "National Football League"
    League.NCAAF -> "College Football"
    League.NHL -> "National Hockey League"
    League.NBA -> "National Basketball Association"
    League.MLB -> "Major League Baseball"
}
