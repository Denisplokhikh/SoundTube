package com.example.localplayer.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localplayer.core.AppLog
import com.example.localplayer.data.local.db.dao.TrackDao
import com.example.localplayer.data.local.files.AudioFileManager
import com.example.localplayer.data.repository.DownloadRepository
import com.example.localplayer.ui.theme.ThemeMode
import com.example.localplayer.ui.theme.ThemePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val repository: DownloadRepository,
    private val trackDao: TrackDao,
    private val fileManager: AudioFileManager,
    private val themePreferences: ThemePreferences,
) : ViewModel() {

    val engineState = repository.engineState
    val themeMode: StateFlow<ThemeMode> = themePreferences.mode
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

    fun updateEngine() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _message.value = "Обновляем yt-dlp…"
            _messageIsError.value = false
            val result = repository.updateEngine()
            _message.value = result.fold(
                onSuccess = { "yt-dlp обновлён" },
                onFailure = { "Не удалось обновить yt-dlp: ${it.message}" },
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
                "Нечего докачивать — всё уже на диске"
            } else {
                "В очередь на докачку: $count"
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
                "Дублей не найдено"
            } else {
                "Убрано дублей: $removed"
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
