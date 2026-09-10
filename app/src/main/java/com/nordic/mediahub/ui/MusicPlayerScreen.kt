package com.nordic.mediahub.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.nordic.mediahub.playback.MusicLyricsUiState
import com.nordic.mediahub.data.NavidromeSong
import com.nordic.mediahub.playback.resolveMusicSeekByPosition
import com.nordic.mediahub.playback.PLAYBACK_SPEED_OPTIONS
import com.nordic.mediahub.playback.resolvePlaybackSpeedLabel
import com.nordic.mediahub.ui.theme.NordicMotion
import com.nordic.mediahub.ui.theme.NordicSpacing
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

internal fun shouldDismissMusicPlayerFromDrag(showLyrics: Boolean, positionInRoot: Offset, lyricsBounds: Rect?): Boolean =
    !showLyrics || (lyricsBounds != null && !lyricsBounds.contains(positionInRoot))

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
    lyricsState: MusicLyricsUiState,
    showLyrics: Boolean,
    lyricsSeekRevision: Long,
    onToggleLyrics: () -> Unit,
    onRetryLyrics: () -> Unit,
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
    onDownloadSong: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val downloadManager = remember(song?.sourceId) { com.nordic.mediahub.data.MusicDownloadManagers.get(context, song?.sourceId.orEmpty()) }
    val downloadStates by downloadManager.downloadStates.collectAsStateWithLifecycle()
    val download = downloadStates[song?.id]
    var showActions by remember { mutableStateOf(false) }
    LaunchedEffect(downloadManager) { withContext(Dispatchers.IO) { downloadManager.restoreDownloadState() } }
    val resolvedDurationSeconds = maxOf(durationSeconds, song?.duration ?: 0, 0)
    val timeline = resolvePlayerTimeline(positionSeconds, resolvedDurationSeconds)
    val currentOnClose by rememberUpdatedState(onClose)
    var scrubPosition by remember(song?.id) { mutableStateOf<Float?>(null) }
    var showSpeedSheet by remember(song?.id) { mutableStateOf(false) }
    var seekFeedback by remember(song?.id) { mutableStateOf<MusicSeekFeedback?>(null) }
    val hasSong = song?.streamUrl?.isNotBlank() == true
    val visiblePosition = scrubPosition ?: positionSeconds.toFloat()
    val playbackStatusIsError = playbackError != null || (song != null && !hasSong)
    val playbackStatus = when {
        playbackError != null -> playbackError
        isBuffering -> "正在缓冲"
        isPlaying -> "正在播放"
        song != null && !hasSong -> "这首歌缺少播放地址"
        song != null -> "已暂停"
        else -> null
    }
    val feedbackScope = rememberCoroutineScope()
    val feedbackJob = remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    DisposableEffect(song?.id) { onDispose { feedbackJob.value?.cancel() } }

    fun showSeekFeedback(delta: Int) {
        feedbackJob.value?.cancel()
        val target = resolveMusicSeekByPosition(positionSeconds, delta, resolvedDurationSeconds)
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
        val statusTopPadding = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
        val topPadding = if (compact) NordicSpacing.sm else NordicSpacing.md
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
        var primaryBounds by remember { mutableStateOf<Rect?>(null) }
        var gestureOrigin by remember { mutableStateOf(Offset.Zero) }
        val lyricsVisible by rememberUpdatedState(showLyrics)

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
                .onGloballyPositioned { gestureOrigin = it.positionInRoot() }
                .pointerInput(screenHeightPx, swipeThresholdPx) {
                    var allowDismiss = true
                    detectVerticalDragGestures(
                        onDragStart = { position ->
                            // Short/empty lyrics may not consume a drag; do not
                            // let a gesture in their surface dismiss the player.
                            allowDismiss = shouldDismissMusicPlayerFromDrag(lyricsVisible, gestureOrigin + position, primaryBounds)
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
                                    currentOnClose()
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
                        if (allowDismiss) {
                            change.consume()
                            val next = (dragYState.floatValue + dragAmountY).coerceAtLeast(0f)
                            dragYState.floatValue = next
                        }
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
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(
                        start = sidePadding,
                        top = topPadding,
                        end = sidePadding,
                        bottom = bottomPadding
                    ),
                verticalArrangement = Arrangement.spacedBy(sectionGap)
            ) {
                MediaPlayerTopBar("音乐播放", colorScheme, onClose, resolvePlaybackSpeedLabel(playbackSpeed),
                    onSpeed = { showSpeedSheet = true }, speedEnabled = hasSong,
                    extraAction = MediaPlayerAction(Icons.Filled.MoreVert, "音乐操作", { showActions = true }, hasSong))
                MediaAudioPlayerBody(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    artwork = { displayModifier ->
                        PlayerPrimaryDisplay(
                            song = song, lyricsState = lyricsState, isPlaying = isPlaying,
                            seekRevision = lyricsSeekRevision, onRetryLyrics = onRetryLyrics,
                            positionMillisFlow = positionMillisFlow,
                            showLyrics = showLyrics, colorScheme = colorScheme, compact = compact,
                            enabled = hasSong, onToggleDisplay = onToggleLyrics,
                            onSeekRelative = { delta -> showSeekFeedback(delta) },
                            modifier = displayModifier.onGloballyPositioned { primaryBounds = it.boundsInRoot() }
                        )
                    },
                    controls = {
                        PlayerTitleMetaRow(song, resolveFavoriteDisplay(song), colorScheme,
                            onToggleFavorite = onToggleFavorite, onOpenQueue = onOpenQueue)
                        PlayerConsole(
                            hasSong = hasSong, isPlaying = isPlaying,
                            position = visiblePosition.coerceIn(0f, timeline.sliderMaxSeconds.toFloat()),
                            duration = resolvedDurationSeconds, colorScheme = colorScheme,
                            playbackStatus = playbackStatus, playbackStatusIsError = playbackStatusIsError,
                            onPositionChange = { scrubPosition = it },
                            onPositionChangeFinished = {
                                val target = scrubPosition ?: visiblePosition
                                onSeek(target.toInt())
                                scrubPosition = null
                            },
                            onPositionChangeCanceled = { scrubPosition = null }, onPlayPause = onPlayPause,
                            repeatMode = repeatMode, shuffleModeEnabled = shuffleModeEnabled,
                            onSeekToNext = onSeekToNext, onSeekToPrevious = onSeekToPrevious,
                            onToggleRepeat = onToggleRepeat, onToggleShuffle = onToggleShuffle
                        )
                    }
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

    if (showActions) {
        MediaPlayerSheet("音乐操作", colorScheme, { showActions = false }) {
            SettingsRow("下载当前歌曲", subtitle = when (download?.state) {
                com.nordic.mediahub.data.DownloadState.DOWNLOADED -> "已下载，可在设置中的存储与下载页面管理"
                com.nordic.mediahub.data.DownloadState.DOWNLOADING -> "正在下载 ${(download.progress * 100).toInt()}%"
                else -> download?.errorMessage ?: "保存在此来源的本机下载目录"
            }, enabled = download?.state != com.nordic.mediahub.data.DownloadState.DOWNLOADED &&
                download?.state != com.nordic.mediahub.data.DownloadState.DOWNLOADING && song?.streamUrl?.startsWith("file:") != true,
                onClick = { onDownloadSong(); showActions = false })
            if (download?.state == com.nordic.mediahub.data.DownloadState.DOWNLOADING) {
                SettingsRow("取消下载", onClick = { song?.id?.let(downloadManager::cancelDownload); showActions = false })
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
private fun PlayerPrimaryDisplay(
    song: NavidromeSong?,
    lyricsState: MusicLyricsUiState,
    isPlaying: Boolean,
    seekRevision: Long,
    onRetryLyrics: () -> Unit,
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
    val seekEnabled by rememberUpdatedState(enabled)
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
            MusicLyricsDisplay(
                state = lyricsStateForSong(song?.id, lyricsState),
                isPlaying = isPlaying,
                seekRevision = seekRevision,
                onRetry = onRetryLyrics,
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
private fun PlayerArtwork(song: NavidromeSong?, colorScheme: ColorScheme, modifier: Modifier = Modifier) {
    MediaPlayerArtwork(song?.title ?: "专辑封面", song?.coverArt, Icons.Filled.MusicNote, colorScheme, modifier)
}

@Composable
private fun PlayerTitleMetaRow(
    song: NavidromeSong?,
    isFavorite: Boolean,
    colorScheme: ColorScheme,
    onToggleFavorite: (String, Boolean) -> Unit,
    onOpenQueue: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NordicSpacing.md),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NordicSpacing.xs)) {
            Text(song?.title ?: "等待播放", style = MaterialTheme.typography.headlineMedium,
                color = colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (song == null) "从音乐库选择曲目" else musicArtistLabel(song.artist),
                style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(NordicSpacing.xs)) {
            MediaPlayerIconAction(MediaPlayerAction(
                if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                if (isFavorite) "取消收藏" else "收藏",
                onClick = { song?.id?.takeIf { it.isNotBlank() }?.let { onToggleFavorite(it, !isFavorite) } },
                enabled = !song?.id.isNullOrBlank(), active = isFavorite
            ), colorScheme)
            MediaPlayerIconAction(MediaPlayerAction(Icons.AutoMirrored.Filled.QueueMusic, "打开播放队列", onOpenQueue), colorScheme)
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
    playbackStatus: String?,
    playbackStatusIsError: Boolean,
    onPositionChange: (Float) -> Unit,
    onPositionChangeFinished: () -> Unit,
    onPositionChangeCanceled: () -> Unit,
    onPlayPause: () -> Unit,
    repeatMode: Int,
    shuffleModeEnabled: Boolean,
    onSeekToNext: () -> Unit,
    onSeekToPrevious: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)) {
        if (playbackStatus != null) Text(playbackStatus, style = MaterialTheme.typography.bodySmall,
            color = if (playbackStatusIsError) colorScheme.error else colorScheme.onSurfaceVariant,
            maxLines = 3, overflow = TextOverflow.Ellipsis)
        MediaPlayerTimeline(position, duration, colorScheme, hasSong, onPositionChange,
            onPositionChangeFinished, onPositionChangeCanceled)
        MediaTransportRow(
            leading = MediaPlayerAction(Icons.Filled.Shuffle, if (shuffleModeEnabled) "关闭随机播放" else "开启随机播放",
                onToggleShuffle, hasSong, shuffleModeEnabled),
            previous = MediaPlayerAction(Icons.Filled.SkipPrevious, "上一首", onSeekToPrevious, hasSong),
            play = MediaPlayerAction(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                if (isPlaying) "暂停" else "播放", onPlayPause, hasSong),
            next = MediaPlayerAction(Icons.Filled.SkipNext, "下一首", onSeekToNext, hasSong),
            trailing = MediaPlayerAction(if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                when (repeatMode) { Player.REPEAT_MODE_ONE -> "单曲循环"; Player.REPEAT_MODE_ALL -> "列表循环"; else -> "循环关闭" },
                onToggleRepeat, hasSong, repeatMode != Player.REPEAT_MODE_OFF),
            colors = colorScheme
        )
    }
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
    MediaTransientPill(
        message = resolveSeekFeedbackLabel(feedback.deltaSeconds),
        detail = formatDuration(feedback.targetPositionSeconds),
        colors = colorScheme,
        modifier = modifier
    )
}

/**
 * Playback-speed selection sheet (音流-style panel). Lists the shared
 * [PLAYBACK_SPEED_OPTIONS] rates and highlights the active one. Selection is
 * applied immediately and closes the sheet.
 */
@Composable
internal fun MusicPlaybackSpeedSheet(currentSpeed: Float, colorScheme: ColorScheme, onSelect: (Float) -> Unit, onDismiss: () -> Unit) {
    MediaPlaybackSpeedSheet(PLAYBACK_SPEED_OPTIONS, currentSpeed, colorScheme, onSelect, onDismiss)
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
        MediaTransientPill(
            message = "收藏操作失败，已恢复",
            colors = colorScheme
        )
    }
}
