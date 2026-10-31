package com.example.localplayer.data.repository

import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.localplayer.core.AppLog
import com.example.localplayer.data.downloader.AudioDownloadWorker
import com.example.localplayer.data.downloader.EngineState
import com.example.localplayer.data.downloader.TrackMeta
import com.example.localplayer.data.downloader.YoutubeDLClient
import com.example.localplayer.data.local.db.dao.TrackDao
import com.example.localplayer.data.local.db.entity.TrackEntity
import com.example.localplayer.data.local.files.AudioFileManager
import com.example.localplayer.domain.model.DownloadState
import com.example.localplayer.domain.model.DownloadTask
import com.example.localplayer.domain.model.SourcePlatform
import com.example.localplayer.domain.util.DownloadErrors
import com.example.localplayer.domain.util.TrackIds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Результат постановки ссылки в очередь. */
sealed interface SubmitResult {
    data class Queued(val trackId: String, val title: String) : SubmitResult

    /** Плейлист/сет: в очередь встало [count] треков, остальные уже были на диске. */
    data class QueuedPlaylist(val count: Int, val total: Int) : SubmitResult

    data class AlreadyDownloaded(val trackId: String, val title: String) : SubmitResult

    data class Failed(val message: String) : SubmitResult

    data object InvalidUrl : SubmitResult
}

/**
 * Постановка загрузок в очередь и чтение их состояния.
 *
 * Метаданные тянем до постановки в WorkManager: так в базе сразу появляется трек
 * с детерминированным id, и повторная ссылка не создаёт дубль.
 * Плейлист раскладывается на отдельные треки, которые качаются по одному — в цепочке
 * WorkManager, иначе телефон захлёбывается параллельными yt-dlp и ffmpeg.
 */
@Singleton
class DownloadRepository @Inject constructor(
    private val client: YoutubeDLClient,
    private val trackDao: TrackDao,
    private val workManager: WorkManager,
    private val fileManager: AudioFileManager,
) {

    val engineState: StateFlow<EngineState> = client.state

    suspend fun prepareEngine(): Result<Unit> = client.ensureInitialized()

    suspend fun updateEngine(): Result<Unit> = client.updateBinaries()

    suspend fun submit(rawUrl: String): SubmitResult {
        val url = rawUrl.trim()
        AppLog.i("Ссылка: $url")
        if (!TrackIds.looksLikeUrl(url)) return SubmitResult.InvalidUrl

        val infos = try {
            fetchTracks(url)
        } catch (error: Exception) {
            AppLog.w("Не удалось прочитать ссылку", error)
            return SubmitResult.Failed(DownloadErrors.humanize(error.message))
        }

        AppLog.i("Найдено треков: ${infos.size}")

        return if (infos.size <= 1) {
            submitSingle(infos.first(), url)
        } else {
            submitPlaylist(infos, url)
        }
    }

    private suspend fun submitSingle(info: TrackMeta, url: String): SubmitResult {
        val canonicalUrl = info.webpageUrl?.takeIf { it.isNotBlank() } ?: url
        val platform = platformFor(canonicalUrl)
        val sourceId = info.sourceId

        // Для YouTube id видео приходит в метаданных — берём его, он каноничнее любого разбора ссылки.
        val trackId = when {
            platform == SourcePlatform.YOUTUBE && !sourceId.isNullOrBlank() -> TrackIds.youtube(sourceId)
            else -> TrackIds.forUrl(canonicalUrl)
        }

        val existing = trackDao.findAnyById(trackId)
        if (existing != null && fileManager.exists(existing.localFilePath)) {
            AppLog.i("Уже скачано: ${existing.title}")
            return SubmitResult.AlreadyDownloaded(trackId, existing.title)
        }

        val title = info.title?.takeIf { it.isNotBlank() } ?: "Без названия"
        trackDao.upsert(info.toEntity(trackId, canonicalUrl, platform, existing))

        val uniqueName = AudioDownloadWorker.uniqueName(trackId)
        val policy = withContext(Dispatchers.IO) {
            val current = runCatching {
                workManager.getWorkInfosForUniqueWork(uniqueName).get(5, TimeUnit.SECONDS)
            }.getOrDefault(emptyList())

            // Активную загрузку не трогаем; завершённую/упавшую — заменяем,
            // чтобы повторная ссылка сразу заработала.
            val active = current.any {
                it.state == WorkInfo.State.ENQUEUED ||
                    it.state == WorkInfo.State.RUNNING ||
                    it.state == WorkInfo.State.BLOCKED
            }
            if (active) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.REPLACE
        }

        workManager.enqueueUniqueWork(
            uniqueName,
            policy,
            AudioDownloadWorker.request(trackId, canonicalUrl),
        )
        AppLog.i("В очереди: $title ($policy)")

        return SubmitResult.Queued(trackId, title)
    }

    /**
     * Плейлист: каждый трек — отдельная задача, но задачи склеены в цепочку,
     * поэтому одновременно качается ровно один файл.
     */
    private suspend fun submitPlaylist(infos: List<TrackMeta>, url: String): SubmitResult {
        val chain = playlistChainName(url)
        var queued = 0
        var skipped = 0

        infos.take(MAX_PLAYLIST_TRACKS).forEach { info ->
            val canonicalUrl = info.webpageUrl?.takeIf { it.isNotBlank() } ?: return@forEach
            val platform = platformFor(canonicalUrl)
            val sourceId = info.sourceId
            val trackId = when {
                platform == SourcePlatform.YOUTUBE && !sourceId.isNullOrBlank() -> TrackIds.youtube(sourceId)
                else -> TrackIds.forUrl(canonicalUrl)
            }

            val existing = trackDao.findAnyById(trackId)
            if (existing != null && fileManager.exists(existing.localFilePath)) {
                skipped++
                return@forEach
            }

            trackDao.upsert(info.toEntity(trackId, canonicalUrl, platform, existing))

            workManager.enqueueUniqueWork(
                chain,
                // Первый трек пересоздаёт цепочку (повторная вставка той же ссылки),
                // остальные дописываются в конец.
                if (queued == 0) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.APPEND_OR_REPLACE,
                AudioDownloadWorker.request(trackId, canonicalUrl),
            )
            queued++

            AppLog.i("В плейлисте ${queued + skipped}/${infos.size}: ${info.title ?: trackId}")
        }

        AppLog.i("Плейлист: в очередь $queued, уже было $skipped")
        return SubmitResult.QueuedPlaylist(count = queued, total = infos.size)
    }

    /** Стабильное имя цепочки для ссылки на плейлист. */
    private fun playlistChainName(url: String): String =
        "playlist:" + TrackIds.sha256Hex(TrackIds.canonicalize(url)).take(16)

    /** Метаданные всех треков ссылки. При обрыве сети пробуем ещё раз. */
    private suspend fun fetchTracks(url: String): List<TrackMeta> {
        var attempt = 1
        while (true) {
            try {
                // Плейлист на 50 треков читается минутами: без отметок кажется, что зависло.
                return coroutineScope {
                    val ticker = launch {
                        var seconds = 0
                        while (isActive) {
                            delay(TICKER_INTERVAL_MS)
                            seconds += (TICKER_INTERVAL_MS / 1000L).toInt()
                            AppLog.i("Читаем ссылку… $seconds с")
                        }
                    }
                    try {
                        client.getTracks(url)
                    } finally {
                        ticker.cancel()
                    }
                }
            } catch (error: Exception) {
                val retryable = DownloadErrors.isNetworkError(error.message) && attempt < INFO_ATTEMPTS
                if (!retryable) throw error
                AppLog.w("Чтение ссылки не удалось (попытка $attempt), повторяю", error)
                delay(INFO_RETRY_DELAY_MS)
                attempt++
            }
        }
    }

    private fun TrackMeta.toEntity(
        trackId: String,
        canonicalUrl: String,
        platform: SourcePlatform,
        existing: TrackEntity?,
    ): TrackEntity {
        val now = System.currentTimeMillis()
        return TrackEntity(
            id = trackId,
            title = title?.takeIf { it.isNotBlank() } ?: "Без названия",
            artist = artist
                ?.removeSuffix(" - Topic")
                ?.takeIf { it.isNotBlank() }
                ?: "Неизвестный исполнитель",
            durationMs = durationMs,
            sourceUrl = canonicalUrl,
            sourcePlatform = platform.key,
            localFilePath = existing?.localFilePath,
            thumbnailPath = thumbnail ?: existing?.thumbnailPath,
            fileHash = existing?.fileHash,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            isDeleted = false,
        )
    }

    /** Очередь и история загрузок из WorkManager, с названиями из базы. */
    fun observeTasks(): Flow<List<DownloadTask>> =
        workManager.getWorkInfosByTagFlow(AudioDownloadWorker.TAG_DOWNLOAD).map { infos ->
            val ids = infos.mapNotNull { it.trackIdOf() }.distinct()
            if (ids.isEmpty()) return@map emptyList<DownloadTask>()

            val tracks = trackDao.findByIds(ids).associateBy(TrackEntity::id)

            infos.mapNotNull { workInfo ->
                val trackId = workInfo.trackIdOf() ?: return@mapNotNull null
                val track = tracks[trackId] ?: return@mapNotNull null
                DownloadTask(
                    trackId = trackId,
                    title = track.title,
                    artist = track.artist,
                    sourceUrl = track.sourceUrl,
                    state = workInfo.toDownloadState(track.sourceUrl, trackId),
                )
            }
                // У одного трека может быть несколько задач (повтор после ошибки) —
                // показываем самую «живую».
                .groupBy { it.trackId }
                .map { (_, sameTrack) -> sameTrack.minBy { it.state.sortOrder() } }
                .sortedWith(compareBy({ it.state.sortOrder() }, { it.title.lowercase() }))
        }

    suspend fun retry(trackId: String): Boolean {
        val track = trackDao.findAnyById(trackId) ?: return false

        // Если трек уже стоит в очереди (например, ждёт своей очереди в цепочке плейлиста),
        // второй раз не ставим: иначе два yt-dlp качают один файл и рядом появляется
        // «Название (2).m4a».
        val active = withContext(Dispatchers.IO) {
            runCatching {
                workManager
                    .getWorkInfosByTag(AudioDownloadWorker.trackTag(trackId))
                    .get(5, TimeUnit.SECONDS)
            }.getOrDefault(emptyList()).any { it.state.isActive() }
        }
        if (active) {
            AppLog.i("Уже в очереди: ${track.title}")
            return false
        }

        workManager.enqueueUniqueWork(
            AudioDownloadWorker.uniqueName(trackId),
            ExistingWorkPolicy.REPLACE,
            AudioDownloadWorker.request(trackId, track.sourceUrl),
        )
        AppLog.i("Повтор загрузки: ${track.title}")
        return true
    }

    fun cancel(trackId: String) {
        client.cancel(trackId)
        workManager.cancelUniqueWork(AudioDownloadWorker.uniqueName(trackId))
        AppLog.i("Загрузка отменена: $trackId")
    }

    /**
     * Докачка всего, что есть в базе, но ещё не лежит на диске: после импорта JSON
     * или после пачки ошибок (например, выключили VPN).
     *
     * Тоже цепочкой: десятки одновременных yt-dlp телефон не переварит.
     */
    suspend fun enqueuePendingDownloads(): Int {
        val pending = trackDao.findPendingDownloads()
        if (pending.isEmpty()) return 0

        // Старую очередь (например, упавшую из-за выключенного VPN) снимаем целиком:
        // иначе её задачи будут качать параллельно с новой цепочкой.
        workManager.cancelAllWorkByTag(AudioDownloadWorker.TAG_DOWNLOAD)

        pending.forEachIndexed { index, track ->
            workManager.enqueueUniqueWork(
                BACKLOG_CHAIN,
                if (index == 0) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.APPEND_OR_REPLACE,
                AudioDownloadWorker.request(track.id, track.sourceUrl),
            )
        }
        AppLog.i("Докачка: в очередь ${pending.size}")
        return pending.size
    }

    private fun platformFor(url: String): SourcePlatform {
        val host = runCatching { URI(url).host?.lowercase() }.getOrNull().orEmpty()
        return when {
            host == "youtu.be" || host.endsWith("youtube.com") -> SourcePlatform.YOUTUBE
            host.endsWith("soundcloud.com") || host == "snd.sc" -> SourcePlatform.SOUNDCLOUD
            else -> SourcePlatform.LOCAL
        }
    }

    private fun WorkInfo.trackIdOf(): String? = tags
        .firstOrNull { it.startsWith(AudioDownloadWorker.TRACK_TAG_PREFIX) }
        ?.removePrefix(AudioDownloadWorker.TRACK_TAG_PREFIX)

    private fun WorkInfo.toDownloadState(sourceUrl: String, trackId: String): DownloadState = when (state) {
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> DownloadState.Idle

        WorkInfo.State.RUNNING -> DownloadState.Downloading(
            url = sourceUrl,
            progress = progress.getFloat(AudioDownloadWorker.KEY_PROGRESS, 0f),
            etaSeconds = progress.getLong(AudioDownloadWorker.KEY_ETA, -1L).takeIf { it > 0 },
            status = progress.getString(AudioDownloadWorker.KEY_STATUS) ?: "Скачивание",
        )

        WorkInfo.State.SUCCEEDED -> DownloadState.Completed(
            url = sourceUrl,
            trackId = trackId,
        )

        WorkInfo.State.FAILED -> DownloadState.Failed(
            url = sourceUrl,
            message = outputData.getString(AudioDownloadWorker.KEY_ERROR) ?: "не удалось скачать",
        )

        WorkInfo.State.CANCELLED -> DownloadState.Failed(sourceUrl, "Загрузка отменена")
    }

    private fun DownloadState.sortOrder(): Int = when (this) {
        is DownloadState.Downloading -> 0
        is DownloadState.FetchingMetadata -> 1
        DownloadState.Idle -> 2
        is DownloadState.Failed -> 3
        is DownloadState.Completed -> 4
    }

    private fun WorkInfo.State.isActive(): Boolean =
        this == WorkInfo.State.ENQUEUED || this == WorkInfo.State.RUNNING || this == WorkInfo.State.BLOCKED

    private companion object {
        /** Сколько раз пробуем получить метаданные при обрыве сети. */
        const val INFO_ATTEMPTS = 3
        const val INFO_RETRY_DELAY_MS = 2_000L

        /** Как часто напоминать в журнале, что чтение плейлиста ещё идёт. */
        const val TICKER_INTERVAL_MS = 10_000L

        /** Защита от гигантских плейлистов: телефон не переварит больше. */
        const val MAX_PLAYLIST_TRACKS = 200

        /** Имя цепочки для «докачать всё, чего нет на диске». */
        const val BACKLOG_CHAIN = "backlog"
    }
}
