package com.example.localplayer.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * Связь «плейлист — трек».
 *
 * Составной первичный ключ делает операцию идемпотентной: повторный импорт того же
 * трека в тот же плейлист обновляет строку, а не создаёт дубль. Удаление элемента —
 * тоже надгробие (`isDeleted`), иначе после синхронизации он «воскреснет».
 */
@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"],
    indices = [Index("trackId")],
)
data class PlaylistTrackCrossRef(
    val playlistId: String,
    val trackId: String,
    val position: Int,
    val addedAt: Long,
    val isDeleted: Boolean = false,
)
