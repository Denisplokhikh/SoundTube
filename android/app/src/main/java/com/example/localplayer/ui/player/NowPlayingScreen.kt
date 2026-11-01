package com.example.localplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.localplayer.domain.model.RepeatMode
import com.example.localplayer.media.QueueItem
import com.example.localplayer.ui.components.EmptyState
import com.example.localplayer.ui.components.SoundTubeTopBar
import com.example.localplayer.ui.components.TrackArtwork
import com.example.localplayer.ui.components.formatDuration

/**
 * Полноэкранный плеер: обложка, перемотка, управление и очередь.
 *
 * Нижняя навигация на этом экране скрыта — мини-плеер тут не нужен.
 */
@Composable
fun NowPlayingScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by playerViewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding(),
    ) {
        SoundTubeTopBar(
            title = "Сейчас играет",
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад")
                }
            },
        )

        if (!state.hasTrack) {
            EmptyState(
                icon = Icons.Rounded.MusicNote,
                title = "Ничего не играет",
                message = "Выберите трек в библиотеке или плейлисте.",
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        TrackArtwork(
                            artwork = state.artworkUri,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f),
                            cornerRadius = 24.dp,
                            iconSize = 64.dp,
                        )
                    }
                }

                item {
                    Column {
                        Text(
                            text = state.title.ifBlank { "Без названия" },
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = state.artist.ifBlank { "Неизвестный исполнитель" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                item {
                    SeekBlock(
                        positionMs = state.positionMs,
                        durationMs = state.durationMs,
                        progress = state.progress,
                        trackId = state.currentTrackId,
                        onSeek = playerViewModel::seekTo,
                    )
                }

                item {
                    PlaybackControls(
                        isPlaying = state.isPlaying,
                        isBuffering = state.isBuffering,
                        shuffle = state.shuffle,
                        repeatMode = state.repeatMode,
                        hasNext = state.hasNext,
                        onPlayPause = playerViewModel::playPause,
                        onPrevious = playerViewModel::previous,
                        onNext = playerViewModel::next,
                        onToggleShuffle = playerViewModel::toggleShuffle,
                        onCycleRepeat = playerViewModel::cycleRepeat,
                    )
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Очередь",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = state.queue.size.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                itemsIndexed(
                    items = state.queue,
                    key = { index, item -> "$index:${item.trackId}" },
                ) { index, item ->
                    QueueRow(
                        item = item,
                        isCurrent = index == state.currentIndex,
                        onClick = { playerViewModel.seekToIndex(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SeekBlock(
    positionMs: Long,
    durationMs: Long,
    progress: Float,
    trackId: String?,
    onSeek: (Long) -> Unit,
) {
    // Пока палец на слайдере, показываем его позицию, а не тикающую из плеера.
    var dragged by remember(trackId) { mutableStateOf<Float?>(null) }
    val value = dragged ?: progress
    val shownPosition = dragged?.let { (it * durationMs).toLong() } ?: positionMs

    Column {
        Slider(
            value = value.coerceIn(0f, 1f),
            onValueChange = { if (durationMs > 0L) dragged = it },
            onValueChangeFinished = {
                dragged?.let { onSeek((it * durationMs).toLong()) }
                dragged = null
            },
            enabled = durationMs > 0L,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = shownPosition.formatDuration(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = durationMs.formatDuration(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlaybackControls(
    isPlaying: Boolean,
    isBuffering: Boolean,
    shuffle: Boolean,
    repeatMode: RepeatMode,
    hasNext: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        IconButton(onClick = onToggleShuffle) {
            Icon(
                imageVector = Icons.Rounded.Shuffle,
                contentDescription = if (shuffle) "Перемешивание включено" else "Перемешивание",
                tint = if (shuffle) MaterialTheme.colorScheme.primary else muted,
            )
        }

        FilledTonalIconButton(
            onClick = onPrevious,
            modifier = Modifier.size(56.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipPrevious,
                contentDescription = "Предыдущий",
                modifier = Modifier.size(30.dp),
            )
        }

        FilledIconButton(
            onClick = onPlayPause,
            modifier = Modifier.size(72.dp),
        ) {
            if (isBuffering) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Пауза" else "Играть",
                    modifier = Modifier.size(38.dp),
                )
            }
        }

        FilledTonalIconButton(
            onClick = onNext,
            enabled = hasNext,
            modifier = Modifier.size(56.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = "Следующий",
                modifier = Modifier.size(30.dp),
            )
        }

        IconButton(onClick = onCycleRepeat) {
            Icon(
                imageVector = if (repeatMode == RepeatMode.ONE) {
                    Icons.Rounded.RepeatOne
                } else {
                    Icons.Rounded.Repeat
                },
                contentDescription = when (repeatMode) {
                    RepeatMode.OFF -> "Повтор выключен"
                    RepeatMode.ALL -> "Повтор очереди"
                    RepeatMode.ONE -> "Повтор трека"
                },
                tint = if (repeatMode == RepeatMode.OFF) muted else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun QueueRow(
    item: QueueItem,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isCurrent) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                } else {
                    Color.Transparent
                },
            )
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TrackArtwork(
            artwork = item.artworkUri,
            modifier = Modifier.size(40.dp),
            cornerRadius = 8.dp,
            iconSize = 18.dp,
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title.ifBlank { "Без названия" },
                style = MaterialTheme.typography.bodyMedium,
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.artist.ifBlank { "Неизвестный исполнитель" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (isCurrent) {
            Icon(
                imageVector = Icons.Rounded.GraphicEq,
                contentDescription = "Играет",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        } else {
            Text(
                text = item.durationMs.formatDuration(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
            )
        }
    }
}
