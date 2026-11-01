package com.example.localplayer.ui.components

import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/** `3:07`, `1:02:45`. Для неизвестной длительности — прочерк. */
fun Long.formatDuration(): String {
    if (this <= 0L) return "--:--"
    val totalSeconds = this / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}

/**
 * Сколько осталось: `~2:10`; null, если ETA неизвестна.
 *
 * [secondsSuffix] — обозначение секунд («с» или «s»): его даёт ресурс,
 * поэтому сама функция остаётся чистой и тестируемой.
 */
fun Long?.formatEta(secondsSuffix: String): String? {
    val seconds = this ?: return null
    if (seconds <= 0L) return null
    val minutesPart = seconds / 60L
    val secondsPart = seconds % 60L
    return if (minutesPart > 0L) {
        String.format(Locale.ROOT, "~%d:%02d", minutesPart, secondsPart)
    } else {
        "~$secondsPart $secondsSuffix"
    }
}

/** Размер библиотеки: «12,4 МБ». */
@Composable
fun Long.formatFileSize(): String =
    Formatter.formatShortFileSize(LocalContext.current, this)
