package com.nordic.mediahub.ui

import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.nordic.mediahub.data.NavidromeSong
import com.nordic.mediahub.ui.theme.NordicAlpha
import com.nordic.mediahub.ui.theme.NordicControlSizes
import com.nordic.mediahub.ui.theme.NordicMotion
import com.nordic.mediahub.ui.theme.NordicShapes
import com.nordic.mediahub.ui.theme.NordicSpacing
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Scale-down applied to a queue row while it is in the drag "lift" state
 * (reaches maximum at half of row height).
 * Interaction-state feedback, not a generic text alpha tier.
 */
private const val QUEUE_DRAG_LIFT_SCALE_DOWN = 0.02f

/**
 * Alpha decay applied to a queue row while it is in the drag "lift" state.
 * Interaction-state feedback, not a generic text alpha tier.
 */
private const val QUEUE_DRAG_LIFT_ALPHA_DECAY = 0.08f

/**
 * Max height of the queue list inside the sheet. Layout sizing, not a
 * spacing token tier.
 */
private val QUEUE_SHEET_LIST_MAX_HEIGHT = 520.dp

/**
 * Tracks the active drag-reorder state at the [MusicQueueSheet] level so every
 * [QueueRow] can compute its own real-time displacement.
 *
 * @property draggedIndex index of the row currently being dragged, or `null` when idle.
 * @property accumulatedPx accumulated drag displacement in pixels (signed).
 */
internal data class QueueDragState(
    val draggedIndex: Int? = null,
    val accumulatedPx: Float = 0f,
    val targetIndex: Int? = null,
    val draggedExtentPx: Float = 0f
)

/**
 * Computes the `translationY` (px) a queue row should apply during an active drag.
 *
 * The dragged row follows the finger (`accumulatedPx`). Other rows shift by one
 * row height in the opposite direction when the dragged row has crossed past
 * them, creating the real-time "make space" effect. Rows that are not affected
 * resolve to `0f`.
 *
 * @param rowIndex this row's index in the queue
 * @param dragState the active drag state (or idle)
 * @param rowHeightPx the pixel height of a single row
 */
internal fun resolveQueueRowDisplacement(
    rowIndex: Int,
    dragState: QueueDragState,
    rowHeightPx: Float
): Float {
    val draggedIndex = dragState.draggedIndex ?: return 0f
    if (rowIndex == draggedIndex) return dragState.accumulatedPx
    if (rowHeightPx <= 0f) return 0f

    val draggedDelta = dragState.accumulatedPx
    if (draggedDelta == 0f) return 0f

    val draggedTargetIndex = dragState.targetIndex ?: (draggedIndex + (draggedDelta / rowHeightPx).roundToInt())
    if (draggedTargetIndex == draggedIndex) return 0f
    val isBetween = if (draggedTargetIndex > draggedIndex) rowIndex in (draggedIndex + 1)..draggedTargetIndex
        else rowIndex in draggedTargetIndex until draggedIndex
    if (!isBetween) return 0f
    val extent = dragState.draggedExtentPx.takeIf { it > 0f } ?: rowHeightPx
    return if (draggedTargetIndex > draggedIndex) -extent else extent
}

internal data class QueueItemBounds(val index: Int, val offset: Int, val size: Int)

/** Drop by rendered item centers, not a hard-coded row height that fails with large text. */
internal fun resolveQueueDropTarget(draggedIndex: Int, deltaPx: Float, items: List<QueueItemBounds>, count: Int): Int {
    if (draggedIndex !in 0 until count) return -1
    if (!deltaPx.isFinite()) return draggedIndex
    val valid = items.filter { it.index in 0 until count && it.size > 0 }
    val current = valid.firstOrNull { it.index == draggedIndex } ?: return draggedIndex
    val center = current.offset + current.size / 2f + deltaPx
    return valid.minByOrNull { abs(it.offset + it.size / 2f - center) }?.index ?: draggedIndex
}

@Composable
fun MusicQueueSheet(
    queue: List<NavidromeSong>,
    currentIndex: Int,
    colorScheme: ColorScheme,
    onSeekToIndex: (Int) -> Unit,
    onPlayNext: (Int) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
    onClearUpcoming: () -> Unit,
    onMoveQueueItem: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    onDismiss: () -> Unit
) {
    val resolvedCurrentIndex = currentIndex.takeIf { it in queue.indices } ?: -1
    val upcomingCount = if (resolvedCurrentIndex >= 0) {
        (queue.lastIndex - resolvedCurrentIndex).coerceAtLeast(0)
    } else {
        0
    }
    val listState = rememberLazyListState()
    var hasInitialScrolled by remember { mutableStateOf(false) }
    var removingIndex by remember { mutableStateOf<Int?>(null) }
    var dragState by remember { mutableStateOf(QueueDragState()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(resolvedCurrentIndex, queue.size) {
        if (resolvedCurrentIndex < 0) return@LaunchedEffect
        if (!hasInitialScrolled) {
            listState.scrollToItem(resolvedCurrentIndex)
            hasInitialScrolled = true
        } else {
            val viewportStart = listState.layoutInfo.viewportStartOffset
            val viewportEnd = listState.layoutInfo.viewportEndOffset
            val itemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == resolvedCurrentIndex }
            val needsAnimatedAlign = itemInfo == null ||
                itemInfo.offset < viewportStart ||
                itemInfo.offset + itemInfo.size > viewportEnd
            if (needsAnimatedAlign) {
                listState.animateScrollToItem(resolvedCurrentIndex)
            }
        }
    }

    MediaPlayerSheet(
        title = "播放队列",
        colors = colorScheme,
        onDismiss = onDismiss,
        subtitle = queueSubtitle(queue.size, resolvedCurrentIndex, upcomingCount),
        skipPartiallyExpanded = false,
        trailingAction = {
            QueueTextAction(
                text = "清空后续",
                enabled = upcomingCount > 0,
                colorScheme = colorScheme,
                onClick = onClearUpcoming
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)
        ) {
            if (queue.isEmpty()) {
                QueueEmptyState(colorScheme = colorScheme)
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = QUEUE_SHEET_LIST_MAX_HEIGHT)
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(NordicSpacing.xs)
                    ) {
                        itemsIndexed(
                            items = queue,
                            key = { index, song -> "${song.id}:$index" },
                            contentType = { _, _ -> "music-queue-row" }
                        ) { index, song ->
                            val isCurrent = index == resolvedCurrentIndex
                            val visible = removingIndex != index
                            androidx.compose.animation.AnimatedVisibility(
                                visible = visible,
                                enter = fadeIn(
                                    animationSpec = tween(
                                        NordicMotion.durationShort,
                                        easing = NordicMotion.easingStandard
                                    )
                                ),
                                exit = fadeOut(
                                    animationSpec = tween(
                                        NordicMotion.durationShort,
                                        easing = NordicMotion.easingStandard
                                    )
                                ) +
                                    shrinkVertically(
                                        animationSpec = tween(
                                            NordicMotion.durationShort,
                                            easing = NordicMotion.easingStandard
                                        )
                                    )
                            ) {
                                QueueRow(
                                    song = song,
                                    isCurrent = isCurrent,
                                    canPlayNext = resolvedCurrentIndex >= 0 &&
                                        !isCurrent &&
                                        index != resolvedCurrentIndex + 1,
                                    canRemove = queue.size > 1,
                                    canMoveUp = index > 0,
                                    canMoveDown = index < queue.lastIndex,
                                    rowIndex = index,
                                    dragState = dragState,
                                    colorScheme = colorScheme,
                                    onClick = { onSeekToIndex(index) },
                                    onPlayNext = { onPlayNext(index) },
                                    onRemove = {
                                        if (removingIndex != null) return@QueueRow
                                        removingIndex = index
                                        scope.launch {
                                            delay(NordicMotion.durationShort.toLong())
                                            onRemoveFromQueue(index)
                                            removingIndex = null
                                        }
                                    },
                                    onMoveUp = { onMoveQueueItem(index, index - 1) },
                                    onMoveDown = { onMoveQueueItem(index, index + 1) },
                                    onDragStart = {
                                        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                                        dragState = QueueDragState(draggedIndex = index, targetIndex = index,
                                            draggedExtentPx = (item?.size ?: 0).toFloat() + listState.layoutInfo.mainAxisItemSpacing)
                                    },
                                    onDrag = { deltaPx ->
                                        val offset = dragState.accumulatedPx + deltaPx
                                        val bounds = listState.layoutInfo.visibleItemsInfo.map { QueueItemBounds(it.index, it.offset, it.size) }
                                        dragState = dragState.copy(accumulatedPx = offset,
                                            targetIndex = resolveQueueDropTarget(index, offset, bounds, queue.size))
                                    },
                                    onDragEnd = {
                                        val targetIndex = dragState.targetIndex ?: index
                                        dragState = QueueDragState()
                                        if (targetIndex in queue.indices && targetIndex != index) onMoveQueueItem(index, targetIndex)
                                    },
                                    onDragCancel = {
                                        dragState = QueueDragState()
                                    }
                                )
                            }
                        }
                    }
                    MusicScrollbar(
                        state = listState,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = NordicSpacing.xs)
                    )
                }
            }
        }
    }
}

private fun queueSubtitle(queueSize: Int, currentIndex: Int, upcomingCount: Int): String {
    return when {
        queueSize <= 0 -> "暂无歌曲"
        currentIndex >= 0 -> "${queueSize} 首 · 当前第 ${currentIndex + 1} 首 · 后续 ${upcomingCount} 首"
        else -> "${queueSize} 首"
    }
}

@Composable
private fun QueueEmptyState(colorScheme: ColorScheme) {
    Surface(
        color = colorScheme.surfaceVariant.copy(alpha = 0.46f),
        shape = NordicShapes.md,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = NordicSpacing.md)
    ) {
        Text(
            "当前没有播放队列",
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = NordicSpacing.lg, vertical = NordicSpacing.xxl)
        )
    }
}

@Composable
private fun QueueRow(
    song: NavidromeSong,
    isCurrent: Boolean,
    canPlayNext: Boolean,
    canRemove: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    rowIndex: Int,
    dragState: QueueDragState,
    colorScheme: ColorScheme,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDragStart: () -> Unit = {},
    onDrag: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {}
) {
    var menuExpanded by remember(song.id) { mutableStateOf(false) }
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val density = LocalDensity.current
    val rowHeightPx = with(density) { 64.dp.toPx() }
    val liftThresholdPx = rowHeightPx * 0.5f
    val isDragged = dragState.draggedIndex == rowIndex
    val dragOffsetY = if (isDragged) dragState.accumulatedPx else 0f
    val displacementPx = resolveQueueRowDisplacement(
        rowIndex = rowIndex,
        dragState = dragState,
        rowHeightPx = rowHeightPx
    )
    val animatedDisplacement by animateFloatAsState(
        targetValue = if (isDragged) 0f else displacementPx,
        animationSpec = tween(
            NordicMotion.durationMicro,
            easing = NordicMotion.easingStandard
        ),
        label = "queue-row-displacement"
    )
    val liftProgress = if (isDragged) {
        (abs(dragOffsetY) / liftThresholdPx).coerceIn(0f, 1f)
    } else {
        0f
    }
    val animatedLiftScale by animateFloatAsState(
        targetValue = 1f - QUEUE_DRAG_LIFT_SCALE_DOWN * liftProgress,
        animationSpec = tween(
            NordicMotion.durationMicro,
            easing = NordicMotion.easingStandard
        ),
        label = "queue-row-lift-scale"
    )
    val animatedLiftAlpha by animateFloatAsState(
        targetValue = 1f - QUEUE_DRAG_LIFT_ALPHA_DECAY * liftProgress,
        animationSpec = tween(
            NordicMotion.durationMicro,
            easing = NordicMotion.easingStandard
        ),
        label = "queue-row-lift-alpha"
    )
    val resolvedTranslationY = if (isDragged) dragOffsetY else animatedDisplacement
    val backgroundColor = if (isCurrent) {
        colorScheme.primaryContainer
    } else {
        colorScheme.surface
    }

    Surface(
        color = backgroundColor,
        shape = NordicShapes.sm,
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (isDragged) 1f else 0f)
            .graphicsLayer {
                translationY = resolvedTranslationY
                scaleY = animatedLiftScale
                alpha = animatedLiftAlpha
            }
            .semantics { selected = isCurrent }
            .clickable(role = Role.Button, onClickLabel = "播放${song.title}", onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = NordicSpacing.sm, vertical = NordicSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(NordicSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QueueDragHandle(
                enabled = canMoveUp || canMoveDown,
                colorScheme = colorScheme,
                modifier = Modifier.pointerInput(canMoveUp, canMoveDown) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            onDragStart()
                        },
                        onDragCancel = {
                            onDragCancel()
                        },
                        onDragEnd = { currentOnDragEnd() },
                        onDrag = { _, dragAmount ->
                            currentOnDrag(dragAmount.y)
                        }
                    )
                }
            )

            Box(Modifier.size(42.dp).testTag("queue-artwork")) {
                CoverArt(song.coverArt, song.title, colorScheme, size = 42.dp,
                    modifier = Modifier.matchParentSize().clearAndSetSemantics {}, shape = NordicShapes.sm, fallbackIcon = Icons.Filled.MusicNote)
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NordicSpacing.xs)) {
                Text(song.title, style = MaterialTheme.typography.titleSmall,
                    color = if (isCurrent) colorScheme.onPrimaryContainer else colorScheme.onSurface,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(if (isCurrent) "当前播放 · ${musicArtistLabel(song.artist)}" else musicArtistLabel(song.artist),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isCurrent) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box {
                AnimatedIconButton(Icons.Filled.MoreVert, "队列操作：${song.title}", { menuExpanded = true },
                    colorScheme = colorScheme, containerColor = androidx.compose.ui.graphics.Color.Transparent)
                DropdownMenu(menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("下一首播放") }, enabled = canPlayNext,
                        onClick = { menuExpanded = false; onPlayNext() })
                    DropdownMenuItem(text = { Text("上移") }, enabled = canMoveUp,
                        onClick = { menuExpanded = false; onMoveUp() })
                    DropdownMenuItem(text = { Text("下移") }, enabled = canMoveDown,
                        onClick = { menuExpanded = false; onMoveDown() })
                    DropdownMenuItem(text = { Text("从队列移除", color = if (canRemove) colorScheme.error else colorScheme.onSurface.copy(alpha = 0.38f)) },
                        enabled = canRemove, onClick = { menuExpanded = false; onRemove() })
                }
            }
        }
    }
}

@Composable
private fun QueueDragHandle(
    enabled: Boolean,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier
) {
    Surface(
        color = colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.48f else 0.28f),
        contentColor = colorScheme.onSurface.copy(alpha = if (enabled) NordicAlpha.subtle else NordicAlpha.faint),
        shape = NordicShapes.full,
        modifier = modifier.size(NordicControlSizes.touchTarget)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.DragHandle,
                contentDescription = "拖动调整顺序",
                tint = colorScheme.onSurface.copy(alpha = if (enabled) NordicAlpha.subtle else NordicAlpha.faint),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun QueueTextAction(
    text: String,
    enabled: Boolean,
    colorScheme: ColorScheme,
    onClick: () -> Unit
) {
    Surface(
        color = if (enabled) {
            colorScheme.primary.copy(alpha = 0.1f)
        } else {
            colorScheme.surfaceVariant.copy(alpha = 0.4f)
        },
        contentColor = if (enabled) {
            colorScheme.primary
        } else {
            colorScheme.onSurface.copy(alpha = NordicAlpha.faint)
        },
        shape = NordicShapes.full,
        modifier = Modifier
            .widthIn(min = NordicControlSizes.touchTarget)
            .heightIn(min = NordicControlSizes.touchTarget)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = NordicSpacing.md, vertical = NordicSpacing.sm)
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}
