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
 * Display-only (non-draggable) scrollbar for a [LazyColumn], driven by
 * [LazyListState]. The thumb size reflects the visible/total ratio and its
 * position reflects scroll progress (index + pixel offset based, so it tracks
 * partial item scrolls smoothly). Hidden automatically when the content fits
 * the viewport, and only the thumb itself recomposes on scroll.
 */
@Composable
internal fun MusicScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = MusicScrollbarAlpha),
    enabled: Boolean = true
) {
    if (!enabled) return
    val layoutInfo by remember(state) { derivedStateOf { state.layoutInfo } }
    val visible by remember(state) {
        derivedStateOf {
            layoutInfo.visibleItemsInfo.isNotEmpty() &&
                layoutInfo.totalItemsCount > layoutInfo.visibleItemsInfo.size
        }
    }
    val thumbFraction by remember(state) {
        derivedStateOf {
            val total = layoutInfo.totalItemsCount
            val shown = layoutInfo.visibleItemsInfo.size
            if (total == 0 || shown == 0) 1f
            else (shown.toFloat() / total.toFloat()).coerceIn(MusicScrollbarMinThumbFraction, 1f)
        }
    }
    val scrollFraction by remember(state) {
        derivedStateOf {
            val total = layoutInfo.totalItemsCount
            val shown = layoutInfo.visibleItemsInfo.size
            val scrollRange = total - shown
            if (total <= 1 || scrollRange <= 0) 0f
            else {
                val itemSize = layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 1
                val ratio = state.firstVisibleItemScrollOffset.toFloat() / itemSize.coerceAtLeast(1)
                ((state.firstVisibleItemIndex + ratio) / scrollRange).coerceIn(0f, 1f)
            }
        }
    }
    if (visible && thumbFraction < 1f) {
        BoxWithConstraints(modifier = modifier.fillMaxHeight()) {
            val trackHeight = maxHeight
            val thumbHeight = trackHeight * thumbFraction
            val offsetY = (trackHeight - thumbHeight) * scrollFraction
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
