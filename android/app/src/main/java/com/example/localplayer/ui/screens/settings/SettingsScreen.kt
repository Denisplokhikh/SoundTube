package com.example.localplayer.ui.screens.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.localplayer.BuildConfig
import com.example.localplayer.core.AppLog
import com.example.localplayer.ui.components.SectionLabel
import com.example.localplayer.ui.components.SoundTubeButton
import com.example.localplayer.ui.components.SoundTubeCard
import com.example.localplayer.ui.components.SoundTubeIconButton
import com.example.localplayer.ui.components.SoundTubeTopBar
import com.example.localplayer.ui.components.describe
import com.example.localplayer.ui.components.formatFileSize
import com.example.localplayer.ui.theme.SoundTubeTheme
import com.example.localplayer.ui.theme.ThemeMode

/** Настройки: тема, движок, хранилище, журнал и версия. */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val colors = SoundTubeTheme.colors
    val engine by viewModel.engineState.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val trackCount by viewModel.trackCount.collectAsStateWithLifecycle()
    val librarySize by viewModel.librarySize.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val messageIsError by viewModel.messageIsError.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var notificationsEnabled by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }

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
            .background(colors.background)
            .navigationBarsPadding(),
    ) {
        SoundTubeTopBar(
            title = "Настройки",
            subtitle = "тема · движок · хранилище",
            navigationIcon = {
                SoundTubeIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Назад",
                    bordered = false,
                    onClick = onBack,
                )
            },
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { SectionLabel("Оформление") }
            item {
                SettingsCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SettingsIcon(Icons.Rounded.Palette)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Тема",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.textPrimary,
                            )
                            Text(
                                text = "Системная следует настройке Android",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            SoundTubeButton(
                                text = mode.label,
                                primary = themeMode == mode,
                                compact = true,
                                onClick = { viewModel.setTheme(mode) },
                            )
                        }
                    }
                }
            }

            item { SectionLabel("Загрузки") }
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
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Rounded.DownloadForOffline,
                        title = "Докачать недостающее",
                        subtitle = "Треки из базы, которых нет на диске",
                        actionLabel = "Запустить",
                        actionEnabled = !busy,
                        onAction = viewModel::downloadMissing,
                    )
                    SettingsDivider()
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
                    SoundTubeCard(
                        borderColor = if (messageIsError) colors.error else colors.border,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (messageIsError) colors.error else colors.textPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            SoundTubeButton(
                                text = "Скрыть",
                                primary = false,
                                compact = true,
                                onClick = viewModel::clearMessage,
                            )
                        }
                    }
                }
            }

            item { SectionLabel("Хранилище") }
            item {
                SettingsCard {
                    SettingsRow(
                        icon = Icons.Rounded.MusicNote,
                        title = "Треков на диске",
                        subtitle = "Готовы играть без интернета",
                        value = trackCount.toString(),
                    )
                    SettingsDivider()
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

            item { SectionLabel("Уведомления") }
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
                        value = if (notificationsEnabled) "включены" else "выключены",
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

            item { SectionLabel("О приложении") }
            item {
                SettingsCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SettingsIcon(Icons.Rounded.Info)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "SoundTube",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.textPrimary,
                            )
                            Text(
                                text = "версия ${BuildConfig.VERSION_NAME}",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary,
                            )
                        }
                    }
                    Text(
                        text = "Локальный плеер и загрузчик. Библиотека хранится на устройстве, без облака.",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    SoundTubeCard(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = SoundTubeTheme.colors.divider,
    )
}

@Composable
private fun SettingsIcon(icon: ImageVector) {
    val colors = SoundTubeTheme.colors
    val shape = RoundedCornerShape(6.dp)

    Box(
        modifier = Modifier
            .size(32.dp)
            .background(colors.surfaceRaised, shape)
            .border(1.dp, colors.borderStrong, shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(16.dp),
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
    val colors = SoundTubeTheme.colors

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SettingsIcon(icon)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
        }

        when {
            actionLabel != null && onAction != null -> SoundTubeButton(
                text = actionLabel,
                primary = false,
                compact = true,
                enabled = actionEnabled,
                onClick = onAction,
            )

            value != null -> Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun DiagnosticsCard(
    entries: List<AppLog.Entry>,
    onClear: () -> Unit,
) {
    val colors = SoundTubeTheme.colors
    val clipboard = LocalClipboardManager.current
    var expanded by rememberSaveable { mutableStateOf(false) }

    SettingsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Журнал",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                )
                Text(
                    text = "${entries.size} записей",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
            }
            SoundTubeButton(
                text = if (expanded) "Свернуть" else "Развернуть",
                primary = false,
                compact = true,
                onClick = { expanded = !expanded },
            )
        }

        if (expanded) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                SoundTubeButton(
                    text = "Копировать",
                    primary = false,
                    compact = true,
                    enabled = entries.isNotEmpty(),
                    onClick = { clipboard.setText(AnnotatedString(AppLog.dump())) },
                )
                SoundTubeButton(
                    text = "Очистить",
                    primary = false,
                    compact = true,
                    enabled = entries.isNotEmpty(),
                    onClick = onClear,
                )
            }

            if (entries.isEmpty()) {
                Text(
                    text = "Пока пусто",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
            } else {
                // Свежие записи снизу; показываем хвост, чтобы не листать сотни строк.
                entries.takeLast(VISIBLE_LOG_LINES).forEach { entry ->
                    Text(
                        text = "${entry.timeText} ${entry.levelText}: ${entry.message}",
                        style = MaterialTheme.typography.labelSmall,
                        color = when (entry.level) {
                            AppLog.Level.ERROR -> colors.error
                            AppLog.Level.WARN -> colors.warning
                            AppLog.Level.INFO -> colors.textSecondary
                        },
                    )
                }
            }
        }
    }
}

private const val VISIBLE_LOG_LINES = 40
