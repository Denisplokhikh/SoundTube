package com.example.localplayer.data.repository

import com.example.localplayer.core.AppLog
import com.example.localplayer.data.local.db.dao.PlaylistDao
import com.example.localplayer.data.local.db.dao.TrackDao
import com.example.localplayer.data.local.db.entity.PlaylistEntity
import com.example.localplayer.data.local.db.entity.PlaylistTrackCrossRef
import com.example.localplayer.data.local.db.toDomain
import com.example.localplayer.data.local.files.AudioFileManager
import com.example.localplayer.domain.model.Playlist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Операции над уже скачанной библиотекой: удаление трека и ручные плейлисты.
 *
 * Удаление — всегда надгробие (`isDeleted`), а не `DELETE`: так трек не «воскреснет»
 * после слияния JSON с другим устройством. Файл при этом стирается с диска сразу.
 */
@Singleton
class LibraryRepository @Inject constructor(
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao,
    private val fileManager: AudioFileManager,
) {

    /** Только пользовательские плейлисты: системные площадок наполняются сами. */
    fun observeUserPlaylists(): Flow<List<Playlist>> = playlistDao.observeSummaries()
        .map { rows -> rows.map { it.toDomain() }.filterNot { it.isSystem } }

    /**
     * Удаляет трек из библиотеки: файл с диска плюс надгробие в базе.
     *
     * Из плейлистов он исчезает сам — запросы INNER JOIN'ят `tracks` и отбрасывают
     * `isDeleted = 1`.
     *
     * @return true, если трек существовал и был помечен удалённым.
     */
    suspend fun deleteTrack(trackId: String): Boolean = withContext(Dispatchers.IO) {
        val track = trackDao.findAnyById(trackId) ?: return@withContext false
        val fileDeleted = fileManager.delete(track.localFilePath)
        trackDao.markDeleted(trackId, System.currentTimeMillis())
        AppLog.i(
            "Track deleted: ${track.title}" + if (fileDeleted) " (file removed)" else "",
        )
        true
    }

    /**
     * Добавляет трек в плейлист. Повторное добавление не создаёт дубль и не меняет
     * позицию — связь идемпотентна по составному ключу.
     *
     * @return true, если плейлист и трек нашлись и связь сохранена.
     */
    suspend fun addToPlaylist(playlistId: String, trackId: String): Boolean =
        withContext(Dispatchers.IO) {
            val playlist = playlistDao.findById(playlistId) ?: return@withContext false
            val track = trackDao.findById(trackId) ?: return@withContext false
            val now = System.currentTimeMillis()

            val existing = playlistDao.findCrossRef(playlistId, trackId)
            val position = existing
                ?.takeIf { !it.isDeleted }
                ?.position
                ?: playlistDao.nextPosition(playlistId)

            playlistDao.upsertCrossRef(
                PlaylistTrackCrossRef(
                    playlistId = playlistId,
                    trackId = trackId,
                    position = position,
                    addedAt = now,
                    isDeleted = false,
                ),
            )
            playlistDao.touch(playlistId, now)
            AppLog.i("Added to playlist «${playlist.name}»: ${track.title}")
            true
        }

    /**
     * Создаёт пользовательский плейлист и сразу кладёт в него трек.
     *
     * @return созданный плейлист или null, если имя пустое.
     */
    suspend fun createPlaylist(name: String, trackId: String?): Playlist? =
        withContext(Dispatchers.IO) {
            val title = name.trim()
            if (title.isEmpty()) return@withContext null

            val now = System.currentTimeMillis()
            val playlist = PlaylistEntity(
                id = UUID.randomUUID().toString(),
                name = title,
                createdAt = now,
                updatedAt = now,
            )
            playlistDao.upsertPlaylist(playlist)
            AppLog.i("Playlist created: $title")

            if (trackId != null) addToPlaylist(playlist.id, trackId)
            playlist.toDomain(trackCount = if (trackId != null) 1 else 0)
        }
}
