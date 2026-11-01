package com.example.localplayer.ui.screens.downloads

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.localplayer.domain.model.DownloadState
import com.example.localplayer.domain.model.DownloadTask
import com.example.localplayer.ui.components.EmptyState
import com.example.localplayer.ui.components.SoundTubeTopBar
import com.example.localplayer.ui.components.describe
import com.example.localplayer.ui.components.formatEta
import com.example.localplayer.ui.player.PlayerViewModel

/**
 * Вкладка «Загрузки»: вставить ссылку и смотреть очередь.
 *
 * Журнал и служебные операции (обновление yt-dlp, уборка дублей) переехали
 * в настройки; здесь остаётся только то, что нужно в момент скачивания.
 */
@Composable
fun DownloadsScreen(
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
    val engine by viewModel.engineState.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val messageIsError by viewModel.messageIsError.collectAsStateWithLifecycle()

    var url by rememberSaveable { mutableStateOf("") }
    val submit = {
        if (url.isNotBlank() && !busy) viewModel.submit(url)
    }

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

    val completed = tasks.filter { it.state is DownloadState.Completed }
    val active = tasks.filterNot { it.state is DownloadState.Completed }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        SoundTubeTopBar(
            title = "Загрузки",
            subtitle = "Движок: ${engine.describe()}",
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                DownloadInputCard(
                    url = url,
                    onUrlChange = {
                        url = it
                        viewModel.clearMessage()
                    },
                    busy = busy,
                    onSubmit = submit,
                    onClear = { url = "" },
                )
            }

            if (!notificationsAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                item {
                    NotificationPermissionCard(
                        onRequest = {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                    )
                }
            }

            message?.let { text ->
                item {
                    MessageCard(
                        text = text,
                        isError = messageIsError,
                        canRetry = messageIsError && url.isNotBlank() && !busy,
                        onRetry = submit,
                    )
                }
            }

            item {
                SectionHeader(
                    title = "Очередь",
                    count = active.size,
                )
            }

            if (active.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.CloudDownload,
                        title = "Очередь пуста",
                        message = "Вставьте ссылку на YouTube или SoundCloud — загрузка начнётся сразу.",
                    )
                }
            } else {
                items(active, key = { it.trackId }) { task ->
                    DownloadTaskCard(
                        task = task,
                        onCancel = { viewModel.cancel(task.trackId) },
                        onRetry = { viewModel.retry(task.trackId) },
                        onPlay = { playerViewModel.play(listOf(task.trackId)) },
                    )
                }
            }

            if (completed.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Готово",
                        count = completed.size,
                    )
                }
                items(completed.take(MAX_COMPLETED), key = { it.trackId }) { task ->
                    DownloadTaskCard(
                        task = task,
                        onCancel = {},
                        onRetry = {},
                        onPlay = { playerViewModel.play(listOf(task.trackId)) },
                    )
                }
                if (completed.size > MAX_COMPLETED) {
                    item {
                        Text(
                            text = "Показаны последние $MAX_COMPLETED из ${completed.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadInputCard(
    url: String,
    onUrlChange: (String) -> Unit,
    busy: Boolean,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Новая загрузка",
                style = MaterialTheme.typography.titleMedium,
            )

            OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://youtube.com/watch?v=…") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onSubmit,
                    enabled = !busy && url.isNotBlank(),
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.size(8.dp))
                    }
                    Text(if (busy) "Готовим…" else "Скачать")
                }

                if (url.isNotBlank() && !busy) {
                    TextButton(onClick = onClear) {
                        Text("Очистить")
                    }
                }
            }

            Text(
                text = "YouTube, SoundCloud, плейлисты и сеты. Файлы сохраняются на устройство.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NotificationPermissionCard(onRequest: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Уведомления выключены",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = "Без них не видно прогресс в шторке.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            TextButton(onClick = onRequest) {
                Text("Включить")
            }
        }
    }
}

@Composable
private fun MessageCard(
    text: String,
    isError: Boolean,
    canRetry: Boolean,
    onRetry: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = if (isError) Icons.Rounded.ErrorOutline else Icons.Rounded.Info,
                contentDescription = null,
                tint = if (isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                },
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                },
                modifier = Modifier.weight(1f),
            )
            if (canRetry) {
                TextButton(onClick = onRetry) {
                    Text("Повторить")
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DownloadTaskCard(
    task: DownloadTask,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onPlay: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = task.state.tint().copy(alpha = 0.12f),
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = task.state.icon(),
                        contentDescription = null,
                        tint = task.state.tint(),
                        modifier = Modifier.size(20.dp),
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = task.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                when {
                    task.isActive -> IconButton(onClick = onCancel) {
                        Icon(Icons.Rounded.Close, contentDescription = "Отменить")
                    }

                    task.state is DownloadState.Failed ||
                        task.state is DownloadState.Cancelled -> IconButton(onClick = onRetry) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Повторить")
                    }

                    task.state is DownloadState.Idle -> IconButton(onClick = onRetry) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Поставить в очередь")
                    }
                }
            }

            when (val state = task.state) {
                DownloadState.Idle -> StatusText("В очереди")

                is DownloadState.FetchingMetadata -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    StatusText("Получаем метаданные…")
                }

                is DownloadState.Downloading -> {
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "${(state.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        Text(
                            text = state.status,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        state.etaSeconds.formatEta()?.let { eta ->
                            Text(
                                text = eta,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                is DownloadState.Completed -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Готово",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onPlay) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.size(4.dp))
                        Text("Слушать")
                    }
                }

                is DownloadState.Failed -> Text(
                    text = "Ошибка: ${state.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )

                is DownloadState.Cancelled -> StatusText("Отменено")
            }
        }
    }
}

@Composable
private fun StatusText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun DownloadState.icon() = when (this) {
    is DownloadState.Completed -> Icons.Rounded.CheckCircle
    is DownloadState.Failed -> Icons.Rounded.ErrorOutline
    is DownloadState.Cancelled -> Icons.Rounded.Cancel
    is DownloadState.Downloading -> Icons.Rounded.Download
    is DownloadState.FetchingMetadata -> Icons.Rounded.HourglassTop
    DownloadState.Idle -> Icons.Rounded.HourglassTop
}

@Composable
private fun DownloadState.tint() = when (this) {
    is DownloadState.Completed -> MaterialTheme.colorScheme.primary
    is DownloadState.Failed -> MaterialTheme.colorScheme.error
    is DownloadState.Downloading -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** Сколько завершённых задач показывать: история не должна хоронить очередь. */
private const val MAX_COMPLETED = 8
