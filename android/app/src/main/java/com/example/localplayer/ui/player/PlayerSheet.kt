package com.example.localplayer.ui.player

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Раскрытие чуть дольше закрытия: выезд листа не должен выглядеть резким. */
private const val EXPAND_DURATION_MS = 320
private const val COLLAPSE_DURATION_MS = 260

/** Доля экрана, после которой отпущенный лист доезжает до полного экрана. */
private const val SETTLE_FRACTION = 0.34f

/** Скорость пальца (px/s), при которой направление броска важнее положения листа. */
private const val FLING_VELOCITY = 900f

/**
 * Состояние листа полноэкранного плеера, который выезжает из мини-плеера.
 *
 * [progress] — положение листа: 0 — он целиком за нижней кромкой экрана (виден
 * только мини-плеер), 1 — развёрнут на весь экран. Одна и та же величина двигает
 * лист и во время свайпа, и во время анимации, поэтому после отпускания пальца
 * лист продолжает движение ровно с того места, где его оставили, без рывка.
 *
 * Жест приходит из двух мест: свайп вверх по мини-плееру и свайп вниз по шапке
 * полноэкранного плеера. Оба зовут [drag] и [settle].
 */
@Stable
class PlayerSheetState internal constructor(
    private val scope: CoroutineScope,
) {
    var progress by mutableFloatStateOf(0f)
        private set

    /**
     * Лист в композиции: становится true на первом кадре раскрытия и снимается
     * только после того, как анимация закрытия доехала до нуля.
     */
    var isExpanded by mutableStateOf(false)
        private set

    private var animation: Job? = null

    /** Раскрыть на весь экран — тап по мини-плееру. */
    fun expand() {
        isExpanded = true
        animateTo(1f, EXPAND_DURATION_MS, FastOutSlowInEasing)
    }

    /** Свернуть обратно в мини-плеер — кнопка «назад», системный back, свайп вниз. */
    fun collapse() {
        if (!isExpanded) return
        animateTo(0f, COLLAPSE_DURATION_MS, FastOutLinearInEasing) {
            isExpanded = false
        }
    }

    /** Восстановить раскрытый лист после поворота экрана — сразу, без анимации. */
    internal fun restoreExpanded() {
        animation?.cancel()
        progress = 1f
        isExpanded = true
    }

    /**
     * Палец тянет мини-плеер вверх или шапку полноэкранного плеера вниз.
     * [deltaPx] — смещение за кадр: вверх отрицательное, вниз положительное.
     * [heightPx] — высота экрана, то есть весь путь листа от 0 до 1.
     */
    fun drag(deltaPx: Float, heightPx: Int) {
        if (heightPx <= 0) return
        // Вниз от свёрнутого мини-плеера тянуть нечего.
        if (!isExpanded && deltaPx >= 0f) return

        animation?.cancel()
        isExpanded = true
        progress = (progress - deltaPx / heightPx).coerceIn(0f, 1f)
    }

    /**
     * Палец отпущен: доезжаем до ближайшего края. Быстрый бросок важнее
     * положения — иначе лист, чуть переваливший середину, всегда раскрывался бы.
     */
    fun settle(velocityPxPerSecond: Float, heightPx: Int) {
        if (!isExpanded || heightPx <= 0) return

        val expand = when {
            velocityPxPerSecond < -FLING_VELOCITY -> true
            velocityPxPerSecond > FLING_VELOCITY -> false
            else -> progress >= SETTLE_FRACTION
        }
        if (expand) expand() else collapse()
    }

    private fun animateTo(
        target: Float,
        durationMs: Int,
        easing: Easing,
        onFinished: () -> Unit = {},
    ) {
        animation?.cancel()
        animation = scope.launch {
            animate(
                initialValue = progress,
                targetValue = target,
                animationSpec = tween(durationMillis = durationMs, easing = easing),
            ) { value, _ -> progress = value }
            onFinished()
        }
    }
}

@Composable
fun rememberPlayerSheetState(): PlayerSheetState {
    val scope = rememberCoroutineScope()
    // Раскрытый плеер переживает поворот экрана: состояние листа не попадает
    // в back stack (его там больше нет), поэтому запоминаем флаг сами.
    val wasExpanded = rememberSaveable { mutableStateOf(false) }
    val state = remember(scope) {
        PlayerSheetState(scope).also { if (wasExpanded.value) it.restoreExpanded() }
    }
    SideEffect { wasExpanded.value = state.isExpanded }
    return state
}
