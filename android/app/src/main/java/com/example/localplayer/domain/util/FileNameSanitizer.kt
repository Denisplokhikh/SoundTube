package com.example.localplayer.domain.util

/**
 * Приводит имя файла/папки к безопасному виду: убирает символы, недопустимые на
 * Windows/macOS/Linux (файлы потом уедут на десктоп через экспорт), режет длину
 * и не даёт получить пустую строку или имя из точек.
 */
object FileNameSanitizer {

    private val ILLEGAL = Regex("""[\\/:*?"<>|\u0000-\u001F]""")
    private val WHITESPACE = Regex("""\s+""")
    private val RESERVED_WINDOWS_NAMES = setOf(
        "CON", "PRN", "AUX", "NUL",
        "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9",
    )

    fun sanitize(raw: String, maxLength: Int = 80, fallback: String = "Без названия"): String {
        val cleaned = raw
            .replace(ILLEGAL, " ")
            .replace(WHITESPACE, " ")
            .trim()
            .trim('.', ' ')
            .take(maxLength)
            .trim()
            .trimEnd('.', ' ')

        if (cleaned.isEmpty()) return fallback

        val upper = cleaned.uppercase()
        return if (upper in RESERVED_WINDOWS_NAMES) "${cleaned}_" else cleaned
    }
}
