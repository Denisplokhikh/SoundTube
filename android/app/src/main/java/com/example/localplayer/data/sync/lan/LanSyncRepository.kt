package com.example.localplayer.data.sync.lan

import android.content.Context
import com.example.localplayer.R
import com.example.localplayer.core.AppLog
import com.example.localplayer.data.local.db.dao.TrackDao
import com.example.localplayer.data.local.files.AudioFileManager
import com.example.localplayer.data.local.files.DurationBackfill
import com.example.localplayer.data.sync.ImportOutcome
import com.example.localplayer.data.sync.SyncImporter
import com.example.localplayer.data.sync.SyncPayload
import com.example.localplayer.data.sync.SyncTrack
import com.example.localplayer.domain.util.TrackIds
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Стягивание библиотеки с ПК по локальной сети.
 *
 * Схема та же, что у ручного импорта: снимок (`/api/sync/export`) попадает
 * в [SyncImporter], то есть треки и плейлисты создаются по ровно тем же
 * правилам, что и из файла. Разница в источнике музыки: недостающие файлы
 * качаются прямо с ПК (`/api/stream`), а не с площадок через yt-dlp, поэтому
 * интернет не нужен — только одна Wi-Fi-сеть с компьютером.
 *
 * После завершения приложение полностью автономно: ПК можно выключать.
 */
@Singleton
class LanSyncRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: LanSyncClient,
    private val preferences: LanSyncPreferences,
    private val syncImporter: SyncImporter,
    private val trackDao: TrackDao,
    private val fileManager: AudioFileManager,
    private val durationBackfill: DurationBackfill,
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<LanSyncState>(LanSyncState.Idle)
    val state: StateFlow<LanSyncState> = _state.asStateFlow()

    /** Запускает синхронизацию: адрес сохраняем, чтобы поле на экране подставилось само. */
    fun sync(rawUrl: String, pin: String) {
        if (_state.value is LanSyncState.Connecting || _state.value is LanSyncState.Syncing) return
        scope.launch {
            _state.value = LanSyncState.Connecting
            try {
                val baseUrl = LanSyncClient.normalizeBaseUrl(rawUrl)
                preferences.setServerUrl(baseUrl)
                runSync(baseUrl, pin.takeIf { it.isNotBlank() })
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLog.e("LAN sync failed", error)
                _state.value = LanSyncState.Failed(explain(error))
            }
        }
    }

    private suspend fun runSync(baseUrl: String, pin: String?) {
        val export = client.export(baseUrl, pin)
        val payload = try {
            json.decodeFromString<SyncPayload>(export)
        } catch (error: Exception) {
            throw LanSyncException(explainFormat(error))
        }

        // Метаданные и плейлисты приложение раскладывает по тем же правилам,
        // что и при ручном импорте файла.
        val result = when (val import = syncImporter.import(export)) {
            is ImportOutcome.Success -> import.result
            is ImportOutcome.Failure -> throw LanSyncException(
                import.reason.ifBlank { context.getString(R.string.msg_lan_sync_invalid_payload) },
            )
        }

        val items = payload.tracks.filter { !it.path.isNullOrBlank() }
        var downloaded = 0
        var failed = 0

        for ((index, item) in items.withIndex()) {
            _state.value = LanSyncState.Syncing(
                done = index,
                total = items.size,
                current = item.title,
            )

            try {
                if (downloadTrack(baseUrl, item)) downloaded++
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // Один сбойный трек не должен рушить всю синхронизацию: остаток
                // докачается, причина уйдёт в журнал.
                failed++
                AppLog.w("LAN sync: ${item.url} failed", error)
                fileManager.discardWorkDir(TrackIds.forUrl(item.url))
            }
        }

        // Свежескачанные файлы обычно приходят без длительности: считаем её
        // из самих файлов, чтобы список библиотеки сразу показывал время.
        durationBackfill.run()

        _state.value = LanSyncState.Completed(
            downloaded = downloaded,
            alreadyOnDisk = items.size - downloaded - failed,
            tracksImported = result.tracksAdded,
            playlistsAdded = result.playlistsAdded,
            failed = failed,
        )
        AppLog.i(
            "LAN sync: downloaded $downloaded, failed $failed, " +
                "import added ${result.tracksAdded} tracks and ${result.playlistsAdded} playlists",
        )
    }

    /** Качает аудио и обложку одного трека. Возвращает true, если файл появился на диске. */
    private suspend fun downloadTrack(baseUrl: String, item: SyncTrack): Boolean {
        val trackId = TrackIds.forUrl(item.url)
        val track = trackDao.findAnyById(trackId) ?: return false
        if (track.isDeleted) return false

        // Уже скачанный файл не трогаем: перекачка породила бы дубликаты
        // («Название (2)») — смена файлов на ПК попадёт в следующую версию.
        if (fileManager.exists(track.localFilePath)) return false

        val relativePath = item.path.orEmpty()
        val workDir = fileManager.newWorkDir(trackId)
        val extension = relativePath.substringAfterLast('.', missingDelimiterValue = "")
        val audio = File(workDir, "track${if (extension.isEmpty()) "" else ".$extension"}")

        client.downloadAudio(baseUrl, relativePath, audio)
        val target = fileManager.moveIntoLibrary(audio, track.artist, track.title)
        if (!target.isFile) throw LanSyncException(context.getString(R.string.msg_lan_sync_invalid_payload))

        // Обложка не критична: без неё трек всё равно играется, а Coil
        // подставит заглушку.
        val artwork = File(target.parentFile, "${target.nameWithoutExtension}.jpg")
        val thumbnail = runCatching {
            client.downloadArtwork(baseUrl, relativePath, artwork)
            artwork.takeIf { it.isFile }?.absolutePath
        }.getOrNull()

        // Плейлисты и очередь работают с записью по id: достаточно обновить путь,
        // сам файл ничем не отличается от скачанного через yt-dlp.
        trackDao.upsert(
            track.copy(
                localFilePath = target.absolutePath,
                thumbnailPath = thumbnail ?: track.thumbnailPath,
                fileHash = fileManager.sha256(target),
                updatedAt = System.currentTimeMillis(),
            ),
        )
        return true
    }

    /** Понятный пользователю текст вместо стека исключений. */
    private fun explain(error: Exception): String = when (error) {
        is LanSyncException -> when (error.message) {
            LanSyncClient.PIN_REQUIRED -> context.getString(R.string.msg_lan_sync_pin_required)
            LanSyncClient.PIN_WRONG -> context.getString(R.string.msg_lan_sync_pin_wrong)
            LanSyncClient.INVALID_URL -> context.getString(R.string.msg_lan_sync_invalid_url)
            else -> error.message.orEmpty()
        }

        is java.io.IOException -> context.getString(R.string.msg_lan_sync_unreachable, error.message.orEmpty())
        else -> error.message.orEmpty()
    }

    private fun explainFormat(error: Exception) =
        context.getString(R.string.msg_lan_sync_invalid_payload) +
            " (${error.message.orEmpty()})"
}
