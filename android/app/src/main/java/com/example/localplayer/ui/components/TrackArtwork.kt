package com.example.localplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import coil.compose.AsyncImage
import java.io.File

/**
 * Обложка трека с заглушкой.
 *
 * yt-dlp отдаёт ссылку на картинку, но старые сборки могли сохранить путь к файлу,
 * поэтому поддерживаем и то, и другое. Битый/отсутствующий файл — просто заглушка.
 */
@Composable
fun TrackArtwork(
    artwork: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 10.dp,
    iconSize: Dp = 22.dp,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val model = remember(artwork) { artworkModel(artwork) }

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (model == null) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(iconSize),
            )
        } else {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

private fun artworkModel(raw: String?): Any? {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return null
    return when {
        value.startsWith("http://") || value.startsWith("https://") -> value
        value.startsWith("content://") || value.startsWith("file://") -> value
        File(value).isFile -> File(value)
        else -> null
    }
}
