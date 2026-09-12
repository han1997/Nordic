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
    fontScale: Float = 1f,
    fixedActionCount: Int = 0
): MediaHeaderActionLayout {
    val count = actionCount.coerceAtLeast(0)
    val fixed = fixedActionCount.coerceIn(0, count)
    if (count == 0) return MediaHeaderActionLayout(0, false, 0.dp)
    val scale = fontScale.takeIf { it.isFinite() && it > 0f }?.coerceIn(1f, 2f) ?: 1f
    val titleWidth = (if (showBack) 96.dp else 112.dp) * scale
    val backWidth = if (showBack) NordicControlSizes.touchTarget + NordicSpacing.md else 0.dp
    val groupPadding = NordicSpacing.xs * 2
    val slotWidth = NordicControlSizes.touchTarget + NordicSpacing.xs
    val remaining = availableWidth - titleWidth - backWidth - NordicSpacing.md
    val slots = ((remaining - groupPadding + NordicSpacing.xs) / slotWidth)
        .toInt().coerceAtLeast(1).coerceAtMost(count)
    // Fixed actions always occupy their slots; only the remaining actions overflow.
    val overflowableCount = count - fixed
    val overflowableSlots = (slots - fixed).coerceAtLeast(0).coerceAtMost(overflowableCount)
    val overflow = overflowableSlots < overflowableCount
    return MediaHeaderActionLayout(
        inlineActionCount = if (overflow) (overflowableSlots - 1).coerceAtLeast(0) else overflowableCount,
        showsOverflow = overflow,
        actionGroupWidth = groupPadding + NordicControlSizes.touchTarget * slots + NordicSpacing.xs * (slots - 1)
    )
}
