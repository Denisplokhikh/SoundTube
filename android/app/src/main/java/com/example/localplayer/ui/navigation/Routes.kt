package com.example.localplayer.ui.navigation

import android.net.Uri

/** Маршруты приложения. Вкладки нижней навигации + экраны поверх них. */
object Routes {

    const val TRACKS = "tracks"
    const val PLAYLISTS = "playlists"
    const val DOWNLOADS = "downloads"
    const val SETTINGS = "settings"

    const val NOW_PLAYING = "now_playing"

    const val ARG_PLAYLIST_ID = "playlistId"
    const val PLAYLIST_DETAIL = "playlist/{$ARG_PLAYLIST_ID}"

    /** Вкладки в порядке нижней навигации (как три пункта в десктопном SoundTube). */
    val tabs: List<String> = listOf(TRACKS, DOWNLOADS, PLAYLISTS)

    /** Все экраны, которые можно открыть напрямую (debug-intent). */
    val destinations: List<String> = tabs + SETTINGS

    /**
     * Ссылка на плейлист. id системных плейлистов содержат двоеточие
     * (`sys:youtube`), поэтому его обязательно кодируем.
     */
    fun playlistDetail(playlistId: String): String =
        "playlist/${Uri.encode(playlistId)}"
}
