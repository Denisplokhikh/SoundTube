package com.example.localplayer.data.local.db.dao

import androidx.room.Embedded
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.localplayer.data.local.db.entity.PlaylistEntity
import com.example.localplayer.data.local.db.entity.PlaylistTrackCrossRef
import com.example.localplayer.data.local.db.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

/** Строка списка плейлистов: сам плейлист плюс число «живых» треков в нём. */
data class PlaylistSummaryRow(
    @Embedded val playlist: PlaylistEntity,
    val trackCount: Int,
)

/** Трек вместе с позицией внутри плейлиста. */
data class PlaylistEntryRow(
    @Embedded val track: TrackEntity,
    val position: Int,
)

@Dao
interface PlaylistDao {

    @Query(
        """
        SELECT p.*,
               (SELECT COUNT(*)
                FROM playlist_tracks pt
                    INNER JOIN tracks t ON t.id = pt.trackId
                WHERE pt.playlistId = p.id AND pt.isDeleted = 0 AND t.isDeleted = 0) AS trackCount
        FROM playlists p
        WHERE p.isDeleted = 0
        ORDER BY p.name COLLATE NOCASE ASC
        """,
    )
    fun observeSummaries(): Flow<List<PlaylistSummaryRow>>

    @Query("SELECT * FROM playlists WHERE isDeleted = 0 AND id = :id")
    suspend fun findById(id: String): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun findAnyById(id: String): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE isDeleted = 0 ORDER BY name COLLATE NOCASE ASC")
    suspend fun findAll(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE updatedAt > :since ORDER BY updatedAt ASC")
    suspend fun findUpdatedSince(since: Long): List<PlaylistEntity>

    @Query(
        """
        SELECT t.*, pt.position AS position
        FROM playlist_tracks pt
            INNER JOIN tracks t ON t.id = pt.trackId
        WHERE pt.playlistId = :playlistId AND pt.isDeleted = 0 AND t.isDeleted = 0
        ORDER BY pt.position ASC
        """,
    )
    fun observeEntries(playlistId: String): Flow<List<PlaylistEntryRow>>

    @Query(
        """
        SELECT t.*, pt.position AS position
        FROM playlist_tracks pt
            INNER JOIN tracks t ON t.id = pt.trackId
        WHERE pt.playlistId = :playlistId AND pt.isDeleted = 0 AND t.isDeleted = 0
        ORDER BY pt.position ASC
        """,
    )
    suspend fun findEntries(playlistId: String): List<PlaylistEntryRow>

    @Query(
        """
        SELECT trackId FROM playlist_tracks
        WHERE playlistId = :playlistId AND isDeleted = 0
        ORDER BY position ASC
        """,
    )
    suspend fun findTrackIds(playlistId: String): List<String>

    /** Все связи, включая надгробия — для LWW-слияния при импорте. */
    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun findAllCrossRefs(playlistId: String): List<PlaylistTrackCrossRef>

    @Query("SELECT * FROM playlist_tracks")
    suspend fun findAllCrossRefs(): List<PlaylistTrackCrossRef>

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun findCrossRef(playlistId: String, trackId: String): PlaylistTrackCrossRef?

    @Query(
        """
        SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_tracks
        WHERE playlistId = :playlistId AND isDeleted = 0
        """,
    )
    suspend fun nextPosition(playlistId: String): Int

    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = :playlistId AND isDeleted = 0")
    suspend fun countTracks(playlistId: String): Int

    @Upsert
    suspend fun upsertPlaylist(playlist: PlaylistEntity)

    @Upsert
    suspend fun upsertPlaylists(playlists: List<PlaylistEntity>)

    @Upsert
    suspend fun upsertCrossRef(ref: PlaylistTrackCrossRef)

    @Upsert
    suspend fun upsertCrossRefs(refs: List<PlaylistTrackCrossRef>)

    @Query("UPDATE playlists SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: String, updatedAt: Long)

    @Query("UPDATE playlists SET isDeleted = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun markDeleted(id: String, updatedAt: Long)

    @Query("UPDATE playlist_tracks SET isDeleted = 1 WHERE playlistId = :playlistId")
    suspend fun markCrossRefsDeleted(playlistId: String)

    @Query("UPDATE playlist_tracks SET isDeleted = 1 WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun markCrossRefDeleted(playlistId: String, trackId: String)

    @Query("UPDATE playlist_tracks SET position = :position WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun updatePosition(playlistId: String, trackId: String, position: Int)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deleteHard(id: String)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun deleteCrossRefsHard(playlistId: String)
}
