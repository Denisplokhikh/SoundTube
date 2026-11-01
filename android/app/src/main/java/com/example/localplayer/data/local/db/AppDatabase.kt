package com.example.localplayer.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.localplayer.data.local.db.dao.PlaylistDao
import com.example.localplayer.data.local.db.dao.TrackDao
import com.example.localplayer.data.local.db.entity.PlaylistEntity
import com.example.localplayer.data.local.db.entity.PlaylistTrackCrossRef
import com.example.localplayer.data.local.db.entity.TrackEntity
import com.example.localplayer.domain.util.SystemPlaylists

@Database(
    entities = [
        TrackEntity::class,
        PlaylistEntity::class,
        PlaylistTrackCrossRef::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun trackDao(): TrackDao

    abstract fun playlistDao(): PlaylistDao

    companion object {
        const val NAME = "localplayer.db"

        /**
         * Системные плейлисты создаются один раз вместе с базой.
         *
         * Только `onCreate`, а не `onOpen`: если пользователь удалил плейлист,
         * надгробие не должно «воскресать» при следующем старте.
         */
        val seedSystemPlaylists = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                val now = System.currentTimeMillis()
                SystemPlaylists.all.forEach { (id, name) ->
                    db.execSQL(
                        """
                        INSERT OR IGNORE INTO playlists (id, name, createdAt, updatedAt, isDeleted)
                        VALUES (?, ?, ?, ?, 0)
                        """.trimIndent(),
                        arrayOf(id, name, now, now),
                    )
                }
            }
        }
    }
}
