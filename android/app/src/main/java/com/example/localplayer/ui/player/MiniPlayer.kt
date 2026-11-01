package com.example.localplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.localplayer.media.PlayerState
import com.example.localplayer.ui.components.TrackArtwork
import com.example.localplayer.ui.components.formatDuration
import com.example.localplayer.ui.theme.SoundTubeTheme

/**
 * Полоска над нижней навигацией: что играет и пауза/следующий.
 *
 * Фон #0E100E, верхняя рамка 1.dp и тонкий зелёный прогресс 2.dp — как нижний
 * плеер десктопа. Появляется только когда в плеере есть трек, поэтому пустой
 * строкой место не занимает. Тап по строке открывает полноэкранный плеер,
 * а свайп вверх вытягивает его следом за пальцем.
 */
@Composable
fun MiniPlayer(
    state: PlayerState,
    positionMs: () -> Long,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onExpand: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragStop: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SoundTubeTheme.colors
    // Лямбды приходят заново на каждую рекомпозицию, а DraggableState запоминается
    // один раз — держим актуальные ссылки, иначе жест будет звать старые.
    val onDragState = rememberUpdatedState(onDrag)
    val onDragStopState = rememberUpdatedState(onDragStop)
    val dragState = remember { DraggableState { delta -> onDragState.value(delta) } }

    Column(
        modifier = modifier
            .fillMaxWidth()
            // Свайп вверх по полоске вытягивает полноэкранный плеер; тап при этом
            // остаётся тапом — draggable отдаёт жест только после touch slop.
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                onDragStopped = { velocity -> onDragStopState.value(velocity) },
            )
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
                .clickable(onClick = onExpand)
                .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TrackArtwork(
                artwork = state.artworkUri,
                modifier = Modifier.size(40.dp),
                cornerRadius = 4.dp,
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.title.ifBlank { "Без названия" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = state.artist.ifBlank { "Неизвестный исполнитель" },
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    MiniTime(positionMs = positionMs, durationMs = state.durationMs)
                }
            }

            // Круглая белая кнопка — единственный «светлый» элемент, как play-btn на ПК.
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colors.textPrimary)
                    .clickable(onClick = onPlayPause),
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
                        contentDescription = if (state.isPlaying) "Пауза" else "Играть",
                        tint = colors.background,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(enabled = state.hasNext, onClick = onNext),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "Следующий",
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
 * а не вся полоска с обложкой и кнопками.
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
        color = SoundTubeTheme.colors.textSecondary,
        maxLines = 1,
    )
}

private fun progressFraction(positionMs: Long, durationMs: Long): Float =
    if (durationMs > 0L) (positionMs / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
