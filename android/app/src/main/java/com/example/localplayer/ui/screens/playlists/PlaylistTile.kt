package com.example.localplayer.ui.screens.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.localplayer.domain.model.Playlist
import com.example.localplayer.domain.util.SystemPlaylists

/**
 * Квадратная «обложка» плейлиста. Настоящих картинок у плейлистов нет,
 * поэтому площадка узнаётся по иконке, а свой плейлист — по значку очереди.
 */
@Composable
fun PlaylistTile(
    playlist: Playlist,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = playlistIcon(playlist.id),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxSize(0.42f),
        )
    }
}

private fun playlistIcon(playlistId: String): ImageVector = when (playlistId) {
    SystemPlaylists.YOUTUBE_ID -> Icons.Rounded.PlayCircle
    SystemPlaylists.SOUNDCLOUD_ID -> Icons.Rounded.Cloud
    else -> Icons.AutoMirrored.Rounded.QueueMusic
}
