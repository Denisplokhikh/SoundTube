package com.example.localplayer.ui.screens.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.localplayer.BuildConfig
import com.example.localplayer.core.AppLog
import com.example.localplayer.ui.components.SoundTubeTopBar
import com.example.localplayer.ui.components.describe
import com.example.localplayer.ui.components.formatFileSize
import com.example.localplayer.ui.theme.ThemeMode

/** Настройки: тема, движок, хранилище, журнал и версия. */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val engine by viewModel.engineState.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val trackCount by viewModel.trackCount.collectAsStateWithLifecycle()
    val librarySize by viewModel.librarySize.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val messageIsError by viewModel.messageIsError.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var notificationsEnabled by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }

    // Вернулись из системных настроек — статус мог измениться.
    LifecycleResumeEffect(Unit) {
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        onPauseOrDispose { }
    }

    val openNotificationSettings: () -> Unit = {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        }
        Unit
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        SoundTubeTopBar(title = "Настройки")

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { SectionTitle("Оформление") }
            item {
                SettingsCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SettingsIcon(Icons.Rounded.Palette)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Тема", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "Системная следует настройке Android",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = themeMode == mode,
                                onClick = { viewModel.setTheme(mode) },
                                label = { Text(mode.label) },
                            )
                        }
                    }
                }
            }

            item { SectionTitle("Загрузки") }
            item {
                SettingsCard {
                    SettingsRow(
                        icon = Icons.Rounded.Download,
                        title = "Движок yt-dlp",
                        subtitle = engine.describe(),
                        actionLabel = if (busy) "…" else "Обновить",
                        actionEnabled = !busy,
                        onAction = viewModel::updateEngine,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(
                        icon = Icons.Rounded.DownloadForOffline,
                        title = "Докачать недостающее",
                        subtitle = "Треки из базы, которых нет на диске",
                        actionLabel = "Запустить",
                        actionEnabled = !busy,
                        onAction = viewModel::downloadMissing,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(
                        icon = Icons.Rounded.DeleteSweep,
                        title = "Убрать дубли файлов",
                        subtitle = "Копии вида «Название (2).m4a»",
                        actionLabel = "Проверить",
                        actionEnabled = !busy,
                        onAction = viewModel::cleanupDuplicates,
                    )
                }
            }

            message?.let { text ->
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (messageIsError) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (messageIsError) {
                                    MaterialTheme.colorScheme.onErrorContainer
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                },
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = viewModel::clearMessage) {
                                Text("Скрыть")
                            }
                        }
                    }
                }
            }

            item { SectionTitle("Хранилище") }
            item {
                SettingsCard {
                    SettingsRow(
                        icon = Icons.Rounded.MusicNote,
                        title = "Треков на диске",
                        subtitle = "Готовы играть без интернета",
                        value = trackCount.toString(),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(
                        icon = Icons.Rounded.Storage,
                        title = "Занято",
                        subtitle = "Аудио и обложки в памяти приложения",
                        value = librarySize.formatFileSize(),
                        actionLabel = "Обновить",
                        onAction = viewModel::refreshStorage,
                    )
                }
            }

            item { SectionTitle("Уведомления") }
            item {
                SettingsCard {
                    SettingsRow(
                        icon = Icons.Rounded.Notifications,
                        title = "Уведомления",
                        subtitle = if (notificationsEnabled) {
                            "Прогресс загрузок виден в шторке"
                        } else {
                            "Прогресс загрузок не показывается"
                        },
                        value = if (notificationsEnabled) "Включены" else "Выключены",
                        actionLabel = "Настроить",
                        onAction = openNotificationSettings,
                    )
                }
            }

            item {
                DiagnosticsCard(
                    entries = logs,
                    onClear = viewModel::clearLogs,
                )
            }

            item { SectionTitle("О приложении") }
            item {
                SettingsCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SettingsIcon(Icons.Rounded.Info)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("SoundTube", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "Версия ${BuildConfig.VERSION_NAME}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        text = "Локальный плеер и загрузчик. Библиотека хранится на устройстве, без облака.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
private fun SettingsIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    actionEnabled: Boolean = true,
    onAction: (() -> Unit)? = null,
    value: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsIcon(icon)

        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        when {
            actionLabel != null && onAction != null -> TextButton(
                onClick = onAction,
                enabled = actionEnabled,
            ) {
                Text(actionLabel)
            }

            value != null -> Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DiagnosticsCard(
    entries: List<AppLog.Entry>,
    onClear: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var expanded by rememberSaveable { mutableStateOf(false) }

    SettingsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Журнал", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "${entries.size} записей",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Свернуть" else "Развернуть")
            }
        }

        if (expanded) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = { clipboard.setText(AnnotatedString(AppLog.dump())) },
                    enabled = entries.isNotEmpty(),
                ) {
                    Text("Копировать")
                }
                TextButton(onClick = onClear, enabled = entries.isNotEmpty()) {
                    Text("Очистить")
                }
            }

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
}

private const val VISIBLE_LOG_LINES = 40
