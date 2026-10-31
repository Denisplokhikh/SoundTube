package com.example.localplayer.data.downloader

import android.content.Context
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
     * `--dump-json` печатает по одному компактному JSON на строку; `--ignore-errors`
     * не даёт упасть всему списку из-за одного приватного трека.
     */
    suspend fun getTracks(url: String): List<TrackMeta> = withContext(Dispatchers.IO) {
        ensureInitialized().getOrThrow()

        val request = YoutubeDLRequest(url).apply {
            addOption("--dump-json")
            addOption("--ignore-errors")
            addOption("--no-warnings")
        }

        val response = YoutubeDL.getInstance().execute(
            request,
            processId = "probe:${url.hashCode()}#${processSeq.incrementAndGet()}",
        )

        val tracks = response.out.lineSequence()
            .filter { it.startsWith("{") }
            .mapNotNull { line -> parseEntry(line) }
            .toList()

        if (tracks.isEmpty()) {
            throw IOException(
                response.err.lineSequence()
                    .lastOrNull { it.isNotBlank() && !it.startsWith("WARNING") }
                    ?: "yt-dlp не вернул ни одного трека",
            )
        }
        tracks
    }

    private fun parseEntry(line: String): TrackMeta? = runCatching {
        val entry = json.decodeFromString<YtDlpEntry>(line)
        TrackMeta(
            sourceId = entry.id,
            title = entry.title,
            artist = entry.uploader,
            durationMs = ((entry.duration ?: 0.0) * 1000.0).toLong(),
            webpageUrl = entry.webpageUrl,
            thumbnail = entry.thumbnail,
        )
    }.getOrNull()

    /** Подмножество `--dump-json`, которое нам нужно. */
    @Serializable
    private data class YtDlpEntry(
        val id: String? = null,
        val title: String? = null,
        val uploader: String? = null,
        val duration: Double? = null,
        @SerialName("webpage_url") val webpageUrl: String? = null,
        val thumbnail: String? = null,
    )

    /**
     * Скачивает лучший аудиопоток и конвертирует в m4a с вшитыми метаданными и обложкой.
     *
     * @param trackId id трека; по нему же можно отменить процесс через [cancel].
     * @param onProgress прогресс 0..1, ETA в секундах, последняя строка вывода yt-dlp.
     * @return скачанный файл внутри [outputDir].
     */
    suspend fun download(
        url: String,
        outputDir: File,
        trackId: String,
        onProgress: (fraction: Float, etaSeconds: Long, line: String) -> Unit,
    ): File = withContext(Dispatchers.IO) {
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

        outputDir.listFiles()
            ?.filter { it.isFile && it.length() > 0 }
            ?.maxByOrNull { it.lastModified() }
            ?: throw IOException("yt-dlp не создал файл в ${outputDir.absolutePath}")
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
}
