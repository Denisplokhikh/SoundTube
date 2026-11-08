package com.example.localplayer.ui.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Высота свёрнутого мини-плеера. */
val MiniPlayerHeight = 64.dp

/** Два якоря единого контейнера плеера. */
enum class PlayerSheetValue {
    /** Свёрнут: над нижней навигацией видна только плашка мини-плеера. */
    Collapsed,

    /** Раскрыт на весь экран. */
    Expanded,
}

/** Порог смещения при отпускании пальца: 30% пути до противоположного якоря. */
private const val SETTLE_POSITION_FRACTION = 0.30f

/** Скорость броска (dp/s), при которой направление жеста важнее положения. */
private const val SETTLE_VELOCITY_DP = 125f

/**
 * Смещение-заглушка до первого измерения: контейнер уезжает далеко за нижнюю кромку,
 * чтобы на самом первом кадре не мигнуть раскрытым плеером.
 */
private const val PLACEHOLDER_OFFSET_PX = 100_000f

/**
 * Состояние выезжающего плеера на [AnchoredDraggableState] с двумя якорями:
 * [PlayerSheetValue.Collapsed] (виден только мини-плеер) и [PlayerSheetValue.Expanded]
 * (полноэкранный плеер).
 *
 * Контейнер перемещается строго за пальцем: `Modifier.anchoredDraggable` двигает
 * [AnchoredDraggableState.requireOffset] на дельту жеста, а на отпускании пружина
 * ([Spring.DampingRatioNoBouncy], [Spring.StiffnessMediumLow]) доводит его до ближайшего
 * якоря с учётом скорости броска и порога [SETTLE_POSITION_FRACTION].
 *
 * Наружу отдаётся не только смещение в пикселях, но и нормализованный
 * [expandProgress] — по нему морфятся обложка, тексты, кнопки и нижняя навигация.
 */
@OptIn(ExperimentalFoundationApi::class)
@Stable
class ExpandablePlayerState internal constructor(
    private val scope: CoroutineScope,
    initiallyExpanded: Boolean,
    velocityThresholdPx: Float,
) {
    /** Полный путь контейнера в пикселях; до первого измерения — условная единица. */
    private var collapsedOffsetPx = PLACEHOLDER_OFFSET_PX

    internal val draggable = AnchoredDraggableState<PlayerSheetValue>(
        initialValue = if (initiallyExpanded) {
            PlayerSheetValue.Expanded
        } else {
            PlayerSheetValue.Collapsed
        },
        anchors = DraggableAnchors {
            // Пока высота экрана и навигации не измерены, контейнер должен быть за
            // пределами экрана: смещение-заглушка даёт прогресс 0.
            PlayerSheetValue.Expanded at 0f
            PlayerSheetValue.Collapsed at PLACEHOLDER_OFFSET_PX
        },
        positionalThreshold = { distance -> distance * SETTLE_POSITION_FRACTION },
        velocityThreshold = { velocityThresholdPx },
        snapAnimationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        decayAnimationSpec = exponentialDecay(),
    )

    /** Контейнер сейчас раскрыт (значение якоря, а не промежуточное смещение). */
    val isExpanded: Boolean
        get() = draggable.currentValue == PlayerSheetValue.Expanded

    /**
     * Смещение контейнера в пикселях: 0 — раскрыт на весь экран,
     * [collapsedOffsetPx] — свёрнут до полоски мини-плеера.
     * Читать можно в фазе отрисовки (`graphicsLayer`, `Modifier.offset`).
     */
    fun offsetPx(): Float = draggable.requireOffset()

    /**
     * Нормализованный прогресс раскрытия: 0f — свёрнут, 1f — развёрнут на весь экран.
     * Обратная величина от смещения, поэтому её можно читать прямо в графическом слое
     * без рекомпозиции кадра.
     */
    fun expandProgress(): Float {
        val collapsed = collapsedOffsetPx
        if (collapsed <= 0f) return 0f
        val offset = draggable.requireOffset()
        return ((collapsed - offset) / collapsed).coerceIn(0f, 1f)
    }

    /**
     * Якоря зависят от высоты экрана и нижней навигации, а они известны только после
     * первого измерения. Обновляем их, сохраняя текущее состояние и смещение.
     */
    internal fun updateAnchors(collapsedOffset: Float) {
        if (collapsedOffset <= 0f) return
        if (collapsedOffset == collapsedOffsetPx && draggable.anchors.size == 2) {
            val current = draggable.anchors.positionOf(PlayerSheetValue.Collapsed)
            if (current == collapsedOffset) return
        }
        collapsedOffsetPx = collapsedOffset
        val anchors = DraggableAnchors {
            PlayerSheetValue.Expanded at 0f
            PlayerSheetValue.Collapsed at collapsedOffset
        }
        draggable.updateAnchors(anchors, draggable.currentValue)
    }

    /** Программно раскрыть плеер (тап по мини-плееру). */
    fun expand() {
        scope.launch { draggable.animateTo(PlayerSheetValue.Expanded) }
    }

    /** Программно свернуть плеер (кнопка «вниз», системный back). */
    fun collapse() {
        scope.launch { draggable.animateTo(PlayerSheetValue.Collapsed) }
    }
}

/**
 * Запоминает [ExpandablePlayerState]. Раскрытое состояние переживает поворот экрана:
 * сам лист не лежит в back stack, поэтому флаг сохраняем в `rememberSaveable`.
 */
@Composable
fun rememberExpandablePlayerState(): ExpandablePlayerState {
    val scope = rememberCoroutineScope()
    val velocityThresholdPx = with(LocalDensity.current) { SETTLE_VELOCITY_DP.dp.toPx() }
    val wasExpanded = rememberSaveable { mutableStateOf(false) }
    val state = remember(scope) {
        ExpandablePlayerState(
            scope = scope,
            initiallyExpanded = wasExpanded.value,
            velocityThresholdPx = velocityThresholdPx,
        )
    }
    SideEffect { wasExpanded.value = state.isExpanded }
    return state
}
