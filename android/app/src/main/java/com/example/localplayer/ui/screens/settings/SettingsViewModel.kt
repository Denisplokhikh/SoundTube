package com.example.localplayer.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localplayer.R
import com.example.localplayer.core.AppLog
import com.example.localplayer.core.i18n.LanguageMode
import com.example.localplayer.core.i18n.LanguagePreferences
import com.example.localplayer.data.local.db.dao.TrackDao
import com.example.localplayer.data.local.files.AudioFileManager
import com.example.localplayer.data.repository.DownloadRepository
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

    fun clearMessage() {
        _message.value = null
        _messageIsError.value = false
    }
}
