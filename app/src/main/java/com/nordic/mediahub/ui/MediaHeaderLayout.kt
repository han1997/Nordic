package com.nordic.mediahub.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nordic.mediahub.ui.theme.NordicControlSizes
import com.nordic.mediahub.ui.theme.NordicSpacing

internal data class MediaHeaderActionLayout(
    val inlineActionCount: Int,
    val showsOverflow: Boolean,
    val actionGroupWidth: Dp
)

/** Keep actions reachable without shrinking hit targets or squeezing the title to nothing. */
internal fun resolveMediaHeaderActionLayout(
    availableWidth: Dp,
    showBack: Boolean,
    actionCount: Int,
    fontScale: Float = 1f
): MediaHeaderActionLayout {
    val count = actionCount.coerceAtLeast(0)
    if (count == 0) return MediaHeaderActionLayout(0, false, 0.dp)
    val scale = fontScale.takeIf { it.isFinite() && it > 0f }?.coerceIn(1f, 2f) ?: 1f
    val titleWidth = (if (showBack) 96.dp else 112.dp) * scale
    val backWidth = if (showBack) NordicControlSizes.touchTarget + NordicSpacing.md else 0.dp
    val groupPadding = NordicSpacing.xs * 2
    val slotWidth = NordicControlSizes.touchTarget + NordicSpacing.xs
    val remaining = availableWidth - titleWidth - backWidth - NordicSpacing.md
    val slots = ((remaining - groupPadding + NordicSpacing.xs) / slotWidth)
        .toInt().coerceAtLeast(1).coerceAtMost(count)
    val overflow = slots < count
    return MediaHeaderActionLayout(
        inlineActionCount = if (overflow) slots - 1 else count,
        showsOverflow = overflow,
        actionGroupWidth = groupPadding + NordicControlSizes.touchTarget * slots + NordicSpacing.xs * (slots - 1)
    )
}
