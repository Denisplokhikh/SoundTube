package com.example.localplayer.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Строки вывода yt-dlp не должны попадать в интерфейс: из них берётся только стадия.
 */
class DownloadStageTest {

    @Test
    fun preparingBeforeAnyPercent() {
        assertEquals(
            DownloadStage.PREPARING,
            DownloadStage.of("[youtube] dQw4w9WgXcQ: Downloading webpage", -1f),
        )
        assertEquals(
            DownloadStage.PREPARING,
            DownloadStage.of("[info] Downloading 1 format(s): 251", -1f),
        )
        assertEquals(
            DownloadStage.PREPARING,
            DownloadStage.of("[download] Destination: /tmp/track.m4a", -1f),
        )
    }

    @Test
    fun downloadingWhenPercentIsKnown() {
        assertEquals(
            DownloadStage.DOWNLOADING,
            DownloadStage.of("[download]   0.0% of 3.60MiB at 10.00KiB/s ETA 06:01", 0f),
        )
        assertEquals(
            DownloadStage.DOWNLOADING,
            DownloadStage.of("[download]  42.5% of 3.60MiB at 10.00KiB/s ETA 03:21", 42.5f),
        )
        // Процент в строке есть, а библиотека его не распознала (нет ETA).
        assertEquals(
            DownloadStage.DOWNLOADING,
            DownloadStage.of("[download] 100.0% of 3.60MiB in 00:01", -1f),
        )
    }

    @Test
    fun processingOnPostProcessors() {
        assertEquals(
            DownloadStage.PROCESSING,
            DownloadStage.of("[Merger] Merging formats into \"/tmp/track.m4a\"", 99f),
        )
        assertEquals(
            DownloadStage.PROCESSING,
            DownloadStage.of("[ExtractAudio] Destination: /tmp/track.m4a", 99f),
        )
        assertEquals(
            DownloadStage.PROCESSING,
            DownloadStage.of("[FFmpegExtractAudio] Destination: /tmp/track.m4a", 99f),
        )
        assertEquals(
            DownloadStage.PROCESSING,
            DownloadStage.of("[EmbedThumbnail] Adding thumbnail to \"/tmp/track.m4a\"", 99f),
        )
        assertEquals(
            DownloadStage.PROCESSING,
            DownloadStage.of("[Metadata] Adding metadata to \"/tmp/track.m4a\"", 99f),
        )
    }

    @Test
    fun unknownLineIsPreparing() {
        assertEquals(DownloadStage.PREPARING, DownloadStage.of(null, -1f))
        assertEquals(DownloadStage.PREPARING, DownloadStage.of("", -1f))
    }
}
