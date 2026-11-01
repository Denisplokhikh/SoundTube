package com.example.localplayer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Типографика терминала: всё моноширинное, как в десктопном SoundTube.
 *
 * Заголовки экранов — 22.sp Bold, подпись под ними — 12.sp обычного начертания.
 * Размеры подобраны так, чтобы длинные названия треков и подписи статусов
 * не «прыгали» при смене шрифта.
 */
private val Mono = FontFamily.Monospace

private fun mono(
    size: Int,
    lineHeight: Int,
    weight: FontWeight = FontWeight.Normal,
) = TextStyle(
    fontFamily = Mono,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = 0.sp,
)

val SoundTubeTypography = Typography(
    displayLarge = mono(34, 40, FontWeight.Bold),
    displayMedium = mono(30, 36, FontWeight.Bold),
    displaySmall = mono(26, 32, FontWeight.Bold),

    headlineLarge = mono(26, 32, FontWeight.Bold),
    headlineMedium = mono(24, 30, FontWeight.Bold),
    headlineSmall = mono(22, 28, FontWeight.Bold),

    titleLarge = mono(22, 28, FontWeight.Bold),
    titleMedium = mono(15, 20, FontWeight.SemiBold),
    titleSmall = mono(13, 18, FontWeight.SemiBold),

    bodyLarge = mono(14, 20),
    bodyMedium = mono(13, 18),
    bodySmall = mono(12, 16, FontWeight.Normal),

    labelLarge = mono(13, 18, FontWeight.Medium),
    labelMedium = mono(12, 16),
    labelSmall = mono(11, 14),
)
