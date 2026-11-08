package com.example.localplayer.data.local.files

import android.media.MediaMetadataRetriever
import com.example.localplayer.core.AppLog
import com.example.localplayer.data.local.db.dao.TrackDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Досчитывает длительность треков, у которых её нет в базе.
 *
 * Треки, приехавшие с ПК синхронизацией, долгое время записывались без длительности:
 * в экспорте её не было, а файл на тот момент ещё не лежал на телефоне. Плеер
 * длительность знает (её отдаёт Media3), поэтому расхождение было видно только
 * в списке библиотеки — вместо времени там стоял прочерк.
 *
 * Длительность читается прямо из файла и пишется в базу; уже известные значения
 * не трогаются, поэтому запускать можно сколько угодно раз.
 */
@Singleton
class DurationBackfill @Inject constructor(
    private val trackDao: TrackDao,
) {

    /** Возвращает, скольким трекам длительность удалось проставить. */
    suspend fun run(): Int = withContext(Dispatchers.IO) {
        val rows = trackDao.findMissingDurations()
        if (rows.isEmpty()) return@withContext 0

        val now = System.currentTimeMillis()
        var fixed = 0
        for (row in rows) {
            val path = row.localFilePath ?: continue
            val durationMs = readDurationMs(path) ?: continue
            if (durationMs <= 0L) continue
            fixed += trackDao.updateDuration(row.id, durationMs, now)
        }

        if (fixed > 0) AppLog.i("Duration backfill: $fixed tracks")
        fixed
    }

    /** Длительность файла по данным контейнера; null — файла нет или он битый. */
    private fun readDurationMs(path: String): Long? {
        if (!File(path).isFile) return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
        } catch (error: Exception) {
            // Один битый файл не должен останавливать проход по остальным.
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}
