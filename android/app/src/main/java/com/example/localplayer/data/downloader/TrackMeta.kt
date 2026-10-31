package com.example.localplayer.data.downloader

/**
 * Метаданные трека в том виде, в каком они нужны приложению.
 *
 * Собственная модель вместо `VideoInfo` из библиотеки: во-первых, `VideoInfo` не умеет
 * разбирать плейлист (на каждую строку JSON — свой объект), во-вторых, так мы не тянем
 * в код поля, которые не используем.
 */
data class TrackMeta(
    /** id у площадки: для YouTube это id видео. */
    val sourceId: String?,
    val title: String?,
    val artist: String?,
    val durationMs: Long,
    /** Каноническая ссылка на трек (у SoundCloud — короткий permalink, не on.soundcloud.com). */
    val webpageUrl: String?,
    val thumbnail: String?,
)
