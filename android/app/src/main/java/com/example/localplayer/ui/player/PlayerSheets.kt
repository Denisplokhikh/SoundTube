package com.example.localplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.localplayer.R
import com.example.localplayer.media.PlayerState
import com.example.localplayer.media.QueueItem
import com.example.localplayer.ui.components.TrackArtwork
import com.example.localplayer.ui.components.formatDuration
import com.example.localplayer.ui.theme.SoundTubeTheme

/** Очередь воспроизведения в нижней шторке: текущий трек и всё, что дальше. */
@Composable
fun PlayerQueueSheetContent(
    queue: List<QueueItem>,
    currentIndex: Int,
    onPick: (Int) -> Unit,
) {
    val colors = SoundTubeTheme.colors

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.queue),
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )

        if (queue.isEmpty()) {
            Text(
                text = stringResource(R.string.queue_empty_title),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
            contentPadding = PaddingValues(bottom = 28.dp),
        ) {
            itemsIndexed(queue, key = { index, item -> "$index:${item.trackId}" }) { index, item ->
                val isCurrent = index == currentIndex

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(index) }
                        .background(
                            if (isCurrent) {
                                colors.primary.copy(alpha = 0.10f)
                            } else {
                                Color.Transparent
                            },
                        )
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
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
                            text = item.artist.ifBlank {
                                stringResource(R.string.unknown_artist)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Text(
                        text = item.durationMs.formatDuration(),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
fun PlayerLyricsSheetContent(
    player: PlayerState,
    state: LyricsUiState,
    activeLine: Int,
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SoundTubeTheme.colors
    val trackId = player.currentTrackId
    val listState = key(trackId) { rememberLazyListState() }
    var following by remember(trackId) { mutableStateOf(true) }
    var viewportHeight by remember { mutableIntStateOf(0) }
    val hasSyncedLyrics = (state as? LyricsUiState.Loaded)?.lyrics?.synced == true
    val scrollConnection = remember(trackId, hasSyncedLyrics) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (hasSyncedLyrics && source == NestedScrollSource.UserInput && available.y != 0f) {
                    following = false
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(trackId, activeLine, following, viewportHeight, hasSyncedLyrics) {
        if (hasSyncedLyrics && activeLine >= 0 && following && viewportHeight > 0) {
            // The metadata uses the first list slot.
            listState.animateScrollToItem(activeLine + 1, scrollOffset = -viewportHeight / 3)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth().navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrackArtwork(
                artwork = player.artworkUri,
                modifier = Modifier.size(40.dp),
                cornerRadius = 4.dp,
            )
            Text(
                text = player.title.ifBlank { stringResource(R.string.untitled) },
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            )
            IconButton(
                onClick = onPlayPause,
                enabled = trackId != null,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = if (player.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(
                        if (player.isPlaying) R.string.cd_pause else R.string.cd_play,
                    ),
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.action_close),
                )
            }
        }

        Text(
            text = stringResource(R.string.lyrics),
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        if (hasSyncedLyrics && !following && activeLine >= 0) {
            TextButton(
                onClick = { following = true },
                modifier = Modifier.align(Alignment.End).padding(end = 8.dp).heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.lyrics_follow))
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .onSizeChanged { viewportHeight = it.height }
                .nestedScroll(scrollConnection),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
        ) {
            lyricsItems(
                state = state,
                activeLine = activeLine,
                onSeek = onSeek,
                onRetry = onRetry,
            )
        }
    }
}
