package com.example.localplayer.domain.lyrics

import kotlin.math.abs

/**
 * Подготовка запросов к LRCLIB и отбор кандидатов поиска.
 *
 * Порт `app/core/lyrics.py` с десктопа. Метаданные YouTube Music и SoundCloud часто
 * содержат в теге «артист» имя канала загрузчика, а в названии — «Artist - Song»,
 * поэтому первым идёт вариант, разобранный из названия.
 */
object LyricsQuery {

    /** Кандидат из поиска LRCLIB. */
    data class Candidate(
        val trackName: String?,
        val artistName: String?,
        val durationSec: Double?,
        val syncedLyrics: String?,
        val plainLyrics: String?,
        val instrumental: Boolean,
    ) {
        val hasLyrics: Boolean
            get() = !syncedLyrics.isNullOrBlank() ||
                !plainLyrics.isNullOrBlank() ||
                instrumental
    }

    private val NOISE_TOKEN =
        """(?:official|music|video|audio|lyrics?|lyric|visuali[sz]er|hd|hq|uhd|4k|8k|remaster(?:ed)?|explicit|clean|clip\s*officiel|m/?v|mv|\d{4})"""
    private val NOISE_RE = Regex(
        """\s*[\(\[]\s*$NOISE_TOKEN(?:[\s\-–—/,&+]*$NOISE_TOKEN)*\s*[\)\]]""",
        RegexOption.IGNORE_CASE,
    )
    private val TRAILING_NOISE_RE = Regex(
        """(?:\s*[-–—|/]\s*|\s+)(?:official\s*(?:music\s*)?(?:video|audio)|lyrics?\s*(?:video)?|lyrics?|hd|hq|uhd|4k|8k|remaster(?:ed)?(?:\s*\d{4})?|visuali[sz]er)\s*$""",
        RegexOption.IGNORE_CASE,
    )
    private val ANY_BRACKET_RE = Regex("""\s*[\(\[]\s*[^\)\]]*[\)\]]""")
    private val TRAILING_PROD_RE = Regex(
        """\s*(?:[\(\[]\s*)?\bprod\.?(?:\s+by)?\s+[^\)\]]*[\)\]]?\s*$""",
        RegexOption.IGNORE_CASE,
    )
    private val TRAILING_PLUS_RE = Regex("""\s*\+\s*[^+]+$""")
    private val SEPARATOR_RE = Regex("""\s+[-–—:|]\s+""")
    private val FEAT_RE = Regex(
        """\s*(?:[\(\[]\s*(?:feat|ft)\b\.?\s+[^\)\]]+[\)\]]|\b(?:feat|ft)\b\.?\s+.+$)""",
        RegexOption.IGNORE_CASE,
    )
    private val NORMALIZE_RE = Regex("""[^0-9a-zа-яё]+""")
    private val JUNK_ARTISTS = setOf(
        "unknown artist",
        "неизвестный исполнитель",
        "unknown",
        "va",
        "various artists",
    )

    /** Убирает из строки скобочный и хвостовой мусор площадок. */
    fun stripNoise(value: String): String {
        var text = NOISE_RE.replace(value, "")
        for (i in 0 until 3) {
            val cleaned = TRAILING_NOISE_RE.replace(text, "").trim()
            if (cleaned == text) break
            text = cleaned
        }
        return text.trim(' ', '-', '–', '—', ':', '|')
    }

    /** Делит «Artist - Title» (в т.ч. через –, —, :, |) на две части. */
    fun splitArtistTitle(title: String): Pair<String, String>? {
        val match = SEPARATOR_RE.find(title) ?: return null
        val left = title.substring(0, match.range.first).trim(' ', '-', '–', '—', ':', '|')
        val right = title.substring(match.range.last + 1).trim(' ', '-', '–', '—', ':', '|')
        return if (left.length in 2..70 && right.length > 1) left to right else null
    }

    fun normalize(value: String?): String =
        NORMALIZE_RE.replace((value ?: "").lowercase(), "")

    /**
     * Собирает варианты запроса к LRCLIB по порядку приоритета.
     * Возвращает пары «название — артист» (артист может быть null).
     */
    fun buildQueryVariants(title: String, artist: String?): List<Pair<String, String?>> {
        val cleanTitle = stripNoise(FEAT_RE.replace(stripNoise(title), "").ifEmpty { title })
        val cleanArtist = stripNoise(FEAT_RE.replace(stripNoise(artist ?: ""), "").ifEmpty { artist ?: "" })
            .let { if (it.lowercase() in JUNK_ARTISTS) "" else it }

        val variants = mutableListOf<Pair<String, String?>>()

        fun push(candidateTitle: String, candidateArtist: String?) {
            val value = candidateTitle.trim(' ', '-', '–', '—', ':', '|')
            val who = candidateArtist?.trim(' ', '-', '–', '—', ':', '|')?.ifEmpty { null }
            if (value.length < 2) return
            val pair = value to who
            if (pair !in variants) variants.add(pair)
        }

        val split = splitArtistTitle(cleanTitle)
        if (split != null) {
            val (left, right) = split
            push(right, left)
            // «Song +FEAT» -> «Song»
            val coreRight = TRAILING_PLUS_RE.replace(right, "").trim(' ', '-', '–', '—', ':', '|')
            if (coreRight.isNotEmpty() && coreRight != right) {
                push(coreRight, left)
            }
            push(right, cleanArtist.ifEmpty { null })
        }

        push(cleanTitle, cleanArtist.ifEmpty { null })

        // Вариант без содержимого скобок и хвостов «prod. by ...»:
        // «LAXVE (PROD. NOIRNOVEL)» -> «LAXVE», «10percOFF prod. by s7eepy» -> «10percOFF».
        val core = TRAILING_PROD_RE.replace(ANY_BRACKET_RE.replace(cleanTitle, ""), "")
            .trim(' ', '-', '–', '—', ':', '|')
        if (core.isNotEmpty() && core != cleanTitle) {
            splitArtistTitle(core)?.let { (coreLeft, coreRight) ->
                push(coreRight, coreLeft)
            }
            push(core, cleanArtist.ifEmpty { null })
        }

        // «SMARTCHAIN +ARLEKIN 40 000» -> «SMARTCHAIN»
        val base = core.ifEmpty { cleanTitle }
        val trimmed = TRAILING_PLUS_RE.replace(base, "").trim(' ', '-', '–', '—', ':', '|')
        if (trimmed.isNotEmpty() && trimmed != base) {
            push(trimmed, cleanArtist.ifEmpty { null })
        }

        return variants.take(5)
    }

    /** Возвращает самый вероятный вариант «название + артист» для запроса. */
    fun cleanQueryMetadata(title: String, artist: String?): Pair<String, String> {
        val variants = buildQueryVariants(title, artist)
        if (variants.isEmpty()) return title.trim() to (artist ?: "").trim()
        val (bestTitle, bestArtist) = variants.first()
        return bestTitle to (bestArtist ?: "")
    }

    private fun artistScore(candidateArtist: String?, targetArtist: String?): Int {
        if (targetArtist.isNullOrEmpty()) return 0
        val left = normalize(candidateArtist)
        val right = normalize(targetArtist)
        if (left.isEmpty() || right.isEmpty()) return 0
        return when {
            left == right -> 2
            left in right || right in left -> 1
            else -> 0
        }
    }

    /**
     * Отсекает чужие песни с тем же названием, которые отдаёт нечёткий поиск.
     * Результат принимается, если совпал артист либо в точности совпало название
     * и длина отличается не больше чем на 3 секунды.
     */
    fun isPlausible(
        item: Candidate,
        targetTitle: String?,
        targetArtist: String?,
        targetDurationSec: Double?,
    ): Boolean {
        if (targetArtist.isNullOrEmpty()) return true
        if (artistScore(item.artistName, targetArtist) >= 1) return true
        val duration = item.durationSec ?: return false
        if (targetDurationSec == null) return false
        return abs(duration - targetDurationSec) <= 3.0 &&
            normalize(item.trackName) == normalize(targetTitle)
    }

    /** Выбирает лучшего кандидата: совпадение артиста, близость длительности, наличие текста. */
    fun pickBestCandidate(
        candidates: List<Candidate>,
        targetDurationSec: Double?,
        targetArtist: String? = null,
        targetTitle: String? = null,
        toleranceSec: Double? = 15.0,
    ): Candidate? {
        if (candidates.isEmpty()) return null

        val plausible = candidates.filter {
            isPlausible(it, targetTitle, targetArtist, targetDurationSec)
        }
        if (plausible.isEmpty()) return null

        val pool = if (toleranceSec != null && targetDurationSec != null) {
            val close = plausible.filter {
                val duration = it.durationSec
                duration == null || abs(duration - targetDurationSec) <= toleranceSec
            }
            if (close.isNotEmpty()) close else plausible
        } else {
            plausible
        }

        fun lyricsWeight(item: Candidate): Int =
            (if (!item.syncedLyrics.isNullOrBlank()) 3 else 0) +
                (if (!item.plainLyrics.isNullOrBlank()) 2 else 0) +
                (if (item.instrumental) 1 else 0)

        val best = pool.maxWithOrNull(
            compareBy(
                { artistScore(it.artistName, targetArtist) },
                {
                    val duration = it.durationSec
                    if (targetDurationSec != null && duration != null) {
                        if (abs(duration - targetDurationSec) <= 15.0) 1 else 0
                    } else {
                        1
                    }
                },
                { lyricsWeight(it) },
                {
                    val duration = it.durationSec
                    if (targetDurationSec != null && duration != null) {
                        -abs(duration - targetDurationSec)
                    } else {
                        0.0
                    }
                },
            ),
        ) ?: return null

        return best.takeIf { it.hasLyrics }
    }
}
