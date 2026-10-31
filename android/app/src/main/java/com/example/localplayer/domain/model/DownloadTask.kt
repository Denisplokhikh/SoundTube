package com.example.localplayer.domain.model

/** Задача загрузки в том виде, в каком её видит UI. */
data class DownloadTask(
    val trackId: String,
    val title: String,
    val artist: String,
    val sourceUrl: String,
    val state: DownloadState,
) {
    val isActive: Boolean
        get() = state is DownloadState.Downloading || state is DownloadState.FetchingMetadata

    val isFinished: Boolean
        get() = state is DownloadState.Completed || state is DownloadState.Failed
}
