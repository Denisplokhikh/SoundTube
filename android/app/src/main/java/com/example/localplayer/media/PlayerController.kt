package com.example.localplayer.media

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.localplayer.core.AppLog
import com.example.localplayer.domain.model.RepeatMode
import com.example.localplayer.domain.model.Track
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Единственная точка общения UI с [PlaybackService].
 *
 * Держит [MediaController] (Media3 сам поднимает сервис и показывает уведомление
 * с управлением на экране блокировки) и публикует снимок состояния в [state].
 * Activity и Compose не знают про ExoPlayer напрямую — только про этот класс.
 */
@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var ticker: Job? = null

    /** Запрос на воспроизведение, пришедший до подключения контроллера. */
    private var pendingQueue: Pair<List<Track>, Int>? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publish(player, includeQueue = true)
        }
    }

    init {
        connect()
    }

    /** Подключается к сервису. Повторные вызовы игнорируются, отключение переподключается. */
    fun connect() {
        if (controller != null || controllerFuture != null) return

        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future

        future.addListener(
            {
                runCatching { future.get() }.fold(
                    onSuccess = { connected ->
                        controller = connected
                        connected.addListener(listener)
                        publish(connected, includeQueue = true)
                        startTicker()
                        pendingQueue?.let { (tracks, startIndex) ->
                            pendingQueue = null
                            playQueue(tracks, startIndex)
                        }
                    },
                    onFailure = { error ->
                        AppLog.w("Не удалось подключиться к PlaybackService", error)
                        controllerFuture = null
                    },
                )
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    /** Ставит в очередь треки, начиная с [startIndex], и запускает воспроизведение. */
    fun playQueue(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        val playing = controller
        if (playing == null) {
            // Подключение к сервису асинхронное: не теряем нажатие «играть» при старте.
            pendingQueue = tracks to startIndex
            connect()
            return
        }
        val items = tracks.map(::mediaItem)
        playing.setMediaItems(items, startIndex.coerceIn(0, tracks.lastIndex), 0L)
        playing.prepare()
        playing.play()
    }

    fun playPause() {
        val player = controller ?: return
        when {
            player.isPlaying -> player.pause()
            player.playbackState == Player.STATE_IDLE -> {
                player.prepare()
                player.play()
            }
            else -> player.play()
        }
    }

    fun next() {
        val player = controller ?: return
        if (player.hasNextMediaItem()) player.seekToNextMediaItem()
    }

    /** Как в обычных плеерах: сначала «в начало трека», и только потом «предыдущий». */
    fun previous() {
        val player = controller ?: return
        if (player.currentPosition > RESTART_THRESHOLD_MS || !player.hasPreviousMediaItem()) {
            player.seekTo(0L)
        } else {
            player.seekToPreviousMediaItem()
        }
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun seekToIndex(index: Int) {
        val player = controller ?: return
        if (index in 0 until player.mediaItemCount) {
            player.seekToDefaultPosition(index)
        }
    }

    fun toggleShuffle() {
        val player = controller ?: return
        player.shuffleModeEnabled = !player.shuffleModeEnabled
    }

    fun cycleRepeat() {
        val player = controller ?: return
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun stop() {
        controller?.stop()
    }

    /** Отвязывается от сервиса (вызывать при выходе из приложения не обязательно). */
    fun release() {
        ticker?.cancel()
        ticker = null
        controller?.let { player ->
            player.removeListener(listener)
            player.release()
        }
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) {
                controller?.let { publish(it, includeQueue = false) }
                delay(TICK_MS)
            }
        }
    }

    /** Полный снимок (с очередью) — по событиям; позицию обновляет таймер. */
    private fun publish(player: Player, includeQueue: Boolean) {
        _state.update { previous ->
            val queue = if (includeQueue) player.queueSnapshot() else previous.queue
            val current = player.currentMediaItem

            PlayerState(
                isConnected = true,
                isPlaying = player.isPlaying,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                currentTrackId = current?.mediaId,
                title = current?.mediaMetadata?.title?.toString().orEmpty(),
                artist = current?.mediaMetadata?.artist?.toString()
                    ?: current?.mediaMetadata?.albumArtist?.toString().orEmpty(),
                artworkUri = current?.mediaMetadata?.artworkUri?.toString(),
                positionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = player.duration.takeIf { it > 0L } ?: previous.durationMs,
                queue = queue,
                currentIndex = player.currentMediaItemIndex,
                shuffle = player.shuffleModeEnabled,
                repeatMode = player.repeatMode.toRepeatMode(),
            )
        }
    }

    private fun Player.queueSnapshot(): List<QueueItem> = (0 until mediaItemCount).map { index ->
        val item = getMediaItemAt(index)
        QueueItem(
            trackId = item.mediaId,
            title = item.mediaMetadata.title?.toString().orEmpty(),
            artist = item.mediaMetadata.artist?.toString()
                ?: item.mediaMetadata.albumArtist?.toString().orEmpty(),
            durationMs = item.mediaMetadata.durationMs ?: 0L,
            artworkUri = item.mediaMetadata.artworkUri?.toString(),
        )
    }

    private fun mediaItem(track: Track): MediaItem {
        val file = track.localFilePath?.let(::File)
        val uri = if (file != null && file.exists()) {
            Uri.fromFile(file)
        } else {
            Uri.parse(track.sourceUrl)
        }

        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setDurationMs(track.durationMs.takeIf { it > 0L })
                    .setArtworkUri(track.thumbnailPath?.let(Uri::parse))
                    .build(),
            )
            .build()
    }

    private fun Int.toRepeatMode(): RepeatMode = when (this) {
        Player.REPEAT_MODE_ALL -> RepeatMode.ALL
        Player.REPEAT_MODE_ONE -> RepeatMode.ONE
        else -> RepeatMode.OFF
    }

    private companion object {
        const val TAG = "Melodown"
        const val TICK_MS = 500L

        /** До 3 секунд «назад» — это перезапуск трека, а не переход к предыдущему. */
        const val RESTART_THRESHOLD_MS = 3_000L
    }
}
