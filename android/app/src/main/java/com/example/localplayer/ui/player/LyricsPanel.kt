package com.example.localplayer.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.localplayer.R
import com.example.localplayer.domain.lyrics.LyricLine
import com.example.localplayer.domain.lyrics.Lyrics
import com.example.localplayer.ui.components.EmptyState
import com.example.localplayer.ui.components.SoundTubeBadge
import com.example.localplayer.ui.components.SoundTubeButton
import com.example.localplayer.ui.theme.SoundTubeTheme

/**
 * Текст песни в плеере: строки с таймкодами, подсветка активной строки и
 * перемотка по тапу — как в полноэкранном плеере десктопа.
 *
 * Строки добавляются в общий список плеера ([LazyListScope]), а не в свой
 * LazyColumn: так автопрокрутка листает экран целиком и не ломает жест
 * «свайп вниз — свернуть плеер».
 */
fun LazyListScope.lyricsItems(
    state: LyricsUiState,
    activeLine: Int,
    onSeek: (Long) -> Unit,
    onRetry: () -> Unit,
) {
    when (state) {
        is LyricsUiState.Idle,
        is LyricsUiState.Loading -> item(key = "lyrics-status") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.5.dp,
                )
            }
        }

        is LyricsUiState.Loaded -> {
            val lyrics = state.lyrics
            item(key = "lyrics-meta") {
                LyricsMeta(lyrics)
            }
            if (lyrics.synced) {
                itemsIndexed(
                    items = lyrics.lines,
                    key = { index, _ -> "lyric:$index" },
                ) { index, line ->
                    LyricLineRow(
                        line = line,
                        isActive = index == activeLine,
                        onClick = { onSeek(line.timeMs) },
                    )
                }
            } else {
                val plainLines = lyrics.plain.orEmpty().split('\n')
                itemsIndexed(
                    items = plainLines,
                    key = { index, _ -> "lyric:$index" },
                ) { _, text ->
                    PlainLyricRow(text)
                }
            }
        }

        is LyricsUiState.Instrumental -> item(key = "lyrics-status") {
            EmptyState(
                icon = Icons.Rounded.MusicOff,
                title = stringResource(R.string.lyrics_instrumental_title),
                message = stringResource(R.string.lyrics_instrumental_message),
            )
        }

        is LyricsUiState.NotFound -> item(key = "lyrics-status") {
            EmptyState(
                icon = Icons.Rounded.MusicNote,
                title = stringResource(R.string.lyrics_not_found_title),
                message = stringResource(R.string.lyrics_not_found_message),
                action = {
                    SoundTubeButton(
                        text = stringResource(R.string.action_retry),
                        onClick = onRetry,
                    )
                },
            )
        }

        is LyricsUiState.Error -> item(key = "lyrics-status") {
            EmptyState(
                icon = Icons.Rounded.CloudOff,
                title = stringResource(R.string.lyrics_error_title),
                message = stringResource(R.string.lyrics_error_message),
                action = {
                    SoundTubeButton(
                        text = stringResource(R.string.action_retry),
                        onClick = onRetry,
                    )
                },
            )
        }
    }
}

/** Строка источника и бейдж «без таймкодов», если текст несинхронный. */
@Composable
private fun LyricsMeta(lyrics: Lyrics) {
    val colors = SoundTubeTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!lyrics.synced) {
            SoundTubeBadge(text = stringResource(R.string.lyrics_unsynced))
            Spacer(modifier = Modifier.size(8.dp))
        }
        val source = when (lyrics.source) {
            Lyrics.SOURCE_LRC -> stringResource(R.string.lyrics_source_lrc)
            Lyrics.SOURCE_LRCLIB -> "LRCLIB"
            Lyrics.SOURCE_OVH -> "lyrics.ovh"
            else -> null
        }
        if (source != null) {
            Text(
                text = source,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LyricLineRow(
    line: LyricLine,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val colors = SoundTubeTheme.colors
    if (line.text.isEmpty()) {
        // Пустая строка между куплетами — держим ритм текста.
        Box(modifier = Modifier.height(12.dp))
        return
    }
    Text(
        text = line.text,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
        color = if (isActive) colors.textPrimary else colors.textSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    )
}

@Composable
private fun PlainLyricRow(text: String) {
    val colors = SoundTubeTheme.colors
    if (text.isEmpty()) {
        Box(modifier = Modifier.height(12.dp))
        return
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = colors.textPrimary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    )
}
