package com.example.localplayer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.localplayer.R
import com.example.localplayer.domain.model.Track
import com.example.localplayer.ui.theme.SoundTubeTheme

/**
 * Компактная строка трека: номер, квадратная обложка 40.dp, название,
 * исполнитель и длительность. Никаких карточек — только тонкий разделитель
 * 0.5.dp, как в таблице десктопного плеера.
 *
 * @param index позиция в текущем списке (с нуля) — слева печатается `index + 1`.
 * @param onAddToPlaylist действие «в плейлист» в меню из трёх точек; null — пункт скрыт.
 * @param onDelete действие «удалить» в меню из трёх точек; null — пункт скрыт.
 */
@Composable
fun TrackRow(
    track: Track,
    index: Int,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    subtitle: String? = null,
    showDivider: Boolean = true,
    onAddToPlaylist: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val colors = SoundTubeTheme.colors
    val titleColor = when {
        !enabled -> colors.textSecondary
        isCurrent -> colors.primary
        else -> colors.textPrimary
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (isCurrent) colors.primary.copy(alpha = 0.07f) else Color.Transparent,
            )
            .drawBehind {
                if (isCurrent) {
                    drawRect(
                        color = colors.primary,
                        size = Size(2.dp.toPx(), size.height),
                    )
                }
                if (showDivider) {
                    val hairline = 0.5.dp.toPx()
                    val y = size.height - hairline / 2f
                    drawLine(
                        color = colors.divider,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = hairline,
                    )
                }
            }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = 12.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier.width(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (isCurrent && isPlaying) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = stringResource(R.string.cd_playing),
                    tint = colors.primary,
                    modifier = Modifier.size(14.dp),
                )
            } else {
                Text(
                    text = (index + 1).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isCurrent) colors.primary else colors.textSecondary,
                    textAlign = TextAlign.End,
                )
            }
        }

        TrackArtwork(
            artwork = track.thumbnailPath,
            modifier = Modifier.size(40.dp),
            cornerRadius = 4.dp,
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle ?: track.artist,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (!enabled) {
            SoundTubeBadge(stringResource(R.string.badge_not_downloaded))
        } else {
            Text(
                text = track.durationMs.formatDuration(),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
        }

        if (onAddToPlaylist != null || onDelete != null) {
            TrackOverflowMenu(
                onAddToPlaylist = onAddToPlaylist,
                onDelete = onDelete,
            )
        }
    }
}

/**
 * Три точки справа в строке трека. Открывают плоское меню в тон карточкам:
 * «В плейлист» и «Удалить» (красным — действие необратимое).
 */
@Composable
private fun TrackOverflowMenu(
    onAddToPlaylist: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    val colors = SoundTubeTheme.colors
    var expanded by remember { mutableStateOf(false) }

    Box {
        SoundTubeIconButton(
            icon = Icons.Rounded.MoreVert,
            contentDescription = stringResource(R.string.cd_track_actions),
            bordered = false,
            size = 30.dp,
            onClick = { expanded = true },
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(8.dp),
            containerColor = colors.surface,
            border = BorderStroke(1.dp, colors.border),
        ) {
            if (onAddToPlaylist != null) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.action_add_to_playlist),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textPrimary,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.PlaylistAdd,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    onClick = {
                        expanded = false
                        onAddToPlaylist()
                    },
                )
            }

            if (onDelete != null) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.action_delete),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.error,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            tint = colors.error,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    onClick = {
                        expanded = false
                        onDelete()
                    },
                )
            }
        }
    }
}
