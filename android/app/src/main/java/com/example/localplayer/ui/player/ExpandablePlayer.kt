package com.example.localplayer.ui.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.localplayer.ui.components.TrackArtwork
import kotlin.math.roundToInt

/** До этой доли прогресса мини-плеер полностью растворяется. */
private const val MINI_FADE_END = 0.25f

/** С этой доли прогресса начинает проявляться полноэкранный интерфейс. */
private const val FULL_FADE_START = 0.20f

/** Размер обложки в свёрнутой плашке и её отступы. */
private val MiniCoverSize = 48.dp
private val MiniCoverStart = 12.dp
private val MiniCoverTop = 8.dp

/**
 * Единый контейнер плеера над нижней навигацией.
 *
 * Весь экран — это один слой, который едет вверх-вниз на `AnchoredDraggableState.offset`.
 * Смещение читается в графическом слое ([graphicsLayer]) и не вызывает рекомпозицию,
 * поэтому контейнер следует за пальцем 1:1 без пересборки дерева на каждом кадре.
 *
 * Внутри слоя:
 *  * полноэкранный плеер ([NowPlayingScreen]) — проявляется на 0.2..1;
 *  * плашка мини-плеера ([MiniPlayerContent]) — растворяется на 0..0.25;
 *  * одна обложка на оба состояния: маленький квадрат 48.dp слева превращается в
 *    большую обложку по центру через линейную интерполяцию смещения и масштаба.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExpandablePlayerHost(
    playerState: ExpandablePlayerState,
    playerViewModel: PlayerViewModel,
    positionMs: () -> Long,
    modifier: Modifier = Modifier,
) {
    val player by playerViewModel.state.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val miniCoverSizePx = with(density) { MiniCoverSize.toPx() }
    val miniCoverLeftPx = with(density) { MiniCoverStart.toPx() }
    val miniCoverTopPx = with(density) { MiniCoverTop.toPx() }

    // Прогресс читается только в фазах layout/draw — ни один из этих лямбда-вызовов
    // не регистрирует чтение состояния в композиции.
    val progress: () -> Float = remember(playerState) { { playerState.expandProgress() } }

    // Полноэкранный интерфейс существует только когда лист приоткрыт: в полностью
    // свёрнутом состоянии он не должен ни рисоваться, ни ловить касания.
    // derivedStateOf перерисовывает композицию только в момент смены флага.
    val fullContentVisible by remember(playerState) {
        derivedStateOf { playerState.isExpanded || playerState.expandProgress() > 0f }
    }
    // Пока плашка видна, её кнопки кликабельны; после 0.25 — уже нет.
    val miniInteractive by remember(playerState) {
        derivedStateOf { playerState.expandProgress() < MINI_FADE_END }
    }

    var contentCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var coverBounds by remember { mutableStateOf<Rect?>(null) }

    // Важно: anchoredDraggable висит НЕ на этом полноэкранном Box, а на видимых
    // плашках (мини и полноэкранной) ниже. Иначе полноэкранный модификатор жеста
    // перехватывал бы вертикальные свайпы по всему экрану и гасил скролл списков
    // библиотеки под плеером. Здесь только визуальное смещение слоя.
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { translationY = playerState.offsetPx() },
    ) {
        if (fullContentVisible) {
            // Обёртка нужна для измерения слота обложки: и обёртка, и слот лежат
            // внутри одного графического слоя контейнера, поэтому translationY
            // контейнера при вычислении относительной позиции сокращается.
            // Свайп вниз по полноэкранному плееру тянет общий якорный стейт.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .anchoredDraggable(
                        state = playerState.draggable,
                        orientation = Orientation.Vertical,
                    )
                    .onGloballyPositioned { contentCoordinates = it },
            ) {
                NowPlayingScreen(
                    onBack = playerState::collapse,
                    modifier = Modifier.fillMaxSize(),
                    playerViewModel = playerViewModel,
                    contentAlpha = { fullScreenAlpha(progress()) },
                    artwork = { slotModifier ->
                        // Пустой слот того же размера, что и полноэкранная обложка:
                        // он задаёт конечную точку морфа, а саму картинку рисует слой ниже.
                        Box(
                            modifier = slotModifier.onGloballyPositioned { coordinates ->
                                val content = contentCoordinates
                                    ?: return@onGloballyPositioned
                                val topLeft = content.localPositionOf(coordinates, Offset.Zero)
                                val bounds = Rect(topLeft, coordinates.size.toSize())
                                if (bounds != coverBounds) coverBounds = bounds
                            },
                        )
                    },
                )
            }
        }

        // Свайп вверх только по плашке мини-плеера раскрывает общий стейт.
        // Остальная площадь экрана свободна — списки под плеером скроллятся.
        MiniPlayerContent(
            state = player,
            positionMs = positionMs,
            onPlayPause = playerViewModel::playPause,
            onNext = playerViewModel::next,
            onExpand = playerState::expand,
            interactive = miniInteractive,
            modifier = Modifier
                .align(Alignment.TopStart)
                .anchoredDraggable(
                    state = playerState.draggable,
                    orientation = Orientation.Vertical,
                )
                .graphicsLayer { alpha = miniAlpha(progress()) },
        )

        MorphingArtwork(
            artwork = player.artworkUri,
            coverBounds = coverBounds,
            progress = progress,
            miniCoverLeftPx = miniCoverLeftPx,
            miniCoverTopPx = miniCoverTopPx,
            miniCoverSizePx = miniCoverSizePx,
        )
    }
}

/**
 * Одна и та же обложка в двух состояниях.
 *
 * Базовый размер — полноэкранный слот; в свёрнутом виде слой масштабируется до 48.dp
 * и переносится в левый верхний угол плашки. Смещение и масштаб интерполируются
 * по `expandProgress` в [graphicsLayer], поэтому кадры морфа не рекомпозируются.
 */
@Composable
private fun MorphingArtwork(
    artwork: String?,
    coverBounds: Rect?,
    progress: () -> Float,
    miniCoverLeftPx: Float,
    miniCoverTopPx: Float,
    miniCoverSizePx: Float,
) {
    val density = LocalDensity.current

    // Пока раскрытый слот ещё ни разу не измерен, показываем обложку сразу в плашке.
    if (coverBounds == null || coverBounds.width <= 0f) {
        TrackArtwork(
            artwork = artwork,
            modifier = Modifier
                .offset { IntOffset(miniCoverLeftPx.roundToInt(), miniCoverTopPx.roundToInt()) }
                .size(MiniCoverSize),
            cornerRadius = 4.dp,
            iconSize = 20.dp,
        )
        return
    }

    val expandedCenter = coverBounds.center
    val collapsedCenter = Offset(
        miniCoverLeftPx + miniCoverSizePx / 2f,
        miniCoverTopPx + miniCoverSizePx / 2f,
    )
    val deltaX = collapsedCenter.x - expandedCenter.x
    val deltaY = collapsedCenter.y - expandedCenter.y
    val scaleAtCollapsed = (miniCoverSizePx / coverBounds.width).coerceIn(0f, 1f)
    val baseSize = with(density) { coverBounds.width.toDp() }
    val coverShadowPx = with(density) { 24.dp.toPx() }
    val coverShape = RoundedCornerShape(8.dp)

    TrackArtwork(
        artwork = artwork,
        modifier = Modifier
            .offset { IntOffset(coverBounds.left.roundToInt(), coverBounds.top.roundToInt()) }
            .size(baseSize)
            .graphicsLayer {
                val value = progress()
                val scale = lerpFloat(scaleAtCollapsed, 1f, value)
                scaleX = scale
                scaleY = scale
                translationX = deltaX * (1f - value)
                translationY = deltaY * (1f - value)
                shadowElevation = coverShadowPx * value
                shape = coverShape
            },
        cornerRadius = 8.dp,
        iconSize = 56.dp,
    )
}

/** Линейная интерполяция: 0f — свёрнуто, 1f — раскрыто. */
private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction.coerceIn(0f, 1f)

private fun miniAlpha(progress: Float): Float =
    1f - (progress / MINI_FADE_END).coerceIn(0f, 1f)

private fun fullScreenAlpha(progress: Float): Float =
    ((progress - FULL_FADE_START) / (1f - FULL_FADE_START)).coerceIn(0f, 1f)
