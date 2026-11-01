package com.example.localplayer.ui.screens.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.localplayer.R
import com.example.localplayer.domain.model.Playlist
import com.example.localplayer.ui.components.EmptyState
import com.example.localplayer.ui.components.SoundTubeButton
import com.example.localplayer.ui.components.SoundTubeIconButton
import com.example.localplayer.ui.components.SoundTubeTopBar
import com.example.localplayer.ui.components.TrackRow
import com.example.localplayer.ui.player.PlayerViewModel
import com.example.localplayer.ui.theme.SoundTubeTheme

/** Треки внутри плейлиста. Играем только то, что уже лежит на диске. */
@Composable
fun PlaylistDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
    val colors = SoundTubeTheme.colors
    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val player by playerViewModel.state.collectAsStateWithLifecycle()

    val playableIds = entries.filter { it.track.isDownloaded }.map { it.track.id }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        SoundTubeTopBar(
            title = stringResource(R.string.playlist_title),
            navigationIcon = {
                SoundTubeIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    bordered = false,
                    onClick = onBack,
                )
            },
        )

        val current = playlist
        if (current == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.primary)
            }
        } else {
            PlaylistHeader(
                playlist = current,
                onPlay = { playerViewModel.play(playableIds) },
                playEnabled = playableIds.isNotEmpty(),
            )

            if (entries.isEmpty()) {
                EmptyState(
                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                    title = stringResource(R.string.playlist_empty_title),
                    message = stringResource(R.string.playlist_empty_message),
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    itemsIndexed(entries, key = { _, entry -> entry.track.id }) { index, entry ->
                        val track = entry.track
                        val playIndex = playableIds.indexOf(track.id)
                        TrackRow(
                            track = track,
                            index = index,
                            isCurrent = track.id == player.currentTrackId,
                            isPlaying = player.isPlaying,
                            enabled = playIndex >= 0,
                            onClick = { playerViewModel.play(playableIds, playIndex) },
                            showDivider = index < entries.lastIndex,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistHeader(
    playlist: Playlist,
    onPlay: () -> Unit,
    playEnabled: Boolean,
) {
    val colors = SoundTubeTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PlaylistTile(
            playlist = playlist,
            modifier = Modifier.size(76.dp),
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (playlist.trackCount == 0) {
                    stringResource(R.string.playlist_empty)
                } else {
                    pluralStringResource(
                        R.plurals.tracks_count,
                        playlist.trackCount,
                        playlist.trackCount,
                    )
                },
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
            )
            Spacer(Modifier.size(10.dp))
            SoundTubeButton(
                text = stringResource(R.string.action_play_all),
                icon = Icons.Rounded.PlayArrow,
                enabled = playEnabled,
                onClick = onPlay,
            )
        }
    }
}
