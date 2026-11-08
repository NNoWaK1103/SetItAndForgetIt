package com.openticker.app.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openticker.app.R
import com.openticker.app.data.model.League
import com.openticker.app.slideshow.DashboardSlide
import com.openticker.app.ui.theme.*
import kotlinx.coroutines.delay
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable fun DashboardHeader(online: Boolean, slide: DashboardSlide, paused: Boolean,
    onSelect: (DashboardSlide) -> Unit, onTogglePlayback: () -> Unit,
    settingsLoaded: Boolean = true, feedSummary: String = "", feedNotice: Boolean = false,
    onFeedStatus: () -> Unit = {}, sportsTab: PhillySportsTab? = PhillySportsTab.FOOTBALL,
    onSportsTab: (PhillySportsTab) -> Unit = {}, visibleSportsLeagues: Set<League> = League.entries.toSet(), onRefresh: () -> Unit = {}, refreshing: Boolean = false,
    onSettings: () -> Unit) {
    val now by produceState(ZonedDateTime.now(PhiladelphiaZone)) {
        while(true) { value=ZonedDateTime.now(PhiladelphiaZone);delay(1000) }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().background(SportsBackground)) {
        val compact=maxWidth<1000.dp
        Column {
            Row(Modifier.fillMaxWidth().height(if(compact) 72.dp else 86.dp).padding(horizontal=if(compact) 16.dp else 28.dp),
                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(if(compact) 8.dp else 16.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(if(compact) 40.dp else 48.dp).background(Color(0xFF0D2021),RoundedCornerShape(12.dp))
                        .border(1.dp,Border,RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center) {
                        Image(painterResource(R.drawable.philly_bird),contentDescription="Philadelphia Birds Eye",modifier=Modifier.size(if(compact) 34.dp else 42.dp))
                    }
                    Column(Modifier.width(if(compact) 83.dp else 116.dp)) {
                        Text("PHILADELPHIA",color=Muted,fontFamily=DisplayFont,fontSize=8.sp,lineHeight=10.sp,letterSpacing=1.6.sp,maxLines=1)
                        Text("BIRDS EYE",color=TextStrong,fontFamily=DisplayFont,fontSize=if(compact) 22.sp else 25.sp,lineHeight=28.sp,maxLines=1)
                    }
                }
                Row(Modifier.weight(1f).fillMaxHeight().horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.Center) {
                    PhillySportsTab.entries.filter { tab -> tab.leagues.any { it in visibleSportsLeagues } }.forEach { tab ->
                        val label=if(tab==PhillySportsTab.FOOTBALL) when {
                            League.NFL !in visibleSportsLeagues -> "College"
                            League.NCAAF !in visibleSportsLeagues -> "NFL"
                            else -> tab.label
                        } else tab.label
                        HeaderNav(label,slide==DashboardSlide.SPORTS && sportsTab==tab,compact) { onSportsTab(tab) }
                    }
                    listOf(DashboardSlide.WEATHER,DashboardSlide.STOCKS).forEach { target ->
                        HeaderNav(target.label,slide==target,compact) { onSelect(target) }
                    }
                }
                Column(Modifier.width(if(compact) 66.dp else 80.dp).heightIn(min=48.dp)
                    .clickable(role=Role.Button,onClickLabel="Show data feed status",onClick=onFeedStatus).wrapContentHeight()
                    .semantics { contentDescription="Philadelphia time. ${if(online) "Online" else "Offline"}. $feedSummary" },horizontalAlignment=Alignment.End) {
                    Text("PHILADELPHIA",color=Muted,fontFamily=DisplayFont,fontSize=8.sp,lineHeight=10.sp,letterSpacing=.6.sp)
                    Text(now.format(DateTimeFormatter.ofPattern("h:mm a",Locale.US)),fontFamily=DisplayFont,fontSize=17.sp,lineHeight=21.sp)
                    if(!online || feedNotice) Text(if(!online) "OFFLINE" else "FEED NOTICE",color=Color(0xFFFFCA82),fontSize=7.sp,lineHeight=9.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
                Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    HeaderControl(if(paused) "Resume automatic slideshow" else "Pause automatic slideshow",onTogglePlayback) {
                        Text(if(paused) "▷" else "Ⅱ",color=Muted,fontSize=17.sp,lineHeight=20.sp)
                    }
                    HeaderControl(if(slide==DashboardSlide.STOCKS) "Market data refreshes automatically" else "Refresh scores and weather",onRefresh,
                        enabled=online && !refreshing && slide!=DashboardSlide.STOCKS) { HeaderGlyph(false) }
                    HeaderControl("Scoreboard settings",onSettings,settingsLoaded) { HeaderGlyph(true) }
                }
            }
            HorizontalDivider(color=Border,thickness=1.dp)
        }
    }
}

@Composable private fun HeaderNav(label: String,active: Boolean,compact: Boolean,onClick: () -> Unit) {
    Column(Modifier.fillMaxHeight().widthIn(min=if(compact) 54.dp else 74.dp)
        .background(if(active) Mint.copy(alpha=.04f) else Color.Transparent)
        .clickable(role=Role.Tab,onClick=onClick).semantics { selected=active },horizontalAlignment=Alignment.CenterHorizontally) {
        Box(Modifier.weight(1f).padding(horizontal=if(compact) 8.dp else 12.dp),contentAlignment=Alignment.Center) {
            Text(label,color=if(active) TextStrong else Muted,fontWeight=FontWeight.Bold,fontSize=if(compact) 11.sp else 12.sp,lineHeight=16.sp,maxLines=1)
        }
        Box(Modifier.width(if(compact) 38.dp else 62.dp).height(2.dp).background(if(active) Mint else Color.Transparent))
    }
}

@Composable private fun HeaderControl(label: String,onClick: () -> Unit,enabled: Boolean=true,content: @Composable () -> Unit) {
    IconButton(onClick=onClick,enabled=enabled,modifier=Modifier.size(40.dp).border(1.dp,Border,RoundedCornerShape(9.dp))
        .semantics { contentDescription=label }) { content() }
}

@Composable private fun HeaderGlyph(settings: Boolean) {
    Canvas(Modifier.size(19.dp)) {
        val color=Muted;val stroke=Stroke(width=1.6.dp.toPx())
        if(settings) {
            drawCircle(color,radius=size.minDimension*.29f,style=stroke)
            drawCircle(color,radius=size.minDimension*.12f,style=stroke)
            listOf(Offset(0.5f,0.05f) to Offset(.5f,.2f),Offset(.5f,.8f) to Offset(.5f,.95f),Offset(.05f,.5f) to Offset(.2f,.5f),Offset(.8f,.5f) to Offset(.95f,.5f)).forEach { (a,b) ->
                drawLine(color,Offset(a.x*size.width,a.y*size.height),Offset(b.x*size.width,b.y*size.height),strokeWidth=stroke.width)
            }
        } else {
            drawArc(color,45f,285f,false,topLeft=Offset(size.width*.16f,size.height*.16f),size=androidx.compose.ui.geometry.Size(size.width*.68f,size.height*.68f),style=stroke)
            drawPath(Path().apply { moveTo(size.width*.84f,size.height*.18f);lineTo(size.width*.84f,size.height*.48f);lineTo(size.width*.55f,size.height*.48f) },color,style=stroke)
        }
    }
}
