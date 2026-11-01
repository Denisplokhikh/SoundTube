package com.example.localplayer.domain.util

import com.example.localplayer.domain.model.SourcePlatform

/**
 * Системные плейлисты площадок. Id фиксированы, поэтому треки, скачанные на телефоне
 * и на десктопе, попадают в один и тот же плейлист после слияния JSON.
 */
object SystemPlaylists {

    const val YOUTUBE_ID = "sys:youtube"
    const val SOUNDCLOUD_ID = "sys:soundcloud"

    const val YOUTUBE_NAME = "YouTube"
    const val SOUNDCLOUD_NAME = "SoundCloud"

    val all: List<Pair<String, String>> = listOf(
        YOUTUBE_ID to YOUTUBE_NAME,
        SOUNDCLOUD_ID to SOUNDCLOUD_NAME,
    )

    fun idFor(platform: SourcePlatform): String? = when (platform) {
        SourcePlatform.YOUTUBE -> YOUTUBE_ID
        SourcePlatform.SOUNDCLOUD -> SOUNDCLOUD_ID
        SourcePlatform.LOCAL -> null
    }

    fun isSystem(playlistId: String): Boolean = all.any { it.first == playlistId }
}
