package com.nordic.mediahub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nordic.mediahub.ui.theme.NordicShapes

private val MusicScrollbarThickness = 4.dp

/**
 * Scrollbar thumb alpha on `onSurface`. The scrollbar overlays content surfaces,
 * so it keys off `onSurface` (not a fixed white) to stay legible in both themes.
 */
private const val MusicScrollbarAlpha = 0.28f

/**
 * Minimum visible/total ratio for the thumb. Real content almost always shows a
 * thin sliver at the extremes; clamping keeps the thumb from collapsing to a dot.
 */
private const val MusicScrollbarMinThumbFraction = 0.05f

/**
 * Snapshot of the scrollbar geometry derived from [LazyListLayoutInfo]. Computed
 * in a single [androidx.compose.runtime.derivedStateOf] so a scroll frame only
 * invalidates the thumb once instead of once per derived value.
 */
private data class MusicScrollbarGeometry(
    val visible: Boolean,
    val thumbFraction: Float,
    val scrollFraction: Float
)

private fun resolveMusicScrollbarGeometry(
    layoutInfo: androidx.compose.foundation.lazy.LazyListLayoutInfo
): MusicScrollbarGeometry {
    val total = layoutInfo.totalItemsCount
    val shown = layoutInfo.visibleItemsInfo.size
    val visible = shown > 0 && total > shown
    val thumbFraction = if (total == 0 || shown == 0) {
        1f
    } else {
        (shown.toFloat() / total.toFloat()).coerceIn(MusicScrollbarMinThumbFraction, 1f)
    }
    val scrollRange = total - shown
    val scrollFraction = if (total <= 1 || scrollRange <= 0) {
        0f
    } else {
        val first = layoutInfo.visibleItemsInfo.firstOrNull()
        if (first == null) {
            0f
        } else {
            // Continuous position = item index + in-item scroll progress. The
            // first visible item's offset is <= 0 while scrolling into it, so
            // the consumed fraction within that item is -offset / itemSize.
            val itemSize = first.size.coerceAtLeast(1)
            val inItemProgress = (-first.offset.toFloat() / itemSize).coerceIn(0f, 1f)
            val position = first.index + inItemProgress
            (position / scrollRange).coerceIn(0f, 1f)
        }
    }
    return MusicScrollbarGeometry(visible, thumbFraction, scrollFraction)
}

/**
 * Display-only (non-draggable) scrollbar for a [LazyColumn], driven by
 * [LazyListState]. The thumb size reflects the visible/total ratio and its
 * position reflects scroll progress (index + pixel offset based, so it tracks
 * partial item scrolls smoothly). Hidden automatically when the content fits
 * the viewport. Geometry is computed in one derivedStateOf, so only the thumb
 * recomposes on scroll — once per frame at most.
 */
@Composable
internal fun MusicScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = MusicScrollbarAlpha),
    enabled: Boolean = true
) {
    if (!enabled) return
    val geometry by remember(state) {
        derivedStateOf { resolveMusicScrollbarGeometry(state.layoutInfo) }
    }
    if (geometry.visible && geometry.thumbFraction < 1f) {
        BoxWithConstraints(modifier = modifier.fillMaxHeight()) {
            val trackHeight = maxHeight
            val thumbHeight = trackHeight * geometry.thumbFraction
            val offsetY = (trackHeight - thumbHeight) * geometry.scrollFraction
            Box(
                modifier = Modifier
                    .offset(y = offsetY)
                    .height(thumbHeight)
                    .width(MusicScrollbarThickness)
                    .clip(NordicShapes.full)
                    .background(color)
            )
        }
    }
}
