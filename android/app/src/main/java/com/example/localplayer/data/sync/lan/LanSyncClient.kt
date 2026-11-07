package com.example.localplayer.data.sync.lan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Ошибка LAN-синхронизации с текстом, готовым к показу пользователю. */
class LanSyncException(message: String) : Exception(message)

/**
 * Транспорт LAN-синхронизации поверх OkHttp.
 *
 * Сервер отдаёт библиотеку и файлы только после входа по PIN: ответ
 * `/api/auth/login` ставит httpOnly-cookie, поэтому клиенту нужен
 * CookieJar поверх [LanSyncPreferences] — сессия переживает перезапуск
 * приложения, повторный PIN спрашивается только после смены на ПК.
 * Ключ cookie — `host:port`, так что рядом могут жить несколько ПК.
 */
@Singleton
class LanSyncClient @Inject constructor(
    private val preferences: LanSyncPreferences,
) {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .cookieJar(PrefsCookieJar(preferences))
        .build()

    private class PrefsCookieJar(
        private val preferences: LanSyncPreferences,
    ) : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            if (cookies.isEmpty()) return
            val line = cookies.joinToString("; ") { "${it.name}=${it.value}" }
            preferences.setCookie(url.hostPort(), line)
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val raw = preferences.cookie(url.hostPort()) ?: return emptyList()
            return raw.split("; ").mapNotNull { Cookie.parse(url, it.trim()) }
        }

        private fun HttpUrl.hostPort(): String = "$host:$port"
    }

    /**
     * Вход по PIN (`POST /api/auth/login`). Cookie попадает в jar
     * автоматически, поэтому последующие запросы авторизованы.
     */
    suspend fun login(baseUrl: String, pin: String): Unit = withContext(Dispatchers.IO) {
        val root = parse(baseUrl)
        val body = buildJsonObject { put("pin", pin) }
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(root.newBuilder().addPathSegments("api/auth/login").build())
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            when {
                response.code == 401 -> throw LanSyncException(PIN_WRONG)
                !response.isSuccessful -> throw LanSyncException(
                    "PC answered ${response.code} at login",
                )
            }
        }
    }

    /** Снимок библиотеки (`GET /api/sync/export`). */
    suspend fun export(baseUrl: String, pin: String?): String = withContext(Dispatchers.IO) {
        val root = parse(baseUrl)
        val request = Request.Builder()
            .url(root.newBuilder().addPathSegments("api/sync/export").build())
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            when {
                response.code == 401 -> {
                    // Кука не подошла или её нет: раз стоило ввести PIN — логинимся и
                    // перечитываем снимок тем же запросом.
                    if (pin.isNullOrBlank()) throw LanSyncException(PIN_REQUIRED)
                    login(baseUrl, pin)
                    fetchText(root)
                }

                !response.isSuccessful -> throw LanSyncException(
                    "PC answered ${response.code} at /sync/export",
                )

                else -> response.body?.string().orEmpty()
            }
        }
    }

    /**
     * Скачивает файл (`api/stream` для трека, `api/artwork` для обложки)
     * в [target]. Оба эндпойнта принимают один и тот же `path` — относительное
     * имя трека в библиотеке ПК.
     */
    suspend fun downloadAudio(baseUrl: String, relativePath: String, target: File) =
        download(baseUrl, "api/stream", relativePath, target)

    suspend fun downloadArtwork(baseUrl: String, relativePath: String, target: File) =
        download(baseUrl, "api/artwork", relativePath, target)

    private suspend fun download(baseUrl: String, endpoint: String, relativePath: String, target: File) =
        withContext(Dispatchers.IO) {
            val root = parse(baseUrl)
            val url = root.newBuilder()
                .addPathSegments(endpoint)
                .addQueryParameter("path", relativePath)
                .build()
            val request = Request.Builder().url(url).get().build()

            client.newCall(request).execute().use { response ->
                if (response.code == 401) throw LanSyncException(PIN_REQUIRED)
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}: $relativePath")
                val body = response.body ?: throw IOException("Empty body: $relativePath")
                target.outputStream().use { out ->
                    body.byteStream().use { input -> input.copyTo(out) }
                }
            }
        }

    private fun fetchText(root: HttpUrl): String {
        val request = Request.Builder()
            .url(root.newBuilder().addPathSegments("api/sync/export").build())
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 401) throw LanSyncException(PIN_WRONG)
            if (!response.isSuccessful) {
                throw LanSyncException("PC answered ${response.code} after login")
            }
            return response.body?.string().orEmpty()
        }
    }

    companion object {
        const val PIN_REQUIRED = "PIN required"
        const val PIN_WRONG = "PC rejected the PIN"
        const val INVALID_URL = "Address like http://192.168.1.5:8765 is required"

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val CONNECT_TIMEOUT_SECONDS = 10L
        private const val READ_TIMEOUT_SECONDS = 60L

        /** Разрешает ввод без схемы («192.168.1.5:8765») и отрезает хвостовой слэш. */
        fun normalizeBaseUrl(raw: String): String {
            val trimmed = raw.trim().removeSuffix("/")
            val withScheme = if (trimmed.contains("://")) trimmed else "http://$trimmed"
            val url = withScheme.toHttpUrlOrNull() ?: throw LanSyncException(INVALID_URL)
            return url.toString().removeSuffix("/")
        }

        private fun parse(baseUrl: String): HttpUrl =
            runCatching { baseUrl.toHttpUrl() }.getOrElse { throw LanSyncException(INVALID_URL) }
    }
}
