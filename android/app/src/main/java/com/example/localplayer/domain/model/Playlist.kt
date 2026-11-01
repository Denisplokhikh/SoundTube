package com.example.localplayer.domain.model

/** Плейлист. Системные плейлисты имеют фиксированные id (`sys:youtube`, `sys:soundcloud`). */
data class Playlist(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
    val trackCount: Int = 0,
) {
    val isSystem: Boolean get() = id.startsWith(SYSTEM_PREFIX)

    companion object {
        const val SYSTEM_PREFIX = "sys:"
    }
}

/** Элемент плейлиста: трек и его позиция в списке. */
data class PlaylistEntry(
    val track: Track,
    val position: Int,
)
