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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.localplayer.domain.model.RepeatMode
import com.example.localplayer.media.QueueItem
import com.example.localplayer.ui.components.EmptyState
import com.example.localplayer.ui.components.SoundTubeIconButton
import com.example.localplayer.ui.components.SoundTubeTopBar
import com.example.localplayer.ui.components.TrackArtwork
import com.example.localplayer.ui.components.formatDuration
import com.example.localplayer.ui.theme.SoundTubeTheme

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
    val colors = SoundTubeTheme.colors
    val state by playerViewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .navigationBarsPadding(),
    ) {
        SoundTubeTopBar(
            title = "Сейчас играет",
            navigationIcon = {
                SoundTubeIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Назад",
                    bordered = false,
                    onClick = onBack,
                )
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
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    TrackArtwork(
                        artwork = state.artworkUri,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                        cornerRadius = 8.dp,
                        iconSize = 56.dp,
                    )
                }

                item {
                    Column {
                        Text(
                            text = state.title.ifBlank { "Без названия" },
                            style = MaterialTheme.typography.headlineSmall,
                            color = colors.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = state.artist.ifBlank { "Неизвестный исполнитель" },
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary,
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
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = state.queue.size.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary,
                        )
                    }
                }

                itemsIndexed(
                    items = state.queue,
                    key = { index, item -> "$index:${item.trackId}" },
                ) { index, item ->
                    QueueRow(
                        item = item,
                        index = index,
                        isCurrent = index == state.currentIndex,
                        onClick = { playerViewModel.seekToIndex(index) },
                        showDivider = index < state.queue.lastIndex,
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
    val colors = SoundTubeTheme.colors
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
            colors = SliderDefaults.colors(
                thumbColor = colors.textPrimary,
                activeTrackColor = colors.primary,
                inactiveTrackColor = colors.border,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = shownPosition.formatDuration(),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
            Text(
                text = durationMs.formatDuration(),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
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
    val colors = SoundTubeTheme.colors
    val muted = colors.textSecondary

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        IconButton(onClick = onToggleShuffle) {
            Icon(
                imageVector = Icons.Rounded.Shuffle,
                contentDescription = if (shuffle) "Перемешивание включено" else "Перемешивание",
                tint = if (shuffle) colors.primary else muted,
                modifier = Modifier.size(20.dp),
            )
        }

        IconButton(onClick = onPrevious) {
            Icon(
                imageVector = Icons.Rounded.SkipPrevious,
                contentDescription = "Предыдущий",
                tint = muted,
                modifier = Modifier.size(28.dp),
            )
        }

        // Круглая белая кнопка — как play-btn в нижнем плеере десктопа.
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(colors.textPrimary)
                .clickable(onClick = onPlayPause),
            contentAlignment = Alignment.Center,
        ) {
            if (isBuffering) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.5.dp,
                    color = colors.background,
                )
            } else {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Пауза" else "Играть",
                    tint = colors.background,
                    modifier = Modifier.size(32.dp),
                )
            }
        }

        IconButton(onClick = onNext, enabled = hasNext) {
            Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = "Следующий",
                tint = if (hasNext) muted else muted.copy(alpha = 0.35f),
                modifier = Modifier.size(28.dp),
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
                tint = if (repeatMode == RepeatMode.OFF) muted else colors.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun QueueRow(
    item: QueueItem,
    index: Int,
    isCurrent: Boolean,
    onClick: () -> Unit,
    showDivider: Boolean,
) {
    val colors = SoundTubeTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isCurrent) colors.primary.copy(alpha = 0.07f) else Color.Transparent,
            )
            .drawBehind {
                if (!showDivider) return@drawBehind
                val hairline = 0.5.dp.toPx()
                val y = size.height - hairline / 2f
                drawLine(
                    color = colors.divider,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = hairline,
                )
            }
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = (index + 1).toString(),
            style = MaterialTheme.typography.labelSmall,
            color = if (isCurrent) colors.primary else colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(20.dp),
        )

        TrackArtwork(
            artwork = item.artworkUri,
            modifier = Modifier.size(40.dp),
            cornerRadius = 4.dp,
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title.ifBlank { "Без названия" },
                style = MaterialTheme.typography.bodyMedium,
                color = if (isCurrent) colors.primary else colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.artist.ifBlank { "Неизвестный исполнитель" },
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (isCurrent) {
            Icon(
                imageVector = Icons.Rounded.GraphicEq,
                contentDescription = "Играет",
                tint = colors.primary,
                modifier = Modifier.size(16.dp),
            )
        } else {
            Text(
                text = item.durationMs.formatDuration(),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                textAlign = TextAlign.End,
            )
        }
    }
}
