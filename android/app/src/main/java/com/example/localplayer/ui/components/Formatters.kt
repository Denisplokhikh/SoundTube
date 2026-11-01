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

/** Сколько осталось: `~2:10`; null, если ETA неизвестна. */
fun Long?.formatEta(): String? {
    val seconds = this ?: return null
    if (seconds <= 0L) return null
    val minutesPart = seconds / 60L
    val secondsPart = seconds % 60L
    return if (minutesPart > 0L) {
        String.format(Locale.ROOT, "~%d:%02d", minutesPart, secondsPart)
    } else {
        "~$secondsPart с"
    }
}

/** Размер библиотеки: «12,4 МБ». */
@Composable
fun Long.formatFileSize(): String =
    Formatter.formatShortFileSize(LocalContext.current, this)

/** Склонение: 1 трек, 2 трека, 5 треков. */
fun Int.tracksWord(): String {
    val mod100 = this % 100
    val mod10 = this % 10
    return when {
        mod100 in 11..14 -> "треков"
        mod10 == 1 -> "трек"
        mod10 in 2..4 -> "трека"
        else -> "треков"
    }
}

/** Склонение: 1 плейлист, 2 плейлиста, 5 плейлистов. */
fun Int.playlistsWord(): String {
    val mod100 = this % 100
    val mod10 = this % 10
    return when {
        mod100 in 11..14 -> "плейлистов"
        mod10 == 1 -> "плейлист"
        mod10 in 2..4 -> "плейлиста"
        else -> "плейлистов"
    }
}
