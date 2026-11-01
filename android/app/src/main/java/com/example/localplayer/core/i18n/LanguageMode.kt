package com.example.localplayer.core.i18n

import androidx.annotation.StringRes
import com.example.localplayer.R

/**
 * Язык интерфейса.
 *
 * По умолчанию английский: приложение англоязычное, русский включается вручную
 * в настройках. [SYSTEM] отдаёт выбор системе, но сам по себе не является
 * значением по умолчанию.
 */
enum class LanguageMode(val tag: String?) {
    SYSTEM(null),
    ENGLISH("en"),
    RUSSIAN("ru"),
    ;

    @StringRes
    fun labelRes(): Int = when (this) {
        SYSTEM -> R.string.language_system
        ENGLISH -> R.string.language_english
        RUSSIAN -> R.string.language_russian
    }

    companion object {
        /** Значение из SharedPreferences; неизвестное или отсутствующее — английский. */
        fun fromStored(value: String?): LanguageMode =
            entries.firstOrNull { it.name == value } ?: ENGLISH
    }
}
