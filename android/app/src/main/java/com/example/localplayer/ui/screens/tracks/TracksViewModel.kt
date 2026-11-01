package com.example.localplayer.ui.screens.tracks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localplayer.data.local.db.dao.TrackDao
import com.example.localplayer.data.local.db.toDomain
import com.example.localplayer.domain.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Порядок треков в библиотеке. */
enum class TrackSort {
    ARTIST,
    TITLE,
    RECENT,
}

data class TracksUiState(
    val tracks: List<Track> = emptyList(),
    val totalCount: Int = 0,
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
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val sort = MutableStateFlow(TrackSort.ARTIST)

    private val downloaded = trackDao.observeDownloaded()
        .map { entities -> entities.map { it.toDomain() } }

    val uiState: StateFlow<TracksUiState> = combine(downloaded, query, sort) { tracks, text, order ->
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
            query = text,
            sort = order,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TracksUiState())

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
