package com.nordic.mediahub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.nordic.mediahub.playback.AudiobookPlaybackState

@Composable
fun AudiobookPlayerScreen(
    state: AudiobookPlaybackState,
    colorScheme: ColorScheme,
    externalError: String? = null,
    onSeek: (Int) -> Unit,
    onSeekBack: () -> Unit = {},
    onSeekForward: () -> Unit = {},
    onSeekToPreviousChapter: () -> Unit = {},
    onSeekToNextChapter: () -> Unit = {},
    onCyclePlaybackSpeed: () -> Unit = {},
    onPlayPause: () -> Unit,
    onClose: () -> Unit
) {
    val session = state.session
    val duration = state.durationSeconds.coerceAtLeast(1)
    var scrubPosition by remember(session?.sessionId) { mutableStateOf<Float?>(null) }
    val visiblePosition = scrubPosition ?: state.positionSeconds.toFloat()
    val errorMessage = externalError ?: state.errorMessage
    val chapterNavigationEnabled = session != null && state.chapters.isNotEmpty()
    val playbackControlsEnabled = session != null
    val statusText = when {
        errorMessage != null -> errorMessage
        state.isBuffering -> "正在缓冲"
        state.isPlaying -> "正在播放"
        else -> "已暂停"
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        colorScheme.primary.copy(alpha = 0.16f),
                        colorScheme.secondary.copy(alpha = 0.06f),
                        colorScheme.background,
                        colorScheme.background
                    )
                )
            )
    ) {
        val compact = maxHeight < 740.dp
        val sidePadding = if (compact) 18.dp else 20.dp
        val statusTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val topPadding = statusTopPadding + if (compact) 8.dp else 12.dp
        val bottomPadding = if (compact) 12.dp else 18.dp
        val sectionGap = if (compact) 10.dp else 14.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(
                    start = sidePadding,
                    top = topPadding,
                    end = sidePadding,
                    bottom = bottomPadding
                ),
            verticalArrangement = Arrangement.spacedBy(sectionGap)
        ) {
            AudiobookPlayerTopBar(colorScheme = colorScheme, onClose = onClose)
            AudiobookPrimaryDisplay(
                title = session?.displayTitle ?: "有声书播放",
                coverUrl = session?.coverUrl,
                colorScheme = colorScheme,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    session?.displayTitle ?: "等待播放",
                    fontSize = if (compact) 22.sp else 25.sp,
                    lineHeight = if (compact) 26.sp else 30.sp,
                    color = colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    session?.displayAuthor?.takeIf { it.isNotBlank() } ?: statusText,
                    fontSize = 14.sp,
                    color = if (errorMessage == null) {
                        colorScheme.onSurface.copy(alpha = 0.64f)
                    } else {
                        colorScheme.error
                    },
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AudiobookPlayerMetaChip(formatDuration(duration), colorScheme)
                    AudiobookPlayerMetaChip(
                        text = formatPlaybackSpeed(state.playbackSpeed),
                        colorScheme = colorScheme,
                        enabled = playbackControlsEnabled,
                        onClick = onCyclePlaybackSpeed
                    )
                    val currentChapter = state.chapters.lastOrNull { chapter ->
                        chapter.startSeconds <= state.positionSeconds
                    }
                    if (currentChapter != null) {
                        AudiobookPlayerMetaChip(currentChapter.title, colorScheme)
                    }
                }
            }
            Surface(
                color = colorScheme.surfaceVariant.copy(alpha = 0.5f),
                contentColor = colorScheme.onSurface,
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.05f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(
                        start = 14.dp,
                        top = if (compact) 8.dp else 10.dp,
                        end = 14.dp,
                        bottom = if (compact) 12.dp else 14.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 9.dp)
                ) {
                    Slider(
                        value = visiblePosition.coerceIn(0f, duration.toFloat()),
                        onValueChange = { scrubPosition = it },
                        onValueChangeFinished = {
                            val target = scrubPosition ?: visiblePosition
                            onSeek(target.toInt())
                            scrubPosition = null
                        },
                        valueRange = 0f..duration.toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = colorScheme.primary,
                            activeTrackColor = colorScheme.primary,
                            inactiveTrackColor = colorScheme.onSurface.copy(alpha = 0.13f)
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            formatDuration(visiblePosition.toInt()),
                            fontSize = 12.sp,
                            color = colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Text(
                            formatDuration(duration),
                            fontSize = 12.sp,
                            color = colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AudiobookControlButton(
                            label = "≪",
                            colorScheme = colorScheme,
                            compact = compact,
                            enabled = chapterNavigationEnabled,
                            onClick = onSeekToPreviousChapter
                        )
                        Spacer(Modifier.size(if (compact) 6.dp else 8.dp))
                        AudiobookControlButton(
                            label = "-30",
                            colorScheme = colorScheme,
                            compact = compact,
                            enabled = playbackControlsEnabled,
                            onClick = onSeekBack
                        )
                        Spacer(Modifier.size(if (compact) 6.dp else 8.dp))
                        AudiobookPlayButton(
                            label = if (state.isPlaying) "Ⅱ" else "▶",
                            colorScheme = colorScheme,
                            compact = compact,
                            enabled = playbackControlsEnabled,
                            onClick = onPlayPause
                        )
                        Spacer(Modifier.size(if (compact) 6.dp else 8.dp))
                        AudiobookControlButton(
                            label = "+30",
                            colorScheme = colorScheme,
                            compact = compact,
                            enabled = playbackControlsEnabled,
                            onClick = onSeekForward
                        )
                        Spacer(Modifier.size(if (compact) 6.dp else 8.dp))
                        AudiobookControlButton(
                            label = "≫",
                            colorScheme = colorScheme,
                            compact = compact,
                            enabled = chapterNavigationEnabled,
                            onClick = onSeekToNextChapter
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AudiobookPrimaryDisplay(
    title: String,
    coverUrl: String?,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val side = minOf(maxWidth, maxHeight)

        Surface(
            color = colorScheme.surfaceVariant.copy(alpha = 0.48f),
            shape = RoundedCornerShape(30.dp),
            shadowElevation = 10.dp,
            border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.06f)),
            modifier = Modifier.size(side)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                colorScheme.primary.copy(alpha = 0.22f),
                                colorScheme.secondary.copy(alpha = 0.14f),
                                colorScheme.surfaceVariant.copy(alpha = 0.82f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (coverUrl != null) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.44f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(28.dp))
                            .background(colorScheme.surface.copy(alpha = 0.62f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("▤", fontSize = 54.sp, color = colorScheme.primary.copy(alpha = 0.72f))
                    }
                }
            }
        }
    }
}

@Composable
private fun AudiobookPlayerTopBar(
    colorScheme: ColorScheme,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = colorScheme.surfaceVariant.copy(alpha = 0.58f),
            contentColor = colorScheme.onSurface,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.05f)),
            modifier = Modifier.size(42.dp).clickable(onClick = onClose)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("⌄", fontSize = 22.sp, color = colorScheme.onSurface.copy(alpha = 0.76f))
            }
        }
        Text(
            "有声书播放",
            fontSize = 14.sp,
            color = colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.size(42.dp))
    }
}

@Composable
private fun AudiobookPlayerMetaChip(
    text: String,
    colorScheme: ColorScheme,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val chipModifier = if (onClick != null) {
        Modifier.clickable(enabled = enabled, onClick = onClick)
    } else {
        Modifier
    }

    Surface(
        color = if (enabled) colorScheme.surfaceVariant.copy(alpha = 0.62f) else colorScheme.surface.copy(alpha = 0.30f),
        contentColor = colorScheme.onSurface,
        shape = RoundedCornerShape(999.dp),
        modifier = chipModifier
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            fontSize = 11.sp,
            color = colorScheme.onSurface.copy(alpha = 0.62f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AudiobookControlButton(
    label: String,
    colorScheme: ColorScheme,
    compact: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val foreground = if (enabled) {
        colorScheme.primary
    } else {
        colorScheme.onSurface.copy(alpha = 0.28f)
    }

    Surface(
        color = if (enabled) colorScheme.primary.copy(alpha = 0.16f) else colorScheme.surface.copy(alpha = 0.30f),
        contentColor = foreground,
        shape = RoundedCornerShape(999.dp),
        modifier = Modifier
            .size(if (compact) 42.dp else 46.dp)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                fontSize = if (label.length > 2) 15.sp else 20.sp,
                color = foreground,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun AudiobookPlayButton(
    label: String,
    colorScheme: ColorScheme,
    compact: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (enabled) colorScheme.primary else colorScheme.primary.copy(alpha = 0.32f),
        contentColor = colorScheme.onPrimary,
        shape = RoundedCornerShape(999.dp),
        shadowElevation = if (enabled) 4.dp else 0.dp,
        modifier = Modifier
            .size(if (compact) 58.dp else 62.dp)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                fontSize = 22.sp,
                color = colorScheme.onPrimary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun formatPlaybackSpeed(speed: Float): String {
    val safeSpeed = speed.takeIf { it.isFinite() && it > 0f } ?: 1f
    val rounded = kotlin.math.round(safeSpeed * 100f) / 100f
    return when (rounded) {
        1f -> "1x"
        1.5f -> "1.5x"
        2f -> "2x"
        else -> "${rounded}x"
    }
}
