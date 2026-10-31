package com.example.localplayer.domain.model

/** Площадка-источник трека. Значение `key` хранится в базе и попадает в JSON-экспорт. */
enum class SourcePlatform(val key: String) {
    YOUTUBE("youtube"),
    SOUNDCLOUD("soundcloud"),
    LOCAL("local"),
    ;

    companion object {
        fun fromKey(key: String?): SourcePlatform =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: LOCAL
    }
}
