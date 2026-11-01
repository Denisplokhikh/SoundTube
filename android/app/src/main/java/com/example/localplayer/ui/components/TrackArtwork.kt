package com.example.localplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.localplayer.ui.theme.SoundTubeTheme
import java.io.File

/**
 * Квадратная обложка с тонкой рамкой — как карточки десктопа.
 *
 * yt-dlp отдаёт ссылку на картинку, но старые сборки могли сохранить путь к файлу,
 * поэтому поддерживаем и то, и другое. Битый/отсутствующий файл — просто заглушка.
 */
@Composable
fun TrackArtwork(
    artwork: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 4.dp,
    iconSize: Dp = 18.dp,
) {
    val colors = SoundTubeTheme.colors
    val shape = RoundedCornerShape(cornerRadius)
    val model = remember(artwork) { artworkModel(artwork) }

    Box(
        modifier = modifier
            .clip(shape)
            .background(colors.surfaceRaised)
            .border(1.dp, colors.border, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (model == null) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = colors.textSecondary,
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
