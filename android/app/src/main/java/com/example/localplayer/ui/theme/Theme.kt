package com.example.localplayer.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Палитра повторяет терминальную тему десктопного SoundTube.
private val TerminalGreen = Color(0xFF7BE0B3)
private val TerminalGreenDark = Color(0xFF1F5B44)
private val TerminalSurface = Color(0xFF0B0F0E)
private val TerminalSurfaceVariant = Color(0xFF16201D)
private val TerminalBackground = Color(0xFF070A09)
private val TerminalText = Color(0xFFD7E4DE)
private val TerminalMuted = Color(0xFF8FA39B)
private val TerminalOutline = Color(0xFF5C6F67)
private val TerminalOutlineVariant = Color(0xFF2A3833)
private val TerminalError = Color(0xFFFF6B6B)

private val DarkColors = darkColorScheme(
    primary = TerminalGreen,
    onPrimary = TerminalBackground,
    primaryContainer = TerminalGreenDark,
    onPrimaryContainer = TerminalText,
    secondary = Color(0xFF6FC3E8),
    onSecondary = TerminalBackground,
    secondaryContainer = Color(0xFF17323F),
    onSecondaryContainer = Color(0xFFCBE7F5),
    tertiary = Color(0xFFE8C87B),
    onTertiary = TerminalBackground,
    background = TerminalBackground,
    onBackground = TerminalText,
    surface = TerminalSurface,
    onSurface = TerminalText,
    surfaceVariant = TerminalSurfaceVariant,
    onSurfaceVariant = TerminalMuted,
    outline = TerminalOutline,
    outlineVariant = TerminalOutlineVariant,
    error = TerminalError,
    onError = TerminalBackground,
    errorContainer = Color(0xFF4A1D1D),
    onErrorContainer = Color(0xFFFFD9D9),
)

private val LightColors = lightColorScheme(
    primary = TerminalGreenDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEFE0),
    onPrimaryContainer = Color(0xFF06251A),
    secondary = Color(0xFF2A6E8F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDE9F5),
    onSecondaryContainer = Color(0xFF06232F),
    tertiary = Color(0xFF7A5B12),
    onTertiary = Color.White,
    background = Color(0xFFF6FAF8),
    onBackground = Color(0xFF15201C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF15201C),
    surfaceVariant = Color(0xFFE2ECE7),
    onSurfaceVariant = Color(0xFF4C5F57),
    outline = Color(0xFF75877F),
    outlineVariant = Color(0xFFC5D3CC),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun LocalPlayerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = themeMode.isDark()

    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = AppShapes,
        content = content,
    )
}
