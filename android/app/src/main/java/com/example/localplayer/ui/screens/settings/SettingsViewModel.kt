package com.example.localplayer.ui.screens.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localplayer.R
import com.example.localplayer.core.AppLog
import com.example.localplayer.core.i18n.LanguageMode
import com.example.localplayer.core.i18n.LanguagePreferences
import com.example.localplayer.data.local.db.dao.TrackDao
import com.example.localplayer.data.local.files.AudioFileManager
import com.example.localplayer.data.repository.DownloadRepository
import com.example.localplayer.data.sync.ImportOutcome
import com.example.localplayer.data.sync.SyncImporter
import com.example.localplayer.ui.theme.ThemeMode
import com.example.localplayer.ui.theme.ThemePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Настройки и служебные операции, которые не нужны на экране загрузок. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DownloadRepository,
    private val trackDao: TrackDao,
    private val fileManager: AudioFileManager,
    private val syncImporter: SyncImporter,
    private val themePreferences: ThemePreferences,
    private val languagePreferences: LanguagePreferences,
) : ViewModel() {

    val engineState = repository.engineState
    val themeMode: StateFlow<ThemeMode> = themePreferences.mode
    val languageMode: StateFlow<LanguageMode> = languagePreferences.mode
    val logs: StateFlow<List<AppLog.Entry>> = AppLog.entries

    val trackCount: StateFlow<Int> = trackDao.observeDownloadedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _librarySize = MutableStateFlow(0L)
    val librarySize: StateFlow<Long> = _librarySize.asStateFlow()

    /**
     * Разрешена ли приложению работа в фоне без ограничений.
     *
     * На MIUI/HyperOS без этого система замораживает процесс, и загрузка
     * «замирает» на середине трека, а очередь — на середине плейлиста.
     * Это же исключение снимает запрет на запуск foreground-сервиса из фона
     * (Android 12+), из-за которого обрываются цепочки плейлистов.
     */
    private val _batteryUnrestricted = MutableStateFlow(isBatteryUnrestricted())
    val batteryUnrestricted: StateFlow<Boolean> = _batteryUnrestricted.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _messageIsError = MutableStateFlow(false)
    val messageIsError: StateFlow<Boolean> = _messageIsError.asStateFlow()

    init {
        // Размер библиотеки — не Flow: пересчитываем, когда меняется число треков
        // (скачали новый, убрали дубль) или пользователь сам попросил.
        viewModelScope.launch {
            trackCount.collect { refreshStorage() }
        }
    }

    fun refreshStorage() {
        viewModelScope.launch {
            val size = withContext(Dispatchers.IO) { fileManager.librarySize() }
            _librarySize.value = size
        }
    }

    fun setTheme(mode: ThemeMode) = themePreferences.setMode(mode)

    fun setLanguage(mode: LanguageMode) = languagePreferences.setMode(mode)

    fun updateEngine() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _message.value = context.getString(R.string.msg_engine_updating)
            _messageIsError.value = false
            val result = repository.updateEngine()
            _message.value = result.fold(
                onSuccess = { context.getString(R.string.msg_engine_updated) },
                onFailure = {
                    context.getString(R.string.msg_engine_update_failed, it.message.orEmpty())
                },
            )
            _messageIsError.value = result.isFailure
            _busy.value = false
        }
    }

    fun downloadMissing() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val count = repository.enqueuePendingDownloads()
            _message.value = if (count == 0) {
                context.getString(R.string.msg_nothing_to_download)
            } else {
                context.getString(R.string.msg_queued_for_download, count)
            }
            _messageIsError.value = false
            _busy.value = false
            refreshStorage()
        }
    }

    fun importLibrary(uri: Uri) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _messageIsError.value = false

            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader().readText()
                    }
                }.getOrNull()
            }

            if (text.isNullOrBlank()) {
                _message.value = context.getString(R.string.msg_import_unreadable)
                _messageIsError.value = true
                _busy.value = false
                return@launch
            }

            when (val outcome = syncImporter.import(text)) {
                is ImportOutcome.Success -> {
                    val queued = repository.enqueuePendingDownloads()
                    val result = outcome.result
                    _message.value = if (
                        result.tracksAdded == 0 && result.playlistEntriesAdded == 0
                    ) {
                        context.getString(R.string.msg_import_nothing)
                    } else {
                        context.getString(
                            R.string.msg_import_done,
                            result.tracksAdded,
                            result.tracksSkipped,
                            result.playlistsAdded,
                            queued,
                        )
                    }
                    _messageIsError.value = false
                }

                is ImportOutcome.Failure -> {
                    _message.value = context.getString(R.string.msg_import_failed)
                    _messageIsError.value = true
                }
            }

            _busy.value = false
            refreshStorage()
        }
    }

    fun cleanupDuplicates() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val removed = repository.removeDuplicateFiles()
            _message.value = if (removed == 0) {
                context.getString(R.string.msg_no_duplicates)
            } else {
                context.getString(R.string.msg_duplicates_removed, removed)
            }
            _messageIsError.value = false
            _busy.value = false
            refreshStorage()
        }
    }

    fun clearLogs() = AppLog.clear()

    /** Перечитывает статус после возврата из системных настроек. */
    fun refreshBackgroundState() {
        _batteryUnrestricted.value = isBatteryUnrestricted()
    }

    private fun isBatteryUnrestricted(): Boolean {
        val power = context.getSystemService(PowerManager::class.java) ?: return true
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Просит исключение из экономии батареи.
     *
     * Сначала показываем системный диалог для нашего приложения, а если
     * прошивка его не поддерживает — общий список исключений. Совсем без
     * поддержки — карточка приложения в настройках.
     */
    fun allowBackgroundWork() {
        val request = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (startActivity(request)) return

        val list = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (startActivity(list)) return

        openAppSettings()
    }

    /**
     * Автозапуск. У MIUI/HyperOS (и части других прошивок) это отдельный
     * список: без него система снимает очередь, когда приложение закрывают.
     */
    fun openAutostartSettings() {
        for (component in AUTOSTART_SCREENS) {
            val intent = Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (startActivity(intent)) return
        }
        openAppSettings()
    }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun startActivity(intent: Intent): Boolean =
        runCatching { context.startActivity(intent) }.isSuccess

    fun clearMessage() {
        _message.value = null
        _messageIsError.value = false
    }

    private companion object {
        /**
         * Экраны автозапуска у популярных прошивок. Пробуем по порядку:
         * на чужой прошивке startActivity просто кинет исключение.
         */
        val AUTOSTART_SCREENS = listOf(
            ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity",
            ),
            ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            ),
            ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            ),
            ComponentName(
                "com.oplus.safecenter",
                "com.oplus.safecenter.permission.startup.StartupAppListActivity",
            ),
            ComponentName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            ),
        )
    }
}
