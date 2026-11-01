package com.example.localplayer.ui.screens.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localplayer.data.local.db.dao.PlaylistDao
import com.example.localplayer.data.local.db.toDomain
import com.example.localplayer.domain.model.Playlist
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Список плейлистов: системные площадки и пользовательские. */
@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    playlistDao: PlaylistDao,
) : ViewModel() {

    val playlists: StateFlow<List<Playlist>> = playlistDao.observeSummaries()
        .map { rows -> rows.map { it.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
