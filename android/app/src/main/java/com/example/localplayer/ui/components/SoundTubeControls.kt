package com.example.localplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.localplayer.ui.theme.SoundTubeTheme

/**
 * Строительные блоки терминальной темы: плоские карточки с тонкой рамкой,
 * кнопки без теней и скруглений «пузырём», бейджи и тонкие полоски прогресса.
 *
 * Все размеры и цвета повторяют десктопный SoundTube, поэтому экраны не
 * занимаются этим сами.
 */

/** Плоская карточка: `#121412`, рамка 1.dp, скругление 8.dp, без тени. */
@Composable
fun SoundTubeCard(
    modifier: Modifier = Modifier,
    background: Color = SoundTubeTheme.colors.surface,
    borderColor: Color = SoundTubeTheme.colors.border,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(background)
            .border(1.dp, borderColor, shape)
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
            )
            .padding(contentPadding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

/**
 * Кнопка терминала. Главная — заливка [SoundTubeTheme.colors.primary] и тёмный
 * текст, второстепенная — прозрачный фон с рамкой. Скругление 6.dp, без тени.
 *
 * @param danger необратимое действие: тёмная плашка с красной рамкой и красным
 *   текстом (удаление). Только для второстепенного вида.
 */
@Composable
fun SoundTubeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = true,
    danger: Boolean = false,
    compact: Boolean = false,
    icon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    val colors = SoundTubeTheme.colors
    val shape = RoundedCornerShape(6.dp)
    val contentColor = when {
        !enabled -> colors.textSecondary.copy(alpha = 0.6f)
        danger -> colors.error
        primary -> colors.onPrimary
        else -> colors.textSecondary
    }

    Row(
        modifier = modifier
            .heightIn(min = if (compact) 28.dp else 34.dp)
            .clip(shape)
            .background(
                when {
                    !enabled -> colors.surfaceRaised
                    danger -> colors.error.copy(alpha = 0.10f)
                    primary -> colors.primary
                    else -> colors.surfaceRaised
                },
            )
            .then(
                when {
                    danger -> Modifier.border(1.dp, colors.error.copy(alpha = 0.55f), shape)
                    primary -> Modifier
                    else -> Modifier.border(1.dp, colors.borderStrong, shape)
                },
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(
                horizontal = if (compact) 10.dp else 14.dp,
                vertical = if (compact) 2.dp else 4.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        leading?.invoke()
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            maxLines = 1,
        )
    }
}

/** Квадратная иконка-кнопка: рамка 1.dp или прозрачный фон в шапке. */
@Composable
fun SoundTubeIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = SoundTubeTheme.colors.textSecondary,
    bordered: Boolean = true,
    size: Dp = 34.dp,
) {
    val colors = SoundTubeTheme.colors
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .then(
                if (bordered) {
                    Modifier
                        .background(colors.surfaceRaised)
                        .border(1.dp, colors.borderStrong, shape)
                } else {
                    Modifier
                },
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = 0.4f),
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

/** Бейдж площадки: `youtube`, `soundcloud`, `local`. */
@Composable
fun SoundTubeBadge(text: String, modifier: Modifier = Modifier) {
    val colors = SoundTubeTheme.colors
    val shape = RoundedCornerShape(4.dp)
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = colors.textSecondary,
        maxLines = 1,
        modifier = modifier
            .clip(shape)
            .background(colors.surfaceRaised)
            .border(1.dp, colors.borderStrong, shape)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/** Зелёная точка статуса: движок готов, сервер отвечает. */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(color),
    )
}

/** Тонкая полоска прогресса без анимаций Material: 3.dp, плоские края. */
@Composable
fun ThinProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = SoundTubeTheme.colors.primary,
    trackColor: Color = SoundTubeTheme.colors.border,
    height: Dp = 3.dp,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(trackColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(color),
        )
    }
}

/** Мелкий технический заголовок секции, как в сайдбаре десктопа. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
        color = SoundTubeTheme.colors.textSecondary,
        modifier = modifier,
    )
}
