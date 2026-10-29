package com.openticker.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Mint = Color(0xFF74E5BC)
val Ice = Color(0xFF9CC6FF)
val Muted = Color(0xFF9CA9BD)
val SportsBackground = Color(0xFF111318)
val WeatherBackground = Color(0xFF101D35)
@Composable fun OpenTickerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Mint, secondary = Ice,
        background = SportsBackground, surface = Color(0xFF1A202B), onSurface = Color(0xFFF1F5FC),
        onSurfaceVariant = Muted), content = content)
}
