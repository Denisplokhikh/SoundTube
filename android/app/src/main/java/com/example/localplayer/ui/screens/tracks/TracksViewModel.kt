package com.example.localplayer.ui.screens.tracks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localplayer.data.local.db.dao.TrackDao
import com.example.localplayer.data.local.db.toDomain
import com.example.localplayer.data.local.files.AudioFileManager
import com.example.localplayer.data.repository.LibraryRepository
import com.example.localplayer.domain.model.Playlist
import com.example.localplayer.domain.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Порядок треков в библиотеке. */
enum class TrackSort {
    ARTIST,
    TITLE,
    RECENT,
}

/** Какое действие открыто в меню из трёх точек. */
sealed interface TrackAction {
    data class Delete(val track: Track) : TrackAction

    data class AddToPlaylist(val track: Track) : TrackAction
}

/** Строка-подтверждение под шапкой библиотеки. */
data class LibraryMessage(
    val text: String,
    val isError: Boolean = false,
)

data class TracksUiState(
    val tracks: List<Track> = emptyList(),
    val totalCount: Int = 0,
    val librarySize: Long = 0L,
    val query: String = "",
    val sort: TrackSort = TrackSort.ARTIST,
) {
    val isFiltering: Boolean get() = query.isNotBlank()
}

/**
 * Скачанные треки плюс поиск и сортировка.
 *
 * Источник — база, а не WorkManager: файл на диске переживает любые чистки очереди,
 * и библиотека не должна «пустеть» после перезапуска приложения.
 */
@HiltViewModel
class TracksViewModel @Inject constructor(
    trackDao: TrackDao,
    private val fileManager: AudioFileManager,
    private val library: LibraryRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val sort = MutableStateFlow(TrackSort.ARTIST)

    private val downloaded = trackDao.observeDownloaded()
        .map { entities -> entities.map { it.toDomain() } }

    /** Размер папки библиотеки: пересчитываем при каждом изменении списка треков. */
    private val librarySize = downloaded.map {
        withContext(Dispatchers.IO) { fileManager.librarySize() }
    }

    val uiState: StateFlow<TracksUiState> =
        combine(downloaded, query, sort, librarySize) { tracks, text, order, size ->
            val needle = text.trim()
            val filtered = if (needle.isEmpty()) {
                tracks
            } else {
                tracks.filter {
                    it.title.contains(needle, ignoreCase = true) ||
                        it.artist.contains(needle, ignoreCase = true)
                }
            }

            TracksUiState(
                tracks = filtered.sortedBy(order),
                totalCount = tracks.size,
                librarySize = size,
                query = text,
                sort = order,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TracksUiState())

    /** Пользовательские плейлисты для меню «В плейлист». */
    val playlists: StateFlow<List<Playlist>> = library.observeUserPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _action = MutableStateFlow<TrackAction?>(null)
    val action: StateFlow<TrackAction?> = _action.asStateFlow()

    /** Короткая строка-подтверждение под шапкой: что удалили или куда добавили. */
    private val _message = MutableStateFlow<LibraryMessage?>(null)
    val message: StateFlow<LibraryMessage?> = _message.asStateFlow()

    fun askDelete(track: Track) {
        _action.value = TrackAction.Delete(track)
    }

    fun askAddToPlaylist(track: Track) {
        _action.value = TrackAction.AddToPlaylist(track)
    }

    fun dismissAction() {
        _action.value = null
    }

    fun confirmDelete() = viewModelScope.launch {
        val target = _action.value as? TrackAction.Delete ?: return@launch
        // Диалог закрываем сразу: удаление файла не должно его «подвешивать».
        _action.value = null
        val deleted = library.deleteTrack(target.track.id)
        _message.value = if (deleted) {
            LibraryMessage("Удалено: ${target.track.title}")
        } else {
            LibraryMessage("Трек уже удалён", isError = true)
        }
    }

    fun addToPlaylist(playlistId: String) = viewModelScope.launch {
        val target = _action.value as? TrackAction.AddToPlaylist ?: return@launch
        _action.value = null
        val name = playlists.value.firstOrNull { it.id == playlistId }?.name ?: "плейлист"
        _message.value = if (library.addToPlaylist(playlistId, target.track.id)) {
            LibraryMessage("«${target.track.title}» → «$name»")
        } else {
            LibraryMessage("Не удалось добавить в плейлист", isError = true)
        }
    }

    fun createPlaylistAndAdd(name: String) = viewModelScope.launch {
        val target = _action.value as? TrackAction.AddToPlaylist ?: return@launch
        val created = library.createPlaylist(name, target.track.id)
        _action.value = null
        _message.value = if (created != null) {
            LibraryMessage("Плейлист «${created.name}» создан")
        } else {
            LibraryMessage("Введите название плейлиста", isError = true)
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun clearQuery() {
        query.value = ""
    }

    fun cycleSort() {
        sort.value = when (sort.value) {
            TrackSort.ARTIST -> TrackSort.TITLE
            TrackSort.TITLE -> TrackSort.RECENT
            TrackSort.RECENT -> TrackSort.ARTIST
        }
    }

    private fun List<Track>.sortedBy(order: TrackSort): List<Track> = when (order) {
        TrackSort.ARTIST -> sortedWith(
            compareBy({ it.artist.lowercase() }, { it.title.lowercase() }),
        )
        TrackSort.TITLE -> sortedBy { it.title.lowercase() }
        TrackSort.RECENT -> sortedByDescending { it.createdAt }
    }
}
