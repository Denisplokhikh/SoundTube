package com.example.localplayer.ui.screens.downloads

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.localplayer.R
import com.example.localplayer.data.downloader.EngineState
import com.example.localplayer.domain.model.DownloadState
import com.example.localplayer.domain.model.DownloadTask
import com.example.localplayer.ui.components.EmptyState
import com.example.localplayer.ui.components.SoundTubeBadge
import com.example.localplayer.ui.components.SoundTubeButton
import com.example.localplayer.ui.components.SoundTubeCard
import com.example.localplayer.ui.components.SoundTubeIconButton
import com.example.localplayer.ui.components.SoundTubeTopBar
import com.example.localplayer.ui.components.ThinProgressBar
import com.example.localplayer.ui.components.describe
import com.example.localplayer.ui.components.formatEta
import com.example.localplayer.ui.player.PlayerViewModel
import com.example.localplayer.ui.theme.SoundTubeTheme

/**
 * Вкладка «Загрузки»: вставить ссылку и смотреть очередь.
 *
 * Журнал и служебные операции (обновление yt-dlp, уборка дублей) переехали
 * в настройки; здесь остаётся только то, что нужно в момент скачивания.
 */
@Composable
fun DownloadsScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
    val colors = SoundTubeTheme.colors
    val engine by viewModel.engineState.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val messageIsError by viewModel.messageIsError.collectAsStateWithLifecycle()
    val confirmClearQueue by viewModel.confirmClearQueue.collectAsStateWithLifecycle()

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
            .background(colors.background)
            .imePadding(),
    ) {
        SoundTubeTopBar(
            title = stringResource(R.string.downloads_title),
            subtitle = stringResource(R.string.downloads_subtitle, engine.describe()),
            subtitleDot = if (engine is EngineState.Ready) colors.statusGreen else colors.warning,
            actions = {
                SoundTubeIconButton(
                    icon = Icons.Rounded.Settings,
                    contentDescription = stringResource(R.string.cd_settings),
                    bordered = false,
                    onClick = onOpenSettings,
                )
            },
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
                    title = stringResource(R.string.queue),
                    count = active.size,
                    action = {
                        SoundTubeButton(
                            text = stringResource(R.string.action_clear),
                            primary = false,
                            compact = true,
                            enabled = active.isNotEmpty() && !busy,
                            onClick = viewModel::askClearQueue,
                        )
                    },
                )
            }

            if (active.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.CloudDownload,
                        title = stringResource(R.string.queue_empty_title),
                        message = stringResource(R.string.queue_empty_message),
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
                        title = stringResource(R.string.completed_title),
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
                            text = stringResource(
                                R.string.completed_shown,
                                MAX_COMPLETED,
                                completed.size,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
        }

        if (confirmClearQueue) {
            ConfirmClearQueueDialog(
                onDismiss = viewModel::dismissClearQueue,
                onConfirm = viewModel::clearQueue,
            )
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
    val colors = SoundTubeTheme.colors
    val busyIndicator: @Composable () -> Unit = {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = colors.onPrimary,
        )
    }

    SoundTubeCard {
        Text(
            text = stringResource(R.string.new_download),
            style = MaterialTheme.typography.titleSmall,
            color = colors.textPrimary,
        )

        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.url_placeholder)) },
            textStyle = MaterialTheme.typography.bodyMedium,
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.surfaceInput,
                unfocusedContainerColor = colors.surfaceInput,
                focusedBorderColor = colors.primary,
                unfocusedBorderColor = colors.border,
                cursorColor = colors.primary,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                focusedPlaceholderColor = colors.textSecondary,
                unfocusedPlaceholderColor = colors.textSecondary,
            ),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SoundTubeButton(
                text = if (busy) {
                    stringResource(R.string.downloading_preparing)
                } else {
                    stringResource(R.string.action_download)
                },
                onClick = onSubmit,
                enabled = !busy && url.isNotBlank(),
                icon = if (busy) null else Icons.Rounded.Download,
                leading = if (busy) busyIndicator else null,
            )

            if (url.isNotBlank() && !busy) {
                SoundTubeButton(
                    text = stringResource(R.string.action_clear),
                    primary = false,
                    onClick = onClear,
                )
            }
        }

        Text(
            text = stringResource(R.string.download_hint),
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun NotificationPermissionCard(onRequest: () -> Unit) {
    val colors = SoundTubeTheme.colors

    SoundTubeCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Notifications,
                contentDescription = null,
                tint = colors.warning,
                modifier = Modifier.size(18.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.notifications_off_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                )
                Text(
                    text = stringResource(R.string.notifications_off_message),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
            }
            SoundTubeButton(
                text = stringResource(R.string.action_enable),
                primary = false,
                compact = true,
                onClick = onRequest,
            )
        }
    }
}

@Composable
private fun ConfirmClearQueueDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = SoundTubeTheme.colors

    Dialog(onDismissRequest = onDismiss) {
        SoundTubeCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteSweep,
                    contentDescription = null,
                    tint = colors.error,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.dialog_clear_queue_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                )
            }

            Text(
                text = stringResource(R.string.dialog_clear_queue_message),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoundTubeButton(
                    text = stringResource(R.string.action_clear),
                    danger = true,
                    modifier = Modifier.weight(1f),
                    onClick = onConfirm,
                )
                SoundTubeButton(
                    text = stringResource(R.string.action_cancel),
                    primary = false,
                    modifier = Modifier.weight(1f),
                    onClick = onDismiss,
                )
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
    val colors = SoundTubeTheme.colors
    val accent = if (isError) colors.error else colors.primary

    SoundTubeCard(borderColor = if (isError) colors.error else colors.border) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = if (isError) Icons.Rounded.ErrorOutline else Icons.Rounded.Info,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) colors.error else colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            if (canRetry) {
                SoundTubeButton(
                    text = stringResource(R.string.action_retry),
                    primary = false,
                    compact = true,
                    onClick = onRetry,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = SoundTubeTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
        )
        action?.invoke()
    }
}

@Composable
private fun DownloadTaskCard(
    task: DownloadTask,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onPlay: () -> Unit,
) {
    val colors = SoundTubeTheme.colors

    SoundTubeCard(contentPadding = PaddingValues(12.dp)) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SoundTubeBadge(platformOf(task.sourceUrl))
                    Text(
                        text = task.artist,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            when {
                task.isActive -> SoundTubeIconButton(
                    icon = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.cd_cancel_download),
                    bordered = false,
                    size = 30.dp,
                    onClick = onCancel,
                )

                task.state is DownloadState.Failed ||
                    task.state is DownloadState.Cancelled -> SoundTubeIconButton(
                    icon = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.action_retry),
                    bordered = false,
                    size = 30.dp,
                    onClick = onRetry,
                )

                task.state is DownloadState.Idle -> SoundTubeIconButton(
                    icon = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.cd_queue_download),
                    bordered = false,
                    size = 30.dp,
                    onClick = onRetry,
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        when (val state = task.state) {
            DownloadState.Idle -> StatusText(stringResource(R.string.status_queued))

            is DownloadState.FetchingMetadata -> {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = colors.primary,
                    trackColor = colors.border,
                    strokeCap = StrokeCap.Butt,
                )
                StatusText(stringResource(R.string.status_fetching_metadata))
            }

            is DownloadState.Downloading -> {
                ThinProgressBar(progress = state.progress)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "${(state.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.statusGreen,
                    )
                    Text(
                        text = state.status,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    state.etaSeconds.formatEta(
                        stringResource(R.string.eta_seconds_suffix),
                    )?.let { eta ->
                        Text(
                            text = eta,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary,
                        )
                    }
                }
            }

            is DownloadState.Completed -> {
                ThinProgressBar(progress = 1f, color = colors.statusGreen)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = colors.statusGreen,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = stringResource(R.string.status_completed),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.statusGreen,
                        modifier = Modifier.weight(1f),
                    )
                    SoundTubeButton(
                        text = stringResource(R.string.action_listen),
                        icon = Icons.Rounded.PlayArrow,
                        primary = false,
                        compact = true,
                        onClick = onPlay,
                    )
                }
            }

            is DownloadState.Failed -> Text(
                text = stringResource(R.string.status_failed, state.message),
                style = MaterialTheme.typography.labelSmall,
                color = colors.error,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            is DownloadState.Cancelled -> StatusText(stringResource(R.string.status_cancelled))
        }
    }
}

@Composable
private fun StatusText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = SoundTubeTheme.colors.textSecondary,
    )
}

/** Площадка по ссылке: бейдж `youtube` / `soundcloud`, как в карточке задачи на ПК. */
@Composable
private fun platformOf(url: String): String = when {
    url.contains("soundcloud", ignoreCase = true) -> "soundcloud"
    url.contains("youtu", ignoreCase = true) -> "youtube"
    else -> stringResource(R.string.platform_link)
}

/** Сколько завершённых задач показывать: история не должна хоронить очередь. */
private const val MAX_COMPLETED = 8
