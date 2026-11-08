package com.example.localplayer.ui.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localplayer.R
import com.example.localplayer.data.local.db.dao.TrackDao
import com.example.localplayer.data.local.db.toDomain
import com.example.localplayer.data.lyrics.LyricsRepository
import com.example.localplayer.data.repository.LibraryRepository
import com.example.localplayer.domain.lyrics.Lyrics
import com.example.localplayer.domain.model.Playlist
import com.example.localplayer.media.PlayerController
import com.example.localplayer.media.PlayerState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Состояние текста песни для плеера. */
sealed interface LyricsUiState {
    /** Текст ещё не запрашивался (или ничего не играет). */
    data object Idle : LyricsUiState

    data object Loading : LyricsUiState

    data class Loaded(val lyrics: Lyrics) : LyricsUiState

    /** Инструментал: в записи нет вокала. */
    data object Instrumental : LyricsUiState

    data object NotFound : LyricsUiState

    data object Error : LyricsUiState
}

/**
 * Мост между плеером и Compose: отдаёт состояние и команды.
 *
 * Очередь строится из скачанных треков (как на десктопе — играем то, что лежит на диске).
 * Текст песни ищется на устройстве ([LyricsRepository]) при смене текущего трека.
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val controller: PlayerController,
    private val trackDao: TrackDao,
    private val lyricsRepository: LyricsRepository,
    private val library: LibraryRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val state: StateFlow<PlayerState> = controller.state

    /** Позиция тикает отдельно от [state], чтобы не перерисовывать списки дважды в секунду. */
    val positionMs: StateFlow<Long> = controller.positionMs

    private val _lyricsState = MutableStateFlow<LyricsUiState>(LyricsUiState.Idle)
    val lyricsState: StateFlow<LyricsUiState> = _lyricsState.asStateFlow()

    /** Пользовательские плейлисты для меню «В плейлист» прямо из плеера. */
    val playlists: StateFlow<List<Playlist>> = library.observeUserPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Короткое подтверждение действия в плеере: UI показывает его тостом. */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    /** Текущая загрузка текста: смена трека отменяет предыдущий запрос. */
    private var lyricsJob: Job? = null

    init {
        viewModelScope.launch {
            controller.state
                .map { it.currentTrackId }
                .distinctUntilChanged()
                .collect { trackId -> loadLyrics(trackId) }
        }
    }

    /** Играет всю библиотеку с начала. */
    fun playAll() = viewModelScope.launch {
        val tracks = trackDao.observeDownloaded().first().map { it.toDomain() }
        if (tracks.isNotEmpty()) controller.playQueue(tracks)
    }

    /** Очередь из конкретных треков (например, из плейлиста), начиная с выбранного. */
    fun play(trackIds: List<String>, startIndex: Int = 0) = viewModelScope.launch {
        if (trackIds.isEmpty()) return@launch
        val byId = trackDao.findByIds(trackIds).associateBy { it.id }
        val tracks = trackIds.mapNotNull(byId::get)
            .filter { it.localFilePath != null }
            .map { it.toDomain() }
        if (tracks.isNotEmpty()) {
            controller.playQueue(tracks, startIndex.coerceIn(0, tracks.lastIndex))
        }
    }

    fun playPause() = controller.playPause()

    fun next() = controller.next()

    fun previous() = controller.previous()

    fun seekTo(positionMs: Long) = controller.seekTo(positionMs)

    fun seekToIndex(index: Int) = controller.seekToIndex(index)

    fun toggleShuffle() = controller.toggleShuffle()

    fun cycleRepeat() = controller.cycleRepeat()

    /** Удалённый с диска трек не должен продолжать играть из очереди. */
    fun onTrackDeleted(trackId: String) = controller.stopIfCurrent(trackId)

    /** Переподключение после «Стоп» системы или падения сервиса. */
    fun reconnect() = controller.connect()

    /** Повторный поиск текста (кнопка в плеере) — игнорирует кеш и `.lrc`. */
    fun retryLyrics() {
        loadLyrics(controller.state.value.currentTrackId, refresh = true)
    }

    fun consumeNotice() {
        _notice.value = null
    }

    /** Быстрое «В плейлист» из плеера: трек берём из текущего состояния контроллера. */
    fun addCurrentToPlaylist(playlistId: String) = viewModelScope.launch {
        val trackId = controller.state.value.currentTrackId ?: return@launch
        if (library.addToPlaylist(playlistId, trackId)) {
            _notice.value = context.getString(R.string.player_added_to_playlist)
        }
    }

    fun createPlaylistAndAdd(name: String) = viewModelScope.launch {
        val trackId = controller.state.value.currentTrackId ?: return@launch
        val created = library.createPlaylist(name, trackId) ?: return@launch
        _notice.value = context.getString(R.string.notice_playlist_created, created.name)
    }

    private fun loadLyrics(trackId: String?, refresh: Boolean = false) {
        lyricsJob?.cancel()
        if (trackId == null) {
            _lyricsState.value = LyricsUiState.Idle
            return
        }
        _lyricsState.value = LyricsUiState.Loading
        lyricsJob = viewModelScope.launch {
            val track = trackDao.findById(trackId)?.toDomain()
            if (track == null) {
                _lyricsState.value = LyricsUiState.NotFound
                return@launch
            }
            val lyrics = runCatching { lyricsRepository.load(track, refresh) }.getOrNull()
            _lyricsState.value = when {
                lyrics == null -> LyricsUiState.Error
                lyrics.instrumental -> LyricsUiState.Instrumental
                lyrics.found -> LyricsUiState.Loaded(lyrics)
                else -> LyricsUiState.NotFound
            }
        }
    }
}
