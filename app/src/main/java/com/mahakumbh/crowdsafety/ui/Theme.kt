package com.mahakumbh.crowdsafety.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF7C4DFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE7FF),
    secondary = Color(0xFFB388FF),
    // Light theme should use light background and dark text
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F1B24),
    surfaceVariant = Color(0xFFF3F2F7),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB388FF),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF5E35B1),
    secondary = Color(0xFF9A67EA),
    background = Color(0xFF0B0B0D),
    surface = Color(0xFF0F0F12),
    onSurface = Color(0xFFF4F3F8),
    surfaceVariant = Color(0xFF1A1A1D),
)

@Composable
fun CrowdSafetyTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        shapes = Shapes(),
        content = content
    )
}
