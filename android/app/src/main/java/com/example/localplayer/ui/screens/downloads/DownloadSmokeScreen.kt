package com.example.localplayer.ui.screens.downloads

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.localplayer.core.AppLog
import com.example.localplayer.data.downloader.EngineState
import com.example.localplayer.domain.model.DownloadState
import com.example.localplayer.domain.model.DownloadTask

/**
 * Отладочная панель шага 3: проверить движок и скачать трек по ссылке.
 *
 * Появится только в debug-сборке; на шаге 5 её заменит нормальный экран «Загрузки».
 */
@Composable
fun DownloadSmokeScreen(
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val engine by viewModel.engineState.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val messageIsError by viewModel.messageIsError.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()

    var url by rememberSaveable { mutableStateOf("") }

    val context = LocalContext.current
    var notificationsAllowed by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationsAllowed = granted ||
            NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Загрузки",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Движок: ${engine.describe()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider()

        if (!notificationsAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            TextButton(
                onClick = {
                    permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                },
            ) {
                Text("Включить уведомления о загрузке")
            }
        }

        OutlinedTextField(
            value = url,
            onValueChange = {
                url = it
                viewModel.clearMessage()
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Ссылка YouTube / SoundCloud") },
            singleLine = true,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { viewModel.submit(url) },
                enabled = !busy && url.isNotBlank(),
            ) {
                Text(if (busy) "Готовим…" else "Скачать")
            }
            OutlinedButton(onClick = viewModel::updateEngine, enabled = !busy) {
                Text("Обновить yt-dlp")
            }
        }

        OutlinedButton(
            onClick = viewModel::downloadMissing,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Докачать недостающее")
        }

        TextButton(onClick = viewModel::cleanupDuplicates, enabled = !busy) {
            Text("Убрать дубли файлов")
        }

        message?.let { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = if (messageIsError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.secondary
                },
            )
            if (messageIsError && url.isNotBlank() && !busy) {
                TextButton(onClick = { viewModel.submit(url) }) { Text("Повторить") }
            }
        }

        HorizontalDivider()

        if (tasks.isEmpty()) {
            Text(
                text = "Очередь пуста",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            tasks.forEach { task ->
                DownloadRow(
                    task = task,
                    onCancel = { viewModel.cancel(task.trackId) },
                    onRetry = { viewModel.retry(task.trackId) },
                )
            }
        }

        HorizontalDivider()

        LogSection(
            entries = logs,
            onClear = viewModel::clearLogs,
        )
    }
}

/** Журнал: последние строки того же самого, что уходит в logcat. */
@Composable
private fun LogSection(
    entries: List<AppLog.Entry>,
    onClear: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var expanded by rememberSaveable { mutableStateOf(true) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Журнал (${entries.size})",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Свернуть" else "Развернуть")
        }
        if (expanded) {
            TextButton(
                onClick = {
                    clipboard.setText(AnnotatedString(AppLog.dump()))
                },
                enabled = entries.isNotEmpty(),
            ) {
                Text("Копировать")
            }
            TextButton(onClick = onClear, enabled = entries.isNotEmpty()) {
                Text("Очистить")
            }
        }
    }

    if (expanded) {
        if (entries.isEmpty()) {
            Text(
                text = "Пока пусто",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            // Свежие записи снизу; показываем хвост, чтобы не листать сотни строк.
            entries.takeLast(VISIBLE_LOG_LINES).forEach { entry ->
                Text(
                    text = "${entry.timeText} ${entry.levelText}: ${entry.message}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = when (entry.level) {
                        AppLog.Level.ERROR -> MaterialTheme.colorScheme.error
                        AppLog.Level.WARN -> MaterialTheme.colorScheme.tertiary
                        AppLog.Level.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun DownloadRow(
    task: DownloadTask,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "${task.artist} — ${task.title}",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        when (val state = task.state) {
            is DownloadState.Downloading -> {
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "${(state.progress * 100).toInt()}% · ${state.status}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = onCancel) { Text("Отмена") }
            }

            DownloadState.Idle -> Text(
                text = "В очереди",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            is DownloadState.Completed -> Text(
                text = "Готово",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            is DownloadState.Failed -> {
                Text(
                    text = "Ошибка: ${state.message}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = onRetry) { Text("Повторить") }
            }

            is DownloadState.FetchingMetadata -> Text(
                text = "Получаем метаданные…",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

private fun EngineState.describe(): String = when (this) {
    EngineState.NotInitialized -> "не инициализирован"
    EngineState.Initializing -> "инициализация…"
    is EngineState.Ready -> "готов${version?.let { " ($it)" } ?: ""}"
    EngineState.Updating -> "обновление…"
    is EngineState.Failed -> "ошибка: $message"
}

/** Сколько строк журнала показывать: хвост, а не всю историю. */
private const val VISIBLE_LOG_LINES = 40
