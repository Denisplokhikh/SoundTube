package com.example.localplayer.data.sync

import androidx.room.withTransaction
import com.example.localplayer.core.AppLog
import com.example.localplayer.data.local.db.AppDatabase
import com.example.localplayer.data.local.db.dao.PlaylistDao
import com.example.localplayer.data.local.db.dao.TrackDao
import com.example.localplayer.data.local.db.entity.PlaylistEntity
import com.example.localplayer.data.local.db.entity.PlaylistTrackCrossRef
import com.example.localplayer.data.local.db.entity.TrackEntity
import com.example.localplayer.domain.model.SourcePlatform
import com.example.localplayer.domain.util.TrackIds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.URI
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Итоги импорта, которые показываются пользователю. */
data class ImportResult(
    val tracksAdded: Int = 0,
    val tracksSkipped: Int = 0,
    val invalidItems: Int = 0,
    val playlistsAdded: Int = 0,
    val playlistEntriesAdded: Int = 0,
)

sealed interface ImportOutcome {
    data class Success(val result: ImportResult) : ImportOutcome

    /** Файл не разобрался: не наш формат или битый JSON. */
    data class Failure(val reason: String) : ImportOutcome
}

/**
 * Импорт JSON-снимка библиотеки с десктопа.
 *
 * Работает только на добавление: существующие треки не перезаписываются,
 * телефонные плейлисты не трогаются. Единственное исключение — надгробия треков,
 * у которых никогда не было файла: их снимаем, иначе отменённая загрузка навсегда
 * блокировала бы повторный импорт. Поэтому один и тот же файл можно импортировать
 * сколько угодно раз — счётчики просто покажут «уже было». Скачивание недостающего
 * запускает вызывающая сторона
 * ([com.example.localplayer.data.repository.DownloadRepository.enqueuePendingDownloads]).
 */
@Singleton
class SyncImporter @Inject constructor(
    private val database: AppDatabase,
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao,
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun import(text: String): ImportOutcome = withContext(Dispatchers.IO) {
        val payload = try {
            json.decodeFromString<SyncPayload>(text)
        } catch (error: Exception) {
            AppLog.w("Library import: couldn't parse JSON", error)
            return@withContext ImportOutcome.Failure(error.message.orEmpty())
        }

        val result = database.withTransaction { apply(payload) }
        AppLog.i(
            "Library import: added ${result.tracksAdded}, " +
                "already there ${result.tracksSkipped}, playlists ${result.playlistsAdded}",
        )
        ImportOutcome.Success(result)
    }

    private suspend fun apply(payload: SyncPayload): ImportResult {
        val now = System.currentTimeMillis()
        var tracksAdded = 0
        var tracksSkipped = 0
        var invalidItems = 0

        // Треки, которые в базе уже есть (в том числе удалённые): удалённые
        // нельзя добавлять в плейлисты, иначе элемент «воскреснет».
        val knownIds = HashSet<String>()
        val deletedIds = HashSet<String>()

        payload.tracks.forEach { item ->
            val url = item.url.trim()
            if (!TrackIds.looksLikeUrl(url)) {
                invalidItems++
                return@forEach
            }

            val trackId = TrackIds.forUrl(url)
            knownIds.add(trackId)

            val existing = trackDao.findAnyById(trackId)
            if (existing != null) {
                // Надгробие с пустым хэшем — трек никогда не скачивался: его убрали
                // из очереди («Очистить очередь») или загрузка не удалась. Такие
                // возвращаем, иначе повторный импорт навсегда считал бы их «уже
                // в библиотеке», хотя файла нет и в плейлисты они не попадут.
                val neverDownloaded = existing.isDeleted && existing.fileHash == null
                if (neverDownloaded) {
                    trackDao.restoreDeleted(
                        id = trackId,
                        title = item.title.trim(),
                        artist = item.artist.trim(),
                        sourceUrl = TrackIds.canonicalize(url),
                        sourcePlatform = platformFor(url, item.platform).key,
                        updatedAt = now,
                    )
                    tracksAdded++
                    return@forEach
                }

                tracksSkipped++
                if (existing.isDeleted) deletedIds.add(trackId)
                return@forEach
            }

            trackDao.upsert(
                TrackEntity(
                    id = trackId,
                    title = item.title.trim(),
                    artist = item.artist.trim(),
                    durationMs = 0L,
                    sourceUrl = TrackIds.canonicalize(url),
                    sourcePlatform = platformFor(url, item.platform).key,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            tracksAdded++
        }

        var playlistsAdded = 0
        var playlistEntriesAdded = 0
        val playlistsByName = playlistDao.findAll().associateBy { it.name.lowercase() }.toMutableMap()

        payload.playlists.forEach { item ->
            val name = item.name.trim()
            if (name.isEmpty()) {
                invalidItems++
                return@forEach
            }

            val playlist = playlistsByName[name.lowercase()] ?: PlaylistEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                createdAt = now,
                updatedAt = now,
            ).also {
                playlistDao.upsertPlaylist(it)
                playlistsByName[name.lowercase()] = it
                playlistsAdded++
            }

            val refs = playlistDao.findAllCrossRefs(playlist.id).associateBy { it.trackId }
            var position = (refs.values.filterNot { it.isDeleted }.maxOfOrNull { it.position } ?: -1) + 1
            var touched = false

            item.tracks.forEach { raw ->
                val url = raw.trim()
                if (!TrackIds.looksLikeUrl(url)) {
                    invalidItems++
                    return@forEach
                }

                val trackId = TrackIds.forUrl(url)
                if (trackId in deletedIds) return@forEach

                val ref = refs[trackId]
                if (ref != null && !ref.isDeleted) return@forEach

                if (trackId !in knownIds && trackDao.findAnyById(trackId) == null) {
                    invalidItems++
                    return@forEach
                }

                playlistDao.upsertCrossRef(
                    PlaylistTrackCrossRef(
                        playlistId = playlist.id,
                        trackId = trackId,
                        position = position++,
                        addedAt = now,
                        isDeleted = false,
                    ),
                )
                playlistEntriesAdded++
                touched = true
            }

            if (touched) playlistDao.touch(playlist.id, now)
        }

        return ImportResult(
            tracksAdded = tracksAdded,
            tracksSkipped = tracksSkipped,
            invalidItems = invalidItems,
            playlistsAdded = playlistsAdded,
            playlistEntriesAdded = playlistEntriesAdded,
        )
    }

    /** Площадка из JSON, а если её нет — по хосту ссылки. */
    private fun platformFor(url: String, platform: String?): SourcePlatform {
        val parsed = SourcePlatform.fromKey(platform)
        if (parsed != SourcePlatform.LOCAL) return parsed

        val host = runCatching { URI(url).host?.lowercase() }.getOrNull().orEmpty()
        return when {
            host == "youtu.be" || host.endsWith("youtube.com") -> SourcePlatform.YOUTUBE
            host.endsWith("soundcloud.com") || host == "snd.sc" -> SourcePlatform.SOUNDCLOUD
            else -> SourcePlatform.LOCAL
        }
    }
}
