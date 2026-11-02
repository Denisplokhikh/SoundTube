package com.example.localplayer.data.sync

import kotlinx.serialization.Serializable

/**
 * Снимок библиотеки, который отдаёт десктоп (`GET /api/sync/export`).
 *
 * Идентификаторов здесь нет намеренно: телефон считает их сам из ссылки через
 * [com.example.localplayer.domain.util.TrackIds], поэтому десктопу не нужно знать
 * про `yt:` / `sc:`. Лишние поля формы игнорируются при разборе.
 */
@Serializable
data class SyncPayload(
    val version: Int = 0,
    val tracks: List<SyncTrack> = emptyList(),
    val playlists: List<SyncPlaylist> = emptyList(),
)

@Serializable
data class SyncTrack(
    val url: String,
    val title: String = "",
    val artist: String = "",
    val platform: String? = null,
)

@Serializable
data class SyncPlaylist(
    val name: String,
    val tracks: List<String> = emptyList(),
)
