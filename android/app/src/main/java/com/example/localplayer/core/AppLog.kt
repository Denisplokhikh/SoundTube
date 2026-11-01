package com.example.localplayer.core

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Журнал приложения, который видно прямо в интерфейсе.
 *
 * Логи yt-dlp и загрузчика раньше уходили только в logcat, а на телефоне без компьютера
 * их не посмотреть. Здесь последние записи лежат в памяти и показываются в панели
 * «Журнал»; в logcat они по-прежнему пишутся.
 */
object AppLog {

    enum class Level { INFO, WARN, ERROR }

    data class Entry(
        val time: Long,
        val level: Level,
        val message: String,
    ) {
        /** Время в формате `12:34:56` — так проще сопоставлять с logcat. */
        val timeText: String get() = TIME_FORMAT.format(Date(time))

        val levelText: String
            get() = when (level) {
                Level.INFO -> "I"
                Level.WARN -> "W"
                Level.ERROR -> "E"
            }
    }

    private const val LIMIT = 400
    private const val TAG = "Melodown"

    private val TIME_FORMAT = SimpleDateFormat("HH:mm:ss", Locale.ROOT)

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    fun i(message: String) = add(Level.INFO, message, null)

    fun w(message: String, error: Throwable? = null) = add(Level.WARN, message, error)

    fun e(message: String, error: Throwable? = null) = add(Level.ERROR, message, error)

    fun clear() {
        _entries.value = emptyList()
    }

    /** Текст для кнопки «Копировать»: старые записи сверху. */
    fun dump(): String = _entries.value.joinToString("\n") { entry ->
        "${entry.timeText} ${entry.levelText}: ${entry.message}"
    }

    private fun add(level: Level, message: String, error: Throwable?) {
        val text = message + error?.let { thrown ->
            val cause = thrown.message?.lineSequence()?.firstOrNull { it.isNotBlank() }
            if (cause.isNullOrBlank()) "" else ": $cause"
        }.orEmpty()

        when (level) {
            Level.INFO -> Log.i(TAG, text)
            Level.WARN -> Log.w(TAG, text)
            Level.ERROR -> Log.e(TAG, text)
        }

        val entry = Entry(System.currentTimeMillis(), level, text)
        _entries.update { current ->
            (current + entry).takeLast(LIMIT)
        }
    }
}
