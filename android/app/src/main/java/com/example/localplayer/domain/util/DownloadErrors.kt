package com.example.localplayer.domain.util

/**
 * Перевод ошибок yt-dlp на человеческий язык.
 *
 * yt-dlp отдаёт в исключении весь stderr: десяток строк `WARNING`, трейсбек на питоне
 * и одну строку `ERROR:` с настоящей причиной. Показывать это в списке загрузок
 * бессмысленно, поэтому причину вытаскиваем и переводим.
 */
object DownloadErrors {

    /** Частые причины: подстрока в тексте ошибки → что сказать пользователю. */
    private val KNOWN: List<Pair<List<String>, String>> = listOf(
        listOf(
            "no address associated with hostname",
            "name or service not known",
            "temporary failure in name resolution",
            "unable to resolve host",
            "network is unreachable",
            "connection abort",
            "connection reset",
            "connection refused",
            "failed to establish a new connection",
            "remote end closed connection",
            "connection aborted",
            "transport",
            "eof occurred in violation",
            "incompleteread",
            "timed out",
            "timeout",
            "ssl",
        ) to "нет связи с интернетом — проверь сеть и попробуй ещё раз",

        listOf(
            "private video",
            "this video is private",
            "login required",
            "sign in to confirm",
            "cookies",
            "requires authentication",
            "http error 401",
        ) to "площадка требует вход в аккаунт — ссылка не откроется без него",

        listOf(
            "video unavailable",
            "is not available",
            "video is not available",
            "this video is not available",
            "not available in your country",
            "http error 404",
        ) to "трек недоступен — возможно, он удалён или скрыт",

        listOf("http error 403") to "площадка отклонила запрос (403) — попробуй позже",

        listOf("http error 429", "too many requests") to
            "слишком много запросов подряд — подожди пару минут",

        listOf("unsupported url", "is not a valid url", "no suitable extractor") to
            "эта ссылка не поддерживается",

        listOf("is live", "live stream", "premieres in") to
            "это прямой эфир или премьера — такое не скачивается",

        listOf("did not get any data blocks", "downloaded file is empty") to
            "площадка не отдала аудио — попробуй ещё раз",

        listOf("ffmpeg", "postprocessing") to
            "не удалось обработать аудио — попробуй обновить yt-dlp",
    )

    /** Причина, которую имеет смысл показать человеку. Никогда не бросает и не пустая. */
    fun humanize(message: String?): String {
        val text = message?.trim().orEmpty()
        if (text.isEmpty()) return "не удалось скачать трек"

        val lower = text.lowercase()
        KNOWN.forEach { (needles, human) ->
            if (needles.any(lower::contains)) return human
        }

        // Последняя строка без WARNING обычно и есть `ERROR: ...`.
        val last = text.lineSequence()
            .map { it.trim() }
            .lastOrNull { it.isNotEmpty() && !it.startsWith("WARNING", ignoreCase = true) }
            ?: text

        return last
            .removePrefix("ERROR:")
            .removePrefix("ERROR: ")
            .trim()
            .take(200)
            .ifEmpty { "не удалось скачать трек" }
    }

    /** Стоит ли повторять запрос: сеть отвалилась, но, может, вернётся. */
    fun isNetworkError(message: String?): Boolean {
        val lower = message?.lowercase().orEmpty()
        return NETWORK_MARKERS.any(lower::contains)
    }

    private val NETWORK_MARKERS = listOf(
        "no address associated with hostname",
        "name or service not known",
        "temporary failure in name resolution",
        "unable to resolve host",
        "network is unreachable",
        "connection abort",
        "connection reset",
        "connection refused",
        "failed to establish a new connection",
        "remote end closed connection",
        "connection aborted",
        "transport",
        "eof occurred in violation",
        "incompleteread",
        "timed out",
        "timeout",
    )
}
