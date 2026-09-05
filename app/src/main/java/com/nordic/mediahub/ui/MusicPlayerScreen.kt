package com.nordic.mediahub.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.nordic.mediahub.data.MusicLyrics
import com.nordic.mediahub.data.MusicLyricsLine
import com.nordic.mediahub.data.NavidromeSong
import com.nordic.mediahub.playback.PLAYBACK_SPEED_OPTIONS
import com.nordic.mediahub.playback.resolvePlaybackSpeedLabel
import com.nordic.mediahub.ui.theme.NordicAlpha
import com.nordic.mediahub.ui.theme.NordicMotion
import com.nordic.mediahub.ui.theme.NordicShapes
import com.nordic.mediahub.ui.theme.NordicSpacing
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Fraction of screen height the swipe-to-dismiss gesture must travel before
 * the release commits to closing the player. Below this the gesture rebounds.
 */
private const val SWIPE_TO_DISMISS_THRESHOLD_RATIO = 0.25f

/**
 * How long the favorite-failure pill stays visible before auto-hiding.
 */
private const val FAVORITE_ERROR_NOTICE_DURATION_MS = 2000L

/**
 * How long the double-tap seek feedback chip stays visible before auto-hiding.
 */
private const val MUSIC_SEEK_FEEDBACK_DURATION_MS = 1200L

/**
 * Maximum alpha decay applied to the player content while swiping down (at
 * the dismiss threshold the content is `1 - 0.6 = 0.4` opaque).
 */
private const val SWIPE_DISMISS_MAX_ALPHA_DECAY = 0.6f

/**
 * Maximum scale-down applied while swiping (at the threshold content is
 * `1 - 0.04 = 0.96` of its natural size).
 */
private const val SWIPE_DISMISS_MAX_SCALE_DOWN = 0.04f

/**
 * Double-tap seek interval on the artwork/lyrics area (left half = back,
 * right half = forward), mirroring mainstream music players.
 */
internal const val MUSIC_DOUBLE_TAP_SEEK_SECONDS = 10

internal data class MusicSeekFeedback(
    val deltaSeconds: Int,
    val targetPositionSeconds: Int
)

@Composable
fun MusicPlayerScreen(
    song: NavidromeSong?,
    colorScheme: ColorScheme,
    isPlaying: Boolean,
    isBuffering: Boolean,
    playbackError: String?,
    positionSeconds: Int,
    positionMillisFlow: StateFlow<Long>,
    durationSeconds: Int,
    lyrics: MusicLyrics?,
    isLyricsLoading: Boolean,
    lyricsError: String?,
    repeatMode: Int = Player.REPEAT_MODE_OFF,
    shuffleModeEnabled: Boolean = false,
    playbackSpeed: Float = 1f,
    onSeek: (Int) -> Unit,
    onPlayPause: () -> Unit,
    onClose: () -> Unit,
    onSeekToNext: () -> Unit = {},
    onSeekToPrevious: () -> Unit = {},
    onToggleRepeat: () -> Unit = {},
    onToggleShuffle: () -> Unit = {},
    onOpenQueue: () -> Unit = {},
    onToggleFavorite: (songId: String, starred: Boolean) -> Unit = { _, _ -> },
    onSetPlaybackSpeed: (Float) -> Unit = {},
    favoriteError: SharedFlow<Unit>? = null,
    modifier: Modifier = Modifier
) {
    val resolvedDurationSeconds = maxOf(durationSeconds, song?.duration ?: 0, 1)
    var scrubPosition by remember(song?.id) { mutableStateOf<Float?>(null) }
    var showLyrics by rememberSaveable(song?.id) { mutableStateOf(false) }
    var showSpeedSheet by remember(song?.id) { mutableStateOf(false) }
    var seekFeedback by remember { mutableStateOf<MusicSeekFeedback?>(null) }
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
    val feedbackScope = rememberCoroutineScope()
    val feedbackJob = remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun showSeekFeedback(delta: Int) {
        feedbackJob.value?.cancel()
        val target = (positionSeconds + delta).coerceIn(0, resolvedDurationSeconds)
        seekFeedback = MusicSeekFeedback(deltaSeconds = delta, targetPositionSeconds = target)
        onSeek(target)
        feedbackJob.value = feedbackScope.launch {
            delay(MUSIC_SEEK_FEEDBACK_DURATION_MS)
            seekFeedback = null
        }
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
        val screenHeightPx = with(LocalDensity.current) { maxHeight.toPx() }
        val swipeThresholdPx = screenHeightPx * SWIPE_TO_DISMISS_THRESHOLD_RATIO

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

        // Swipe-to-dismiss gesture state. `dragY` is the live accumulated
        // downward displacement driven by `detectVerticalDragGestures`; it
        // feeds the inner content's `graphicsLayer` for the progressive
        // translate + slight scale + alpha decay. `animatedDismiss` is the
        // Animatable that drives the final collapse animation when the user
        // releases past the threshold (or the spring rebound when below it).
        val dismissScope = rememberCoroutineScope()
        val dragYState = remember { mutableFloatStateOf(0f) }
        val animatedDismiss = remember { Animatable(0f) }
        var isDismissing by remember { mutableStateOf(false) }
        var favoriteNoticeVisible by remember { mutableStateOf(false) }

        // Collect the one-shot favorite-error event from the playback VM; show
        // the pill for 2s then auto-hide. Subsequent emits while visible reset
        // the timer via the `LaunchedEffect` re-launch keyed on collects.
        val favoriteErrorFlow = favoriteError
        LaunchedEffect(favoriteErrorFlow) {
            favoriteErrorFlow?.collect {
                favoriteNoticeVisible = true
                delay(FAVORITE_ERROR_NOTICE_DURATION_MS)
                favoriteNoticeVisible = false
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            // Whole-screen start region (no top-half restriction);
                            // the lyrics display below consumes its own taps so
                            // vertical drags that start there will not reach here.
                            dragYState.floatValue = 0f
                            isDismissing = false
                            dismissScope.launch { animatedDismiss.snapTo(0f) }
                        },
                        onDragEnd = {
                            // Snap to current visual displacement, then animate
                            // to the resolved target (dismiss or rebound home).
                            val accumulated = dragYState.floatValue
                            dragYState.floatValue = 0f
                            if (accumulated >= swipeThresholdPx) {
                                isDismissing = true
                                dismissScope.launch {
                                    animatedDismiss.snapTo(accumulated)
                                    animatedDismiss.animateTo(
                                        targetValue = screenHeightPx,
                                        animationSpec = tween(
                                            NordicMotion.durationShort,
                                            easing = NordicMotion.easingStandard
                                        )
                                    )
                                    onClose()
                                }
                            } else if (accumulated > 0f) {
                                // Rebound to 0 with a non-bouncy spring — pure
                                // gesture-return physics, no decorative overshoot.
                                dismissScope.launch {
                                    animatedDismiss.snapTo(accumulated)
                                    animatedDismiss.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            // Cancel: snap visuals home, drop the gesture state.
                            dragYState.floatValue = 0f
                            isDismissing = false
                            dismissScope.launch { animatedDismiss.snapTo(0f) }
                        }
                    ) { change, dragAmountY ->
                        change.consume()
                        // Only accumulate downward drags for the dismiss gesture;
                        // upward drags are ignored so they don't fight any
                        // future vertical content scrolling on the surface.
                        val next = (dragYState.floatValue + dragAmountY).coerceAtLeast(0f)
                        dragYState.floatValue = next
                    }
                }
                .graphicsLayer {
                    // The final collapse uses `animatedDismiss.value`; while
                    // dragging it is 0 so the live `dragYState` drives the
                    // gesture. After release, `animatedDismiss` interpolates
                    // to either `screenHeightPx` (dismiss) or `0f` (rebound).
                    val live = if (isDismissing) animatedDismiss.value else dragYState.floatValue
                    val progress = (live / screenHeightPx).coerceIn(0f, 1f)
                    translationY = live
                    val scale = 1f - SWIPE_DISMISS_MAX_SCALE_DOWN * progress
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f - SWIPE_DISMISS_MAX_ALPHA_DECAY * progress
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
                    playbackSpeed = playbackSpeed,
                    onShowSpeedSheet = { showSpeedSheet = true },
                    onClose = onClose
                )
                PlayerPrimaryDisplay(
                    song = song,
                    lyrics = lyrics,
                    isLyricsLoading = isLyricsLoading,
                    lyricsError = lyricsError,
                    positionMillisFlow = positionMillisFlow,
                    showLyrics = showLyrics,
                    colorScheme = colorScheme,
                    compact = compact,
                    enabled = hasSong,
                    onToggleDisplay = { showLyrics = !showLyrics },
                    onSeekRelative = { delta -> showSeekFeedback(delta) },
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
                    onSeekToNext = onSeekToNext,
                    onSeekToPrevious = onSeekToPrevious,
                    onToggleRepeat = onToggleRepeat,
                    onToggleShuffle = onToggleShuffle
                )
            }

            // Favorite-failure notice — auto-dismissing pill overlay. Stays
            // out of the gesture-affected `Column` so it does not transform
            // with the swipe-to-dismiss content; it should sit in screen
            // space until its 2s timer elapses.
            FavoriteErrorNotice(
                visible = favoriteNoticeVisible,
                colorScheme = colorScheme,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = statusTopPadding + NordicSpacing.md)
            )

            // Double-tap seek feedback chip — mirrors the video player's
            // transient "+10s / -10s" overlay so the gesture vocabulary is
            // consistent across players.
            seekFeedback?.let { feedback ->
                MusicSeekFeedbackChip(
                    feedback = feedback,
                    colorScheme = colorScheme,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }

    if (showSpeedSheet) {
        MusicPlaybackSpeedSheet(
            currentSpeed = playbackSpeed,
            colorScheme = colorScheme,
            onSelect = { speed ->
                onSetPlaybackSpeed(speed)
                showSpeedSheet = false
            },
            onDismiss = { showSpeedSheet = false }
        )
    }
}

@Composable
private fun PlayerTopBar(
    colorScheme: ColorScheme,
    compact: Boolean,
    playbackSpeed: Float,
    onShowSpeedSheet: () -> Unit,
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
        // Right-side playback-speed entry (音流-style): shows the active rate
        // as text; opens the speed selection sheet.
        Surface(
            color = colorScheme.surface.copy(alpha = 0.58f),
            contentColor = colorScheme.onSurface.copy(alpha = NordicAlpha.medium),
            shape = NordicShapes.full,
            modifier = Modifier
                .size(42.dp)
                .clickable(onClick = onShowSpeedSheet)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = resolvePlaybackSpeedLabel(playbackSpeed),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun PlayerPrimaryDisplay(
    song: NavidromeSong?,
    lyrics: MusicLyrics?,
    isLyricsLoading: Boolean,
    lyricsError: String?,
    positionMillisFlow: StateFlow<Long>,
    showLyrics: Boolean,
    colorScheme: ColorScheme,
    compact: Boolean,
    enabled: Boolean,
    onToggleDisplay: () -> Unit,
    onSeekRelative: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentToggle by rememberUpdatedState(onToggleDisplay)
    val currentSeekRelative by rememberUpdatedState(onSeekRelative)
    val seekEnabled = enabled
    Box(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures(
                onTap = { currentToggle() },
                onDoubleTap = { offset ->
                    if (seekEnabled) {
                        val half = size.width / 2f
                        val delta = if (offset.x < half) {
                            -MUSIC_DOUBLE_TAP_SEEK_SECONDS
                        } else {
                            MUSIC_DOUBLE_TAP_SEEK_SECONDS
                        }
                        currentSeekRelative(delta)
                    }
                }
            )
        }
    ) {
        if (showLyrics) {
            PlayerLyricsDisplay(
                lyrics = lyrics,
                isLoading = isLyricsLoading,
                error = lyricsError,
                positionMillisFlow = positionMillisFlow,
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
    positionMillisFlow: StateFlow<Long>,
    colorScheme: ColorScheme,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val lineCount = if (compact) 5 else 7
    val filteredLines = remember(lyrics) {
        lyrics?.lines?.filter { it.text.isNotBlank() }.orEmpty()
    }
    val isSynced = lyrics?.synced == true && filteredLines.any { it.startMillis != null }
    // Collect the 100ms position tick here, at the leaf that actually needs it,
    // so ancestors (MusicPlayerScreen / MainActivity) never recompose on every tick.
    val positionMillis by positionMillisFlow.collectAsStateWithLifecycle()
    // `activeIndex` changes far less often than `positionMillis`. `derivedStateOf`
    // only emits when the resolved line index crosses a boundary, so the lyric
    // list re-composes only when the highlighted line actually changes.
    val activeIndex by remember(filteredLines, isSynced) {
        derivedStateOf {
            if (isSynced) resolveActiveLyricIndex(filteredLines, positionMillis) else null
        }
    }
    val staticVisibleLines = remember(filteredLines, isSynced, lineCount) {
        if (!isSynced) filteredLines.take(lineCount).map { VisibleLyricLine(it.text, active = false) }
        else emptyList()
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
            filteredLines.isEmpty() -> PlayerLyricsStatus(error ?: "暂无歌词", colorScheme)
            isSynced -> {
                val listState = rememberLazyListState()
                var hasInitialScrolled by remember(filteredLines) { mutableStateOf(false) }
                LaunchedEffect(activeIndex, filteredLines.size) {
                    val target = activeIndex ?: return@LaunchedEffect
                    if (target < 0 || target >= filteredLines.size) return@LaunchedEffect
                    if (!hasInitialScrolled) {
                        listState.scrollToItem(target)
                        hasInitialScrolled = true
                    } else {
                        val viewportStart = listState.layoutInfo.viewportStartOffset
                        val viewportEnd = listState.layoutInfo.viewportEndOffset
                        val itemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == target }
                        val needsAnimatedAlign = itemInfo == null ||
                            itemInfo.offset < viewportStart ||
                            itemInfo.offset + itemInfo.size > viewportEnd
                        if (needsAnimatedAlign) {
                            listState.animateScrollToItem(target)
                        }
                    }
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
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
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(
                                if (compact) NordicSpacing.sm else NordicSpacing.md
                            )
                        ) {
                            itemsIndexed(
                                items = filteredLines,
                                key = { index, _ -> "lyric-$index" },
                                contentType = { _, _ -> "lyric-line" }
                            ) { index, line ->
                                LyricLineText(
                                    text = line.text,
                                    active = index == activeIndex,
                                    compact = compact,
                                    colorScheme = colorScheme
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
                    staticVisibleLines.forEach { line ->
                        LyricLineText(
                            text = line.text,
                            active = false,
                            compact = compact,
                            colorScheme = colorScheme
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricLineText(
    text: String,
    active: Boolean,
    compact: Boolean,
    colorScheme: ColorScheme
) {
    val activeColor = colorScheme.onSurface
    val inactiveColor = colorScheme.onSurface.copy(alpha = NordicAlpha.subtle)
    val animatedColor by animateColorAsState(
        targetValue = if (active) activeColor else inactiveColor,
        animationSpec = tween(NordicMotion.durationShort, easing = NordicMotion.easingStandard),
        label = "lyric-color"
    )
    val animatedWeight by animateFloatAsState(
        targetValue = if (active) FontWeight.Bold.weight.toFloat() else FontWeight.Medium.weight.toFloat(),
        animationSpec = tween(NordicMotion.durationShort, easing = NordicMotion.easingStandard),
        label = "lyric-weight"
    )
    Text(
        text = text,
        fontSize = if (compact) 16.sp else 18.sp,
        lineHeight = if (compact) 20.sp else 24.sp,
        color = animatedColor,
        fontWeight = FontWeight(weight = animatedWeight.toInt()),
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
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
                icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                colorScheme = colorScheme,
                size = if (compact) 62.dp else 68.dp,
                filled = true,
                enabled = hasSong,
                onClick = onPlayPause,
                contentDescription = if (isPlaying) "暂停" else "播放"
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
                // Single-track-repeat "1" badge. Per Accent Scarcity the badge
                // stays low-key: a small translucent surface chip (not a solid
                // onPrimary block) with a thin primary-tinted border, nudged
                // 2dp outward so it reads as an overlay rather than clipped
                // to the icon edge.
                Surface(
                    color = colorScheme.surface.copy(alpha = 0.94f),
                    contentColor = colorScheme.primary,
                    shape = NordicShapes.full,
                    border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.24f)),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 2.dp, y = 2.dp)
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

internal fun resolveActiveLyricIndex(
    lines: List<MusicLyricsLine>,
    positionMillis: Long
): Int? {
    if (lines.isEmpty()) return null
    val normalizedPositionMillis = positionMillis.coerceAtLeast(0L)
    return lines.indexOfLast { line ->
        line.startMillis != null && line.startMillis <= normalizedPositionMillis
    }.takeIf { it >= 0 }
}

/**
 * Returns the displayed favorite state. Currently driven by [NavidromeSong.starred] directly;
 * the playback VM owns optimistic updates via [com.nordic.mediahub.playback.MusicPlaybackEngine.setCurrentSongStarred],
 * so reading from the published song is sufficient for both initial state and revert-after-failure.
 */
private fun resolveFavoriteDisplay(song: NavidromeSong?): Boolean = song?.starred != null

/**
 * Transient chip shown after a double-tap seek on the artwork/lyrics area,
 * mirroring the video player's seek feedback overlay in light-theme styling.
 */
@Composable
private fun MusicSeekFeedbackChip(
    feedback: MusicSeekFeedback,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier
) {
    Surface(
        color = colorScheme.surface.copy(alpha = 0.92f),
        contentColor = colorScheme.onSurface,
        shape = NordicShapes.full,
        border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.24f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = NordicSpacing.lg, vertical = NordicSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(NordicSpacing.sm, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = resolveSeekFeedbackLabel(feedback.deltaSeconds),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colorScheme.primary,
                maxLines = 1
            )
            Text(
                text = formatDuration(feedback.targetPositionSeconds),
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurface.copy(alpha = NordicAlpha.subtle),
                maxLines = 1
            )
        }
    }
}

/**
 * Playback-speed selection sheet (音流-style panel). Lists the shared
 * [PLAYBACK_SPEED_OPTIONS] rates and highlights the active one. Selection is
 * applied immediately and closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MusicPlaybackSpeedSheet(
    currentSpeed: Float,
    colorScheme: ColorScheme,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colorScheme.surface,
        shape = NordicShapes.xl,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = NordicSpacing.lg)
                .padding(bottom = NordicSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)
        ) {
            Text(
                "播放速度",
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.onSurface,
                modifier = Modifier.padding(bottom = NordicSpacing.xs)
            )
            PLAYBACK_SPEED_OPTIONS.forEach { speed ->
                val selected = kotlin.math.abs(speed - currentSpeed) < 0.001f
                Surface(
                    color = if (selected) {
                        colorScheme.primary.copy(alpha = 0.16f)
                    } else {
                        colorScheme.surfaceVariant.copy(alpha = 0.42f)
                    },
                    contentColor = colorScheme.onSurface,
                    shape = NordicShapes.md,
                    border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.045f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(speed) }
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = NordicSpacing.md, vertical = NordicSpacing.md)
                    ) {
                        Text(
                            text = resolvePlaybackSpeedLabel(speed),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (selected) colorScheme.primary else colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * Auto-dismissing pill shown when an optimistic favorite toggle fails and the
 * VM silently reverts the ♥. The pill sits in screen space (outside the
 * swipe-to-dismiss transformed content) so it never tilts with the gesture.
 *
 * Visual language stays within DESIGN.md: pill shape, surface alpha (0.94f)
 * container, onSurface primary-tinted text, no shadow (it is not a dock /
 * player / lyrics surface). The error context is a transient notice, not a
 * persistent card, so it transparently overlays the player content.
 */
@Composable
private fun FavoriteErrorNotice(
    visible: Boolean,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = tween(NordicMotion.durationShort, easing = NordicMotion.easingStandard)
        ) + slideInVertically(
            initialOffsetY = { -it / 4 },
            animationSpec = tween(NordicMotion.durationShort, easing = NordicMotion.easingStandard)
        ),
        exit = fadeOut(
            animationSpec = tween(NordicMotion.durationShort, easing = NordicMotion.easingStandard)
        ) + slideOutVertically(
            targetOffsetY = { -it / 4 },
            animationSpec = tween(NordicMotion.durationShort, easing = NordicMotion.easingStandard)
        ),
        modifier = modifier
    ) {
        Surface(
            color = colorScheme.surface.copy(alpha = 0.94f),
            contentColor = colorScheme.onSurface,
            shape = NordicShapes.full,
            border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.24f))
        ) {
            Text(
                "收藏操作失败，已恢复",
                style = MaterialTheme.typography.labelLarge,
                color = colorScheme.primary,
                modifier = Modifier.padding(horizontal = NordicSpacing.lg, vertical = NordicSpacing.sm)
            )
        }
    }
}
