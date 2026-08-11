package com.nordic.mediahub.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.nordic.mediahub.data.MusicLyrics
import com.nordic.mediahub.data.NavidromeSong
import com.nordic.mediahub.ui.theme.NordicAlpha
import com.nordic.mediahub.ui.theme.NordicShapes
import com.nordic.mediahub.ui.theme.NordicSpacing
import kotlin.math.roundToInt

@Composable
fun MusicPlayerScreen(
    song: NavidromeSong?,
    colorScheme: ColorScheme,
    isPlaying: Boolean,
    isBuffering: Boolean,
    playbackError: String?,
    positionSeconds: Int,
    positionMillis: Long,
    durationSeconds: Int,
    lyrics: MusicLyrics?,
    isLyricsLoading: Boolean,
    lyricsError: String?,
    repeatMode: Int = Player.REPEAT_MODE_OFF,
    shuffleModeEnabled: Boolean = false,
    onSeek: (Int) -> Unit,
    onPlayPause: () -> Unit,
    onClose: () -> Unit,
    onSeekBack: () -> Unit = {},
    onSeekForward: () -> Unit = {},
    onSeekToNext: () -> Unit = {},
    onSeekToPrevious: () -> Unit = {},
    onToggleRepeat: () -> Unit = {},
    onToggleShuffle: () -> Unit = {},
    onOpenQueue: () -> Unit = {},
    onToggleFavorite: (songId: String, starred: Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val resolvedDurationSeconds = maxOf(durationSeconds, song?.duration ?: 0, 1)
    var scrubPosition by remember(song?.id) { mutableStateOf<Float?>(null) }
    var showLyrics by rememberSaveable(song?.id) { mutableStateOf(false) }
    val hasSong = song?.streamUrl?.isNotBlank() == true
    val visiblePosition = scrubPosition ?: positionSeconds.toFloat()
    val playbackStatusIsError = playbackError != null || (song != null && !hasSong)
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
                        colorScheme.primary.copy(alpha = 0.14f),
                        colorScheme.secondary.copy(alpha = 0.10f),
                        colorScheme.surfaceVariant.copy(alpha = 0.26f),
                        colorScheme.surfaceVariant.copy(alpha = 0.50f),
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
        val sectionGap = if (compact) NordicSpacing.sm else NordicSpacing.md
        val swipeThreshold = maxHeight * 0.5f

        if (song?.coverArt != null) {
            AuthedAsyncImage(
                url = song.coverArt,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.16f)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                colorScheme.background.copy(alpha = 0.30f),
                                colorScheme.background.copy(alpha = 0.78f),
                                colorScheme.background
                            )
                        )
                    )
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    var dragStartedInTopHalf = false
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            dragStartedInTopHalf = offset.y < swipeThreshold.toPx()
                        },
                        onDragEnd = { },
                        onDragCancel = { }
                    ) { change, dragAmountY ->
                        change.consume()
                        val netDown = dragAmountY
                        if (netDown > 1f) {
                            if (dragStartedInTopHalf && netDown > 6f) {
                                onClose()
                            }
                        }
                    }
                }
        ) {
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
                    colorScheme = colorScheme,
                    compact = compact,
                    onClose = onClose
                )
                PlayerPrimaryDisplay(
                    song = song,
                    lyrics = lyrics,
                    isLyricsLoading = isLyricsLoading,
                    lyricsError = lyricsError,
                    positionMillis = positionMillis,
                    showLyrics = showLyrics,
                    colorScheme = colorScheme,
                    compact = compact,
                    onToggleDisplay = { showLyrics = !showLyrics },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
                PlayerTitleMetaRow(
                    song = song,
                    isFavorite = resolveFavoriteDisplay(song),
                    colorScheme = colorScheme,
                    compact = compact,
                    onToggleFavorite = { id, starred -> onToggleFavorite(id, starred) },
                    onOpenQueue = onOpenQueue
                )
                PlayerConsole(
                    hasSong = hasSong,
                    isPlaying = isPlaying,
                    position = visiblePosition.coerceIn(0f, resolvedDurationSeconds.toFloat()),
                    duration = resolvedDurationSeconds,
                    colorScheme = colorScheme,
                    compact = compact,
                    playbackStatus = playbackStatus,
                    playbackStatusIsError = playbackStatusIsError,
                    onPositionChange = { scrubPosition = it },
                    onPositionChangeFinished = {
                        val target = scrubPosition ?: visiblePosition
                        onSeek(target.toInt())
                        scrubPosition = null
                    },
                    onPositionChangeCanceled = { scrubPosition = null },
                    onPlayPause = onPlayPause,
                    repeatMode = repeatMode,
                    shuffleModeEnabled = shuffleModeEnabled,
                    onSeekBack = onSeekBack,
                    onSeekForward = onSeekForward,
                    onSeekToNext = onSeekToNext,
                    onSeekToPrevious = onSeekToPrevious,
                    onToggleRepeat = onToggleRepeat,
                    onToggleShuffle = onToggleShuffle
                )
            }
        }
    }
}

@Composable
private fun PlayerTopBar(
    colorScheme: ColorScheme,
    compact: Boolean,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 44.dp else 52.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlayerIconButton(
            icon = Icons.Filled.KeyboardArrowDown,
            colorScheme = colorScheme,
            size = 42.dp,
            onClick = onClose,
            contentDescription = "关闭播放页"
        )
        Text(
            text = "正在播放",
            style = MaterialTheme.typography.labelLarge,
            color = colorScheme.onSurface.copy(alpha = NordicAlpha.medium),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        // Right-side placeholder keeps the label centered (no more button here per Option B scope).
        Spacer(modifier = Modifier.size(42.dp))
    }
}

@Composable
private fun PlayerPrimaryDisplay(
    song: NavidromeSong?,
    lyrics: MusicLyrics?,
    isLyricsLoading: Boolean,
    lyricsError: String?,
    positionMillis: Long,
    showLyrics: Boolean,
    colorScheme: ColorScheme,
    compact: Boolean,
    onToggleDisplay: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures(onTap = { onToggleDisplay() })
        }
    ) {
        if (showLyrics) {
            PlayerLyricsDisplay(
                lyrics = lyrics,
                isLoading = isLyricsLoading,
                error = lyricsError,
                positionMillis = positionMillis,
                colorScheme = colorScheme,
                compact = compact,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            PlayerArtwork(
                song = song,
                colorScheme = colorScheme,
                modifier = Modifier.fillMaxSize()
            )
        }
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
            shape = NordicShapes.xl,
            shadowElevation = 12.dp,
            border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.06f)),
            modifier = Modifier.size(side)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                colorScheme.primary.copy(alpha = 0.24f),
                                colorScheme.secondary.copy(alpha = 0.14f),
                                colorScheme.surfaceVariant.copy(alpha = 0.82f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (song?.coverArt != null) {
                    AuthedAsyncImage(
                        url = song.coverArt,
                        contentDescription = song.title,
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
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = colorScheme.primary.copy(alpha = NordicAlpha.medium),
                            modifier = Modifier.size(42.dp)
                        )
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
    positionMillis: Long,
    colorScheme: ColorScheme,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val lineCount = if (compact) 5 else 7
    val visibleLines = remember(lyrics, positionMillis, lineCount) {
        selectVisibleLyricLines(lyrics, positionMillis, lineCount)
    }

    Box(
        modifier = modifier
            .clip(NordicShapes.xl)
            .background(
                Brush.linearGradient(
                    listOf(
                        colorScheme.surfaceVariant.copy(alpha = 0.50f),
                        colorScheme.primary.copy(alpha = 0.10f),
                        colorScheme.secondary.copy(alpha = 0.06f),
                        colorScheme.surfaceVariant.copy(alpha = 0.42f)
                    )
                )
            )
            .padding(
                horizontal = NordicSpacing.xl,
                vertical = if (compact) NordicSpacing.lg else NordicSpacing.xl
            ),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> PlayerLyricsStatus("正在加载歌词", colorScheme)
            visibleLines.isEmpty() -> PlayerLyricsStatus(error ?: "暂无歌词", colorScheme)
            else -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(
                        if (compact) NordicSpacing.sm else NordicSpacing.md
                    )
                ) {
                    resolveLyricsModeLabel(lyrics)?.let { label ->
                        Text(
                            label,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.primary.copy(alpha = NordicAlpha.medium),
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
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
                                colorScheme.onSurface.copy(alpha = NordicAlpha.subtle)
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

@Composable
private fun PlayerLyricsStatus(
    text: String,
    colorScheme: ColorScheme
) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = colorScheme.onSurface.copy(alpha = NordicAlpha.subtle),
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun PlayerTitleMetaRow(
    song: NavidromeSong?,
    isFavorite: Boolean,
    colorScheme: ColorScheme,
    compact: Boolean,
    onToggleFavorite: (String, Boolean) -> Unit,
    onOpenQueue: () -> Unit
) {
    val title = song?.title ?: "等待播放"
    val artist = song?.artist ?: "音乐库"

    Column(verticalArrangement = Arrangement.spacedBy(NordicSpacing.xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(NordicSpacing.xs)
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurface.copy(alpha = NordicAlpha.medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // favorite ♥
            PlayerIconButton(
                icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                colorScheme = colorScheme,
                size = if (compact) 36.dp else 40.dp,
                tint = if (isFavorite) colorScheme.primary else colorScheme.onSurface.copy(alpha = NordicAlpha.medium),
                onClick = {
                    val id = song?.id
                    if (!id.isNullOrBlank()) {
                        onToggleFavorite(id, !isFavorite)
                    }
                },
                contentDescription = if (isFavorite) "取消收藏" else "收藏"
            )
        }
        // secondary meta: queue (right-aligned) — moved out of primary control row per Option B.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayerIconButton(
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                colorScheme = colorScheme,
                size = 36.dp,
                tint = colorScheme.onSurface.copy(alpha = NordicAlpha.medium),
                onClick = onOpenQueue,
                contentDescription = "打开播放队列"
            )
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
    playbackStatus: String?,
    playbackStatusIsError: Boolean,
    onPositionChange: (Float) -> Unit,
    onPositionChangeFinished: () -> Unit,
    onPositionChangeCanceled: () -> Unit,
    onPlayPause: () -> Unit,
    repeatMode: Int = Player.REPEAT_MODE_OFF,
    shuffleModeEnabled: Boolean = false,
    onSeekBack: () -> Unit = {},
    onSeekForward: () -> Unit = {},
    onSeekToNext: () -> Unit = {},
    onSeekToPrevious: () -> Unit = {},
    onToggleRepeat: () -> Unit = {},
    onToggleShuffle: () -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)) {
        PlayerThinSlider(
            position = position,
            duration = duration,
            colorScheme = colorScheme,
            enabled = hasSong,
            onPositionChange = onPositionChange,
            onPositionChangeFinished = onPositionChangeFinished,
            onPositionChangeCanceled = onPositionChangeCanceled
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                formatDuration(position.toInt()),
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurface.copy(alpha = NordicAlpha.subtle)
            )
            if (playbackStatus != null) {
                Text(
                    playbackStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (playbackStatusIsError) colorScheme.error
                    else colorScheme.onSurface.copy(alpha = NordicAlpha.subtle)
                )
            }
            Text(
                formatDuration(duration),
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurface.copy(alpha = NordicAlpha.subtle)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val repeatActive = repeatMode != Player.REPEAT_MODE_OFF
            val repeatIcon: ImageVector = when (repeatMode) {
                Player.REPEAT_MODE_ONE -> Icons.Filled.RepeatOne
                else -> Icons.Filled.Repeat
            }
            val sideButtonSize: Dp = if (compact) 32.dp else 36.dp
            val skipButtonSize: Dp = if (compact) 38.dp else 42.dp
            PlayerIconButton(
                icon = Icons.Filled.Shuffle,
                colorScheme = colorScheme,
                size = sideButtonSize,
                enabled = hasSong,
                active = shuffleModeEnabled,
                onClick = onToggleShuffle,
                contentDescription = "随机播放"
            )
            PlayerIconButton(
                icon = Icons.Filled.SkipPrevious,
                colorScheme = colorScheme,
                size = skipButtonSize,
                enabled = hasSong,
                onClick = onSeekToPrevious,
                contentDescription = "上一首"
            )
            PlayerIconButton(
                icon = Icons.Filled.FastRewind,
                colorScheme = colorScheme,
                size = sideButtonSize,
                enabled = hasSong,
                onClick = onSeekBack,
                contentDescription = "后退 10 秒"
            )
            PlayerIconButton(
                icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                colorScheme = colorScheme,
                size = if (compact) 62.dp else 68.dp,
                filled = true,
                enabled = hasSong,
                onClick = onPlayPause,
                contentDescription = if (isPlaying) "暂停" else "播放"
            )
            PlayerIconButton(
                icon = Icons.Filled.FastForward,
                colorScheme = colorScheme,
                size = sideButtonSize,
                enabled = hasSong,
                onClick = onSeekForward,
                contentDescription = "前进 30 秒"
            )
            PlayerIconButton(
                icon = Icons.Filled.SkipNext,
                colorScheme = colorScheme,
                size = skipButtonSize,
                enabled = hasSong,
                onClick = onSeekToNext,
                contentDescription = "下一首"
            )
            PlayerIconButton(
                icon = repeatIcon,
                colorScheme = colorScheme,
                size = sideButtonSize,
                enabled = hasSong,
                active = repeatActive,
                showOneBadge = repeatMode == Player.REPEAT_MODE_ONE,
                onClick = onToggleRepeat,
                contentDescription = "循环模式"
            )
        }
    }
}

/**
 * Thin-line progress bar with a small circular thumb — replaces the stock Material3 [androidx.compose.material3.Slider]
 * to match the mainstream music-app aesthetic. Keeps the scrub-local-state contract:
 * onPositionChange fires during drag, onPositionChangeFinished fires on release and is
 * the only place a real [onSeek] should land.
 */
@Composable
private fun PlayerThinSlider(
    position: Float,
    duration: Int,
    colorScheme: ColorScheme,
    enabled: Boolean,
    onPositionChange: (Float) -> Unit,
    onPositionChangeFinished: () -> Unit,
    onPositionChangeCanceled: () -> Unit
) {
    val safeDuration = maxOf(duration, 1)
    val progress = (position / safeDuration).coerceIn(0f, 1f)
    val trackHeight = 4.dp
    val thumbSize = 12.dp

    val activeColor = if (enabled) colorScheme.primary else colorScheme.onSurface.copy(alpha = NordicAlpha.faint)
    val inactiveColor = colorScheme.onSurface.copy(alpha = if (enabled) 0.14f else 0.08f)
    val thumbColor = if (enabled) colorScheme.primary else colorScheme.onSurface.copy(alpha = 0.2f)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(thumbSize + trackHeight) // touch target room
            .pointerInput(enabled, safeDuration) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset ->
                        onPositionChange(resolvePlayerThinSliderPosition(offset.x, size.width, safeDuration))
                    },
                    onDragEnd = { onPositionChangeFinished() },
                    onDragCancel = { onPositionChangeCanceled() }
                ) { change, _ ->
                    change.consume()
                    onPositionChange(resolvePlayerThinSliderPosition(change.position.x, size.width, safeDuration))
                }
            }
            .padding(vertical = (thumbSize - trackHeight) / 2),
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val thumbOffsetPx = resolvePlayerThinSliderThumbOffsetPx(
            trackWidthPx = constraints.maxWidth.toFloat(),
            thumbSizePx = with(density) { thumbSize.toPx() },
            progress = progress
        )
        // Inactive track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(NordicShapes.full)
                .background(inactiveColor)
        )
        // Active track
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .height(trackHeight)
                .clip(NordicShapes.full)
                .background(activeColor)
        )
        // Thumb
        Box(
            modifier = Modifier
                .offset { IntOffset(thumbOffsetPx.roundToInt(), 0) }
                .size(thumbSize)
                .clip(NordicShapes.full)
                .background(SolidColor(thumbColor))
        )
    }
}

@Composable
private fun PlayerIconButton(
    icon: ImageVector,
    colorScheme: ColorScheme,
    size: Dp,
    filled: Boolean = false,
    active: Boolean = false,
    enabled: Boolean = true,
    showOneBadge: Boolean = false,
    tint: androidx.compose.ui.graphics.Color? = null,
    onClick: () -> Unit = {},
    contentDescription: String? = null
) {
    val background = when {
        filled && enabled -> colorScheme.primary
        filled -> colorScheme.primary.copy(alpha = 0.32f)
        active && enabled -> colorScheme.primary.copy(alpha = 0.18f)
        else -> colorScheme.surface.copy(alpha = if (enabled) 0.58f else 0.30f)
    }
    val foreground = tint ?: when {
        filled -> colorScheme.onPrimary
        active && enabled -> colorScheme.primary
        enabled -> colorScheme.onSurface.copy(alpha = NordicAlpha.medium)
        else -> colorScheme.onSurface.copy(alpha = NordicAlpha.faint)
    }

    Surface(
        color = background,
        contentColor = foreground,
        shape = NordicShapes.full,
        shadowElevation = if (filled && enabled) 4.dp else 0.dp,
        modifier = Modifier
            .size(size)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = foreground,
                modifier = Modifier.size((size.value * 0.52f).dp)
            )
            if (showOneBadge) {
                Surface(
                    color = colorScheme.onPrimary,
                    contentColor = colorScheme.primary,
                    shape = NordicShapes.full,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(10.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "1",
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

internal data class VisibleLyricLine(
    val text: String,
    val active: Boolean
)

internal fun resolveLyricsModeLabel(lyrics: MusicLyrics?): String? {
    val lines = lyrics?.lines?.filter { it.text.isNotBlank() }.orEmpty()
    if (lines.isEmpty()) return null
    return if (lyrics?.synced == true && lines.any { it.startMillis != null }) {
        "同步歌词"
    } else {
        "普通歌词"
    }
}

internal fun selectVisibleLyricLines(
    lyrics: MusicLyrics?,
    positionMillis: Long,
    maxLineCount: Int
): List<VisibleLyricLine> {
    val lines = lyrics?.lines?.filter { it.text.isNotBlank() }.orEmpty()
    if (lines.isEmpty()) return emptyList()

    if (lyrics?.synced != true) {
        return lines.take(maxLineCount).map { VisibleLyricLine(it.text, active = false) }
    }

    val normalizedPositionMillis = positionMillis.coerceAtLeast(0L)
    val activeIndex = lines.indexOfLast { line ->
        line.startMillis != null && line.startMillis <= normalizedPositionMillis
    }.takeIf { it >= 0 }
    val halfWindow = maxLineCount / 2
    val startIndex = when {
        activeIndex == null -> 0
        activeIndex + halfWindow >= lines.size -> (lines.size - maxLineCount).coerceAtLeast(0)
        else -> (activeIndex - halfWindow).coerceAtLeast(0)
    }

    return lines
        .drop(startIndex)
        .take(maxLineCount)
        .mapIndexed { index, line ->
            VisibleLyricLine(
                text = line.text,
                active = activeIndex != null && startIndex + index == activeIndex
            )
        }
}

internal fun resolvePlayerThinSliderPosition(pointerX: Float, trackWidth: Int, durationSeconds: Int): Float {
    val safeDuration = maxOf(durationSeconds, 1)
    if (trackWidth <= 0) return 0f
    val ratio = (pointerX / trackWidth.toFloat()).coerceIn(0f, 1f)
    return ratio * safeDuration
}

internal fun resolvePlayerThinSliderThumbOffsetPx(trackWidthPx: Float, thumbSizePx: Float, progress: Float): Float {
    val travelPx = (trackWidthPx - thumbSizePx).coerceAtLeast(0f)
    return travelPx * progress.coerceIn(0f, 1f)
}

/**
 * Returns the displayed favorite state. Currently driven by [NavidromeSong.starred] directly;
 * the playback VM owns optimistic updates via [com.nordic.mediahub.playback.MusicPlaybackEngine.setCurrentSongStarred],
 * so reading from the published song is sufficient for both initial state and revert-after-failure.
 */
private fun resolveFavoriteDisplay(song: NavidromeSong?): Boolean = song?.starred != null
