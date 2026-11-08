package com.example.localplayer.ui.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import com.example.localplayer.ui.components.artworkModel
import com.example.localplayer.ui.theme.SoundTubeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Палитра полноэкранного плеера: глубокий фон из цвета обложки.
 *
 * Зелёный градиент Spotify получается из доминирующего оттенка картинки —
 * поэтому у Beach Boys фон зелёный, у тёмной обложки почти чёрный. Текст
 * на таком фоне всегда белый: плеер сознательно тёмный в любой теме.
 */
@Immutable
data class PlayerPalette(
    val top: Color,
    val middle: Color,
    val bottom: Color,
    /** Светлый акцент того же оттенка: ползунок, кнопки устройства, активные режимы. */
    val accent: Color,
    val hasArtwork: Boolean,
)

private const val PALETTE_SAMPLE_SIZE = 64

/** Доля насыщенности и света, которые остаются у фоновых ступеней градиента. */
private const val TOP_SATURATION = 0.55f
private const val MID_SATURATION = 0.50f
private const val BOTTOM_SATURATION = 0.45f

@Composable
fun rememberPlayerPalette(artwork: String?): PlayerPalette {
    val context = LocalContext.current
    val fallbackAccent = SoundTubeTheme.colors.primary
    val fallback = PlayerPalette(
        top = Color(0xFF1D241D),
        middle = Color(0xFF101410),
        bottom = Color(0xFF060806),
        accent = fallbackAccent,
        hasArtwork = false,
    )

    val palette by produceState(initialValue = fallback, artwork) {
        val model = artworkModel(artwork)
        if (model == null) {
            value = fallback
            return@produceState
        }
        value = runCatching { loadPlayerPalette(context, model) }.getOrNull() ?: fallback
    }
    return palette
}

/**
 * Загружает небольшой превью обложки и считает доминирующий оттенок.
 *
 * Жёсткие фильтры (серое, почти чёрное и почти белое не участвуют) не дают
 * градиенту стать грязно-коричневым из-за одного тёмного угла картинки.
 */
private suspend fun loadPlayerPalette(context: Context, model: Any): PlayerPalette? {
    val request = ImageRequest.Builder(context)
        .data(model)
        .size(PALETTE_SAMPLE_SIZE)
        .allowHardware(false)
        .build()
    val drawable = withContext(Dispatchers.IO) { context.imageLoader.execute(request).drawable }
    val bitmap = when (drawable) {
        null -> null
        is BitmapDrawable -> drawable.bitmap
        else -> runCatching { drawable.toBitmap(PALETTE_SAMPLE_SIZE, PALETTE_SAMPLE_SIZE) }
            .getOrNull()
    } ?: return null

    val tone = withContext(Dispatchers.Default) { dominantTone(bitmap) } ?: return null
    return PlayerPalette(
        top = tone.background(TOP_SATURATION, 0.22f),
        middle = tone.background(MID_SATURATION, 0.12f),
        bottom = tone.background(BOTTOM_SATURATION, 0.06f),
        accent = tone.accent(),
        hasArtwork = true,
    )
}

@Immutable
private data class Tone(val hue: Float, val saturation: Float, val lightness: Float)

private fun Tone.background(saturationScale: Float, lightness: Float): Color = Color.hsl(
    hue = normalizeHue(hue),
    saturation = (saturation * saturationScale).coerceIn(0.10f, 0.46f),
    lightness = lightness.coerceIn(0f, 1f),
)

private fun Tone.accent(): Color = Color.hsl(
    hue = normalizeHue(hue),
    saturation = (saturation * 0.7f).coerceIn(0.35f, 0.75f),
    lightness = 0.72f,
)

private fun normalizeHue(hue: Float): Float = ((hue % 360f) + 360f) % 360f

/**
 * Доминирующий оттенок картинки — усреднение по корзинам в 30°.
 *
 * Оттенок внутри корзины считается по кругу (cos/sin): обычное среднее
 * красного на стыке 0°/360° даёт ~180° и превращает цвет в противоположный.
 */
private fun dominantTone(bitmap: Bitmap): Tone? {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= 0 || height <= 0) return null

    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    val weights = DoubleArray(12)
    val cosSum = DoubleArray(12)
    val sinSum = DoubleArray(12)
    val satSum = DoubleArray(12)
    val lightSum = DoubleArray(12)

    for (pixel in pixels) {
        val alpha = (pixel ushr 24) and 0xFF
        if (alpha < 200) continue

        val red = ((pixel shr 16) and 0xFF) / 255.0
        val green = ((pixel shr 8) and 0xFF) / 255.0
        val blue = (pixel and 0xFF) / 255.0

        val maxChannel = max(red, max(green, blue))
        val minChannel = min(red, min(green, blue))
        val delta = maxChannel - minChannel
        if (delta < 0.001) continue

        val lightness = (maxChannel + minChannel) / 2.0
        val saturation = if (lightness > 0.5) {
            delta / (2.0 - maxChannel - minChannel)
        } else {
            delta / (maxChannel + minChannel)
        }
        if (saturation < 0.16 || lightness < 0.14 || lightness > 0.9) continue

        val hue = when (maxChannel) {
            red -> {
                val raw = (green - blue) / delta
                ((if (green < blue) raw + 6.0 else raw) * 60.0)
            }
            green -> ((blue - red) / delta + 2.0) * 60.0
            else -> ((red - green) / delta + 4.0) * 60.0
        }

        // Насыщенный и средний по светлоте цвет заметнее блёклого и тёмного.
        val weight = saturation * (1.0 - abs(lightness - 0.55) * 1.15)
        if (weight <= 0.02) continue

        val bucketIndex = ((hue / 30.0).toInt() % 12 + 12) % 12
        val radians = Math.toRadians(hue)
        weights[bucketIndex] += weight
        cosSum[bucketIndex] += cos(radians) * weight
        sinSum[bucketIndex] += sin(radians) * weight
        satSum[bucketIndex] += saturation * weight
        lightSum[bucketIndex] += lightness * weight
    }

    var best = -1
    var bestWeight = 0.0
    for (index in weights.indices) {
        if (weights[index] > bestWeight) {
            bestWeight = weights[index]
            best = index
        }
    }
    if (best < 0 || bestWeight <= 0.0) return null

    val hue = Math.toDegrees(atan2(sinSum[best] / bestWeight, cosSum[best] / bestWeight))
    return Tone(
        hue = hue.toFloat(),
        saturation = (satSum[best] / bestWeight).toFloat(),
        lightness = (lightSum[best] / bestWeight).toFloat(),
    )
}
