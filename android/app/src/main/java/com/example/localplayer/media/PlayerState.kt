package com.example.localplayer.media

import com.example.localplayer.domain.model.RepeatMode

/** Элемент очереди воспроизведения в том виде, в каком его рисует UI. */
data class QueueItem(
    val trackId: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val artworkUri: String?,
)

/**
 * Снимок состояния плеера для Compose.
 *
 * Позиция обновляется по таймеру, остальные поля — по событиям [androidx.media3.common.Player],
 * поэтому UI не зависит от того, жив ли сервис прямо сейчас: до подключения контроллера
 * состояние просто пустое.
 */
data class PlayerState(
    val isConnected: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentTrackId: String? = null,
    val title: String = "",
    val artist: String = "",
    val artworkUri: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val queue: List<QueueItem> = emptyList(),
    val currentIndex: Int = -1,
    val shuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
) {
    val hasNext: Boolean get() = queue.isNotEmpty() && (shuffle || currentIndex < queue.lastIndex)
    val hasPrevious: Boolean get() = queue.isNotEmpty() && positionMs > 3_000L

    val hasTrack: Boolean get() = currentTrackId != null

    /** 0..1 для прогресс-бара; 0, пока длительность неизвестна. */
    val progress: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}
