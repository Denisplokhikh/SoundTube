package com.example.localplayer.ui.screens.playlists

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localplayer.data.local.db.dao.PlaylistDao
import com.example.localplayer.data.local.db.toDomain
import com.example.localplayer.domain.model.Playlist
import com.example.localplayer.domain.model.PlaylistEntry
import com.example.localplayer.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Содержимое одного плейлиста; id приходит из навигации. */
@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    playlistDao: PlaylistDao,
) : ViewModel() {

    private val playlistId: String =
        savedStateHandle.get<String>(Routes.ARG_PLAYLIST_ID).orEmpty()

    val playlist: StateFlow<Playlist?> = playlistDao.observeSummaries()
        .map { rows -> rows.firstOrNull { it.playlist.id == playlistId }?.toDomain() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val entries: StateFlow<List<PlaylistEntry>> = playlistDao.observeEntries(playlistId)
        .map { rows -> rows.map { it.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
