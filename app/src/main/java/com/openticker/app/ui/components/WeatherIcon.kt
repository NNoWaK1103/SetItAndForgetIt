package com.openticker.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.openticker.app.data.model.*
import kotlin.math.cos
import kotlin.math.sin

@Composable fun WeatherIcon(code: Int?, size: Dp = 32.dp, isDay: Boolean = true) {
    val glyph = weatherCondition(code, isDay).glyph
    Canvas(Modifier.size(size)) {
        val u = this.size.width / 100f
        val blue = Color(0xFF91BDFA); val cloud = Color(0xFFC9D8EB); val gold = Color(0xFFFFD276)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Color, width: Float = 4f) =
            drawLine(color, Offset(x1*u, y1*u), Offset(x2*u, y2*u), width*u)
        fun sun(cx: Float, cy: Float, r: Float) {
            drawCircle(gold, r*u, Offset(cx*u, cy*u))
            for (i in 0..7) {
                val a = i * Math.PI / 4
                line(cx + cos(a).toFloat()*(r+6), cy+sin(a).toFloat()*(r+6), cx+cos(a).toFloat()*(r+13), cy+sin(a).toFloat()*(r+13), gold, 3f)
            }
        }
        fun clouds() {
            drawCircle(cloud, 17*u, Offset(34*u, 53*u)); drawCircle(cloud, 23*u, Offset(53*u, 44*u))
            drawCircle(cloud, 16*u, Offset(73*u, 55*u)); drawRect(cloud, Offset(30*u, 52*u), Size(45*u, 18*u))
        }
        when (glyph) {
            WeatherGlyph.SUN -> sun(50f,50f,21f)
            WeatherGlyph.MOON -> {
                drawArc(gold, 35f, 285f, false, Offset(25*u,18*u), Size(52*u,60*u), style=Stroke(9*u))
                drawCircle(gold, 3*u, Offset(75*u,25*u))
            }
            WeatherGlyph.CLOUD_SUN -> { sun(35f,32f,14f); clouds() }
            WeatherGlyph.CLOUD -> clouds()
            WeatherGlyph.FOG -> { clouds(); line(20f,78f,75f,78f,blue,3f); line(30f,88f,85f,88f,blue,3f) }
            WeatherGlyph.RAIN -> { clouds(); for (x in listOf(33f,53f,73f)) line(x,76f,x-5,88f,blue) }
            WeatherGlyph.SNOW -> { clouds(); for (x in listOf(33f,53f,73f)) { line(x-4,81f,x+4,89f,blue,2f); line(x+4,81f,x-4,89f,blue,2f) } }
            WeatherGlyph.STORM -> {
                clouds(); val p = Path().apply { moveTo(52*u,68*u); lineTo(42*u,85*u); lineTo(53*u,85*u); lineTo(47*u,98*u); lineTo(68*u,77*u); lineTo(56*u,77*u); close() }; drawPath(p,gold)
            }
            WeatherGlyph.UNKNOWN -> drawCircle(blue, 24*u, Offset(50*u,50*u), style=Stroke(3*u))
        }
    }
}
