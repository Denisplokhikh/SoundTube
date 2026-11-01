package com.example.localplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.localplayer.R
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
 * Экран живёт поверх приложения листом ([PlayerSheetState]): выезжает из
 * мини-плеера, а свайпом вниз убирается обратно. Тянуть можно за шапку или за
 * список, когда он уже прокручен в самое начало, — обложка первая в списке,
 * поэтому на открытом плеере она и служит ручкой. Обычная прокрутка очереди
 * при этом не ломается: вверх список листается как всегда.
 */
@Composable
fun NowPlayingScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onDrag: (Float) -> Unit = {},
    onDragStop: (Float) -> Unit = {},
    sheetProgress: () -> Float = { 1f },
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
    val colors = SoundTubeTheme.colors
    val state by playerViewModel.state.collectAsStateWithLifecycle()
    // Читается только внутри SeekBlock, поэтому тикающая позиция не перерисовывает экран.
    val positionMs = playerViewModel.positionMs.collectAsStateWithLifecycle()

    val onDragState = rememberUpdatedState(onDrag)
    val onDragStopState = rememberUpdatedState(onDragStop)
    val sheetProgressState = rememberUpdatedState(sheetProgress)
    val dragState = remember { DraggableState { delta -> onDragState.value(delta) } }

    // Свайп вниз по обложке (и по любому месту списка у его начала) утягивает лист.
    // Реагируем на остаток скролла после списка: пока очередь листается, остатка
    // нет, а как только она упёрлась в начало — весь жест уходит листу.
    val swipeToCollapse = remember {
        object : NestedScrollConnection {
            /** Тянули ли в этом жесте сам лист — по отпусканию решаем, куда доехать. */
            private var dragged = false

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                // Лист уже приспущен: сначала поднимаем его обратно, а не листаем.
                if (available.y >= 0f || sheetProgressState.value() >= 1f) return Offset.Zero
                dragged = true
                onDragState.value(available.y)
                return Offset(0f, available.y)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                // available.y > 0 — список уже в начале, палец тянет дальше вниз.
                if (available.y <= 0f) return Offset.Zero
                dragged = true
                onDragState.value(available.y)
                return Offset(0f, available.y)
            }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity {
                if (dragged) {
                    dragged = false
                    onDragStopState.value(available.y)
                }
                return Velocity.Zero
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            // Лист лежит поверх приложения, но фон сам по себе тапы не ловит:
            // без этого они проваливались бы на список под ним.
            .pointerInput(Unit) { detectTapGestures(onTap = {}) }
            .navigationBarsPadding(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .draggable(
                    state = dragState,
                    orientation = Orientation.Vertical,
                    onDragStopped = { velocity -> onDragStopState.value(velocity) },
                ),
        ) {
            SoundTubeTopBar(
                title = stringResource(R.string.now_playing),
                navigationIcon = {
                    SoundTubeIconButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        bordered = false,
                        onClick = onBack,
                    )
                },
            )
        }

        if (!state.hasTrack) {
            EmptyState(
                icon = Icons.Rounded.MusicNote,
                title = stringResource(R.string.nothing_playing_title),
                message = stringResource(R.string.nothing_playing_message),
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .nestedScroll(swipeToCollapse),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    // 84% ширины контента, чтобы обложка не упиралась в края экрана.
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        TrackArtwork(
                            artwork = state.artworkUri,
                            modifier = Modifier
                                .fillMaxWidth(0.84f)
                                .aspectRatio(1f),
                            cornerRadius = 8.dp,
                            iconSize = 48.dp,
                        )
                    }
                }

                item {
                    Column {
                        Text(
                            text = state.title.ifBlank { stringResource(R.string.untitled) },
                            style = MaterialTheme.typography.headlineSmall,
                            color = colors.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = state.artist.ifBlank {
                                stringResource(R.string.unknown_artist)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                item {
                    SeekBlock(
                        positionMs = { positionMs.value },
                        durationMs = state.durationMs,
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
                            text = stringResource(R.string.queue),
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
    positionMs: () -> Long,
    durationMs: Long,
    trackId: String?,
    onSeek: (Long) -> Unit,
) {
    val colors = SoundTubeTheme.colors
    // Пока палец на слайдере, показываем его позицию, а не тикающую из плеера.
    var dragged by remember(trackId) { mutableStateOf<Float?>(null) }
    val position = positionMs()
    val progress = if (durationMs > 0L) {
        (position / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val value = dragged ?: progress
    val shownPosition = dragged?.let { (it * durationMs).toLong() } ?: position

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
                contentDescription = if (shuffle) {
                    stringResource(R.string.cd_shuffle_on)
                } else {
                    stringResource(R.string.cd_shuffle)
                },
                tint = if (shuffle) colors.primary else muted,
                modifier = Modifier.size(20.dp),
            )
        }

        IconButton(onClick = onPrevious) {
            Icon(
                imageVector = Icons.Rounded.SkipPrevious,
                contentDescription = stringResource(R.string.cd_previous),
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
                    contentDescription = if (isPlaying) {
                        stringResource(R.string.cd_pause)
                    } else {
                        stringResource(R.string.cd_play)
                    },
                    tint = colors.background,
                    modifier = Modifier.size(32.dp),
                )
            }
        }

        IconButton(onClick = onNext, enabled = hasNext) {
            Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = stringResource(R.string.cd_next),
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
                    RepeatMode.OFF -> stringResource(R.string.cd_repeat_off)
                    RepeatMode.ALL -> stringResource(R.string.cd_repeat_all)
                    RepeatMode.ONE -> stringResource(R.string.cd_repeat_one)
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
                text = item.title.ifBlank { stringResource(R.string.untitled) },
                style = MaterialTheme.typography.bodyMedium,
                color = if (isCurrent) colors.primary else colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.artist.ifBlank { stringResource(R.string.unknown_artist) },
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (isCurrent) {
            Icon(
                imageVector = Icons.Rounded.GraphicEq,
                contentDescription = stringResource(R.string.cd_playing),
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
