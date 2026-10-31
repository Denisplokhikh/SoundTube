package com.example.localplayer.domain.model

/** Состояние одной загрузки, которое отдаётся в UI. */
sealed interface DownloadState {

    data object Idle : DownloadState

    /** Получаем метаданные по ссылке (--dump-json). */
    data class FetchingMetadata(val url: String) : DownloadState

    data class Downloading(
        val url: String,
        val progress: Float,
        val etaSeconds: Long?,
        val status: String,
        val downloadedBytes: Long = 0L,
        val totalBytes: Long = 0L,
    ) : DownloadState

    data class Completed(val url: String, val trackId: String?) : DownloadState

    data class Failed(val url: String, val message: String) : DownloadState
}
