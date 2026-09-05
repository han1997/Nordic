package com.nordic.mediahub.ui

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.SurfaceView
import android.view.Window
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import com.nordic.mediahub.data.VideoItem
import com.nordic.mediahub.playback.AspectRatioMode
import com.nordic.mediahub.playback.VideoPlaybackState
import com.nordic.mediahub.playback.resolvePlaybackSpeedLabel
import com.nordic.mediahub.ui.theme.NordicMotion
import com.nordic.mediahub.ui.theme.NordicShapes
import com.nordic.mediahub.ui.theme.NordicSpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

internal const val VIDEO_PLAYER_CONTROLS_AUTO_HIDE_MS = 4000L
private const val VIDEO_PLAYER_CHROME_FADE_MS = NordicMotion.durationShort

/** Speed rates offered by the video playback-speed sheet (Hills/Yamby-style). */
internal val VIDEO_PLAYBACK_SPEED_OPTIONS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

/** Temporary long-press playback speed. */
internal const val VIDEO_TEMP_SPEED = 2f

/**
 * How close to the end (seconds) the "next episode" overlay appears.
 */
internal const val VIDEO_NEXT_EPISODE_OVERLAY_LEAD_SECONDS = 30

/**
 * Holds the mutable brightness/volume gesture state shared between the
 * gesture recognizer and the center overlay. Lives outside recomposition so
 * drags do not allocate per frame. Progress is tracked per side so repeated
 * drags within one gesture session accumulate smoothly.
 */
internal class VideoAdjustGestureState {
    var side by mutableStateOf(VideoGestureSide.Left)
    var progress by mutableStateOf(0f)
        private set
    var visible by mutableStateOf(false)
        private set
    private var leftProgress = 0.5f
    private var rightProgress = 0f

    /**
     * Applies [step] to the accumulated progress of [requestedSide] and
     * returns the new value. Starts from a sensible baseline when a new
     * gesture session begins (side change or fresh drag): brightness from
     * mid-screen, volume from the current stream level.
     */
    fun applyStep(requestedSide: VideoGestureSide, step: Float, initialFraction: Float): Float {
        if (!visible || side != requestedSide) {
            if (requestedSide == VideoGestureSide.Left) {
                leftProgress = initialFraction
            } else {
                rightProgress = initialFraction
            }
        }
        val next = ((if (requestedSide == VideoGestureSide.Left) leftProgress else rightProgress) + step)
            .coerceIn(0f, 1f)
        if (requestedSide == VideoGestureSide.Left) leftProgress = next else rightProgress = next
        side = requestedSide
        progress = next
        visible = true
        return next
    }

    fun reset() {
        visible = false
        progress = 0f
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    state: VideoPlaybackState,
    colorScheme: ColorScheme,
    externalError: String? = null,
    onSurfaceReady: (SurfaceView) -> Unit,
    onSurfaceDisposed: (SurfaceView) -> Unit,
    onSeek: (Int) -> Unit,
    onSeekBack: () -> Unit = {},
    onSeekForward: () -> Unit = {},
    onSeekRelative: (Int) -> Unit = {},
    onPlayPause: () -> Unit,
    onCycleAspectRatio: () -> Unit = {},
    onSetPlaybackSpeed: (Float) -> Unit = {},
    nextEpisode: VideoItem? = null,
    onPlayNextEpisode: () -> Unit = {},
    onToggleFullscreen: () -> Unit = {},
    isFullscreen: Boolean = false,
    onClose: () -> Unit,
    onCloseAnyway: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val video = state.video
    val durationSeconds = state.durationSeconds.coerceAtLeast(video?.durationSeconds ?: 0)
    var scrubPosition by remember(video?.id) { mutableStateOf<Float?>(null) }
    val errorMessage = externalError ?: state.errorMessage
    val statusText = videoPlayerStatusText(
        hasVideo = video != null,
        isBuffering = state.isBuffering,
        errorMessage = errorMessage
    )
    val statusTone = resolveVideoStatusTone(
        hasVideo = video != null,
        isBuffering = state.isBuffering,
        errorMessage = errorMessage
    )
    val playerSubtitle = remember(video) { video?.metaTextForPlayer() }
    val videoAspectRatio = state.videoAspectRatio.takeIf { it > 0f } ?: 16f / 9f
    val currentOnSurfaceReady by rememberUpdatedState(onSurfaceReady)
    val currentOnSurfaceDisposed by rememberUpdatedState(onSurfaceDisposed)
    val surfaceReadyCallback = remember {
        { surface: SurfaceView -> currentOnSurfaceReady(surface) }
    }
    val surfaceDisposedCallback = remember {
        { surface: SurfaceView -> currentOnSurfaceDisposed(surface) }
    }

    var controlsVisible by remember { mutableStateOf(true) }
    var infoVisible by remember(video?.id) { mutableStateOf(false) }
    var seekFeedback by remember { mutableStateOf<SeekFeedback?>(null) }
    var gesturesLocked by remember(video?.id) { mutableStateOf(false) }
    var showSpeedSheet by remember(video?.id) { mutableStateOf(false) }
    var isTempSpeeding by remember(video?.id) { mutableStateOf(false) }
    val showNextEpisodeOverlay = nextEpisode != null && !state.isBuffering && errorMessage == null &&
        durationSeconds > 0 && state.positionSeconds >= durationSeconds - VIDEO_NEXT_EPISODE_OVERLAY_LEAD_SECONDS
    val adjustGestureState = remember { VideoAdjustGestureState() }
    val context = LocalContext.current
    val activityWindow = remember(context) {
        (context as? Activity)?.window
    }
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }
    val brightnessController = remember(activityWindow) {
        activityWindow?.let { VideoBrightnessController(it) }
    }
    val volumeController = remember(audioManager) {
        audioManager?.let { VideoVolumeController(it) }
    }
    val feedbackScope = rememberCoroutineScope()
    val feedbackJob = remember { AtomicReference<kotlinx.coroutines.Job?>(null) }

    fun showSeekFeedback(delta: Int) {
        feedbackJob.get()?.cancel()
        val targetPosition = (state.positionSeconds + delta).coerceAtLeast(0)
        seekFeedback = SeekFeedback(deltaSeconds = delta, targetPositionSeconds = targetPosition)
        feedbackJob.set(
            feedbackScope.launch {
                delay(1200L)
                seekFeedback = null
            }
        )
    }

    // Long-press temporary speed: remember the pre-press rate, jump to 2x on
    // press, restore on release. Guarded so overlapping events cannot stack.
    val prePressSpeed = remember { AtomicReference(1f) }
    fun startTempSpeed() {
        if (isTempSpeeding || video == null) return
        isTempSpeeding = true
        prePressSpeed.set(state.playbackSpeed)
        onSetPlaybackSpeed(VIDEO_TEMP_SPEED)
    }

    fun endTempSpeed() {
        if (!isTempSpeeding) return
        isTempSpeeding = false
        onSetPlaybackSpeed(prePressSpeed.get())
    }

    LaunchedEffect(controlsVisible, state.isPlaying, scrubPosition, statusTone, infoVisible) {
        if (controlsVisible && state.isPlaying && scrubPosition == null && statusTone == null && !infoVisible) {
            delay(VIDEO_PLAYER_CONTROLS_AUTO_HIDE_MS)
            controlsVisible = false
        }
    }

    BackHandler(enabled = isFullscreen) {
        onToggleFullscreen()
    }

    BackHandler(enabled = infoVisible) {
        infoVisible = false
    }

    BackHandler(enabled = gesturesLocked) {
        gesturesLocked = false
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .videoPlayerGestures(
                enabled = video != null && !gesturesLocked,
                isFullscreen = isFullscreen,
                durationSeconds = durationSeconds,
                currentPositionSeconds = state.positionSeconds,
                onToggleControls = { controlsVisible = !controlsVisible },
                onSeekRelative = { delta ->
                    showSeekFeedback(delta)
                    onSeekRelative(delta)
                },
                onScrubChange = { scrubPosition = it },
                onSeek = onSeek,
                onCycleAspectRatio = onCycleAspectRatio,
                onBrightnessDrag = { step ->
                    val controller = brightnessController
                    if (controller != null) {
                        val next = adjustGestureState.applyStep(
                            requestedSide = VideoGestureSide.Left,
                            step = step,
                            initialFraction = 0.5f
                        )
                        controller.adjustByFraction(next)
                    }
                },
                onVolumeDrag = { step ->
                    val controller = volumeController
                    if (controller != null) {
                        val next = adjustGestureState.applyStep(
                            requestedSide = VideoGestureSide.Right,
                            step = step,
                            initialFraction = controller.volumeFraction
                        )
                        controller.adjustByFraction(next)
                    }
                },
                onGestureEnd = { adjustGestureState.reset() },
                onLongPressStart = { startTempSpeed() },
                onLongPressEnd = { endTempSpeed() }
            )
    ) {
        VideoPlayerSurface(
            aspectRatioMode = state.aspectRatioMode,
            videoAspectRatio = videoAspectRatio,
            onSurfaceReady = surfaceReadyCallback,
            onSurfaceDisposed = surfaceDisposedCallback,
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(VIDEO_PLAYER_CHROME_FADE_MS, easing = NordicMotion.easingStandard)),
            exit = fadeOut(tween(VIDEO_PLAYER_CHROME_FADE_MS, easing = NordicMotion.easingStandard))
        ) {
            VideoPlayerScrim()
        }

        if (video == null) {
            VideoPlayerCenterMessage(
                title = "暂无视频",
                subtitle = "从媒体库选择一个视频开始播放"
            )
        } else if (errorMessage != null) {
            VideoPlayerCenterMessage(
                title = "播放异常",
                subtitle = errorMessage,
                onCloseAnyway = onCloseAnyway
            )
        } else if (state.isBuffering) {
            VideoPlayerCenterMessage(
                title = "缓冲中",
                subtitle = "正在准备视频流"
            )
        }

        if (seekFeedback != null) {
            VideoPlayerSeekFeedbackOverlay(
                feedback = seekFeedback!!,
                colorScheme = colorScheme,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Long-press temporary-speed indicator.
        if (isTempSpeeding) {
            VideoPlayerTempSpeedChip(
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // "Next episode" floating action near the end of an episode.
        if (showNextEpisodeOverlay && !gesturesLocked) {
            VideoPlayerNextEpisodeOverlay(
                episodeTitle = nextEpisode?.title.orEmpty(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .then(if (isFullscreen) Modifier else Modifier.navigationBarsPadding())
                    .padding(NordicSpacing.lg),
                onClick = onPlayNextEpisode
            )
        }

        // Brightness/volume vertical-drag indicator (Hills/Yamby-style center
        // vertical bar with icon + progress).
        if (adjustGestureState.visible) {
            VideoAdjustGestureOverlay(
                side = adjustGestureState.side,
                progress = adjustGestureState.progress,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Gesture lock overlay: when locked, only the unlock button responds.
        if (gesturesLocked) {
            VideoPlayerLockOverlay(
                onUnlock = { gesturesLocked = false },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(NordicSpacing.lg)
            )
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(VIDEO_PLAYER_CHROME_FADE_MS, easing = NordicMotion.easingStandard)),
            exit = fadeOut(tween(VIDEO_PLAYER_CHROME_FADE_MS, easing = NordicMotion.easingStandard))
        ) {
            val timeline = resolveVideoPlayerTimeline(
                positionSeconds = state.positionSeconds,
                durationSeconds = durationSeconds
            )
            val visiblePosition = (scrubPosition ?: timeline.positionSeconds.toFloat())
                .coerceIn(0f, timeline.sliderMaxSeconds.toFloat())

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (isFullscreen) Modifier else Modifier.statusBarsPadding())
                    .then(if (isFullscreen) Modifier else Modifier.navigationBarsPadding())
                    .padding(horizontal = NordicSpacing.lg, vertical = NordicSpacing.md),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                VideoPlayerTopBar(
                    title = video?.title ?: "视频播放器",
                    subtitle = playerSubtitle,
                    statusText = statusText,
                    statusTone = statusTone,
                    colorScheme = colorScheme,
                    hasVideo = video != null,
                    infoVisible = infoVisible,
                    gesturesLocked = gesturesLocked,
                    onToggleLock = { gesturesLocked = !gesturesLocked },
                    onToggleInfo = { infoVisible = !infoVisible },
                    onClose = onClose
                )

                VideoPlayerControls(
                    visiblePosition = visiblePosition,
                    durationSeconds = durationSeconds,
                    bufferedPositionSeconds = state.bufferedPositionSeconds,
                    timeline = timeline,
                    scrubPosition = scrubPosition,
                    isPlaying = state.isPlaying,
                    hasVideo = video != null,
                    isFullscreen = isFullscreen,
                    playbackSpeed = state.playbackSpeed,
                    hasNextEpisode = nextEpisode != null,
                    colorScheme = colorScheme,
                    onScrubChange = { scrubPosition = it },
                    onScrubFinished = {
                        val target = scrubPosition ?: visiblePosition
                        onSeek(target.roundToInt())
                        scrubPosition = null
                    },
                    onScrubCanceled = { scrubPosition = null },
                    onSeekBack = onSeekBack,
                    onPlayPause = onPlayPause,
                    onSeekForward = onSeekForward,
                    onCycleAspectRatio = onCycleAspectRatio,
                    onShowSpeedSheet = { showSpeedSheet = true },
                    onPlayNextEpisode = onPlayNextEpisode,
                    onToggleFullscreen = onToggleFullscreen
                )
            }
        }

        AnimatedVisibility(
            visible = infoVisible && video != null,
            enter = fadeIn(tween(VIDEO_PLAYER_CHROME_FADE_MS, easing = NordicMotion.easingStandard)),
            exit = fadeOut(tween(VIDEO_PLAYER_CHROME_FADE_MS, easing = NordicMotion.easingStandard)),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .then(if (isFullscreen) Modifier else Modifier.statusBarsPadding())
                .then(if (isFullscreen) Modifier else Modifier.navigationBarsPadding())
                .padding(NordicSpacing.lg)
        ) {
            video?.let {
                VideoPlayerInfoPanel(
                    video = it,
                    positionSeconds = state.positionSeconds,
                    durationSeconds = durationSeconds,
                    colorScheme = colorScheme,
                    onClose = { infoVisible = false }
                )
            }
        }
    }

    if (showSpeedSheet) {
        VideoPlaybackSpeedSheet(
            currentSpeed = state.playbackSpeed,
            colorScheme = colorScheme,
            onSelect = { speed ->
                onSetPlaybackSpeed(speed)
                showSpeedSheet = false
            },
            onDismiss = { showSpeedSheet = false }
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VideoPlayerSurface(
    aspectRatioMode: AspectRatioMode,
    videoAspectRatio: Float,
    onSurfaceReady: (SurfaceView) -> Unit,
    onSurfaceDisposed: (SurfaceView) -> Unit,
    modifier: Modifier = Modifier
) {
    var attachedSurface by remember { mutableStateOf<SurfaceView?>(null) }

    DisposableEffect(attachedSurface) {
        val surface = attachedSurface
        if (surface != null) {
            onSurfaceReady(surface)
        }
        onDispose {
            if (surface != null) {
                onSurfaceDisposed(surface)
            }
        }
    }

    AndroidView(
        factory = { context ->
            AspectRatioFrameLayout(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                val surface = SurfaceView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }
                addView(surface)
                attachedSurface = surface
            }
        },
        update = { frameLayout ->
            frameLayout.resizeMode = resolveVideoPlayerResizeMode(aspectRatioMode)
            frameLayout.setAspectRatio(videoAspectRatio)
        },
        modifier = modifier
    )
}

@Composable
private fun VideoPlayerScrim() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color.Black.copy(alpha = 0.66f),
                        0.18f to Color.Transparent,
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.82f)
                    )
                )
            )
    )
}

@Composable
private fun VideoPlayerTopBar(
    title: String,
    subtitle: String?,
    statusText: String?,
    statusTone: VideoStatusTone?,
    colorScheme: ColorScheme,
    hasVideo: Boolean,
    infoVisible: Boolean,
    gesturesLocked: Boolean,
    onToggleLock: () -> Unit,
    onToggleInfo: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(NordicSpacing.md),
        verticalAlignment = Alignment.Top
    ) {
        VideoPlayerChromeButton(
            icon = Icons.Filled.Close,
            colorScheme = colorScheme,
            primary = false,
            onClick = onClose
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                lineHeight = 22.sp,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Normal,
                    color = Color.White.copy(alpha = 0.66f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (statusTone != null && !statusText.isNullOrBlank()) {
            VideoPlayerStatusPill(
                tone = statusTone,
                text = statusText,
                colorScheme = colorScheme
            )
        }
        VideoPlayerChromeButton(
            icon = Icons.Filled.Lock,
            colorScheme = colorScheme,
            primary = gesturesLocked,
            enabled = hasVideo,
            onClick = onToggleLock
        )
        VideoPlayerChromeButton(
            icon = Icons.Filled.Info,
            colorScheme = colorScheme,
            primary = infoVisible,
            enabled = hasVideo,
            onClick = onToggleInfo
        )
    }
}

@Composable
private fun VideoPlayerStatusPill(
    tone: VideoStatusTone,
    text: String,
    colorScheme: ColorScheme
) {
    val containerColor = when (tone) {
        VideoStatusTone.Buffering -> colorScheme.primary.copy(alpha = 0.18f)
        VideoStatusTone.Error -> Color.Black.copy(alpha = 0.42f)
        VideoStatusTone.Idle -> Color.Black.copy(alpha = 0.42f)
    }
    Surface(
        color = containerColor,
        contentColor = Color.White,
        shape = NordicShapes.full,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = NordicSpacing.md, vertical = NordicSpacing.sm),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.86f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BoxScope.VideoPlayerCenterMessage(
    title: String,
    subtitle: String?,
    onCloseAnyway: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = NordicSpacing.xxl)
            .align(Alignment.Center),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)
    ) {
        Text(
            title,
            color = Color.White.copy(alpha = 0.84f),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle,
                color = Color.White.copy(alpha = 0.60f),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Normal,
                lineHeight = 18.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (onCloseAnyway != null) {
            Surface(
                color = Color.White.copy(alpha = 0.16f),
                contentColor = Color.White,
                shape = NordicShapes.full,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                modifier = Modifier.clickable(onClick = onCloseAnyway)
            ) {
                Text(
                    "仍要关闭",
                    modifier = Modifier.padding(horizontal = NordicSpacing.lg, vertical = NordicSpacing.sm),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun BoxScope.VideoPlayerSeekFeedbackOverlay(
    feedback: SeekFeedback,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.Black.copy(alpha = 0.56f),
        contentColor = Color.White,
        shape = NordicShapes.full,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = NordicSpacing.lg, vertical = NordicSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(NordicSpacing.sm, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val deltaLabel = if (feedback.deltaSeconds >= 0) {
                "+${formatDuration(feedback.deltaSeconds)}"
            } else {
                "-${formatDuration(-feedback.deltaSeconds)}"
            }
            Text(
                deltaLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (feedback.deltaSeconds >= 0) Color.White else colorScheme.primary,
                maxLines = 1
            )
            Text(
                formatVideoPlayerDurationLabel(feedback.targetPositionSeconds),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Normal,
                color = Color.White.copy(alpha = 0.66f),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun VideoPlayerInfoPanel(
    video: VideoItem,
    positionSeconds: Int,
    durationSeconds: Int,
    colorScheme: ColorScheme,
    onClose: () -> Unit
) {
    val chips = remember(video) { videoPlayerInfoChips(video) }
    val rows = remember(video, positionSeconds, durationSeconds) {
        videoPlayerInfoRows(
            video = video,
            positionSeconds = positionSeconds,
            durationSeconds = durationSeconds
        )
    }
    Surface(
        color = Color.Black.copy(alpha = 0.68f),
        contentColor = Color.White,
        shape = NordicShapes.xl,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        shadowElevation = 6.dp,
        modifier = Modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .fillMaxHeight(0.78f)
    ) {
        Column(
            modifier = Modifier
                .padding(NordicSpacing.lg)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(NordicSpacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NordicSpacing.md),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)
                ) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (chips.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(NordicSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            chips.take(3).forEach { chip ->
                                VideoPlayerInfoChip(text = chip, colorScheme = colorScheme)
                            }
                        }
                    }
                }
                VideoPlayerChromeButton(
                    icon = Icons.Filled.Close,
                    colorScheme = colorScheme,
                    size = 40.dp,
                    onClick = onClose
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.10f))
            )

            rows.forEach { row ->
                VideoPlayerInfoRow(row)
            }

            val overview = video.overview.trim()
            if (overview.isNotBlank()) {
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp,
                    color = Color.White.copy(alpha = 0.76f)
                )
            } else {
                Text(
                    text = "暂无简介",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.52f)
                )
            }
        }
    }
}

@Composable
private fun VideoPlayerInfoChip(text: String, colorScheme: ColorScheme) {
    Surface(
        color = colorScheme.primary.copy(alpha = 0.16f),
        contentColor = Color.White,
        shape = NordicShapes.full,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = NordicSpacing.md, vertical = NordicSpacing.xs),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.84f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun VideoPlayerInfoRow(row: VideoPlayerInfoLine) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = row.label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.52f)
        )
        Text(
            text = row.value,
            modifier = Modifier
                .weight(1f)
                .padding(start = NordicSpacing.lg),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.82f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun VideoPlayerControls(
    visiblePosition: Float,
    durationSeconds: Int,
    bufferedPositionSeconds: Int,
    timeline: VideoPlayerTimeline,
    scrubPosition: Float?,
    isPlaying: Boolean,
    hasVideo: Boolean,
    isFullscreen: Boolean,
    playbackSpeed: Float,
    hasNextEpisode: Boolean,
    colorScheme: ColorScheme,
    onScrubChange: (Float) -> Unit,
    onScrubFinished: () -> Unit,
    onScrubCanceled: () -> Unit,
    onSeekBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekForward: () -> Unit,
    onCycleAspectRatio: () -> Unit,
    onShowSpeedSheet: () -> Unit,
    onPlayNextEpisode: () -> Unit,
    onToggleFullscreen: () -> Unit
) {
    Surface(
        color = Color.Black.copy(alpha = 0.56f),
        contentColor = Color.White,
        shape = NordicShapes.xl,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = NordicSpacing.lg, vertical = NordicSpacing.md),
            verticalArrangement = Arrangement.spacedBy(NordicSpacing.md)
        ) {
            PlayerThinSlider(
                position = visiblePosition,
                duration = timeline.sliderMaxSeconds,
                colorScheme = colorScheme,
                enabled = hasVideo,
                activeColor = colorScheme.primary,
                inactiveColor = Color.White.copy(alpha = 0.22f),
                thumbColor = colorScheme.primary,
                bufferedPosition = bufferedPositionSeconds.takeIf { it > 0 }?.toFloat(),
                bufferColor = Color.White.copy(alpha = 0.30f),
                onPositionChange = onScrubChange,
                onPositionChangeFinished = onScrubFinished,
                onPositionChangeCanceled = onScrubCanceled
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    formatDuration(visiblePosition.roundToInt()),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Normal,
                    color = Color.White.copy(alpha = 0.68f),
                    maxLines = 1
                )
                Text(
                    formatVideoPlayerRemainingLabel(durationSeconds, visiblePosition.roundToInt()),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Normal,
                    color = Color.White.copy(alpha = 0.52f),
                    maxLines = 1
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                VideoPlayerChromeButton(
                    icon = Icons.Filled.AspectRatio,
                    colorScheme = colorScheme,
                    enabled = hasVideo,
                    size = 44.dp,
                    onClick = onCycleAspectRatio
                )
                Spacer(modifier = Modifier.width(NordicSpacing.md))
                VideoPlayerChromeButton(
                    text = resolvePlaybackSpeedLabel(playbackSpeed),
                    colorScheme = colorScheme,
                    enabled = hasVideo,
                    size = 44.dp,
                    onClick = onShowSpeedSheet
                )
                Spacer(modifier = Modifier.width(NordicSpacing.md))
                VideoPlayerChromeButton(
                    icon = Icons.Filled.FastRewind,
                    colorScheme = colorScheme,
                    enabled = hasVideo,
                    size = 48.dp,
                    onClick = onSeekBack
                )
                Spacer(modifier = Modifier.width(NordicSpacing.lg))
                VideoPlayerChromeButton(
                    icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    colorScheme = colorScheme,
                    primary = true,
                    enabled = hasVideo,
                    size = 58.dp,
                    onClick = onPlayPause
                )
                Spacer(modifier = Modifier.width(NordicSpacing.lg))
                VideoPlayerChromeButton(
                    icon = Icons.Filled.FastForward,
                    colorScheme = colorScheme,
                    enabled = hasVideo,
                    size = 48.dp,
                    onClick = onSeekForward
                )
                Spacer(modifier = Modifier.width(NordicSpacing.md))
                if (hasNextEpisode) {
                    VideoPlayerChromeButton(
                        icon = Icons.Filled.SkipNext,
                        colorScheme = colorScheme,
                        enabled = true,
                        size = 44.dp,
                        onClick = onPlayNextEpisode
                    )
                    Spacer(modifier = Modifier.width(NordicSpacing.md))
                }
                VideoPlayerChromeButton(
                    icon = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                    colorScheme = colorScheme,
                    enabled = hasVideo,
                    size = 44.dp,
                    onClick = onToggleFullscreen
                )
            }

            if (scrubPosition != null) {
                Text(
                    "松开以跳转至 ${formatDuration(scrubPosition.roundToInt())}",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Normal,
                    color = Color.White.copy(alpha = 0.58f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun VideoPlayerChromeButton(
    icon: ImageVector? = null,
    text: String? = null,
    colorScheme: ColorScheme,
    primary: Boolean = false,
    enabled: Boolean = true,
    size: Dp = 44.dp,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = rememberPressScale(
        interactionSource = interactionSource,
        pressedScale = 0.94f,
        enabled = enabled
    )
    val containerColor = when {
        primary && enabled -> colorScheme.primary
        primary -> colorScheme.primary.copy(alpha = 0.30f)
        enabled -> Color.Black.copy(alpha = 0.46f)
        else -> Color.Black.copy(alpha = 0.24f)
    }
    val contentColor = when {
        primary -> colorScheme.onPrimary
        enabled -> Color.White
        else -> Color.White.copy(alpha = 0.34f)
    }

    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = NordicShapes.full,
        border = if (primary) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
        shadowElevation = if (primary && enabled) 4.dp else 0.dp,
        modifier = Modifier
            .size(size)
            .scale(scale)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size((size.value * 0.52f).dp)
                )
            } else if (!text.isNullOrBlank()) {
                Text(
                    text,
                    fontSize = when {
                        text.length > 2 -> 13.sp
                        size > 50.dp -> 24.sp
                        else -> 18.sp
                    },
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
            }
        }
    }
}

private fun com.nordic.mediahub.data.VideoItem.metaTextForPlayer(): String {
    return buildList {
        type.takeIf { it.isNotBlank() }?.let { add(it) }
        year?.let { add(it.toString()) }
        if (durationSeconds > 0) add(formatDuration(durationSeconds))
    }.joinToString("  /  ")
}

internal data class VideoPlayerInfoLine(
    val label: String,
    val value: String
)

internal fun videoPlayerInfoChips(video: VideoItem): List<String> {
    return buildList {
        video.type.trim().takeIf { it.isNotBlank() }?.let { add(it) }
        video.year?.let { add(it.toString()) }
        if (video.durationSeconds > 0) add(formatDuration(video.durationSeconds))
    }
}

internal fun videoPlayerInfoRows(
    video: VideoItem,
    positionSeconds: Int,
    durationSeconds: Int
): List<VideoPlayerInfoLine> {
    val resolvedDuration = durationSeconds.coerceAtLeast(video.durationSeconds)
    return buildList {
        video.seriesName?.trim()?.takeIf { it.isNotBlank() }?.let {
            add(VideoPlayerInfoLine("剧集", it))
        }
        videoPlayerEpisodeLabel(video)?.let {
            add(VideoPlayerInfoLine("分集", it))
        }
        video.communityRating?.takeIf { it > 0f }?.let {
            add(VideoPlayerInfoLine("评分", String.format(Locale.US, "%.1f", it)))
        }
        if (resolvedDuration > 0) {
            add(VideoPlayerInfoLine("时长", formatDuration(resolvedDuration)))
        }
        val progressLabel = videoPlayerProgressLabel(
            positionSeconds = positionSeconds,
            durationSeconds = resolvedDuration
        )
        if (progressLabel != null) {
            add(VideoPlayerInfoLine("进度", progressLabel))
        }
    }
}

internal fun videoPlayerEpisodeLabel(video: VideoItem): String? {
    val season = video.seasonNumber?.takeIf { it > 0 }
    val episode = video.episodeNumber?.takeIf { it > 0 }
    return when {
        season != null && episode != null -> "S${season}E${episode}"
        season != null -> "第 ${season} 季"
        episode != null -> "第 ${episode} 集"
        else -> null
    }
}

internal fun videoPlayerProgressLabel(
    positionSeconds: Int,
    durationSeconds: Int
): String? {
    val safePosition = positionSeconds.coerceAtLeast(0)
    if (safePosition <= 0) return null

    return if (durationSeconds > 0) {
        "${formatDuration(safePosition.coerceAtMost(durationSeconds))} / ${formatDuration(durationSeconds)}"
    } else {
        formatDuration(safePosition)
    }
}

internal data class VideoPlayerTimeline(
    val positionSeconds: Int,
    val sliderMaxSeconds: Int
)

internal data class SeekFeedback(
    val deltaSeconds: Int,
    val targetPositionSeconds: Int
)

internal fun resolveSeekFeedbackLabel(deltaSeconds: Int): String {
    return if (deltaSeconds >= 0) {
        "+${formatDuration(deltaSeconds)}"
    } else {
        "-${formatDuration(-deltaSeconds)}"
    }
}

internal fun resolveVideoPlayerTimeline(
    positionSeconds: Int,
    durationSeconds: Int
): VideoPlayerTimeline {
    val safePosition = positionSeconds.coerceAtLeast(0)
    val sliderMax = if (durationSeconds > 0) {
        maxOf(durationSeconds, safePosition, 1)
    } else {
        maxOf(safePosition, 1)
    }

    return VideoPlayerTimeline(
        positionSeconds = safePosition.coerceIn(0, sliderMax),
        sliderMaxSeconds = sliderMax
    )
}

internal fun formatVideoPlayerDurationLabel(durationSeconds: Int): String {
    return if (durationSeconds > 0) formatDuration(durationSeconds) else "--:--"
}

internal fun formatVideoPlayerRemainingLabel(durationSeconds: Int, positionSeconds: Int): String {
    if (durationSeconds <= 0) return "--:--"
    val safePosition = positionSeconds.coerceIn(0, durationSeconds)
    return "-${formatDuration(durationSeconds - safePosition)}"
}

internal fun videoPlayerStatusText(
    hasVideo: Boolean,
    isBuffering: Boolean,
    errorMessage: String?
): String? {
    return when {
        !errorMessage.isNullOrBlank() -> "播放异常"
        isBuffering -> "缓冲中"
        !hasVideo -> "暂无视频"
        else -> null
    }
}

internal enum class VideoStatusTone { Error, Buffering, Idle }

internal fun resolveVideoStatusTone(
    hasVideo: Boolean,
    isBuffering: Boolean,
    errorMessage: String?
): VideoStatusTone? {
    return when {
        !errorMessage.isNullOrBlank() -> VideoStatusTone.Error
        isBuffering -> VideoStatusTone.Buffering
        !hasVideo -> VideoStatusTone.Idle
        else -> null
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun resolveVideoPlayerResizeMode(aspectRatioMode: AspectRatioMode): Int {
    return when (aspectRatioMode) {
        AspectRatioMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        AspectRatioMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
        AspectRatioMode.CROP -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    }
}

/**
 * Playback-speed selection sheet (Hills/Yamby-style menu). Selection is
 * applied immediately via the engine and closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoPlaybackSpeedSheet(
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
            VIDEO_PLAYBACK_SPEED_OPTIONS.forEach { speed ->
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
 * Center vertical indicator for the brightness/volume drag gesture: icon +
 * thin vertical progress bar, shown only while the gesture is active.
 */
@Composable
private fun VideoAdjustGestureOverlay(
    side: VideoGestureSide,
    progress: Float,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.Black.copy(alpha = 0.62f),
        contentColor = Color.White,
        shape = NordicShapes.full,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(NordicSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)
        ) {
            Icon(
                imageVector = if (side == VideoGestureSide.Left) {
                    Icons.Filled.BrightnessLow
                } else {
                    Icons.Filled.VolumeUp
                },
                contentDescription = if (side == VideoGestureSide.Left) "亮度" else "音量",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(120.dp)
                    .clip(NordicShapes.full)
                    .background(Color.White.copy(alpha = 0.22f))
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(progress.coerceIn(0f, 1f))
                        .clip(NordicShapes.full)
                        .background(Color.White)
                )
            }
            Text(
                text = "${(progress.coerceIn(0f, 1f) * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.86f),
                maxLines = 1
            )
        }
    }
}

/**
 * Locked-state overlay: a single unlock button; all other gestures are
 * disabled by the gesture modifier so pocket touches cannot seek or close.
 */
@Composable
private fun VideoPlayerLockOverlay(
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.Black.copy(alpha = 0.56f),
        contentColor = Color.White,
        shape = NordicShapes.full,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
        modifier = modifier.size(44.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.clickable(onClick = onUnlock)
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = "解除手势锁",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Wraps window brightness adjustments for the left-half vertical gesture.
 * `screenBrightness` is already normalized to 0..1 (BRIGHTNESS_OVERRIDE_NONE
 * sentinel is negative), so [fraction] maps directly; keeps a small floor so
 * the screen never goes fully black mid-gesture.
 */
internal class VideoBrightnessController(private val window: Window) {
    fun adjustByFraction(fraction: Float) {
        val clamped = fraction.coerceIn(0f, 1f)
        val target = BRIGHTNESS_FRACTION_FLOOR + clamped * (1f - BRIGHTNESS_FRACTION_FLOOR)
        val attributes = window.attributes
        attributes.screenBrightness = target
        window.attributes = attributes
    }

    companion object {
        /** Keep a sliver of brightness so the screen never goes fully black mid-gesture. */
        private const val BRIGHTNESS_FRACTION_FLOOR = 0.02f
    }
}

/**
 * Wraps STREAM_MUSIC volume adjustments for the right-half vertical gesture.
 * Fraction 0..1 maps across [0, maxVolume]; reads the current volume as the
 * baseline when constructed.
 */
internal class VideoVolumeController(private val audioManager: AudioManager) {
    private val maxVolume: Int = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    private val currentVolume: Int = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

    val volumeFraction: Float
        get() = currentVolume.toFloat() / maxVolume.toFloat()

    fun adjustByFraction(fraction: Float) {
        val clamped = fraction.coerceIn(0f, 1f)
        val target = (clamped * maxVolume).roundToInt().coerceIn(0, maxVolume)
        if (target != currentVolume) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        }
    }
}

/**
 * Transient chip shown while the long-press temporary-speed gesture is active.
 */
@Composable
private fun VideoPlayerTempSpeedChip(
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.Black.copy(alpha = 0.62f),
        contentColor = Color.White,
        shape = NordicShapes.full,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = NordicSpacing.lg, vertical = NordicSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(NordicSpacing.sm, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.FastForward,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "${VIDEO_TEMP_SPEED.toInt()}x 倍速中",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1
            )
        }
    }
}

/**
 * Floating "next episode" action shown near the end of an episode
 * (Hills/Yamby-style). Hidden for movies / final episodes via the caller.
 */
@Composable
private fun VideoPlayerNextEpisodeOverlay(
    episodeTitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.Black.copy(alpha = 0.68f),
        contentColor = Color.White,
        shape = NordicShapes.md,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
        shadowElevation = 6.dp,
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = NordicSpacing.lg, vertical = NordicSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(NordicSpacing.sm, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.widthIn(max = 220.dp),
                verticalArrangement = Arrangement.spacedBy(NordicSpacing.xs)
            ) {
                Text(
                    text = "即将播放下一集",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.66f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = episodeTitle.ifBlank { "下一集" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = "播放下一集",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
