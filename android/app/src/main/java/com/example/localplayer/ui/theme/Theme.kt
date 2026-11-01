package com.example.localplayer.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Тема приложения — копия терминального оформления десктопного SoundTube.
 *
 * Динамические цвета Material You (dynamicColor) сознательно не используются:
 * палитра фиксированная, поэтому приложение выглядит одинаково на любом
 * устройстве и в любой прошивке.
 */

private val DarkScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = Color(0xFF1B2A1B),
    onPrimaryContainer = Primary,
    inversePrimary = Color(0xFF3C6E3F),
    secondary = Primary,
    onSecondary = OnPrimary,
    secondaryContainer = SurfaceRaised,
    onSecondaryContainer = TextPrimary,
    tertiary = WarnAmber,
    onTertiary = OnPrimary,
    tertiaryContainer = Color(0xFF2A2415),
    onTertiaryContainer = WarnAmber,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceInput,
    onSurfaceVariant = TextSecondary,
    surfaceTint = Color.Transparent,
    inverseSurface = TextPrimary,
    inverseOnSurface = Background,
    outline = BorderStrong,
    outlineVariant = Border,
    error = ErrorRed,
    onError = OnPrimary,
    errorContainer = Color(0xFF2A1616),
    onErrorContainer = Color(0xFFF3C7C4),
    scrim = Color.Black,
)

private val LightScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = Color(0xFFD6EBD4),
    onPrimaryContainer = Color(0xFF0C2410),
    inversePrimary = Primary,
    secondary = LightPrimary,
    onSecondary = LightOnPrimary,
    secondaryContainer = LightSurfaceRaised,
    onSecondaryContainer = LightTextPrimary,
    tertiary = LightWarnAmber,
    onTertiary = LightOnPrimary,
    tertiaryContainer = Color(0xFFF4E8C8),
    onTertiaryContainer = Color(0xFF2A2105),
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceInput,
    onSurfaceVariant = LightTextSecondary,
    surfaceTint = Color.Transparent,
    inverseSurface = LightTextPrimary,
    inverseOnSurface = LightBackground,
    outline = LightBorderStrong,
    outlineVariant = LightBorder,
    error = LightErrorRed,
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    scrim = Color.Black,
)

/** Никаких «пузырей»: карточки и поля — 8.dp, кнопки — 6.dp, обложки — 4.dp. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(12.dp),
)

@Composable
fun LocalPlayerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = themeMode.isDark()

    CompositionLocalProvider(
        LocalSoundTubeColors provides if (darkTheme) DarkSoundTubeColors else LightSoundTubeColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = SoundTubeTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
