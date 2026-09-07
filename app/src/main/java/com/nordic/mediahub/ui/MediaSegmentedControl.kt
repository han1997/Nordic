package com.nordic.mediahub.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nordic.mediahub.ui.theme.NordicControlSizes
import com.nordic.mediahub.ui.theme.NordicMotion
import com.nordic.mediahub.ui.theme.NordicShapes
import com.nordic.mediahub.ui.theme.NordicSpacing

internal fun shouldScrollMediaSegments(availableWidth: Dp, minimumWidths: List<Dp>): Boolean {
    if (minimumWidths.isEmpty()) return false
    if (minimumWidths.size > 4) return true
    val widest = minimumWidths.maxOrNull()!!.coerceAtLeast(NordicControlSizes.touchTarget)
    val required = widest * minimumWidths.size + NordicSpacing.xs * (minimumWidths.size - 1) + NordicSpacing.xs * 2
    return required > availableWidth
}

/** Shared tab/sort geometry. Measure real labels instead of truncating them at large font scales. */
@Composable
internal fun <T : Any> MediaSegmentedControl(
    options: List<T>,
    selectedOption: T?,
    label: (T) -> String,
    optionKey: (T) -> String,
    colorScheme: ColorScheme,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    if (options.isEmpty()) return
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val textStyle = MaterialTheme.typography.titleSmall
    val labels = options.map(label)
    val minimumWidths = remember(labels, textStyle, textMeasurer, density) {
        labels.map { text ->
            with(density) {
                textMeasurer.measure(AnnotatedString(text), textStyle, softWrap = false, maxLines = 1).size.width.toDp()
            }.plus(NordicSpacing.md * 2).coerceAtLeast(NordicControlSizes.touchTarget)
        }
    }
    val listState = rememberLazyListState()
    Surface(
        color = colorScheme.surfaceVariant.copy(alpha = 0.56f),
        shape = NordicShapes.md,
        border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.06f)),
        modifier = modifier.fillMaxWidth()
    ) {
        BoxWithConstraints(Modifier.heightIn(min = NordicControlSizes.touchTarget + NordicSpacing.xs * 2)) {
            val scrollable = shouldScrollMediaSegments(maxWidth, minimumWidths)
            LaunchedEffect(selectedOption, scrollable, options) {
                if (scrollable) {
                    val index = options.indexOf(selectedOption)
                    if (index >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
                        listState.scrollToItem(index)
                    }
                }
            }
            if (scrollable) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().selectableGroup(),
                    state = listState,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(NordicSpacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(NordicSpacing.xs)
                ) {
                    itemsIndexed(options, key = { _, option -> optionKey(option) }, contentType = { _, _ -> "media-segment" }) { index, option ->
                        MediaSegmentItem(labels[index], option == selectedOption, colorScheme,
                            Modifier.width(minimumWidths[index])) { onOptionSelected(option) }
                    }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().selectableGroup().padding(NordicSpacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(NordicSpacing.xs)
                ) {
                    options.forEachIndexed { index, option ->
                        MediaSegmentItem(labels[index], option == selectedOption, colorScheme,
                            Modifier.weight(1f)) { onOptionSelected(option) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaSegmentItem(
    text: String,
    selected: Boolean,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interactionSource)
    val container by animateColorAsState(
        if (selected) colorScheme.surface.copy(alpha = 0.96f) else Color.Transparent,
        tween(NordicMotion.durationMicro, easing = NordicMotion.easingStandard), label = "segment-container"
    )
    val content by animateColorAsState(
        if (selected) colorScheme.primary else colorScheme.onSurfaceVariant,
        tween(NordicMotion.durationMicro, easing = NordicMotion.easingStandard), label = "segment-content"
    )
    Surface(
        color = container, contentColor = content, shape = NordicShapes.sm,
        tonalElevation = if (selected) 2.dp else 0.dp,
        modifier = modifier.heightIn(min = NordicControlSizes.touchTarget).scale(scale)
            .selectable(selected = selected, role = Role.Tab, interactionSource = interactionSource,
                indication = null, onClick = onClick)
    ) {
        Box(Modifier.padding(horizontal = NordicSpacing.md, vertical = NordicSpacing.sm), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
