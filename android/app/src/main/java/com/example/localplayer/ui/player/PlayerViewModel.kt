package com.example.localplayer.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localplayer.data.local.db.dao.TrackDao
import com.example.localplayer.data.local.db.toDomain
import com.example.localplayer.media.PlayerController
import com.example.localplayer.media.PlayerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Мост между плеером и Compose: отдаёт состояние и команды.
 *
 * Очередь строится из скачанных треков (как на десктопе — играем то, что лежит на диске).
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val controller: PlayerController,
    private val trackDao: TrackDao,
) : ViewModel() {

    val state: StateFlow<PlayerState> = controller.state

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

    /** Переподключение после «Стоп» системы или падения сервиса. */
    fun reconnect() = controller.connect()
}
