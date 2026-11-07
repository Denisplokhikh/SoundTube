package com.example.localplayer.data.sync.lan

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Адрес сервера SoundTube на ПК и cookie его PIN-сессии.
 *
 * Хранится рядом с языком и темой в обычных SharedPreferences:
 * cookie выдаёт `/api/auth/login` (срок жизни 180 дней), поэтому PIN
 * приходится вводить только однажды или после смены на ПК.
 */
@Singleton
class LanSyncPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _serverUrl = MutableStateFlow(prefs.getString(KEY_SERVER_URL, "").orEmpty())
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    fun setServerUrl(url: String) {
        prefs.edit().putString(KEY_SERVER_URL, url).apply()
        _serverUrl.value = url
    }

    /** Cookie конкретного ПК: ключ — `host:port`, чтобы ПК было несколько. */
    fun cookie(hostPort: String): String? =
        prefs.getString(KEY_COOKIE_PREFIX + hostPort, null)?.takeIf { it.isNotBlank() }

    fun setCookie(hostPort: String, value: String?) {
        prefs.edit()
            .apply {
                if (value.isNullOrBlank()) remove(KEY_COOKIE_PREFIX + hostPort)
                else putString(KEY_COOKIE_PREFIX + hostPort, value)
            }
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "soundtube.settings"
        const val KEY_SERVER_URL = "lan_server_url"
        const val KEY_COOKIE_PREFIX = "lan_cookie:"
    }
}
