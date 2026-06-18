package com.nordic.mediahub.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.nordic.mediahub.api.NavidromeSong
import com.nordic.mediahub.data.MusicLyrics

@Composable
fun MusicPlayerScreen(
    song: NavidromeSong?,
    colorScheme: ColorScheme,
    isPlaying: Boolean,
    isBuffering: Boolean,
    playbackError: String?,
    positionSeconds: Int,
    durationSeconds: Int,
    lyrics: MusicLyrics?,
    isLyricsLoading: Boolean,
    lyricsError: String?,
    repeatMode: Int = Player.REPEAT_MODE_OFF,
    onSeek: (Int) -> Unit,
    onPlayPause: () -> Unit,
    onClose: () -> Unit,
    onSeekToNext: () -> Unit = {},
    onSeekToPrevious: () -> Unit = {},
    onToggleRepeat: () -> Unit = {},
    onOpenQueue: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val resolvedDurationSeconds = maxOf(durationSeconds, song?.duration ?: 0, 1)
    var scrubPosition by remember(song?.id) { mutableStateOf<Float?>(null) }
    var showLyrics by rememberSaveable(song?.id) { mutableStateOf(false) }
    val hasSong = song?.streamUrl?.isNotBlank() == true
    val visiblePosition = scrubPosition ?: positionSeconds.toFloat()
    val playbackStatus = when {
        playbackError != null -> playbackError
        isBuffering -> "正在缓冲"
        isPlaying -> "正在播放"
        song != null && !hasSong -> "这首歌缺少播放地址"
        else -> null
    }

    BoxWithConstraints(
        modifier = modifier
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
            PlayerTopBar(
                album = song?.album ?: "音乐库",
                colorScheme = colorScheme,
                modeLabel = if (showLyrics) "封" else "词",
                onClose = onClose,
                onModeToggle = { showLyrics = !showLyrics }
            )
            PlayerPrimaryDisplay(
                song = song,
                lyrics = lyrics,
                isLyricsLoading = isLyricsLoading,
                lyricsError = lyricsError,
                positionSeconds = visiblePosition.toInt(),
                showLyrics = showLyrics,
                colorScheme = colorScheme,
                compact = compact,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
            PlayerTrackInfo(
                song = song,
                colorScheme = colorScheme,
                compact = compact,
                playbackStatus = playbackStatus
            )
            PlayerConsole(
                hasSong = hasSong,
                isPlaying = isPlaying,
                position = visiblePosition.coerceIn(0f, resolvedDurationSeconds.toFloat()),
                duration = resolvedDurationSeconds,
                colorScheme = colorScheme,
                compact = compact,
                onPositionChange = { scrubPosition = it },
                onPositionChangeFinished = {
                    val target = scrubPosition ?: visiblePosition
                    onSeek(target.toInt())
                    scrubPosition = null
                },
                onPlayPause = onPlayPause,
                repeatMode = repeatMode,
                onSeekToNext = onSeekToNext,
                onSeekToPrevious = onSeekToPrevious,
                onToggleRepeat = onToggleRepeat,
                onOpenQueue = onOpenQueue
            )
        }
    }
}

@Composable
private fun PlayerTopBar(
    album: String,
    colorScheme: ColorScheme,
    modeLabel: String,
    onClose: () -> Unit,
    onModeToggle: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(42.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlayerTopButton("⌄", colorScheme, onClick = onClose)
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                "正在播放",
                fontSize = 12.sp,
                color = colorScheme.onSurface.copy(alpha = 0.54f),
                fontWeight = FontWeight.Medium
            )
            Text(
                album,
                fontSize = 14.sp,
                color = colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        PlayerTopButton(modeLabel, colorScheme, onClick = onModeToggle)
    }
}

@Composable
private fun PlayerTopButton(
    label: String,
    colorScheme: ColorScheme,
    onClick: () -> Unit
) {
    Surface(
        color = colorScheme.surfaceVariant.copy(alpha = 0.58f),
        contentColor = colorScheme.onSurface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.05f)),
        modifier = Modifier.size(42.dp).clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, fontSize = 22.sp, color = colorScheme.onSurface.copy(alpha = 0.76f))
        }
    }
}

@Composable
private fun PlayerPrimaryDisplay(
    song: NavidromeSong?,
    lyrics: MusicLyrics?,
    isLyricsLoading: Boolean,
    lyricsError: String?,
    positionSeconds: Int,
    showLyrics: Boolean,
    colorScheme: ColorScheme,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    if (showLyrics) {
        PlayerLyricsDisplay(
            lyrics = lyrics,
            isLoading = isLyricsLoading,
            error = lyricsError,
            positionSeconds = positionSeconds,
            colorScheme = colorScheme,
            compact = compact,
            modifier = modifier
        )
    } else {
        PlayerArtwork(
            song = song,
            colorScheme = colorScheme,
            modifier = modifier
        )
    }
}

@Composable
private fun PlayerArtwork(
    song: NavidromeSong?,
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
                if (song?.coverArt != null) {
                    AsyncImage(
                        model = song.coverArt,
                        contentDescription = song.title,
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
                        Text("♪", fontSize = 54.sp, color = colorScheme.primary.copy(alpha = 0.72f))
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerLyricsDisplay(
    lyrics: MusicLyrics?,
    isLoading: Boolean,
    error: String?,
    positionSeconds: Int,
    colorScheme: ColorScheme,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val lineCount = if (compact) 5 else 7
    val visibleLines = remember(lyrics, positionSeconds, lineCount) {
        selectVisibleLyricLines(lyrics, positionSeconds, lineCount)
    }

    Surface(
        color = colorScheme.surfaceVariant.copy(alpha = 0.44f),
        contentColor = colorScheme.onSurface,
        shape = RoundedCornerShape(30.dp),
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.05f)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            colorScheme.surface.copy(alpha = 0.9f),
                            colorScheme.primary.copy(alpha = 0.08f),
                            colorScheme.secondary.copy(alpha = 0.05f)
                        )
                    )
                )
                .padding(horizontal = 22.dp, vertical = if (compact) 16.dp else 22.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> PlayerLyricsStatus("正在加载歌词", colorScheme)
                visibleLines.isEmpty() -> PlayerLyricsStatus(error ?: "暂无歌词", colorScheme)
                else -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 11.dp)
                    ) {
                        visibleLines.forEach { line ->
                            Text(
                                line.text,
                                fontSize = if (line.active) {
                                    if (compact) 19.sp else 22.sp
                                } else {
                                    if (compact) 14.sp else 16.sp
                                },
                                lineHeight = if (line.active) {
                                    if (compact) 23.sp else 27.sp
                                } else {
                                    if (compact) 18.sp else 20.sp
                                },
                                color = if (line.active) {
                                    colorScheme.onSurface
                                } else {
                                    colorScheme.onSurface.copy(alpha = 0.46f)
                                },
                                fontWeight = if (line.active) FontWeight.Bold else FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerLyricsStatus(
    text: String,
    colorScheme: ColorScheme
) {
    Text(
        text,
        fontSize = 15.sp,
        color = colorScheme.onSurface.copy(alpha = 0.48f),
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun PlayerTrackInfo(
    song: NavidromeSong?,
    colorScheme: ColorScheme,
    compact: Boolean,
    playbackStatus: String?
) {
    val subtitle = playbackStatus ?: song?.artist ?: "从最近播放选择一首歌"
    val subtitleColor = when {
        playbackStatus == null -> colorScheme.onSurface.copy(alpha = 0.62f)
        playbackStatus.startsWith("播放失败") || playbackStatus.contains("缺少") -> colorScheme.error
        else -> colorScheme.primary.copy(alpha = 0.76f)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)
    ) {
        Text(
            song?.title ?: "等待播放",
            fontSize = if (compact) 22.sp else 25.sp,
            lineHeight = if (compact) 26.sp else 30.sp,
            color = colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = if (compact) 1 else 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            subtitle,
            fontSize = 14.sp,
            color = subtitleColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlayerMetaChip(song?.album ?: "Nordic", colorScheme)
            PlayerMetaChip(formatDuration(song?.duration ?: 0), colorScheme)
        }
    }
}

@Composable
private fun PlayerConsole(
    hasSong: Boolean,
    isPlaying: Boolean,
    position: Float,
    duration: Int,
    colorScheme: ColorScheme,
    compact: Boolean,
    onPositionChange: (Float) -> Unit,
    onPositionChangeFinished: () -> Unit,
    onPlayPause: () -> Unit,
    repeatMode: Int = Player.REPEAT_MODE_OFF,
    onSeekToNext: () -> Unit = {},
    onSeekToPrevious: () -> Unit = {},
    onToggleRepeat: () -> Unit = {},
    onOpenQueue: () -> Unit = {}
) {
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
                value = position,
                onValueChange = onPositionChange,
                onValueChangeFinished = onPositionChangeFinished,
                enabled = hasSong,
                valueRange = 0f..duration.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = colorScheme.primary,
                    activeTrackColor = colorScheme.primary,
                    inactiveTrackColor = colorScheme.onSurface.copy(alpha = 0.13f),
                    disabledThumbColor = colorScheme.onSurface.copy(alpha = 0.2f),
                    disabledActiveTrackColor = colorScheme.onSurface.copy(alpha = 0.16f),
                    disabledInactiveTrackColor = colorScheme.onSurface.copy(alpha = 0.08f)
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    formatDuration(position.toInt()),
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val repeatLabel = when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> "↺1"
                    Player.REPEAT_MODE_ALL -> "↺A"
                    else -> "↺"
                }
                val repeatActive = repeatMode != Player.REPEAT_MODE_OFF
                PlayerControlButton(repeatLabel, colorScheme, size = if (compact) 38 else 42, enabled = hasSong, active = repeatActive, onClick = onToggleRepeat)
                PlayerControlButton("‹", colorScheme, size = if (compact) 46 else 50, enabled = hasSong, onClick = onSeekToPrevious)
                PlayerControlButton(
                    label = if (isPlaying) "Ⅱ" else "▶",
                    colorScheme = colorScheme,
                    size = if (compact) 62 else 68,
                    filled = true,
                    enabled = hasSong,
                    onClick = onPlayPause
                )
                PlayerControlButton("›", colorScheme, size = if (compact) 46 else 50, enabled = hasSong, onClick = onSeekToNext)
                PlayerControlButton("≡", colorScheme, size = if (compact) 38 else 42, enabled = hasSong, onClick = onOpenQueue)
            }
        }
    }
}

@Composable
private fun PlayerMetaChip(
    text: String,
    colorScheme: ColorScheme
) {
    Surface(
        color = colorScheme.surfaceVariant.copy(alpha = 0.62f),
        contentColor = colorScheme.onSurface,
        shape = RoundedCornerShape(999.dp)
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
private fun PlayerControlButton(
    label: String,
    colorScheme: ColorScheme,
    size: Int,
    filled: Boolean = false,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit = {}
) {
    val background = when {
        filled && enabled -> colorScheme.primary
        filled -> colorScheme.primary.copy(alpha = 0.32f)
        active && enabled -> colorScheme.primary.copy(alpha = 0.18f)
        else -> colorScheme.surface.copy(alpha = if (enabled) 0.72f else 0.34f)
    }
    val foreground = when {
        filled -> colorScheme.onPrimary
        active && enabled -> colorScheme.primary
        enabled -> colorScheme.onSurface.copy(alpha = 0.76f)
        else -> colorScheme.onSurface.copy(alpha = 0.28f)
    }

    Surface(
        color = background,
        contentColor = foreground,
        shape = RoundedCornerShape(999.dp),
        shadowElevation = if (filled && enabled) 4.dp else 0.dp,
        modifier = Modifier
            .size(size.dp)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                fontSize = if (filled) 22.sp else 18.sp,
                color = foreground,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private data class VisibleLyricLine(
    val text: String,
    val active: Boolean
)

private fun selectVisibleLyricLines(
    lyrics: MusicLyrics?,
    positionSeconds: Int,
    maxLineCount: Int
): List<VisibleLyricLine> {
    val lines = lyrics?.lines?.filter { it.text.isNotBlank() }.orEmpty()
    if (lines.isEmpty()) return emptyList()

    if (lyrics?.synced != true) {
        return lines.take(maxLineCount).map { VisibleLyricLine(it.text, active = false) }
    }

    val positionMillis = positionSeconds.coerceAtLeast(0) * 1000
    val activeIndex = lines.indexOfLast { line ->
        line.startMillis != null && line.startMillis <= positionMillis
    }.coerceAtLeast(0)
    val halfWindow = maxLineCount / 2
    val startIndex = when {
        activeIndex + halfWindow >= lines.size -> (lines.size - maxLineCount).coerceAtLeast(0)
        else -> (activeIndex - halfWindow).coerceAtLeast(0)
    }

    return lines
        .drop(startIndex)
        .take(maxLineCount)
        .mapIndexed { index, line ->
            VisibleLyricLine(
                text = line.text,
                active = startIndex + index == activeIndex
            )
        }
}
