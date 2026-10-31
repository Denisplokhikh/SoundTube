package com.example.localplayer.ui.screens.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localplayer.core.AppLog
import com.example.localplayer.data.repository.DownloadRepository
import com.example.localplayer.data.repository.SubmitResult
import com.example.localplayer.domain.model.DownloadTask
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val repository: DownloadRepository,
) : ViewModel() {

    val engineState = repository.engineState

    val tasks: StateFlow<List<DownloadTask>> = repository.observeTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Журнал приложения — тот же, что в logcat, но виден на телефоне. */
    val logs: StateFlow<List<AppLog.Entry>> = AppLog.entries

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Сообщение — ошибка, а не подтверждение: рисуем красным и предлагаем повторить. */
    private val _messageIsError = MutableStateFlow(false)
    val messageIsError: StateFlow<Boolean> = _messageIsError.asStateFlow()

    fun submit(url: String) {
        if (url.isBlank() || _busy.value) return

        viewModelScope.launch {
            _busy.value = true
            // Плейлист читается целиком до постановки в очередь: это может занять минуту,
            // поэтому сразу говорим, что приложение не зависло.
            _message.value = "Читаем ссылку…"
            _messageIsError.value = false

            val result = repository.submit(url)
            _message.value = when (result) {
                is SubmitResult.Queued -> "В очереди: ${result.title}"
                is SubmitResult.QueuedPlaylist -> when {
                    result.count == 0 -> "Все ${result.total} треков уже скачаны"
                    else -> "В очереди: ${result.count} треков из ${result.total}"
                }
                is SubmitResult.AlreadyDownloaded -> "Уже скачано: ${result.title}"
                is SubmitResult.Failed -> "Ошибка: ${result.message}"
                SubmitResult.InvalidUrl -> "Нужна ссылка вида https://…"
            }
            _messageIsError.value = result is SubmitResult.Failed || result is SubmitResult.InvalidUrl
            _busy.value = false
        }
    }

    fun updateEngine() {
        viewModelScope.launch {
            _busy.value = true
            val result = repository.updateEngine()
            _message.value = result.fold(
                onSuccess = { "yt-dlp обновлён" },
                onFailure = { "Не удалось обновить yt-dlp: ${it.message}" },
            )
            _messageIsError.value = result.isFailure
            _busy.value = false
        }
    }

    fun retry(trackId: String) {
        viewModelScope.launch { repository.retry(trackId) }
    }

    /** Докачать всё, чего нет на диске: после пачки ошибок или импорта. */
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
        }
    }

    fun cancel(trackId: String) = repository.cancel(trackId)

    fun clearMessage() {
        _message.value = null
        _messageIsError.value = false
    }

    fun clearLogs() = AppLog.clear()
}
