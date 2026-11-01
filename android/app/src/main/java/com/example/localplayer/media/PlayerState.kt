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
 * Поля меняются только по событиям [androidx.media3.common.Player], поэтому UI не зависит
 * от того, жив ли сервис прямо сейчас: до подключения контроллера состояние просто пустое.
 *
 * Позиции здесь нет намеренно: она тикает по таймеру, и если держать её в этом снимке,
 * каждые полсекунды перерисовывались бы все экраны со списками треков. Позицию отдаёт
 * отдельный поток `PlayerController.positionMs`.
 */
data class PlayerState(
    val isConnected: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentTrackId: String? = null,
    val title: String = "",
    val artist: String = "",
    val artworkUri: String? = null,
    val durationMs: Long = 0L,
    val queue: List<QueueItem> = emptyList(),
    val currentIndex: Int = -1,
    val shuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
) {
    val hasNext: Boolean
        get() = queue.isNotEmpty() &&
            (shuffle || repeatMode == RepeatMode.ALL || currentIndex < queue.lastIndex)

    val hasTrack: Boolean get() = currentTrackId != null
}
