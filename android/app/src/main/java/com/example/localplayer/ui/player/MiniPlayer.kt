package com.example.localplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.localplayer.R
import com.example.localplayer.media.PlayerState
import com.example.localplayer.ui.components.formatDuration
import com.example.localplayer.ui.theme.SoundTubeTheme

/**
 * Содержимое свёрнутой плашки плеера: прогресс, название, тайминги и кнопки.
 *
 * Обложка сюда не входит: это отдельный морфящийся слой
 * ([ExpandablePlayerHost]), который переезжает из плашки в центр полноэкранного плеера.
 * Поэтому слева оставлено ровно 48.dp — место под неё.
 *
 * Свайп вверх по плашке тянет общий якорный стейт ([ExpandablePlayerState]):
 * `anchoredDraggable` добавляется к [modifier] снаружи, в [ExpandablePlayerHost],
 * поэтому скролл списков под плеером жест не перехватывает.
 */
@Composable
fun MiniPlayerContent(
    state: PlayerState,
    positionMs: () -> Long,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onExpand: () -> Unit,
    interactive: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = SoundTubeTheme.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(MiniPlayerHeight)
            .background(colors.playerSurface)
            .drawBehind {
                drawLine(
                    color = colors.border,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                )
            },
    ) {
        // Тонкий прогресс — он же показывает, сколько осталось до конца трека.
        MiniProgressBar(positionMs = positionMs, durationMs = state.durationMs)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        enabled = interactive,
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.player_open),
                        onClick = onExpand,
                    )
                    .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Обложка рисуется отдельным слоем в ExpandablePlayerHost.
                Spacer(modifier = Modifier.size(48.dp))

                Column(modifier = Modifier.weight(1f)) {
                    val title = state.title.ifBlank { stringResource(R.string.untitled) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowUp,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = state.artist.ifBlank { stringResource(R.string.unknown_artist) },
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        MiniTime(positionMs = positionMs, durationMs = state.durationMs)
                    }
                }
            }

            // Круглая светлая кнопка — единственный «яркий» элемент плашки.
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(enabled = interactive, role = Role.Button, onClick = onPlayPause),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier.size(36.dp).background(colors.textPrimary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = colors.background,
                        )
                    } else {
                        Icon(
                            imageVector = if (state.isPlaying) {
                                Icons.Rounded.Pause
                            } else {
                                Icons.Rounded.PlayArrow
                            },
                            contentDescription = if (state.isPlaying) {
                                stringResource(R.string.cd_pause)
                            } else {
                                stringResource(R.string.cd_play)
                            },
                            tint = colors.background,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(
                        enabled = interactive && state.hasNext,
                        role = Role.Button,
                        onClick = onNext,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = stringResource(R.string.cd_next),
                    tint = if (state.hasNext) {
                        colors.textSecondary
                    } else {
                        colors.textSecondary.copy(alpha = 0.35f)
                    },
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * Прогресс мини-плеера и тайминги вынесены в отдельные композаблы:
 * позиция тикает дважды в секунду, и перерисовываться должны только они,
 * а не вся плашка с обложкой и кнопками.
 */
@Composable
private fun MiniProgressBar(positionMs: () -> Long, durationMs: Long) {
    val colors = SoundTubeTheme.colors

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(colors.border),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progressFraction(positionMs(), durationMs))
                .fillMaxHeight()
                .background(colors.primary),
        )
    }
}

@Composable
private fun MiniTime(positionMs: () -> Long, durationMs: Long) {
    Text(
        text = "${positionMs().formatDuration()} / ${durationMs.formatDuration()}",
        style = MaterialTheme.typography.labelSmall,
        color = SoundTubeTheme.colors.textPrimary,
        maxLines = 1,
    )
}

private fun progressFraction(positionMs: Long, durationMs: Long): Float =
    if (durationMs > 0L) (positionMs / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
