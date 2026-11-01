package com.example.localplayer.domain.util

import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Детерминированные идентификаторы треков.
 *
 * Один и тот же источник всегда даёт один и тот же id на любом устройстве,
 * поэтому библиотеку можно сверять с десктопным SoundTube без общего бэкенда.
 *
 * Правила:
 *  - YouTube:   `yt:<video_id>`
 *  - SoundCloud: `sc:<artist>/<track-slug>` (нижний регистр)
 *  - всё остальное: `sha256:<hex от канонического URL>`
 *
 * Канонический URL: нижний регистр схемы и хоста, без `www.`, без хвостового `/`,
 * без трекинговых параметров (`utm_*`, `si`, `feature`).
 */
object TrackIds {

    const val YOUTUBE_PREFIX = "yt:"
    const val SOUNDCLOUD_PREFIX = "sc:"
    const val HASH_PREFIX = "sha256:"

    private val VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")
    private val YOUTUBE_PATH_PREFIXES = listOf("shorts/", "embed/", "live/", "v/", "e/")
    private val DROPPED_QUERY_KEYS = setOf("si", "feature")
    private val TRACKING_QUERY_PREFIX = "utm_"

    fun youtube(videoId: String): String = YOUTUBE_PREFIX + videoId

    fun soundcloud(slug: String): String = SOUNDCLOUD_PREFIX + slug.trim('/').lowercase()

    fun fallback(canonicalUrl: String): String = HASH_PREFIX + sha256Hex(canonicalUrl)

    /** Строит id по любой пользовательской ссылке. Не бросает исключений. */
    fun forUrl(rawUrl: String): String {
        val uri = runCatching { URI(rawUrl.trim()) }.getOrNull()
        val host = uri?.host?.lowercase()?.removePrefix("www.")
        if (uri == null || host == null) return fallback(rawUrl.trim())

        return when {
            host == "youtu.be" || host.endsWith("youtube.com") || host.endsWith("youtube-nocookie.com") ->
                youTubeVideoId(uri)?.let(::youtube) ?: fallback(canonicalize(rawUrl))

            host.endsWith("soundcloud.com") ->
                soundCloudSlug(uri)?.let(::soundcloud) ?: fallback(canonicalize(rawUrl))

            else -> fallback(canonicalize(rawUrl))
        }
    }

    /** Грубая проверка «похоже на ссылку», чтобы не отправлять мусор в yt-dlp. */
    fun looksLikeUrl(rawUrl: String): Boolean {
        val value = rawUrl.trim()
        return value.startsWith("http://") || value.startsWith("https://")
    }

    fun canonicalize(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return trimmed
        val scheme = (uri.scheme ?: "https").lowercase()
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return trimmed
        val defaultPort = (scheme == "http" && uri.port == 80) || (scheme == "https" && uri.port == 443)
        val port = if (uri.port > 0 && !defaultPort) ":${uri.port}" else ""
        val path = uri.path.orEmpty().trimEnd('/').ifEmpty { "/" }
        val query = uri.rawQuery
            ?.split('&')
            ?.filterNot { part ->
                val key = part.substringBefore('=')
                key in DROPPED_QUERY_KEYS || key.startsWith(TRACKING_QUERY_PREFIX)
            }
            ?.joinToString("&")
            ?.takeIf { it.isNotEmpty() }

        return buildString {
            append(scheme).append("://").append(host).append(port).append(path)
            if (query != null) append('?').append(query)
        }
    }

    fun sha256Hex(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    private fun youTubeVideoId(uri: URI): String? {
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null
        if (host == "youtu.be") {
            return uri.path.orEmpty().trim('/').takeIf { VIDEO_ID.matches(it) }
        }

        queryParam(uri.rawQuery, "v")?.let { if (VIDEO_ID.matches(it)) return it }

        val path = uri.path.orEmpty().trim('/')
        YOUTUBE_PATH_PREFIXES.forEach { prefix ->
            if (path.startsWith(prefix)) {
                val candidate = path.removePrefix(prefix).substringBefore('/')
                if (VIDEO_ID.matches(candidate)) return candidate
            }
        }
        return null
    }

    /**
     * Slug берём из пути: `soundcloud.com/artist/track` → `artist/track`.
     * Короткие ссылки `on.soundcloud.com/xxxx` офлайн не разворачиваются, для них
     * остаётся хэш; шаг загрузки позже заменит id на канонический permalink.
     */
    private fun soundCloudSlug(uri: URI): String? {
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null
        if (host == "on.soundcloud.com" || host == "snd.sc") return null

        val segments = uri.path.orEmpty()
            .split('/')
            .filter { it.isNotBlank() }
        return if (segments.size >= 2) segments.joinToString("/") else null
    }

    private fun queryParam(rawQuery: String?, key: String): String? {
        if (rawQuery.isNullOrEmpty()) return null
        return rawQuery.split('&')
            .firstOrNull { it.substringBefore('=') == key }
            ?.substringAfter('=', missingDelimiterValue = "")
            ?.takeIf { it.isNotEmpty() }
    }
}
