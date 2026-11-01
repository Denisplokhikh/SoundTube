package com.example.localplayer.domain.util

import android.content.Context
import androidx.annotation.StringRes
import com.example.localplayer.R

/**
 * Перевод ошибок yt-dlp на человеческий язык.
 *
 * yt-dlp отдаёт в исключении весь stderr: десяток строк `WARNING`, трейсбек на питоне
 * и одну строку `ERROR:` с настоящей причиной. Показывать это в списке загрузок
 * бессмысленно, поэтому причину вытаскиваем и переводим.
 *
 * Текст причины зависит от языка интерфейса, поэтому [humanize] принимает контекст,
 * а чистая классификация ([classify]) остаётся тестируемой без Android.
 */
object DownloadErrors {

    /** Причина, которую имеет смысл показать человеку. */
    enum class Cause(@StringRes val messageRes: Int) {
        NETWORK(R.string.error_network),
        AUTH(R.string.error_auth),
        UNAVAILABLE(R.string.error_unavailable),
        FORBIDDEN(R.string.error_forbidden),
        RATE_LIMITED(R.string.error_rate_limited),
        UNSUPPORTED(R.string.error_unsupported_link),
        LIVE(R.string.error_live),
        NO_AUDIO(R.string.error_no_audio),
        PROCESSING(R.string.error_processing),
    }

    /** Частые причины: подстрока в тексте ошибки → что сказать пользователю. */
    private val KNOWN: List<Pair<List<String>, Cause>> = listOf(
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
        ) to Cause.NETWORK,

        listOf(
            "private video",
            "this video is private",
            "login required",
            "sign in to confirm",
            "cookies",
            "requires authentication",
            "http error 401",
        ) to Cause.AUTH,

        listOf(
            "video unavailable",
            "is not available",
            "video is not available",
            "this video is not available",
            "not available in your country",
            "http error 404",
        ) to Cause.UNAVAILABLE,

        listOf("http error 403") to Cause.FORBIDDEN,

        listOf("http error 429", "too many requests") to Cause.RATE_LIMITED,

        listOf("unsupported url", "is not a valid url", "no suitable extractor") to
            Cause.UNSUPPORTED,

        listOf("is live", "live stream", "premieres in") to Cause.LIVE,

        listOf("did not get any data blocks", "downloaded file is empty") to Cause.NO_AUDIO,

        listOf("ffmpeg", "postprocessing") to Cause.PROCESSING,
    )

    /** Известная причина или null, если ошибку не узнали. */
    fun classify(message: String?): Cause? {
        val lower = message?.trim()?.lowercase().orEmpty()
        if (lower.isEmpty()) return null
        return KNOWN.firstOrNull { (needles, _) -> needles.any(lower::contains) }?.second
    }

    /** Последняя строка без WARNING обычно и есть `ERROR: ...`; не зависит от языка. */
    fun detail(message: String?): String {
        val text = message?.trim().orEmpty()
        if (text.isEmpty()) return ""

        val last = text.lineSequence()
            .map { it.trim() }
            .lastOrNull { it.isNotEmpty() && !it.startsWith("WARNING", ignoreCase = true) }
            ?: text

        return last
            .removePrefix("ERROR:")
            .removePrefix("ERROR: ")
            .trim()
            .take(200)
    }

    /** Причина, которую имеет смысл показать человеку. Никогда не бросает и не пустая. */
    fun humanize(context: Context, message: String?): String =
        classify(message)?.let { context.getString(it.messageRes) }
            ?: detail(message).ifEmpty { context.getString(R.string.error_download_failed) }

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
