package com.openticker.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.openticker.app.data.model.League

/** Lightweight sport symbols, drawn locally rather than depending on a league logo CDN. */
@Composable fun LeagueIcon(league: League) {
    Canvas(Modifier.size(34.dp).semantics { contentDescription="${league.name} sport icon" }) {
        val u=size.width/100f
        val ink=when(league) {
            League.NFL -> Color(0xFF74E5BC)
            League.NCAAF -> Color(0xFFFFD276)
            League.NHL -> Color(0xFF9CC6FF)
            League.NBA -> Color(0xFFFFB274)
            League.MLB -> Color(0xFFEDA6B2)
        }
        fun line(x: Float,y: Float,x2: Float,y2: Float) = drawLine(ink,Offset(x*u,y*u),Offset(x2*u,y2*u),3*u)
        when(league) {
            League.NFL,League.NCAAF -> {
                drawOval(ink,Offset(10*u,27*u),Size(80*u,46*u),style=Stroke(3*u))
                line(30f,50f,70f,50f)
                listOf(40f,50f,60f).forEach { line(it,41f,it,59f) }
            }
            League.NBA -> {
                drawCircle(ink,35*u,Offset(50*u,50*u),style=Stroke(3*u))
                line(15f,50f,85f,50f);line(50f,15f,50f,85f)
                drawArc(ink,-80f,160f,false,Offset(-28*u,16*u),Size(65*u,68*u),style=Stroke(3*u))
                drawArc(ink,100f,160f,false,Offset(63*u,16*u),Size(65*u,68*u),style=Stroke(3*u))
            }
            League.MLB -> {
                drawCircle(ink,35*u,Offset(50*u,50*u),style=Stroke(3*u))
                for(y in listOf(30f,43f,56f,69f)) { line(31f,y,38f,y+4);line(62f,y,69f,y-4) }
            }
            League.NHL -> {
                line(24f,14f,62f,72f);line(76f,14f,38f,72f)
                line(62f,72f,84f,72f);line(38f,72f,16f,72f)
                drawOval(ink,Offset(39*u,79*u),Size(22*u,10*u))
            }
        }
    }
}
