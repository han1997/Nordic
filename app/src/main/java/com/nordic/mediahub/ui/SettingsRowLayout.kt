package com.nordic.mediahub.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nordic.mediahub.ui.theme.NordicControlSizes
import com.nordic.mediahub.ui.theme.NordicSpacing

/** Budget actual measured text, not character counts; long titles may use two readable lines. */
internal fun shouldInlineSettingsValue(
    availableWidth: Dp,
    titleWidth: Dp,
    valueWidth: Dp,
    fontScale: Float,
    hasIcon: Boolean = false,
    hasNavigation: Boolean = false
): Boolean {
    if (!fontScale.isFinite() || fontScale <= 0f ||
        listOf(availableWidth, titleWidth, valueWidth).any { !it.value.isFinite() || it.value < 0f }) return false
    val leading = if (hasIcon) NordicControlSizes.icon + NordicSpacing.md else 0.dp
    val trailing = if (hasNavigation) NordicControlSizes.compactIcon + NordicSpacing.md else 0.dp
    val readableTitleWidth = minOf(titleWidth, 160.dp * fontScale.coerceAtLeast(1f))
    return availableWidth >= leading + trailing + readableTitleWidth + NordicSpacing.md + valueWidth
}
