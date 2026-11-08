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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.localplayer.R
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

/**
 * Текст песни в нижней шторке: те же строки и подсветка, что были в старом
 * инлайн-списке, но с автопрокруткой к активной строке.
 */
@Composable
fun PlayerLyricsSheetContent(
    state: LyricsUiState,
    activeLine: Int,
    onSeek: (Long) -> Unit,
    onRetry: () -> Unit,
) {
    val colors = SoundTubeTheme.colors
    val listState = rememberLazyListState()

    LaunchedEffect(activeLine, state) {
        if (activeLine < 0) return@LaunchedEffect
        val target = activeLine + 1
        if (target <= listState.layoutInfo.totalItemsCount) {
            listState.animateScrollToItem(target)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.lyrics),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRetry) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.action_retry),
                    tint = colors.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
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
