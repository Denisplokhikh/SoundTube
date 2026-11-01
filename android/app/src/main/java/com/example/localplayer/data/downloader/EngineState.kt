package com.example.localplayer.data.downloader

/** Состояние нативного движка yt-dlp + ffmpeg. */
sealed interface EngineState {

    data object NotInitialized : EngineState

    /** Распаковка python/ffmpeg при первом запуске: занимает несколько секунд. */
    data object Initializing : EngineState

    data class Ready(val version: String?) : EngineState

    data object Updating : EngineState

    data class Failed(val message: String) : EngineState

    val isReady: Boolean get() = this is Ready
}
