package com.openticker.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.openticker.app.R

val Mint = Color(0xFF20C8B5)
val Ice = Color(0xFF9DD5D0)
val Muted = Color(0xFF8CA3A3)
val TextStrong = Color(0xFFEDF4F1)
val SportsBackground = Color(0xFF061313)
val WeatherBackground = SportsBackground
val Panel = Color(0xFF0B1B1C)
val PanelRaised = Color(0xFF102425)
val Border = Color(0xFF203536)
// Bundled with its redistribution notice. No network font request is needed.
val DisplayFont = FontFamily(Font(R.font.barlow_condensed_bold, FontWeight.Bold))
@Composable fun OpenTickerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Mint, secondary = Ice,
        background = SportsBackground, surface = Panel, surfaceVariant = PanelRaised,
        onSurface = TextStrong, onBackground = TextStrong, onSurfaceVariant = Muted,
        outline = Border), content = content)
}
