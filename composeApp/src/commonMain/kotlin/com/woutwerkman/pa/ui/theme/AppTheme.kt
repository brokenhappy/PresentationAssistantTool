package com.woutwerkman.pa.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF0D1B2A),
    primaryContainer = Color(0xFF1B3A5C),
    onPrimaryContainer = Color(0xFFD0E4FF),
    secondary = Color(0xFFA5D6A7),
    onSecondary = Color(0xFF0D1B2A),
    background = Color(0xFF1A1A2E),
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF222240),
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF2A2A4A),
    onSurfaceVariant = Color(0xFFB0B0C0),
    error = Color(0xFFEF9A9A),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD0E4FF),
    onPrimaryContainer = Color(0xFF0D1B2A),
    secondary = Color(0xFF388E3C),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF8F8FC),
    onBackground = Color(0xFF1A1A2E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1A2E),
    surfaceVariant = Color(0xFFE8E8F0),
    onSurfaceVariant = Color(0xFF505060),
    error = Color(0xFFD32F2F),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
