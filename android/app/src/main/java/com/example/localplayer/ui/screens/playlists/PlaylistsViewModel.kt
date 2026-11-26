package com.example.localplayer.ui.screens.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localplayer.data.local.db.dao.PlaylistDao
import com.example.localplayer.data.local.db.toDomain
import com.example.localplayer.data.repository.LibraryRepository
import com.example.localplayer.domain.model.Playlist
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Список плейлистов: системные площадки и пользовательские. */
@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    playlistDao: PlaylistDao,
    private val library: LibraryRepository,
) : ViewModel() {

    val playlists: StateFlow<List<Playlist>> = playlistDao.observeSummaries()
        .map { rows -> rows.map { it.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _creation = MutableStateFlow<PlaylistCreation>(PlaylistCreation.Idle)
    val creation = _creation.asStateFlow()

    fun createPlaylist(name: String) {
        if (name.isBlank() || _creation.value == PlaylistCreation.Saving) return
        _creation.value = PlaylistCreation.Saving
        viewModelScope.launch {
            _creation.value = try {
                val playlist = library.createPlaylist(name, trackId = null)
                if (playlist == null) PlaylistCreation.Failed else PlaylistCreation.Created
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                PlaylistCreation.Failed
            }
        }
    }

    fun clearCreation() {
        if (_creation.value != PlaylistCreation.Saving) _creation.value = PlaylistCreation.Idle
    }
}

enum class PlaylistCreation { Idle, Saving, Failed, Created }
