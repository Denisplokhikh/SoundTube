package com.example.localplayer.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localplayer.data.local.db.dao.TrackDao
import com.example.localplayer.data.local.db.toDomain
import com.example.localplayer.domain.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Список скачанного.
 *
 * Источник — база, а не WorkManager: файл на диске переживает любые чистки очереди,
 * и библиотека не должна «пустеть» после перезапуска приложения.
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    trackDao: TrackDao,
) : ViewModel() {

    val tracks: StateFlow<List<Track>> = trackDao.observeDownloaded()
        .map { entities -> entities.map { it.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
