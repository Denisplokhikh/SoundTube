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

    /** Всё, что уже лежит на диске, — для сверки и уборки дублей. */
    @Query("SELECT * FROM tracks WHERE isDeleted = 0 AND localFilePath IS NOT NULL")
    suspend fun findDownloaded(): List<TrackEntity>

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

    /** Сколько файлов реально лежит в библиотеке. */
    @Query("SELECT COUNT(*) FROM tracks WHERE isDeleted = 0 AND localFilePath IS NOT NULL")
    fun observeDownloadedCount(): Flow<Int>

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

    /**
     * Уточнение метаданных после скачивания.
     *
     * Плоский список SoundCloud приходит без названий; настоящие название, исполнитель
     * и обложка появляются только в метаданных скачанного файла.
     *
     * `sourceUrl` перезаписываем канонической ссылкой из метаданных: в плоском списке
     * вместо неё бывает служебный адрес api-v2, и по нему повторный импорт уже
     * скачанный трек не узнает.
     */
    @Query(
        """
        UPDATE tracks
        SET title = :title,
            artist = :artist,
            durationMs = :durationMs,
            thumbnailPath = :thumbnailPath,
            sourceUrl = COALESCE(:sourceUrl, sourceUrl),
            updatedAt = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun updateMetadata(
        id: String,
        title: String,
        artist: String,
        durationMs: Long,
        thumbnailPath: String?,
        sourceUrl: String?,
        updatedAt: Long,
    )

    /**
     * Догоняет метаданные трека, который ещё стоит в очереди.
     *
     * Нужно фоновому дозапросу SoundCloud: название приходит уже после того, как
     * трек записан в базу и показан в очереди. Условия в `WHERE` защищают от гонки
     * со скачиванием — если файл уже готов или трек убрали из очереди, ничего не меняем.
     *
     * @return сколько строк обновилось.
     */
    @Query(
        """
        UPDATE tracks
        SET title = :title,
            artist = COALESCE(:artist, artist),
            durationMs = CASE WHEN :durationMs > 0 THEN :durationMs ELSE durationMs END,
            thumbnailPath = COALESCE(:thumbnailPath, thumbnailPath),
            updatedAt = :updatedAt
        WHERE id = :id AND isDeleted = 0 AND localFilePath IS NULL
        """,
    )
    suspend fun updateQueuedMetadata(
        id: String,
        title: String,
        artist: String?,
        durationMs: Long,
        thumbnailPath: String?,
        updatedAt: Long,
    ): Int

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun deleteHard(id: String)

    /**
     * Чинит названия, сохранённые старыми сборками.
     *
     * Тогда у треков из плоского списка SoundCloud в базу попадал служебный адрес
     * `api-v2.soundcloud.com/tracks/<id>`: название становилось числом, исполнитель — «tracks».
     * Настоящие придут при скачивании, но до тех пор в очереди был мусор.
     */
    @Query(
        """
        UPDATE tracks
        SET title = 'Трек ' || title,
            artist = 'SoundCloud',
            updatedAt = :updatedAt
        WHERE isDeleted = 0
          AND localFilePath IS NULL
          AND artist = 'tracks'
          AND title GLOB '[0-9]*'
        """,
    )
    suspend fun fixServiceTitles(updatedAt: Long): Int
}
