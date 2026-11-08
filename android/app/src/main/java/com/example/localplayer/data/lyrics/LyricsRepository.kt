package com.example.localplayer.data.lyrics

import com.example.localplayer.core.AppLog
import com.example.localplayer.domain.lyrics.LrcParser
import com.example.localplayer.domain.lyrics.Lyrics
import com.example.localplayer.domain.lyrics.LyricsQuery
import com.example.localplayer.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Поиск текстов песен на устройстве: LRCLIB с запасным источником lyrics.ovh.
 *
 * Порядок поиска повторяет десктопный `app/core/lyrics.py`: сначала соседний `.lrc`
 * файл рядом с аудио, затем LRCLIB (точный запрос `/api/get`, потом `/api/search` по
 * вариантам названия), затем lyrics.ovh. Найденный текст сохраняется в `.lrc` рядом
 * с аудиофайлом, чтобы второй раз не ходить в сеть.
 */
@Singleton
class LyricsRepository @Inject constructor() {

    @Serializable
    private data class LrclibEntry(
        val trackName: String? = null,
        val artistName: String? = null,
        val duration: Double? = null,
        val syncedLyrics: String? = null,
        val plainLyrics: String? = null,
        val instrumental: Boolean = false,
    )

    @Serializable
    private data class OvhEntry(
        val lyrics: String? = null,
    )

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(REQUEST_TIMEOUT_SEC, TimeUnit.SECONDS)
        .readTimeout(REQUEST_TIMEOUT_SEC, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /** Кеш неудачных запросов «trackId -> (когда, инструментал)», чтобы не дёргать API повторно. */
    private val missCache = ConcurrentHashMap<String, Pair<Long, Boolean>>()

    suspend fun load(track: Track, refresh: Boolean = false): Lyrics =
        withContext(Dispatchers.IO) {
            val audioFile = track.localFilePath?.let { File(it) }

            if (!refresh) {
                if (audioFile != null) {
                    readSidecar(audioFile)?.let { return@withContext it }
                }
                val cached = missCache[track.id]
                if (cached != null && System.currentTimeMillis() - cached.first < MISS_TTL_MS) {
                    return@withContext Lyrics(
                        found = false,
                        instrumental = cached.second,
                        source = if (cached.second) Lyrics.SOURCE_LRCLIB else null,
                    )
                }
            }

            val payload = fetchLrclib(track)
            var raw = ""
            var source = Lyrics.SOURCE_LRCLIB
            var instrumental = false

            if (payload != null) {
                if (payload.instrumental &&
                    payload.syncedLyrics.isNullOrBlank() &&
                    payload.plainLyrics.isNullOrBlank()
                ) {
                    instrumental = true
                } else {
                    raw = (payload.syncedLyrics ?: payload.plainLyrics ?: "").trim()
                }
            }

            if (raw.isEmpty() && !instrumental) {
                fetchOvh(track)?.let {
                    raw = it
                    source = Lyrics.SOURCE_OVH
                }
            }

            if (instrumental) {
                rememberMiss(track.id, instrumental = true)
                return@withContext Lyrics(
                    found = false,
                    instrumental = true,
                    source = Lyrics.SOURCE_LRCLIB,
                )
            }

            if (raw.isEmpty()) {
                rememberMiss(track.id, instrumental = false)
                // Как на десктопе: при принудительном обновлении ещё раз смотрим на диск.
                val local = if (refresh && audioFile != null) readSidecar(audioFile) else null
                return@withContext local ?: Lyrics(found = false)
            }

            missCache.remove(track.id)
            if (audioFile != null) saveSidecar(audioFile, raw)
            buildResult(raw, source)
        }

    // --- источники ---------------------------------------------------------

    private fun fetchLrclib(track: Track): LyricsQuery.Candidate? {
        val variants = LyricsQuery.buildQueryVariants(track.title, track.artist)
        if (variants.isEmpty()) return null
        val durationSec = track.durationMs.takeIf { it > 0L }?.div(1000.0)

        for ((variantTitle, variantArtist) in variants) {
            lookupVariant(variantTitle, variantArtist, durationSec)?.let { return it }
        }

        // Свободный поиск по `q` — принимаем только близкие по длительности версии.
        val attempts = mutableListOf<Triple<String, String, String?>>()
        for ((variantTitle, variantArtist) in variants.take(2)) {
            val query = listOfNotNull(variantTitle, variantArtist)
                .joinToString(" ")
                .trim()
            if (query.isNotEmpty() && attempts.none { it.first == query }) {
                attempts.add(Triple(query, variantTitle, variantArtist))
            }
        }
        val (firstTitle, firstArtist) = variants.first()
        if (attempts.none { it.first == firstTitle }) {
            attempts.add(Triple(firstTitle, firstTitle, firstArtist))
        }

        for ((query, targetTitle, targetArtist) in attempts.take(3)) {
            val builder = LRCLIB_SEARCH.newBuilder().addQueryParameter("q", query)
            val items = parseEntryList(get(builder.build())) ?: continue
            LyricsQuery.pickBestCandidate(
                items,
                durationSec,
                targetArtist = targetArtist,
                targetTitle = targetTitle,
                toleranceSec = 10.0,
            )?.let { return it }
        }
        return null
    }

    /** Пробует точный `/api/get` и затем `/api/search` для одного варианта. */
    private fun lookupVariant(
        title: String,
        artist: String?,
        durationSec: Double?,
    ): LyricsQuery.Candidate? {
        if (!artist.isNullOrEmpty()) {
            val builder = LRCLIB_GET.newBuilder()
                .addQueryParameter("track_name", title)
                .addQueryParameter("artist_name", artist)
            val entry = parseEntry(get(builder.build()))
            if (entry != null && entry.hasLyrics) {
                return entry
            }
        }

        val builder = LRCLIB_SEARCH.newBuilder().addQueryParameter("track_name", title)
        if (!artist.isNullOrEmpty()) {
            builder.addQueryParameter("artist_name", artist)
        } else {
            builder.addQueryParameter("q", title)
        }
        val items = parseEntryList(get(builder.build())) ?: return null
        return LyricsQuery.pickBestCandidate(
            items,
            durationSec,
            targetArtist = artist,
            targetTitle = title,
        )
    }

    /** Запасной источник простого (несинхронного) текста — api.lyrics.ovh. */
    private fun fetchOvh(track: Track): String? {
        val (cleanTitle, cleanArtist) = LyricsQuery.cleanQueryMetadata(track.title, track.artist)
        if (cleanTitle.isEmpty() || cleanArtist.isEmpty()) return null

        val url = LYRICS_OVH.newBuilder()
            .addPathSegment(cleanArtist)
            .addPathSegment(cleanTitle)
            .build()
        val body = get(url) ?: return null
        val entry = runCatching { json.decodeFromString<OvhEntry>(body) }
            .onFailure { AppLog.w("lyrics: не удалось разобрать ответ lyrics.ovh") }
            .getOrNull() ?: return null

        val text = entry.lyrics?.replace("\r\n", "\n")?.trim().orEmpty()
        if (text.isEmpty()) return null
        // Иногда вместо текста приходит заглушка «Paroles de la chanson ...».
        if (text.lowercase().startsWith("paroles de la chanson") && '\n' !in text) return null
        return TRIPLE_NEWLINES.replace(text, "\n\n")
    }

    // --- транспорт и парсинг ----------------------------------------------

    private fun get(url: HttpUrl): String? {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.string()
            }
        } catch (e: IOException) {
            AppLog.w("lyrics: сетевая ошибка", e)
            null
        }
    }

    private fun parseEntry(body: String?): LyricsQuery.Candidate? {
        if (body == null) return null
        val entry = runCatching { json.decodeFromString<LrclibEntry>(body) }.getOrNull() ?: return null
        return entry.toCandidate()
    }

    private fun parseEntryList(body: String?): List<LyricsQuery.Candidate>? {
        if (body == null) return null
        val items = runCatching { json.decodeFromString<List<LrclibEntry>>(body) }.getOrNull() ?: return null
        return items.map { it.toCandidate() }
    }

    private fun LrclibEntry.toCandidate() = LyricsQuery.Candidate(
        trackName = trackName,
        artistName = artistName,
        durationSec = duration,
        syncedLyrics = syncedLyrics,
        plainLyrics = plainLyrics,
        instrumental = instrumental,
    )

    // --- локальный кеш -----------------------------------------------------

    private fun sidecarFor(audio: File): File =
        File(audio.parentFile, "${audio.nameWithoutExtension}.lrc")

    private fun readSidecar(audio: File): Lyrics? {
        val sidecar = sidecarFor(audio)
        if (!sidecar.isFile) return null
        val content = runCatching { sidecar.readText() }.getOrNull()?.trim().orEmpty()
        if (content.isEmpty()) return null
        return buildResult(content, Lyrics.SOURCE_LRC)
    }

    private fun saveSidecar(audio: File, content: String) {
        val text = content.trim()
        if (text.isEmpty()) return
        runCatching { sidecarFor(audio).writeText(text + "\n") }
            .onFailure { AppLog.w("lyrics: не удалось сохранить ${sidecarFor(audio).name}", it) }
    }

    private fun buildResult(raw: String, source: String): Lyrics {
        val parsed = LrcParser.parse(raw)
        return if (parsed.lines.isNotEmpty()) {
            Lyrics(
                found = true,
                synced = true,
                source = source,
                plain = parsed.plain.ifEmpty { null },
                lines = parsed.lines,
            )
        } else {
            Lyrics(found = true, synced = false, source = source, plain = raw.trim())
        }
    }

    private fun rememberMiss(trackId: String, instrumental: Boolean) {
        missCache[trackId] = System.currentTimeMillis() to instrumental
    }

    private companion object {
        const val REQUEST_TIMEOUT_SEC = 8L
        const val MISS_TTL_MS = 10 * 60 * 1000L
        const val USER_AGENT = "SoundTube (https://github.com/Denisplokhikh/SoundTube)"

        val LRCLIB_GET: HttpUrl = "https://lrclib.net/api/get".toHttpUrl()
        val LRCLIB_SEARCH: HttpUrl = "https://lrclib.net/api/search".toHttpUrl()
        val LYRICS_OVH: HttpUrl = "https://api.lyrics.ovh/v1".toHttpUrl()
        val TRIPLE_NEWLINES = Regex("\n{3,}")
    }
}
