package com.example.localplayer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Палитра повторяет терминальную тему десктопного SoundTube.
private val TerminalGreen = Color(0xFF7BE0B3)
private val TerminalGreenDark = Color(0xFF1F5B44)
private val TerminalSurface = Color(0xFF0B0F0E)
private val TerminalSurfaceVariant = Color(0xFF16201D)
private val TerminalBackground = Color(0xFF070A09)
private val TerminalText = Color(0xFFD7E4DE)
private val TerminalMuted = Color(0xFF8FA39B)
private val TerminalError = Color(0xFFFF6B6B)

private val DarkColors = darkColorScheme(
    primary = TerminalGreen,
    onPrimary = TerminalBackground,
    primaryContainer = TerminalGreenDark,
    onPrimaryContainer = TerminalText,
    secondary = Color(0xFF6FC3E8),
    onSecondary = TerminalBackground,
    background = TerminalBackground,
    onBackground = TerminalText,
    surface = TerminalSurface,
    onSurface = TerminalText,
    surfaceVariant = TerminalSurfaceVariant,
    onSurfaceVariant = TerminalMuted,
    error = TerminalError,
)

private val LightColors = lightColorScheme(
    primary = TerminalGreenDark,
    secondary = Color(0xFF2A6E8F),
    background = Color(0xFFF6FAF8),
    surface = Color(0xFFFFFFFF),
)

@Composable
fun LocalPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
