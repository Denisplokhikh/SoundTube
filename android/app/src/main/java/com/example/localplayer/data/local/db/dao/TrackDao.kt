package com.example.localplayer.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.localplayer.data.local.db.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {

    @Query(
        """
        SELECT * FROM tracks
        WHERE isDeleted = 0
        ORDER BY artist COLLATE NOCASE ASC, title COLLATE NOCASE ASC
        """,
    )
    fun observeAll(): Flow<List<TrackEntity>>

    @Query(
        """
        SELECT * FROM tracks
        WHERE isDeleted = 0 AND localFilePath IS NOT NULL
        ORDER BY artist COLLATE NOCASE ASC, title COLLATE NOCASE ASC
        """,
    )
    fun observeDownloaded(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE isDeleted = 0 AND id = :id")
    suspend fun findById(id: String): TrackEntity?

    /** Для слияния: нужны и надгробия, иначе удаление на другом устройстве потеряется. */
    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun findAnyById(id: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE isDeleted = 0 AND sourceUrl = :sourceUrl LIMIT 1")
    suspend fun findBySourceUrl(sourceUrl: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE id IN (:ids)")
    suspend fun findByIds(ids: List<String>): List<TrackEntity>

    /** Очередь докачки: импортированные, но ещё не скачанные треки. */
    @Query(
        """
        SELECT * FROM tracks
        WHERE isDeleted = 0 AND localFilePath IS NULL
        ORDER BY createdAt ASC
        """,
    )
    suspend fun findPendingDownloads(): List<TrackEntity>

    /** Инкрементальная синхронизация: всё, что изменилось после отметки. */
    @Query("SELECT * FROM tracks WHERE updatedAt > :since ORDER BY updatedAt ASC")
    suspend fun findUpdatedSince(since: Long): List<TrackEntity>

    @Query("SELECT COUNT(*) FROM tracks WHERE isDeleted = 0")
    fun observeCount(): Flow<Int>

    @Upsert
    suspend fun upsert(track: TrackEntity)

    @Upsert
    suspend fun upsertAll(tracks: List<TrackEntity>)

    @Query("UPDATE tracks SET isDeleted = 1, localFilePath = NULL, updatedAt = :updatedAt WHERE id = :id")
    suspend fun markDeleted(id: String, updatedAt: Long)

    /** Перекачка/обновление файла: путь, хэш и метаданные приходят из загрузчика. */
    @Query(
        """
        UPDATE tracks
        SET localFilePath = :localFilePath,
            thumbnailPath = :thumbnailPath,
            fileHash = :fileHash,
            updatedAt = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun markDownloaded(
        id: String,
        localFilePath: String,
        thumbnailPath: String?,
        fileHash: String?,
        updatedAt: Long,
    )

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun deleteHard(id: String)
}
