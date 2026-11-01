package com.example.localplayer.data.downloader

import android.content.Context
import com.example.localplayer.core.AppLog
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Пакетный дозапрос названий треков SoundCloud.
 *
 * Плоский список (`--flat-playlist`) отдаёт станции и подборки «прозрачными» записями:
 * в них есть только числовой id, без названия и исполнителя. yt-dlp узнаёт их при
 * скачивании, но до тех пор в очереди висят заглушки вида `Track 12345` или slug ссылки.
 *
 * Как и десктопная версия (`app/platforms/soundcloud.py`), спрашиваем api-v2 пачками
 * по 50 id. Публичного API у SoundCloud нет: нужен `client_id` из JS-бандла страницы,
 * поэтому его добываем один раз и кэшируем в SharedPreferences.
 *
 * Любая ошибка здесь не критична: очередь уже создана, а настоящие метаданные всё равно
 * придут из `<id>.info.json` после скачивания — это и есть старый фоллбэк.
 */
@Singleton
class SoundcloudMetadata @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val json = Json { ignoreUnknownKeys = true }

    private val http = HttpClient(OkHttp) {
        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
            socketTimeoutMillis = REQUEST_TIMEOUT_MS
        }
    }

    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** `client_id` меняется после ротации, поэтому кэш живёт до первой 401/403. */
    @Volatile
    private var cachedClientId: String? = null

    /** Два параллельных дозапроса не должны одновременно ходить за `client_id`. */
    private val clientIdMutex = Mutex()

    /**
     * Метаданные для переданных id, сгруппированные по id.
     *
     * Никогда не бросает: сломанный `client_id`, таймаут или битый JSON просто дают
     * пустой/частичный ответ, а очередь продолжает работать на заглушках.
     */
    suspend fun fetchTracks(ids: Collection<String>): Map<String, SoundcloudTrack> {
        val unique = ids
            .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
            .distinct()
        if (unique.isEmpty()) return emptyMap()

        val resolved = LinkedHashMap<String, SoundcloudTrack>()

        // Общий предел на весь дозапрос: даже если api-v2 «висит», фоновой задаче
        // нечего ждать дольше. Частичный результат при этом сохраняем.
        try {
            withTimeoutOrNull(TOTAL_TIMEOUT_MS) {
                unique.chunked(IDS_PER_REQUEST).forEach { chunk ->
                    try {
                        queryChunk(chunk).forEach { track -> resolved[track.id] = track }
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (error: Exception) {
                        AppLog.w(
                            "SoundCloud API: couldn't read ${chunk.size} track(s), " +
                                "keeping the placeholders",
                            error,
                        )
                    }
                }
            }
        } catch (cancellation: CancellationException) {
            // withTimeoutOrNull сам гасит корутину, но внешняя отмена (закрытие
            // приложения) должна дойти наверх как обычно.
            throw cancellation
        }

        return resolved
    }

    /** Один запрос к api-v2; при протухшем `client_id` повторяем с новым. */
    private suspend fun queryChunk(ids: List<String>): List<SoundcloudTrack> {
        val clientId = clientId() ?: throw IOException("SoundCloud: no client_id")
        return try {
            requestTracks(ids, clientId)
        } catch (unauthorized: Unauthorized) {
            AppLog.i("SoundCloud API: client_id expired, refreshing")
            invalidateClientId()
            val fresh = clientId() ?: throw IOException("SoundCloud: no client_id")
            requestTracks(ids, fresh)
        }
    }

    private suspend fun requestTracks(ids: List<String>, clientId: String): List<SoundcloudTrack> {
        val response = http.get(API_TRACKS) {
            header(HttpHeaders.UserAgent, USER_AGENT)
            header(HttpHeaders.Accept, "application/json")
            parameter("ids", ids.joinToString(","))
            parameter("client_id", clientId)
        }

        if (response.status.value == 401 || response.status.value == 403) throw Unauthorized()
        if (!response.status.isSuccess()) {
            throw IOException("SoundCloud API: HTTP ${response.status.value}")
        }
        return SoundcloudJson.parse(json, response.bodyAsText())
    }

    private suspend fun clientId(): String? {
        cachedClientId?.let { return it }
        preferences.getString(KEY_CLIENT_ID, null)
            ?.takeIf { it.isNotBlank() }
            ?.let {
                cachedClientId = it
                return it
            }

        return clientIdMutex.withLock {
            cachedClientId ?: discoverClientId()?.also { found ->
                cachedClientId = found
                preferences.edit().putString(KEY_CLIENT_ID, found).apply()
            }
        }
    }

    private fun invalidateClientId() {
        cachedClientId = null
        preferences.edit().remove(KEY_CLIENT_ID).apply()
    }

    /**
     * `client_id` лежит в главном JS-бандле страницы SoundCloud. Скрипты перебираем
     * с конца: нужный бандл — последний/предпоследний, так что обычно качается один файл.
     */
    private suspend fun discoverClientId(): String? = withTimeoutOrNull(DISCOVERY_TIMEOUT_MS) {
        val html = getText(HOME_URL)
        CLIENT_ID.find(html)?.groupValues?.get(1)?.let { return@withTimeoutOrNull it }

        val scripts = SCRIPT_SRC.findAll(html)
            .map { it.value }
            .distinct()
            .toList()

        for (script in scripts.asReversed()) {
            val body = try {
                getText(script)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                // Битый/недоступный бандл — пробуем следующий, это не фатально.
                continue
            }
            CLIENT_ID.find(body)?.groupValues?.get(1)?.let { return@withTimeoutOrNull it }
        }
        null
    }

    private suspend fun getText(url: String): String {
        val response = http.get(url) {
            header(HttpHeaders.UserAgent, USER_AGENT)
            header(HttpHeaders.Accept, "*/*")
        }
        if (!response.status.isSuccess()) {
            throw IOException("SoundCloud: HTTP ${response.status.value} for $url")
        }
        return response.bodyAsText()
    }

    /** api-v2 ответил 401/403 — `client_id` больше не годится. */
    private class Unauthorized : IOException()

    private companion object {
        const val HOME_URL = "https://soundcloud.com/"
        const val API_TRACKS = "https://api-v2.soundcloud.com/tracks"

        /** Как в десктопной версии: один запрос на 50 id. */
        const val IDS_PER_REQUEST = 50

        const val CONNECT_TIMEOUT_MS = 5_000L
        const val REQUEST_TIMEOUT_MS = 10_000L
        const val DISCOVERY_TIMEOUT_MS = 15_000L
        const val TOTAL_TIMEOUT_MS = 20_000L

        const val PREFS_NAME = "soundcloud_api"
        const val KEY_CLIENT_ID = "client_id"

        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0 Safari/537.36"

        val SCRIPT_SRC = Regex("""https://a-v2\.sndcdn\.com/assets/[^"'\s]+\.js""")
        val CLIENT_ID = Regex("""client_id\s*[:=]\s*["']([0-9a-zA-Z]{32})["']""")
    }
}

/** Нужные приложению поля ответа api-v2 (`/tracks?ids=...`). */
data class SoundcloudTrack(
    val id: String,
    val title: String,
    val artist: String?,
    val durationMs: Long,
    val thumbnail: String?,
)

/**
 * Разбор ответа api-v2 вынесен отдельно от сети, чтобы его можно было проверить
 * юнит-тестом без Android и HTTP.
 */
internal object SoundcloudJson {

    fun parse(json: Json, body: String): List<SoundcloudTrack> =
        runCatching { json.decodeFromString<List<Entry?>>(body) }
            .getOrElse { emptyList() }
            .mapNotNull { entry -> entry?.toTrack() }

    private fun Entry.toTrack(): SoundcloudTrack? {
        val trackId = id ?: return null
        val trackTitle = clean(title) ?: clean(track) ?: return null
        return SoundcloudTrack(
            id = trackId.toString(),
            title = trackTitle,
            artist = clean(user?.username)
                ?: clean(publisher?.artist)
                ?: clean(artist),
            durationMs = duration ?: 0L,
            thumbnail = clean(artworkUrl),
        )
    }

    /** Аналог `clean_text` из десктопной версии: схлопываем пробелы, пустое — null. */
    private fun clean(value: String?): String? = value
        ?.replace(WHITESPACE, " ")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    private val WHITESPACE = Regex("""\s+""")

    @Serializable
    private data class Entry(
        val id: Long? = null,
        val title: String? = null,
        /** Старые ответы API называют название трека `track`. */
        val track: String? = null,
        val duration: Long? = null,
        @SerialName("artwork_url") val artworkUrl: String? = null,
        val user: User? = null,
        @SerialName("publisher_metadata") val publisher: Publisher? = null,
        val artist: String? = null,
    )

    @Serializable
    private data class User(val username: String? = null)

    @Serializable
    private data class Publisher(val artist: String? = null)
}
