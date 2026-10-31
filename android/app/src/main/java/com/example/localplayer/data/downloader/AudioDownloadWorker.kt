package com.example.localplayer.data.downloader

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.SystemClock
import com.example.localplayer.core.AppLog
import androidx.hilt.work.HiltWorker
import androidx.room.withTransaction
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.localplayer.data.local.db.AppDatabase
import com.example.localplayer.data.local.db.dao.PlaylistDao
import com.example.localplayer.data.local.db.dao.TrackDao
import com.example.localplayer.data.local.db.entity.PlaylistTrackCrossRef
import com.example.localplayer.data.local.db.entity.TrackEntity
import com.example.localplayer.data.local.files.AudioFileManager
import com.example.localplayer.domain.model.DownloadState
import com.example.localplayer.domain.model.SourcePlatform
import com.example.localplayer.domain.util.DownloadErrors
import com.example.localplayer.domain.util.SystemPlaylists
import com.yausername.youtubedl_android.YoutubeDL
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Фоновая загрузка одного трека.
 *
 * Работает как foreground-сервис: Android не убивает процесс, пока yt-dlp качает.
 * Прогресс прокидывается и в уведомление, и в [Data] задачи (её читает UI),
 * поэтому состояние переживает перезапуск приложения.
 */
@HiltWorker
class AudioDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val client: YoutubeDLClient,
    private val fileManager: AudioFileManager,
    private val database: AppDatabase,
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao,
) : CoroutineWorker(appContext, params) {

    private val progressScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override suspend fun doWork(): Result {
        val trackId = ownTrackId() ?: return Result.failure()
        val url = inputData.getString(KEY_URL) ?: return Result.failure()
        val track = trackDao.findAnyById(trackId) ?: return Result.failure()
        AppLog.i("Загрузка: ${track.title} (попытка ${runAttemptCount + 1})")

        // Файл уже на месте: задача повторяется после рестарта или повторной ссылки.
        // Скачивать заново нельзя — иначе рядом появится «Название (2).m4a».
        if (fileManager.exists(track.localFilePath)) {
            return Result.success()
        }

        DownloadNotifications.ensureChannel(applicationContext)

        val progress = MutableStateFlow<DownloadState>(
            DownloadState.Downloading(
                url = url,
                progress = 0f,
                etaSeconds = null,
                status = "Подготовка",
            ),
        )

        // Обновления прогресса живут в отдельном скоупе: даже если WorkManager
        // задержится с перерисовкой уведомления, doWork() всё равно завершится.
        val reporter = progressScope.launch {
            var lastUpdate = 0L
            var lastStatus = ""
            progress.collect { state ->
                if (state !is DownloadState.Downloading) return@collect
                val now = SystemClock.elapsedRealtime()
                if (now - lastUpdate < PROGRESS_INTERVAL_MS && state.progress < 1f) return@collect
                if (state.status == lastStatus && state.progress < 1f) return@collect
                lastUpdate = now
                lastStatus = state.status

                runCatching {
                    setProgress(progressData(state))
                    // Текст в уведомлении меняется только через setForeground.
                    setForeground(foregroundInfo(track.title, state))
                }
            }
        }

        return try {
            runCatching {
                setForeground(
                    foregroundInfo(track.title, progress.value as DownloadState.Downloading),
                )
            }

            val downloaded = client.download(url, fileManager.newWorkDir(track.id), track.id) { fraction, eta, line ->
                progress.value = DownloadState.Downloading(
                    url = url,
                    progress = fraction.coerceIn(0f, 1f),
                    etaSeconds = eta.takeIf { it > 0 },
                    status = DownloadNotifications.statusLine(line),
                )
            }

            progress.value = DownloadState.Downloading(url, 1f, null, "Обработка файла")

            val target = fileManager.moveIntoLibrary(downloaded, track.artist, track.title)
            val hash = runCatching { fileManager.sha256(target) }.getOrNull()
            val now = System.currentTimeMillis()

            database.withTransaction {
                trackDao.markDownloaded(
                    id = track.id,
                    localFilePath = target.absolutePath,
                    thumbnailPath = track.thumbnailPath,
                    fileHash = hash,
                    updatedAt = now,
                )
                linkToSystemPlaylist(track, now)
            }

            DownloadNotifications.done(applicationContext, track.title)
            progress.value = DownloadState.Completed(url, track.id)
            AppLog.i("Готово: ${track.title} → ${target.name}")
            // Данные наружу не отдаём: WorkManager перекладывает output предыдущей
            // задачи в inputData следующей, и в цепочке плейлиста это ломает треки.
            Result.success()
        } catch (cancellation: CancellationException) {
            // Задачу отменили вручную или системой — гасим нативный процесс yt-dlp.
            fileManager.discardWorkDir(track.id)
            client.cancel(track.id)
            throw cancellation
        } catch (canceled: YoutubeDL.CanceledException) {
            fileManager.discardWorkDir(track.id)
            Result.failure(workDataOf(KEY_ERROR to "Загрузка отменена"))
        } catch (error: Exception) {
            fileManager.discardWorkDir(track.id)
            val message = DownloadErrors.humanize(error.message)
            AppLog.w("Ошибка: ${track.title} — $message", error)
            if (shouldRetry(error) && runAttemptCount < MAX_ATTEMPTS) {
                Result.retry()
            } else {
                DownloadNotifications.failed(applicationContext, track.title, message)
                Result.failure(workDataOf(KEY_ERROR to message))
            }
        } finally {
            reporter.cancel()
        }
    }

    /**
     * id своего трека.
     *
     * В цепочке (плейлист) WorkManager сливает output предыдущей задачи в inputData
     * следующей через `OverwritingInputMerger`: после первого трека `track_id` в input
     * указывал уже на него, и все остальные задачи «находили» один и тот же файл.
     * Тег задачи задаётся при постановке и не сливается, поэтому он — источник истины.
     */
    private fun ownTrackId(): String? {
        val fromTag = tags
            .firstOrNull { it.startsWith(TRACK_TAG_PREFIX) }
            ?.removePrefix(TRACK_TAG_PREFIX)
        val fromInput = inputData.getString(KEY_TRACK_ID)

        if (fromTag != null && fromInput != null && fromTag != fromInput) {
            AppLog.w("Задача ${fromTag}: inputData перезаписан (${fromInput}), беру тег")
        }
        return fromTag ?: fromInput
    }

    /** Трек попадает в системный плейлист площадки — как на десктопе. */
    private suspend fun linkToSystemPlaylist(track: TrackEntity, now: Long) {
        val platform = SourcePlatform.fromKey(track.sourcePlatform)
        val playlistId = SystemPlaylists.idFor(platform) ?: return

        val existing = playlistDao.findCrossRef(playlistId, track.id)
        if (existing != null && !existing.isDeleted) return

        val position = playlistDao.nextPosition(playlistId)
        playlistDao.upsertCrossRef(
            PlaylistTrackCrossRef(
                playlistId = playlistId,
                trackId = track.id,
                position = position,
                addedAt = now,
                isDeleted = false,
            ),
        )
        playlistDao.touch(playlistId, now)
    }

    private fun foregroundInfo(title: String, state: DownloadState.Downloading): ForegroundInfo {
        val notification = DownloadNotifications.progress(
            context = applicationContext,
            title = title,
            status = state.status,
            fraction = state.progress,
        )
        return ForegroundInfo(
            DownloadNotifications.PROGRESS_NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    private fun progressData(state: DownloadState.Downloading): Data = workDataOf(
        KEY_PROGRESS to state.progress,
        KEY_ETA to (state.etaSeconds ?: -1L),
        KEY_STATUS to state.status,
    )

    private fun shouldRetry(error: Throwable): Boolean {
        if (error is IOException) return true

        val message = error.message.orEmpty().lowercase()
        if (PERMANENT_ERRORS.any(message::contains)) return false
        return TRANSIENT_ERRORS.any(message::contains)
    }

    companion object {

        const val KEY_TRACK_ID = "track_id"
        const val KEY_URL = "url"
        const val KEY_PROGRESS = "progress"
        const val KEY_ETA = "eta"
        const val KEY_STATUS = "status"
        const val KEY_ERROR = "error"

        const val TAG_DOWNLOAD = "download"
        const val TRACK_TAG_PREFIX = "track:"
        const val MAX_ATTEMPTS = 3

        private const val PROGRESS_INTERVAL_MS = 1_000L

        private val PERMANENT_ERRORS = listOf(
            "private video",
            "sign in",
            "not available",
            "unsupported url",
            "video unavailable",
            "removed",
            "age-restricted",
        )
        private val TRANSIENT_ERRORS = listOf(
            "network",
            "timed out",
            "timeout",
            "connection",
            "temporarily",
            "rate limit",
            "429",
            "502",
            "503",
            "504",
            "reset by peer",
            // Иногда площадка отдаёт пустой поток — со второго раза обычно получается.
            "did not get any data blocks",
            "downloaded file is empty",
        )

        fun uniqueName(trackId: String): String = "download:$trackId"

        fun trackTag(trackId: String): String = TRACK_TAG_PREFIX + trackId

        fun request(trackId: String, url: String): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<AudioDownloadWorker>()
                .setInputData(workDataOf(KEY_TRACK_ID to trackId, KEY_URL to url))
                .addTag(TAG_DOWNLOAD)
                .addTag(trackTag(trackId))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
    }
}
