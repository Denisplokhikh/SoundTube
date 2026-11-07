package com.example.localplayer.domain.model

/** Состояние одной загрузки, которое отдаётся в UI. */
sealed interface DownloadState {

    data object Idle : DownloadState

    /** Получаем метаданные по ссылке (--dump-json). */
    data class FetchingMetadata(val url: String) : DownloadState

    /**
     * Идёт загрузка.
     *
     * Показываем только долю и стадию: скорость, ETA и служебные строки yt-dlp
     * человеку ничего не дают (см. [DownloadStage]).
     */
    data class Downloading(
        val url: String,
        val progress: Float,
        val stage: DownloadStage,
    ) : DownloadState

    data class Completed(val url: String, val trackId: String?) : DownloadState

    data class Failed(val url: String, val message: String) : DownloadState

    /**
     * Пользователь снял загрузку.
     *
     * Отдельное состояние, а не [Failed]: это не сбой, и очередь плейлиста при
     * отмене одного трека должна продолжаться.
     */
    data class Cancelled(val url: String) : DownloadState
}
