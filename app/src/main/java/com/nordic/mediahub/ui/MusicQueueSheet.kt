package com.nordic.mediahub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.nordic.mediahub.data.NavidromeSong
import com.nordic.mediahub.ui.theme.NordicAlpha
import com.nordic.mediahub.ui.theme.NordicShapes
import com.nordic.mediahub.ui.theme.NordicSpacing
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
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

    LaunchedEffect(resolvedCurrentIndex, queue.size) {
        if (resolvedCurrentIndex >= 0) {
            listState.scrollToItem(resolvedCurrentIndex)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colorScheme.surface,
        shape = NordicShapes.xl
    ) {
        Column(
            modifier = Modifier.padding(bottom = NordicSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)
        ) {
            QueueSheetHeader(
                queueSize = queue.size,
                currentIndex = resolvedCurrentIndex,
                upcomingCount = upcomingCount,
                colorScheme = colorScheme,
                onClearUpcoming = onClearUpcoming
            )

            if (queue.isEmpty()) {
                QueueEmptyState(colorScheme = colorScheme)
            } else {
                QueueCurrentHint(
                    currentSong = queue.getOrNull(resolvedCurrentIndex),
                    upcomingCount = upcomingCount,
                    colorScheme = colorScheme
                )
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(NordicSpacing.xs)
                ) {
                    itemsIndexed(
                        items = queue,
                        key = { index, song -> "${song.id}:$index" },
                        contentType = { _, _ -> "music-queue-row" }
                    ) { index, song ->
                        val isCurrent = index == resolvedCurrentIndex
                        QueueRow(
                            song = song,
                            isCurrent = isCurrent,
                            canPlayNext = resolvedCurrentIndex >= 0 &&
                                !isCurrent &&
                                index != resolvedCurrentIndex + 1,
                            canRemove = queue.size > 1,
                            canMoveUp = index > 0,
                            canMoveDown = index < queue.lastIndex,
                            colorScheme = colorScheme,
                            onClick = { onSeekToIndex(index) },
                            onPlayNext = { onPlayNext(index) },
                            onRemove = { onRemoveFromQueue(index) },
                            onMoveUp = { onMoveQueueItem(index, index - 1) },
                            onMoveDown = { onMoveQueueItem(index, index + 1) },
                            onDragByRows = { rowDelta ->
                                val targetIndex = (index + rowDelta).coerceIn(queue.indices)
                                if (targetIndex != index) {
                                    onMoveQueueItem(index, targetIndex)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueSheetHeader(
    queueSize: Int,
    currentIndex: Int,
    upcomingCount: Int,
    colorScheme: ColorScheme,
    onClearUpcoming: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = NordicSpacing.xl),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(NordicSpacing.xs)
        ) {
            Text(
                "播放队列",
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                queueSubtitle(queueSize, currentIndex, upcomingCount),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Normal,
                color = colorScheme.onSurface.copy(alpha = NordicAlpha.subtle),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(NordicSpacing.md))
        QueueTextAction(
            text = "清空后续",
            enabled = upcomingCount > 0,
            colorScheme = colorScheme,
            onClick = onClearUpcoming
        )
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
            color = colorScheme.onSurface.copy(alpha = NordicAlpha.subtle),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = NordicSpacing.lg, vertical = NordicSpacing.xxl)
        )
    }
}

@Composable
private fun QueueCurrentHint(
    currentSong: NavidromeSong?,
    upcomingCount: Int,
    colorScheme: ColorScheme
) {
    Surface(
        color = colorScheme.primary.copy(alpha = 0.08f),
        contentColor = colorScheme.onSurface,
        shape = NordicShapes.md,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = NordicSpacing.md)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = NordicSpacing.md, vertical = NordicSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(NordicSpacing.xs)
        ) {
            Text(
                currentSong?.title ?: "未定位当前播放",
                style = MaterialTheme.typography.titleSmall,
                color = colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (currentSong == null) {
                    "点击任意歌曲即可开始播放"
                } else if (upcomingCount > 0) {
                    "已定位到当前播放，后续还有 $upcomingCount 首"
                } else {
                    "已定位到当前播放，后续队列为空"
                },
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurface.copy(alpha = NordicAlpha.subtle),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
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
    colorScheme: ColorScheme,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    onDragByRows: (Int) -> Unit = {}
) {
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val rowHeightPx = with(density) { 64.dp.toPx() }
    val dragShadowPx = with(density) { NordicSpacing.sm.toPx() }
    val backgroundColor = if (isCurrent) {
        colorScheme.primary.copy(alpha = 0.1f)
    } else {
        colorScheme.surface
    }

    Surface(
        color = backgroundColor,
        shape = NordicShapes.sm,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = NordicSpacing.md)
            .zIndex(if (dragOffsetY != 0f) 1f else 0f)
            .graphicsLayer {
                translationY = dragOffsetY
                shadowElevation = if (dragOffsetY != 0f) dragShadowPx else 0f
            }
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = NordicSpacing.md, vertical = NordicSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(NordicSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QueueDragHandle(
                enabled = canMoveUp || canMoveDown,
                colorScheme = colorScheme,
                modifier = Modifier.pointerInput(canMoveUp, canMoveDown) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { dragOffsetY = 0f },
                        onDragCancel = { dragOffsetY = 0f },
                        onDragEnd = {
                            val rowDelta = (dragOffsetY / rowHeightPx).roundToInt()
                            val allowedDelta = when {
                                rowDelta < 0 && canMoveUp -> rowDelta
                                rowDelta > 0 && canMoveDown -> rowDelta
                                else -> 0
                            }
                            if (allowedDelta != 0) {
                                onDragByRows(allowedDelta)
                            }
                            dragOffsetY = 0f
                        },
                        onDrag = { _, dragAmount ->
                            dragOffsetY += dragAmount.y
                        }
                    )
                }
            )

            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(NordicShapes.sm)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                colorScheme.primary.copy(alpha = if (isCurrent) 0.28f else 0.16f),
                                colorScheme.secondary.copy(alpha = if (isCurrent) 0.2f else 0.1f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (song.coverArt != null) {
                    AuthedAsyncImage(
                        url = song.coverArt,
                        contentDescription = song.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                } else {
                    Text(
                        "♪",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Normal,
                        color = if (isCurrent) colorScheme.primary else colorScheme.onSurface.copy(alpha = NordicAlpha.subtle)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(NordicSpacing.xs)
            ) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isCurrent) colorScheme.primary else colorScheme.onSurface,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    song.artist ?: "Unknown",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Normal,
                    color = colorScheme.onSurface.copy(alpha = NordicAlpha.subtle),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(NordicSpacing.xs))

            QueueTextAction(
                text = "下一首",
                enabled = canPlayNext,
                colorScheme = colorScheme,
                onClick = onPlayNext
            )
            QueueIconAction(
                text = "×",
                enabled = canRemove,
                colorScheme = colorScheme,
                onClick = onRemove
            )

            if (isCurrent) {
                Text(
                    "♪",
                    style = MaterialTheme.typography.titleSmall,
                    color = colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
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
        modifier = modifier.size(28.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                "≡",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
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
            .width(58.dp)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(vertical = NordicSpacing.sm)
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

@Composable
private fun QueueIconAction(
    text: String,
    enabled: Boolean,
    colorScheme: ColorScheme,
    onClick: () -> Unit
) {
    Surface(
        color = if (enabled) {
            colorScheme.error.copy(alpha = 0.1f)
        } else {
            colorScheme.surfaceVariant.copy(alpha = 0.4f)
        },
        contentColor = if (enabled) {
            colorScheme.error
        } else {
            colorScheme.onSurface.copy(alpha = NordicAlpha.faint)
        },
        shape = NordicShapes.full,
        modifier = Modifier
            .size(32.dp)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
