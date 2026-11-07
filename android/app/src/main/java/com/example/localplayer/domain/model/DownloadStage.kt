package com.example.localplayer.domain.model

import androidx.annotation.StringRes
import com.example.localplayer.R

/**
 * Стадия загрузки — единственное, что о загрузке показываем человеку.
 *
 * yt-dlp в каждую строку вывода пишет служебное: имя экстрактора, «Downloading webpage»,
 * скорость и «ETA 03:12». В списке загрузок это выглядит как шум, поэтому строка
 * разбирается в одну короткую стадию, а стадия переводится в подпись.
 */
enum class DownloadStage(@StringRes val labelRes: Int) {

    /** Ссылка разбирается: страница, форматы, слияние потоков. */
    PREPARING(R.string.status_preparing),

    /** Идёт сама загрузка аудио. */
    DOWNLOADING(R.string.status_downloading),

    /** Файл скачан, идёт конвертация и вшивание тегов с обложкой. */
    PROCESSING(R.string.status_processing),
    ;

    companion object {

        /**
         * Теги постобработки: yt-dlp печатает их в квадратных скобках перед сообщением.
         *
         * Имена классов у разных версий yt-dlp разные (`ExtractAudio` и
         * `FFmpegExtractAudio`), поэтому список расширен, а лишние слова отсекаются
         * по вхождению «ffmpeg».
         */
        private val POST_PROCESSORS = setOf(
            "merger",
            "extractaudio",
            "embedthumbnail",
            "metadata",
            "convertor",
            "thumbnailsconvertor",
            "fixupm3u8",
            "modifychapters",
            "sponsorblock",
            "xattrmetadata",
        )

        /**
         * Стадия по строке вывода yt-dlp.
         *
         * @param line строка stdout yt-dlp («[ExtractAudio] Destination: …»).
         * @param progress доля скачанного. Пока yt-dlp не отдал ни одной строки с
         *   процентами, библиотека отдаёт -1 — значит, файл ещё не качается.
         */
        fun of(line: String?, progress: Float): DownloadStage {
            val tag = line
                ?.substringBefore(']')
                ?.removePrefix("[")
                ?.trim()
                ?.lowercase()
                .orEmpty()

            if (tag.isNotEmpty() && (tag in POST_PROCESSORS || tag.contains("ffmpeg"))) {
                return PROCESSING
            }
            if (progress >= 0f) return DOWNLOADING
            if (line != null && line.contains("% of ")) return DOWNLOADING
            return PREPARING
        }
    }
}
