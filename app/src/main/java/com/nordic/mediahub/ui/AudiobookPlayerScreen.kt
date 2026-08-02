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
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
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
import com.nordic.mediahub.data.AudiobookChapter
import com.nordic.mediahub.playback.AudiobookPlaybackState
import com.nordic.mediahub.ui.theme.NordicAlpha
import com.nordic.mediahub.ui.theme.NordicShapes
import com.nordic.mediahub.ui.theme.NordicSpacing

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
    val sortedChapters = remember(state.chapters) {
        state.chapters.sortedBy { chapter -> chapter.startSeconds }
    }
    val currentChapter = resolveCurrentAudiobookChapterFromSorted(
        sortedChapters = sortedChapters,
        positionSeconds = visiblePosition.toInt()
    )
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
        val sidePadding = if (compact) NordicSpacing.lg else NordicSpacing.xl
        val statusTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val topPadding = statusTopPadding + if (compact) NordicSpacing.sm else NordicSpacing.md
        val bottomPadding = if (compact) NordicSpacing.md else NordicSpacing.lg
        val sectionGap = NordicSpacing.md

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
                verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)
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
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (errorMessage == null) {
                        colorScheme.onSurface.copy(alpha = NordicAlpha.medium)
                    } else {
                        colorScheme.error
                    },
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(NordicSpacing.sm)) {
                    MetaChip(formatDuration(duration), colorScheme)
                    MetaChip(
                        text = formatPlaybackSpeed(state.playbackSpeed),
                        colorScheme = colorScheme,
                        enabled = playbackControlsEnabled,
                        onClick = onCyclePlaybackSpeed
                    )
                    if (currentChapter != null) {
                        MetaChip(currentChapter.title, colorScheme)
                    }
                }
            }
            Surface(
                color = colorScheme.surfaceVariant.copy(alpha = 0.5f),
                contentColor = colorScheme.onSurface,
                shape = NordicShapes.xl,
                border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.05f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(
                        start = NordicSpacing.md,
                        top = if (compact) NordicSpacing.sm else NordicSpacing.md,
                        end = NordicSpacing.md,
                        bottom = NordicSpacing.md
                    ),
                    verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)
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
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurface.copy(alpha = NordicAlpha.subtle)
                        )
                        Text(
                            formatDuration(duration),
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurface.copy(alpha = NordicAlpha.subtle)
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
                        Spacer(Modifier.size(NordicSpacing.sm))
                        AudiobookControlButton(
                            label = "-30",
                            colorScheme = colorScheme,
                            compact = compact,
                            enabled = playbackControlsEnabled,
                            onClick = onSeekBack
                        )
                        Spacer(Modifier.size(NordicSpacing.sm))
                        AudiobookPlayButton(
                            label = if (state.isPlaying) "Ⅱ" else "▶",
                            colorScheme = colorScheme,
                            compact = compact,
                            enabled = playbackControlsEnabled,
                            onClick = onPlayPause
                        )
                        Spacer(Modifier.size(NordicSpacing.sm))
                        AudiobookControlButton(
                            label = "+30",
                            colorScheme = colorScheme,
                            compact = compact,
                            enabled = playbackControlsEnabled,
                            onClick = onSeekForward
                        )
                        Spacer(Modifier.size(NordicSpacing.sm))
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
            shape = NordicShapes.xl,
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
                    AuthedAsyncImage(
                        url = coverUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.44f)
                            .aspectRatio(1f)
                            .clip(NordicShapes.xl)
                            .background(colorScheme.surface.copy(alpha = 0.62f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("▤", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Normal, color = colorScheme.primary.copy(alpha = NordicAlpha.medium))
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
            shape = NordicShapes.md,
            border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.05f)),
            modifier = Modifier.size(42.dp).clickable(onClick = onClose)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("⌄", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Normal, color = colorScheme.onSurface.copy(alpha = NordicAlpha.medium))
            }
        }
        Text(
            "有声书播放",
            style = MaterialTheme.typography.titleSmall,
            color = colorScheme.onSurface
        )
        Spacer(Modifier.size(42.dp))
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
        colorScheme.onSurface.copy(alpha = NordicAlpha.faint)
    }

    Surface(
        color = if (enabled) colorScheme.primary.copy(alpha = 0.16f) else colorScheme.surface.copy(alpha = 0.30f),
        contentColor = foreground,
        shape = NordicShapes.full,
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
        shape = NordicShapes.full,
        shadowElevation = if (enabled) 4.dp else 0.dp,
        modifier = Modifier
            .size(if (compact) 58.dp else 62.dp)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.headlineMedium,
                color = colorScheme.onPrimary
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

internal fun resolveCurrentAudiobookChapter(
    chapters: List<AudiobookChapter>,
    positionSeconds: Int
): AudiobookChapter? {
    val sortedChapters = chapters.sortedBy { chapter -> chapter.startSeconds }
    return resolveCurrentAudiobookChapterFromSorted(sortedChapters, positionSeconds)
}

internal fun resolveCurrentAudiobookChapterFromSorted(
    sortedChapters: List<AudiobookChapter>,
    positionSeconds: Int
): AudiobookChapter? {
    val safePosition = positionSeconds.coerceAtLeast(0)
    return sortedChapters.lastOrNull { chapter -> chapter.startSeconds <= safePosition }
}
