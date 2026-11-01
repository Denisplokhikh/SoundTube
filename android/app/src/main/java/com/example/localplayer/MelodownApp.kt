package com.example.localplayer

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.localplayer.core.AppLog
import com.example.localplayer.data.downloader.YoutubeDLClient
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Точка входа приложения.
 *
 * WorkManager инициализируется вручную (см. AndroidManifest), чтобы его воркеры
 * создавал Hilt. Движок yt-dlp/ffmpeg инициализируется в фоне: при первом запуске
 * он распаковывает python и ffmpeg (десятки мегабайт), это не должно блокировать UI.
 */
@HiltAndroidApp
class MelodownApp : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var youtubeDL: YoutubeDLClient

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        AppLog.i("Запуск приложения")
        appScope.launch {
            val result = youtubeDL.ensureInitialized()
            result.fold(
                onSuccess = { AppLog.i("Движок yt-dlp готов") },
                onFailure = { AppLog.e("Движок yt-dlp не запустился", it) },
            )
        }
    }

    /**
     * Обложки приходят ссылками с площадок (YouTube, SoundCloud), поэтому кеш важнее
     * экономии места: без него каждая прокрутка списка заново тянет картинки из сети
     * и декодирует их на ходу — отсюда просадки кадров.
     *
     * `respectCacheHeaders = false` — чтобы CDN с заголовками `no-store`/коротким
     * `max-age` не мешали складывать обложки на диск.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .crossfade(false)
        .respectCacheHeaders(false)
        .memoryCache {
            MemoryCache.Builder(this@MelodownApp)
                .maxSizePercent(MEMORY_CACHE_PERCENT)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(this@MelodownApp.cacheDir.resolve("image_cache"))
                .maxSizeBytes(DISK_CACHE_BYTES)
                .build()
        }
        .build()

    private companion object {
        const val MEMORY_CACHE_PERCENT = 0.25
        const val DISK_CACHE_BYTES = 256L * 1024 * 1024
    }
}
