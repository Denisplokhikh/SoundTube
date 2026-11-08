package com.example.localplayer.domain.lyrics

/** Строка текста песни с таймкодом (миллисекунды от начала трека). */
data class LyricLine(
    val timeMs: Long,
    val text: String,
)

/**
 * Результат поиска текста песни.
 *
 * [found] — текст найден и его можно показывать. [instrumental] — в записи нет вокала,
 * текста не будет вовсе. [synced] — у строк есть таймкоды ([lines]), иначе показывается
 * просто [plain]. [source] — откуда текст взят (константы [SOURCE_LRC] и т.п.).
 */
data class Lyrics(
    val found: Boolean,
    val synced: Boolean = false,
    val instrumental: Boolean = false,
    val source: String? = null,
    val plain: String? = null,
    val lines: List<LyricLine> = emptyList(),
) {
    companion object {
        /** Соседний `.lrc` файл рядом с аудио. */
        const val SOURCE_LRC = "lrc"

        /** Найден в LRCLIB. */
        const val SOURCE_LRCLIB = "lrclib"

        /** Запасной источник простого текста. */
        const val SOURCE_OVH = "lyrics.ovh"
    }
}
