package com.example.localplayer.domain.lyrics

/**
 * Разбор `.lrc`-текста в строки с таймкодами и чистый текст.
 *
 * Порт `app/core/lyrics.py::parse_lrc` с десктопа, чтобы формат и поведение
 * (тег `[offset:...]`, метатеги, несколько таймкодов на строку) совпадали.
 */
object LrcParser {

    /** Строки с таймкодами (отсортированы) и текст без таймкодов. */
    data class Parsed(
        val lines: List<LyricLine>,
        val plain: String,
    )

    private val TIME_TAG = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\]""")
    private val OFFSET_TAG = Regex("""^\[offset:\s*([+-]?\d+)\s*\]$""", RegexOption.IGNORE_CASE)
    private val META_TAG = Regex(
        """^\[(?:ar|ti|al|au|by|length|re|ve|id|tool|offset):[^\]]*\]$""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(raw: String): Parsed {
        var offsetMs = 0L
        val lines = mutableListOf<LyricLine>()
        val plainLines = mutableListOf<String>()

        for (rawLine in raw.lineSequence()) {
            val stripped = rawLine.trim()
            if (stripped.isEmpty()) continue

            val offsetMatch = OFFSET_TAG.matchEntire(stripped)
            if (offsetMatch != null) {
                offsetMs = offsetMatch.groupValues[1].toLongOrNull() ?: 0L
                continue
            }

            val matches = TIME_TAG.findAll(stripped).toList()
            if (matches.isEmpty()) {
                if (META_TAG.matches(stripped)) continue
                plainLines.add(stripped)
                continue
            }

            val text = TIME_TAG.replace(stripped, "").trim()
            if (text.isNotEmpty()) plainLines.add(text)

            for (match in matches) {
                val minutes = match.groupValues[1].toLongOrNull() ?: 0L
                val seconds = match.groupValues[2].toLongOrNull() ?: 0L
                val fractionRaw = match.groupValues[3].ifEmpty { "0" }
                // «[01:23.4]» — доля секунды без ведущих нулей: 4 -> 400 мс.
                val fractionMs = (fractionRaw.toLongOrNull() ?: 0L) * pow10(3 - fractionRaw.length)
                val timestampMs = (minutes * 60_000L + seconds * 1_000L + fractionMs - offsetMs)
                    .coerceAtLeast(0L)
                lines.add(LyricLine(timeMs = timestampMs, text = text))
            }
        }

        val sorted = lines.sortedBy { it.timeMs }
        val plain = if (sorted.isNotEmpty() && plainLines.isEmpty()) {
            sorted.map { it.text }.filter { it.isNotEmpty() }
        } else {
            plainLines
        }
        return Parsed(lines = sorted, plain = plain.joinToString("\n").trim())
    }

    private fun pow10(exponent: Int): Long = when {
        exponent <= 0 -> 1L
        exponent == 1 -> 10L
        exponent == 2 -> 100L
        else -> 1_000L
    }
}
