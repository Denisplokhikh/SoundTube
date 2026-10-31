package com.example.localplayer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.localplayer.ui.player.PlayerViewModel
import com.example.localplayer.ui.screens.downloads.DownloadSmokeScreen
import com.example.localplayer.ui.screens.downloads.DownloadsViewModel
import com.example.localplayer.ui.theme.LocalPlayerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val downloadsViewModel: DownloadsViewModel by viewModels()
    private val playerViewModel: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleDebugIntent(intent)
        setContent {
            LocalPlayerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val contentModifier = Modifier.padding(innerPadding)
                    if (BuildConfig.DEBUG) {
                        // Временная панель проверки загрузчика; на шаге 5 её заменит навигация.
                        DownloadSmokeScreen(
                            viewModel = downloadsViewModel,
                            modifier = contentModifier,
                        )
                    } else {
                        PlaceholderScreen(modifier = contentModifier)
                    }
                }
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

        if (intent?.getBooleanExtra(EXTRA_DEBUG_PLAY, false) == true) {
            playerViewModel.playAll()
        }

        if (intent?.getBooleanExtra(EXTRA_DEBUG_RETRY_MISSING, false) == true) {
            downloadsViewModel.downloadMissing()
        }

        val url = intent?.getStringExtra(EXTRA_DEBUG_URL)?.takeIf { it.isNotBlank() } ?: return
        downloadsViewModel.submit(url)
    }

    companion object {
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
    }
}

@Composable
private fun PlaceholderScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "SoundTube",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "База, загрузчик и плеер в работе.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
