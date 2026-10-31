package com.example.localplayer.data.downloader

import android.content.Context
import android.os.SystemClock
import com.example.localplayer.core.AppLog
import com.example.localplayer.domain.util.TrackIds
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Тонкая обёртка над youtubedl-android: инициализация движка, метаданные, скачивание.
 *
 * Всё, что делает [YoutubeDL] / [FFmpeg], уходит на [Dispatchers.IO]: они запускают
 * нативные процессы и распаковывают архивы, на главном потоке это ANR.
 */
@Singleton
class YoutubeDLClient @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val initMutex = Mutex()

    private val json = Json { ignoreUnknownKeys = true }

    /** Текущий нативный процесс каждого трека: нужно, чтобы «Отмена» убивала нужный. */
    private val runningProcesses = ConcurrentHashMap<String, String>()
    private val processSeq = AtomicLong()

    private val _state = MutableStateFlow<EngineState>(EngineState.NotInitialized)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    /** Идемпотентно инициализирует движок. Повторные вызовы ждут первый и не дублируют работу. */
    suspend fun ensureInitialized(): Result<Unit> = withContext(Dispatchers.IO) {
        initMutex.withLock {
            if (_state.value.isReady) return@withLock Result.success(Unit)

            _state.value = EngineState.Initializing
            runCatching {
                YoutubeDL.getInstance().init(context)
                FFmpeg.getInstance().init(context)
            }.fold(
                onSuccess = {
                    _state.value = EngineState.Ready(YoutubeDL.getInstance().versionName(context))
                    Result.success(Unit)
                },
                onFailure = { error ->
                    _state.value = EngineState.Failed(
                        error.message ?: "не удалось инициализировать yt-dlp",
                    )
                    Result.failure(error)
                },
            )
        }
    }

    /**
     * Полный список треков по ссылке: одиночный трек даёт список из одного элемента,
     * плейлист/сет — все входящие в него треки.
     *
     * Читаем «плоским» проходом (`--flat-playlist`): площадка отдаёт все записи одним
     * ответом за секунды. Обычный проход выкачивал страницу каждого трека — на сете
     * SoundCloud это десятки запросов, несколько минут и упор в rate limit, из-за чего
     * часть треков молча терялась (из сета на 50 песен приходило 16).
     *
     * У плоского списка SoundCloud нет названий — только ссылки. Их подтянет загрузчик
     * из `<id>.info.json` уже скачанного трека, без отдельного запроса.
     */
    suspend fun getTracks(url: String): List<TrackMeta> = withContext(Dispatchers.IO) {
        ensureInitialized().getOrThrow()

        val started = SystemClock.elapsedRealtime()
        val tracks = probe(url, flat = true)
        val unnamed = tracks.count { it.title.isNullOrBlank() }
        AppLog.i(
            "Ссылка: ${tracks.size} треков за ${elapsedSeconds(started)} с" +
                if (unnamed > 0) ", без названия $unnamed (подтянем при скачивании)" else "",
        )
        tracks
    }

    /** Один проход yt-dlp по ссылке: либо плоский список, либо полные метаданные. */
    private fun probe(url: String, flat: Boolean): List<TrackMeta> {
        val request = YoutubeDLRequest(url).apply {
            addOption("--dump-json")
            addOption("--ignore-errors")
            addOption("--no-warnings")
            if (flat) addOption("--flat-playlist")
        }

        val response = YoutubeDL.getInstance().execute(
            request,
            processId = "probe:${url.hashCode()}#${processSeq.incrementAndGet()}",
        )

        // Молча пропущенные записи — главная причина «нашёл не все песни», поэтому
        // пишем их количество в журнал, а не прячем в stderr.
        val failed = response.err.lineSequence().count { it.startsWith("ERROR:") }
        if (failed > 0) {
            AppLog.w("yt-dlp: не удалось прочитать $failed записей")
        }

        val tracks = response.out.lineSequence()
            .filter { it.startsWith("{") }
            .mapNotNull { line -> parseEntry(line) }
            .filter { !it.webpageUrl.isNullOrBlank() }
            .distinctBy { it.identityKey() }
            .toList()

        if (tracks.isEmpty()) {
            throw IOException(
                response.err.lineSequence()
                    .lastOrNull { it.isNotBlank() && !it.startsWith("WARNING") }
                    ?: "yt-dlp не вернул ни одного трека",
            )
        }
        return tracks
    }

    private fun parseEntry(line: String): TrackMeta? = runCatching {
        json.decodeFromString<YtDlpEntry>(line).toTrackMeta()
    }.getOrNull()

    private fun YtDlpEntry.toTrackMeta(): TrackMeta = TrackMeta(
        sourceId = id,
        title = title,
        artist = uploader,
        durationMs = ((duration ?: 0.0) * 1000.0).toLong(),
        // В плоском списке SoundCloud отдаёт permalink в `url`, а `webpage_url` нет.
        webpageUrl = webpageUrl ?: url,
        thumbnail = thumbnail,
    )

    /** Один и тот же трек из плоского и полного прохода склеиваем по ссылке. */
    private fun TrackMeta.identityKey(): String =
        webpageUrl?.let { TrackIds.canonicalize(it) } ?: sourceId ?: title.orEmpty()

    private fun elapsedSeconds(started: Long): Long =
        (SystemClock.elapsedRealtime() - started) / 1000L

    /** Подмножество `--dump-json`, которое нам нужно. */
    @Serializable
    private data class YtDlpEntry(
        val id: String? = null,
        val title: String? = null,
        val uploader: String? = null,
        val duration: Double? = null,
        @SerialName("webpage_url") val webpageUrl: String? = null,
        /** В плоском списке вместо `webpage_url` приходит `url`. */
        val url: String? = null,
        val thumbnail: String? = null,
    )

    /**
     * Скачивает лучший аудиопоток и конвертирует в m4a с вшитыми метаданными и обложкой.
     *
     * `--write-info-json` оставляет рядом `<id>.info.json`: если площадка не отдала
     * название и исполнителя при чтении ссылки, берём их отсюда — без лишнего запроса.
     *
     * @param trackId id трека; по нему же можно отменить процесс через [cancel].
     * @param onProgress прогресс 0..1, ETA в секундах, последняя строка вывода yt-dlp.
     * @return скачанный файл и метаданные, которые отдал yt-dlp.
     */
    suspend fun download(
        url: String,
        outputDir: File,
        trackId: String,
        onProgress: (fraction: Float, etaSeconds: Long, line: String) -> Unit,
    ): DownloadResult = withContext(Dispatchers.IO) {
        ensureInitialized().getOrThrow()
        outputDir.mkdirs()

        val request = YoutubeDLRequest(url).apply {
            addOption("--newline")
            addOption("--no-playlist")
            addOption("-f", "bestaudio[ext=m4a]/bestaudio")
            addOption("--extract-audio")
            addOption("--audio-format", "m4a")
            addOption("--embed-thumbnail")
            addOption("--add-metadata")
            addOption("--write-info-json")
            addOption("-o", File(outputDir, "%(id)s.%(ext)s").absolutePath)
        }

        // id процесса у библиотеки должен быть уникальным на каждый запуск: при повторе
        // («Повторить», ретрай) старый процесс ещё зарегистрирован, и yt-dlp падал
        // с «Process ID already exists». Для отмены храним актуальный id по треку.
        val processId = "$trackId#${processSeq.incrementAndGet()}"
        runningProcesses[trackId] = processId
        try {
            YoutubeDL.getInstance().execute(request, processId) { fraction, eta, line ->
                onProgress(fraction, eta, line)
            }
        } finally {
            runningProcesses.remove(trackId, processId)
        }

        val files = outputDir.listFiles()?.filter { it.isFile && it.length() > 0 }.orEmpty()

        val meta = files
            .firstOrNull { it.name.endsWith(".info.json") }
            ?.let { file ->
                runCatching {
                    json.decodeFromString<YtDlpEntry>(file.readText()).toTrackMeta()
                }.getOrNull()
            }

        // info.json тоже лежит в папке и он «свежее» — обязательно фильтруем по расширению.
        val audio = files
            .filter { it.extension.lowercase() in AUDIO_EXTENSIONS }
            .maxByOrNull { it.lastModified() }
            ?: throw IOException("yt-dlp не создал файл в ${outputDir.absolutePath}")

        DownloadResult(audio, meta)
    }

    /** Досрочно останавливает нативный процесс (кнопка «Отмена» в очереди). */
    fun cancel(trackId: String) {
        val processId = runningProcesses[trackId] ?: trackId
        runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
    }

    /** Обновление бинарника yt-dlp (YouTube регулярно ломает старые версии). */
    suspend fun updateBinaries(
        channel: YoutubeDL.UpdateChannel = YoutubeDL.UpdateChannel._NIGHTLY,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        ensureInitialized().getOrThrow()
        _state.value = EngineState.Updating
        runCatching { YoutubeDL.getInstance().updateYoutubeDL(context, channel) }
            .fold(
                onSuccess = {
                    _state.value = EngineState.Ready(YoutubeDL.getInstance().versionName(context))
                    Result.success(Unit)
                },
                onFailure = { error ->
                    _state.value = EngineState.Ready(YoutubeDL.getInstance().versionName(context))
                    Result.failure(error)
                },
            )
    }

    private companion object {
        /** Что считать скачанным аудио: `info.json` рядом с файлом — не трек. */
        val AUDIO_EXTENSIONS = setOf("m4a", "mp3", "opus", "webm", "aac", "ogg", "flac")
    }
}
