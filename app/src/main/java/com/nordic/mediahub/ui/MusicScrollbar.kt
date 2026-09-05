package com.nordic.mediahub.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nordic.mediahub.ui.theme.NordicShapes
import kotlin.math.roundToInt

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
 * Thumb geometry resolved from the list layout info: [thumbFraction] is the
 * visible/total ratio (the thumb's height share of the track), [scrollFraction]
 * is the scroll progress (the thumb's top offset share of the free track space).
 */
internal data class MusicScrollbarThumb(
    val thumbFraction: Float,
    val scrollFraction: Float
)

/**
 * Computes the scrollbar thumb geometry from [LazyListState] snapshot values.
 * Returns null when the scrollbar should be hidden (no content, everything
 * visible, or single item).
 *
 * Scroll progress is index + pixel-offset based so it tracks partial item
 * scrolls smoothly.
 */
internal fun resolveMusicScrollbarThumb(
    totalItemsCount: Int,
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    visibleItemsCount: Int,
    averageItemSizePx: Float
): MusicScrollbarThumb? {
    if (totalItemsCount <= 0 || visibleItemsCount <= 0) return null
    if (visibleItemsCount >= totalItemsCount) return null

    val thumbFraction = (visibleItemsCount.toFloat() / totalItemsCount.toFloat())
        .coerceIn(MusicScrollbarMinThumbFraction, 1f)
    if (thumbFraction >= 1f) return null

    val scrollRangeItems = (totalItemsCount - visibleItemsCount).coerceAtLeast(1)
    val inItemProgress = if (averageItemSizePx > 0f) {
        (firstVisibleItemScrollOffset / averageItemSizePx).coerceIn(0f, 1f)
    } else {
        0f
    }
    val position = firstVisibleItemIndex + inItemProgress
    val scrollFraction = (position / scrollRangeItems).coerceIn(0f, 1f)

    return MusicScrollbarThumb(thumbFraction = thumbFraction, scrollFraction = scrollFraction)
}

/**
 * Display-only (non-draggable) scrollbar for a [LazyColumn], driven by
 * [LazyListState]. Rendered with [androidx.compose.ui.draw.drawBehind] so a
 * scroll frame only redraws the thumb — no recomposition, no layout, no
 * subcomposition (the previous implementation re-entered `BoxWithConstraints`
 * on every scroll frame). Hidden automatically when the content fits the
 * viewport.
 */
@Composable
internal fun MusicScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = MusicScrollbarAlpha),
    enabled: Boolean = true
) {
    if (!enabled) return
    val thumbWidth = MusicScrollbarThickness
    val clipShape = NordicShapes.full

    Box(
        modifier
            .fillMaxHeight()
            .width(thumbWidth)
            .drawBehind {
                val layoutInfo = state.layoutInfo
                val visibleItems = layoutInfo.visibleItemsInfo
                if (visibleItems.isEmpty()) return@drawBehind
                val averageItemSizePx = visibleItems
                    .fold(0f) { acc, item -> acc + item.size } / visibleItems.size.toFloat()
                if (averageItemSizePx <= 0f) return@drawBehind
                val thumb = resolveMusicScrollbarThumb(
                    totalItemsCount = layoutInfo.totalItemsCount,
                    firstVisibleItemIndex = state.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = state.firstVisibleItemScrollOffset,
                    visibleItemsCount = visibleItems.size,
                    averageItemSizePx = averageItemSizePx
                ) ?: return@drawBehind

                val trackHeight = size.height
                val thumbHeight = trackHeight * thumb.thumbFraction
                val offsetY = ((trackHeight - thumbHeight) * thumb.scrollFraction).roundToInt().toFloat()
                val radius = thumbWidth.toPx() / 2f
                drawRoundRect(
                    color = color,
                    topLeft = Offset(0f, offsetY),
                    size = Size(thumbWidth.toPx(), thumbHeight),
                    cornerRadius = CornerRadius(radius, radius)
                )
            }
            .clip(clipShape)
    )
}
