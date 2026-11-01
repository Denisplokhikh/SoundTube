package com.example.localplayer

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
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
class MelodownApp : Application(), Configuration.Provider {

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
}
