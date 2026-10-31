package com.example.localplayer.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Трек в локальной базе.
 *
 * [id] — детерминированный ключ вида `yt:dQw4w9WgXcQ` / `sc:artist/track` / `sha256:...`.
 * Автоинкрементных ключей нет: они не переживают синхронизацию.
 */
@Entity(
    tableName = "tracks",
    indices = [
        Index("sourceUrl"),
        Index("sourcePlatform"),
        Index("updatedAt"),
        Index("isDeleted"),
    ],
)
data class TrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    /** Исходная ссылка: по ней трек можно перекачать на другом устройстве. */
    val sourceUrl: String,
    val sourcePlatform: String,
    /** Null — трек ещё не скачан или файл удалён. */
    val localFilePath: String? = null,
    val thumbnailPath: String? = null,
    /** SHA-256 аудиофайла, считается после загрузки. */
    val fileHash: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    /** Надгробие для синхронизации: физический файл уже удалён. */
    val isDeleted: Boolean = false,
)
