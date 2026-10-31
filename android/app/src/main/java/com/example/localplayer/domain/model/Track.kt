package com.example.localplayer.domain.model

/**
 * Доменная модель трека. Не зависит от Room.
 *
 * [id] детерминирован (см. [com.example.localplayer.domain.util.TrackIds]) и одинаков
 * на всех устройствах, поэтому по нему можно сверять библиотеки при синхронизации.
 */
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val sourceUrl: String,
    val sourcePlatform: SourcePlatform,
    val localFilePath: String? = null,
    val thumbnailPath: String? = null,
    val fileHash: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val isDeleted: Boolean = false,
) {
    val isDownloaded: Boolean get() = localFilePath != null

    val durationSeconds: Long get() = durationMs / 1000L
}
