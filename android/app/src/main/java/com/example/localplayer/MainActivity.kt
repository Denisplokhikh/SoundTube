package com.example.localplayer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.localplayer.ui.Routes
import com.example.localplayer.ui.SoundTubeApp
import com.example.localplayer.ui.player.PlayerViewModel
import com.example.localplayer.ui.screens.downloads.DownloadsViewModel
import com.example.localplayer.ui.theme.LocalPlayerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val downloadsViewModel: DownloadsViewModel by viewModels()
    private val playerViewModel: PlayerViewModel by viewModels()

    /** Вкладка, открытая по debug-intent: тапать по экрану через adb MIUI не даёт. */
    private var debugTab by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleDebugIntent(intent)
        setContent {
            LocalPlayerTheme {
                SoundTubeApp(
                    startDestination = if (debugTab == TAB_DOWNLOADS) Routes.DOWNLOADS else Routes.LIBRARY,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDebugIntent(intent)
    }

    /**
     * Читает ссылку из intent-extra и сразу ставит её в очередь.
     *
     * Специально не через Compose: если экран заблокирован или окно не отрисовано,
     * композиция стоит, а загрузку всё равно надо запустить. Нужно для отладки:
     * MIUI запрещает adb-инъекцию тапов.
     */
    private fun handleDebugIntent(intent: Intent?) {
        if (!BuildConfig.DEBUG) return

        intent?.getStringExtra(EXTRA_DEBUG_TAB)?.let { debugTab = it }

        if (intent?.getBooleanExtra(EXTRA_DEBUG_PLAY, false) == true) {
            playerViewModel.playAll()
        }

        if (intent?.getBooleanExtra(EXTRA_DEBUG_RETRY_MISSING, false) == true) {
            downloadsViewModel.downloadMissing()
        }

        if (intent?.getBooleanExtra(EXTRA_DEBUG_CLEANUP, false) == true) {
            downloadsViewModel.cleanupDuplicates()
        }

        val url = intent?.getStringExtra(EXTRA_DEBUG_URL)?.takeIf { it.isNotBlank() } ?: return
        downloadsViewModel.submit(url)
    }

    companion object {
        private const val TAB_DOWNLOADS = "downloads"

        /**
         * Только для debug-сборки: `adb shell am start -n com.example.localplayer/.MainActivity
         * --es debug_url <ссылка>` сразу ставит трек в очередь.
         */
        const val EXTRA_DEBUG_URL = "debug_url"

        /**
         * Только для debug-сборки: `--ez debug_play true` запускает воспроизведение
         * всей библиотеки. Нужно для проверки плеера без тапов по экрану.
         */
        const val EXTRA_DEBUG_PLAY = "debug_play"

        /**
         * Только для debug-сборки: `--ez debug_retry_missing true` ставит в очередь
         * все треки из базы, которых нет на диске.
         */
        const val EXTRA_DEBUG_RETRY_MISSING = "debug_retry_missing"

        /**
         * Только для debug-сборки: `--ez debug_cleanup true` убирает файлы-копии
         * вида `Название (2).m4a`, которые остались от старых сборок без защиты от дублей.
         */
        const val EXTRA_DEBUG_CLEANUP = "debug_cleanup"

        /**
         * Только для debug-сборки: `--es debug_tab downloads` открывает нужную вкладку
         * сразу — для скриншотов и проверок, где нужен тап по экрану.
         */
        const val EXTRA_DEBUG_TAB = "debug_tab"
    }
}
