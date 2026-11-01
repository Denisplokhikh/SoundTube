package com.example.localplayer.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Палитра терминальной темы SoundTube.
 *
 * Цвета взяты у десктопного плеера: глубокий чёрный фон, панели на тон светлее,
 * тонкие рамки вместо теней и шалфейно-зелёный акцент. Никаких динамических
 * цветов Material You здесь нет — оформление должно быть одинаковым на всех
 * устройствах.
 */

// --- Основные токены тёмной темы (значения из дизайна десктопа) ---
val Background = Color(0xFF0A0B0A)
val Surface = Color(0xFF121412)
val Border = Color(0xFF232723)
val Primary = Color(0xFFA8D5A2)
val OnPrimary = Color(0xFF0A0B0A)
val StatusGreen = Color(0xFF4ADE80)
val TextPrimary = Color(0xFFEDEDED)
val TextSecondary = Color(0xFF7A807A)

// --- Производные оттенки: поля ввода, второстепенные кнопки, плеер ---
val SurfaceInput = Color(0xFF111311)
val SurfaceRaised = Color(0xFF161916)
val BorderStrong = Color(0xFF2A2E2A)
val PlayerSurface = Color(0xFF0E100E)
val Divider = Color(0xFF161816)
val NavActive = Color(0xFF161C16)
val ErrorRed = Color(0xFFCF766F)
val WarnAmber = Color(0xFFCDB26A)

// --- Светлая «бумажная» версия той же темы (режим «Светлая» в настройках) ---
val LightBackground = Color(0xFFF4F6F2)
val LightSurface = Color(0xFFFFFFFF)
val LightBorder = Color(0xFFD7DCD3)
val LightPrimary = Color(0xFF3C6E3F)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightStatusGreen = Color(0xFF187A38)
val LightTextPrimary = Color(0xFF161A15)
val LightTextSecondary = Color(0xFF5E665C)
val LightSurfaceInput = Color(0xFFF7F9F5)
val LightSurfaceRaised = Color(0xFFEDF0EA)
val LightBorderStrong = Color(0xFFC3C9BE)
val LightPlayerSurface = Color(0xFFECEFE9)
val LightDivider = Color(0xFFE6E9E3)
val LightNavActive = Color(0xFFE3EBDD)
val LightErrorRed = Color(0xFFB3261E)
val LightWarnAmber = Color(0xFF8A6B12)

/**
 * Набор токенов, которого нет в [androidx.compose.material3.ColorScheme]:
 * [borderStrong], [statusGreen], [divider] и т. п. Компоненты берут цвета
 * отсюда, а не из сырых констант, — иначе светлая тема перестала бы работать.
 */
@Immutable
data class SoundTubeColors(
    val background: Color,
    val surface: Color,
    val surfaceInput: Color,
    val surfaceRaised: Color,
    val border: Color,
    val borderStrong: Color,
    val primary: Color,
    val onPrimary: Color,
    val statusGreen: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val playerSurface: Color,
    val divider: Color,
    val navActive: Color,
    val error: Color,
    val warning: Color,
)

val DarkSoundTubeColors = SoundTubeColors(
    background = Background,
    surface = Surface,
    surfaceInput = SurfaceInput,
    surfaceRaised = SurfaceRaised,
    border = Border,
    borderStrong = BorderStrong,
    primary = Primary,
    onPrimary = OnPrimary,
    statusGreen = StatusGreen,
    textPrimary = TextPrimary,
    textSecondary = TextSecondary,
    playerSurface = PlayerSurface,
    divider = Divider,
    navActive = NavActive,
    error = ErrorRed,
    warning = WarnAmber,
)

val LightSoundTubeColors = SoundTubeColors(
    background = LightBackground,
    surface = LightSurface,
    surfaceInput = LightSurfaceInput,
    surfaceRaised = LightSurfaceRaised,
    border = LightBorder,
    borderStrong = LightBorderStrong,
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    statusGreen = LightStatusGreen,
    textPrimary = LightTextPrimary,
    textSecondary = LightTextSecondary,
    playerSurface = LightPlayerSurface,
    divider = LightDivider,
    navActive = LightNavActive,
    error = LightErrorRed,
    warning = LightWarnAmber,
)

val LocalSoundTubeColors = staticCompositionLocalOf { DarkSoundTubeColors }

/** Точка входа к токенам темы: `SoundTubeTheme.colors.border`. */
object SoundTubeTheme {
    val colors: SoundTubeColors
        @Composable
        @ReadOnlyComposable
        get() = LocalSoundTubeColors.current
}
