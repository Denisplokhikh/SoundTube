package com.example.localplayer.ui.screens.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Settings
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
import com.example.localplayer.ui.components.SoundTubeCard
import com.example.localplayer.ui.components.SoundTubeIconButton
import com.example.localplayer.ui.components.SoundTubeTopBar
import com.example.localplayer.ui.theme.SoundTubeTheme

/** Вкладка «Плейлисты»: системные площадки и пользовательские списки. */
@Composable
fun PlaylistsScreen(
    onOpenPlaylist: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistsViewModel = hiltViewModel(),
) {
    val colors = SoundTubeTheme.colors
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val totalTracks = playlists.sumOf { it.trackCount }
    val playlistsLabel = pluralStringResource(
        R.plurals.playlists_count,
        playlists.size,
        playlists.size,
    )
    val tracksLabel = pluralStringResource(R.plurals.tracks_count, totalTracks, totalTracks)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        SoundTubeTopBar(
            title = stringResource(R.string.playlists_title),
            subtitle = if (totalTracks > 0) "$playlistsLabel · $tracksLabel" else playlistsLabel,
            actions = {
                SoundTubeIconButton(
                    icon = Icons.Rounded.Settings,
                    contentDescription = stringResource(R.string.cd_settings),
                    bordered = false,
                    onClick = onOpenSettings,
                )
            },
        )

        if (playlists.isEmpty()) {
            EmptyState(
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                title = stringResource(R.string.playlists_empty_title),
                message = stringResource(R.string.playlists_empty_message),
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(playlists, key = { it.id }) { playlist ->
                    PlaylistCard(
                        playlist = playlist,
                        onClick = { onOpenPlaylist(playlist.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit,
) {
    val colors = SoundTubeTheme.colors

    SoundTubeCard(
        onClick = onClick,
        contentPadding = PaddingValues(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PlaylistTile(
                playlist = playlist,
                modifier = Modifier.size(44.dp),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                    maxLines = 1,
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
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
            }

            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
