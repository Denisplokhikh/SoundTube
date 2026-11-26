package com.example.localplayer.ui.screens.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import android.widget.Toast
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.localplayer.R
import com.example.localplayer.domain.model.Playlist
import com.example.localplayer.ui.components.EmptyState
import com.example.localplayer.ui.components.PlaylistNameField
import com.example.localplayer.ui.components.SectionLabel
import com.example.localplayer.ui.components.SoundTubeButton
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
    val creation by viewModel.creation.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showCreate by rememberSaveable { mutableStateOf(false) }
    val (sources, personal) = playlists.partition { it.isSystem }
    val totalTracks = personal.sumOf { it.trackCount }
    val playlistsLabel = pluralStringResource(
        R.plurals.playlists_count,
        personal.size,
        personal.size,
    )
    val tracksLabel = pluralStringResource(R.plurals.tracks_count, totalTracks, totalTracks)

    LaunchedEffect(creation) {
        if (creation == PlaylistCreation.Created) {
            showCreate = false
            Toast.makeText(context, R.string.playlist_created, Toast.LENGTH_SHORT).show()
            viewModel.clearCreation()
        }
    }

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
                    icon = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.action_new_playlist),
                    size = 48.dp,
                    bordered = false,
                    onClick = { showCreate = true },
                )
                SoundTubeIconButton(
                    icon = Icons.Rounded.Settings,
                    contentDescription = stringResource(R.string.cd_settings),
                    bordered = false,
                    size = 48.dp,
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
                action = {
                    SoundTubeButton(
                        text = stringResource(R.string.action_new_playlist),
                        minHeight = 48.dp,
                        onClick = { showCreate = true },
                    )
                },
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (personal.isNotEmpty()) {
                    item { SectionLabel(stringResource(R.string.playlists_personal)) }
                    items(personal, key = { it.id }) { playlist ->
                        PlaylistCard(playlist = playlist, onClick = { onOpenPlaylist(playlist.id) })
                    }
                }
                if (sources.isNotEmpty()) {
                    item { SectionLabel(stringResource(R.string.playlists_sources)) }
                    items(sources, key = { it.id }) { playlist ->
                        PlaylistCard(playlist = playlist, onClick = { onOpenPlaylist(playlist.id) })
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreatePlaylistDialog(
            creation = creation,
            onDismiss = {
                showCreate = false
                viewModel.clearCreation()
            },
            onEdit = viewModel::clearCreation,
            onConfirm = viewModel::createPlaylist,
        )
    }
}

@Composable
private fun CreatePlaylistDialog(
    creation: PlaylistCreation,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val colors = SoundTubeTheme.colors
    val saving = creation == PlaylistCreation.Saving || creation == PlaylistCreation.Created
    var name by rememberSaveable { mutableStateOf("") }
    val confirm = { if (!saving && name.isNotBlank()) onConfirm(name) }

    Dialog(onDismissRequest = { if (!saving) onDismiss() }) {
        SoundTubeCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.action_new_playlist),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            PlaylistNameField(
                name = name,
                onNameChange = {
                    name = it
                    onEdit()
                },
                onConfirm = confirm,
                enabled = !saving,
            )
            if (creation == PlaylistCreation.Failed) {
                Text(
                    text = stringResource(R.string.playlist_create_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.error,
                )
            }
            SoundTubeButton(
                text = stringResource(R.string.action_create),
                enabled = !saving && name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                minHeight = 48.dp,
                onClick = confirm,
            )
            SoundTubeButton(
                text = stringResource(R.string.action_cancel),
                enabled = !saving,
                primary = false,
                modifier = Modifier.fillMaxWidth(),
                minHeight = 48.dp,
                onClick = onDismiss,
            )
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
