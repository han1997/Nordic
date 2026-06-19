package com.nordic.mediahub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nordic.mediahub.api.NavidromeSong

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
    onDismiss: () -> Unit
) {
    val resolvedCurrentIndex = currentIndex.takeIf { it in queue.indices } ?: -1
    val upcomingCount = if (resolvedCurrentIndex >= 0) {
        (queue.lastIndex - resolvedCurrentIndex).coerceAtLeast(0)
    } else {
        0
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier.padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    itemsIndexed(queue, key = { index, song -> "${song.id}:$index" }) { index, song ->
                        val isCurrent = index == resolvedCurrentIndex
                        QueueRow(
                            song = song,
                            isCurrent = isCurrent,
                            canPlayNext = resolvedCurrentIndex >= 0 &&
                                !isCurrent &&
                                index != resolvedCurrentIndex + 1,
                            canRemove = queue.size > 1,
                            colorScheme = colorScheme,
                            onClick = { onSeekToIndex(index) },
                            onPlayNext = { onPlayNext(index) },
                            onRemove = { onRemoveFromQueue(index) }
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
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                "播放队列",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                queueSubtitle(queueSize, currentIndex, upcomingCount),
                fontSize = 13.sp,
                color = colorScheme.onSurface.copy(alpha = 0.56f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(12.dp))
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
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    ) {
        Text(
            "当前没有播放队列",
            fontSize = 14.sp,
            color = colorScheme.onSurface.copy(alpha = 0.58f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 28.dp)
        )
    }
}

@Composable
private fun QueueRow(
    song: NavidromeSong,
    isCurrent: Boolean,
    canPlayNext: Boolean,
    canRemove: Boolean,
    colorScheme: ColorScheme,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onRemove: () -> Unit
) {
    val backgroundColor = if (isCurrent) {
        colorScheme.primary.copy(alpha = 0.1f)
    } else {
        colorScheme.surface
    }

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
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
                    AsyncImage(
                        model = song.coverArt,
                        contentDescription = song.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                } else {
                    Text(
                        "♪",
                        fontSize = 16.sp,
                        color = if (isCurrent) colorScheme.primary else colorScheme.onSurface.copy(alpha = 0.48f)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    song.title,
                    fontSize = 14.sp,
                    color = if (isCurrent) colorScheme.primary else colorScheme.onSurface,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    song.artist ?: "Unknown",
                    fontSize = 12.sp,
                    color = colorScheme.onSurface.copy(alpha = 0.52f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(2.dp))

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
                    fontSize = 14.sp,
                    color = colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
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
            colorScheme.onSurface.copy(alpha = 0.3f)
        },
        shape = RoundedCornerShape(999.dp),
        modifier = Modifier
            .width(58.dp)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(vertical = 7.dp)
        ) {
            Text(
                text,
                fontSize = 12.sp,
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
            colorScheme.onSurface.copy(alpha = 0.3f)
        },
        shape = RoundedCornerShape(999.dp),
        modifier = Modifier
            .size(32.dp)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
