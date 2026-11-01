package com.example.localplayer.core.i18n

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Выбранный язык интерфейса.
 *
 * Хранится рядом с темой в обычных SharedPreferences. Чтение из [readStored]
 * статическое: `attachBaseContext` вызывается до создания Hilt-графа.
 */
@Singleton
class LanguagePreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _mode = MutableStateFlow(readStored(context))
    val mode: StateFlow<LanguageMode> = _mode.asStateFlow()

    init {
        // Application уже обёрнут в attachBaseContext; здесь досылаем локаль
        // в ресурсы процесса — их видят уведомления и фоновые задачи.
        AppLocale.applyToApplication(context, _mode.value)
    }

    fun setMode(mode: LanguageMode) {
        prefs.edit().putString(KEY_LANGUAGE, mode.name).apply()
        AppLocale.applyToApplication(context, mode)
        _mode.value = mode
    }

    companion object {
        private const val PREFS_NAME = "soundtube.settings"
        private const val KEY_LANGUAGE = "language_mode"

        fun readStored(context: Context): LanguageMode {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return LanguageMode.fromStored(prefs.getString(KEY_LANGUAGE, null))
        }
    }
}
