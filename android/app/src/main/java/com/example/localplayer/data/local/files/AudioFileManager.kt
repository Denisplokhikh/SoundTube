package com.example.localplayer.data.local.files

import android.content.Context
import android.os.Environment
import com.example.localplayer.domain.util.FileNameSanitizer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Файловое хранилище аудио.
 *
 * Скачивание идёт в кэш (там может лежать мусор от прерванных задач), а в библиотеку
 * попадает только готовый файл с нормальным именем. Путь к файлу — свойство устройства,
 * в идентичности трека он не участвует.
 */
@Singleton
class AudioFileManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Внешний app-specific каталог: виден файловым менеджерам, не требует разрешений. */
    private val libraryRoot: File
        get() = (context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: File(context.filesDir, "music"))
            .resolve("SoundTube")
            .apply { mkdirs() }

    private val tempRoot: File
        get() = File(context.cacheDir, "downloads").apply { mkdirs() }

    /** Отдельная папка под одну задачу, чтобы yt-dlp не смешивал файлы разных треков. */
    fun newWorkDir(trackId: String): File = workDir(trackId).apply {
        deleteRecursively()
        mkdirs()
    }

    fun discardWorkDir(trackId: String) {
        workDir(trackId).deleteRecursively()
    }

    private fun workDir(trackId: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(trackId.toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
            .take(16)
        return File(tempRoot, digest)
    }

    /**
     * Переносит скачанный файл в библиотеку: `SoundTube/<Исполнитель>/<Исполнитель> - <Название>.m4a`.
     * При совпадении имени добавляет суффикс ` (2)`, ` (3)`.
     */
    fun moveIntoLibrary(source: File, artist: String, title: String): File {
        val safeArtist = FileNameSanitizer.sanitize(artist, fallback = "Неизвестный исполнитель")
        val safeTitle = FileNameSanitizer.sanitize(title)
        val extension = source.extension.ifEmpty { "m4a" }

        val targetDir = libraryRoot.resolve(safeArtist).apply { mkdirs() }
        val baseName = "$safeArtist - $safeTitle"

        var target = File(targetDir, "$baseName.$extension")
        var counter = 2
        while (target.exists()) {
            target = File(targetDir, "$baseName ($counter).$extension")
            counter++
        }

        source.copyTo(target, overwrite = false)
        source.parentFile?.deleteRecursively()

        return target
    }

    /** SHA-256 файла — для сверки содержимого между устройствами. */
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { stream ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    /** Удаляет физический файл трека. Путь из базы может быть уже неактуален. */
    fun delete(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        val file = File(path)
        val deleted = file.isFile && file.delete()
        file.parentFile
            ?.takeIf { it != libraryRoot && it.list()?.isEmpty() == true }
            ?.delete()
        return deleted
    }

    fun exists(path: String?): Boolean = !path.isNullOrBlank() && File(path).isFile

    /** Размер библиотеки на диске, байты. */
    fun librarySize(): Long = libraryRoot.walkTopDown()
        .filter { it.isFile }
        .sumOf { it.length() }
}
