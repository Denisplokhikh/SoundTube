package com.example.localplayer.data.downloader

import java.io.File

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
) {

    /**
     * Название для списка до скачивания.
     *
     * В плоском списке SoundCloud названий нет — площадка отдаёт только ссылки.
     * Тогда показываем последний сегмент permalink: `.../deadcastlefl/whitedistrictflow`
     * → `whitedistrictflow`. После загрузки настоящее название придёт из метаданных файла.
     */
    val displayTitle: String
        get() = title?.takeIf { it.isNotBlank() }
            ?: apiTrackId()?.let { "Трек $it" }
            ?: webpageUrl
                ?.substringAfterLast('/')
                ?.substringBefore('?')
                ?.takeIf { it.isNotBlank() }
                ?.replace('-', ' ')
                ?.replace('_', ' ')
            ?: "Без названия"

    /** Исполнитель до скачивания: первый сегмент permalink (`soundcloud.com/<artist>/<track>`). */
    val displayArtist: String
        get() = artist?.removeSuffix(" - Topic")?.takeIf { it.isNotBlank() }
            ?: if (apiTrackId() != null) {
                "SoundCloud"
            } else {
                webpageUrl
                    ?.takeIf { it.contains("soundcloud.com") }
                    ?.substringAfter("://")
                    ?.substringAfter('/')
                    ?.substringBefore('/')
                    ?.takeIf { it.isNotBlank() }
                    ?: "Неизвестный исполнитель"
            }

    /**
     * Служебная ссылка `api-v2.soundcloud.com/tracks/<id>` вместо нормального permalink.
     *
     * Так SoundCloud отдаёт часть сетов в плоском списке: ни исполнителя, ни названия
     * там нет, только номер. Показываем «Трек 12345», а не голое число, — настоящее
     * название подтянется при скачивании.
     */
    private fun apiTrackId(): String? =
        API_TRACK.find(webpageUrl.orEmpty())?.groupValues?.get(1)

    private companion object {
        val API_TRACK = Regex("api-v2\\.soundcloud\\.com/tracks/(\\d+)")
    }
}

/** Что вернул yt-dlp после скачивания: файл и (если получилось) его метаданные. */
data class DownloadResult(
    val file: File,
    val meta: TrackMeta?,
)
