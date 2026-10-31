package com.example.localplayer.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Плейлист. Пользовательские получают UUID, системные — фиксированные
 * `sys:youtube` / `sys:soundcloud` (см. SystemPlaylists).
 */
@Entity(
    tableName = "playlists",
    indices = [Index("updatedAt")],
)
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
)
