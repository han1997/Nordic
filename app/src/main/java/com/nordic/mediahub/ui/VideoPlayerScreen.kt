package com.nordic.mediahub.ui

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.SurfaceView
import android.view.Window
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import com.nordic.mediahub.data.VideoItem
import com.nordic.mediahub.playback.resolveVideoRelativeSeekPositionSeconds
import com.nordic.mediahub.playback.AspectRatioMode
import com.nordic.mediahub.playback.VideoPlaybackState
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
    modifier: Modifier = Modifier,
    externalError: String? = null,
    onSurfaceReady: (SurfaceView) -> Unit,
    onSurfaceDisposed: (SurfaceView) -> Unit,
    onSeek: (Int) -> Unit,
    onSeekRelative: (Int) -> Unit = {},
    onPlayPause: () -> Unit,
    onCycleAspectRatio: () -> Unit = {},
    onSetPlaybackSpeed: (Float) -> Unit = {},
    onSetPreferredTextTrack: (com.nordic.mediahub.data.VideoStreamInfo?) -> Unit = {},
    onSetPreferredAudioTrack: (com.nordic.mediahub.data.VideoStreamInfo?) -> Unit = {},
    onAttachSubtitleView: (androidx.media3.ui.SubtitleView?) -> Unit = {},
    pipEnabled: Boolean = true,
    onTogglePip: (Boolean) -> Unit = {},
    autoSkipIntro: Boolean = true,
    onToggleAutoSkipIntro: (Boolean) -> Unit = {},
    onSkipIntro: () -> Unit = {},
    isInPipMode: Boolean = false,
    nextEpisode: VideoItem? = null,
    episodeContext: List<VideoItem> = emptyList(),
    onPlayEpisode: (VideoItem) -> Unit = {},
    onPlayNextEpisode: () -> Unit = {},
    onToggleFullscreen: () -> Unit = {},
    isFullscreen: Boolean = false,
    onClose: () -> Unit,
    onCloseAnyway: () -> Unit = {}
) {
    val video = state.video
    val durationSeconds = state.durationSeconds.coerceAtLeast(video?.durationSeconds ?: 0)
    val errorMessage = (externalError ?: state.errorMessage)?.takeIf { it.isNotBlank() }
    val statusTone = resolveVideoStatusTone(video != null, state.isBuffering, errorMessage)
    val playerSubtitle = remember(video) { video?.metaTextForPlayer() }
    val episodes = remember(video, episodeContext) { resolveVideoPlayerEpisodes(video, episodeContext) }
    val hasNextEpisode = !nextEpisode?.streamUrl.isNullOrBlank()
    val videoAspectRatio = state.videoAspectRatio.takeIf { it > 0f } ?: 16f / 9f
    val currentOnSurfaceReady by rememberUpdatedState(onSurfaceReady)
    val currentOnSurfaceDisposed by rememberUpdatedState(onSurfaceDisposed)
    val surfaceReadyCallback = remember { { surface: SurfaceView -> currentOnSurfaceReady(surface) } }
    val surfaceDisposedCallback = remember { { surface: SurfaceView -> currentOnSurfaceDisposed(surface) } }

    var scrubPosition by remember(video?.id) { mutableStateOf<Float?>(null) }
    var controlsVisible by remember(video?.id) { mutableStateOf(true) }
    var activePanel by remember(video?.id) { mutableStateOf<VideoPlayerPanel?>(null) }
    var seekFeedback by remember(video?.id) { mutableStateOf<SeekFeedback?>(null) }
    var gesturesLocked by remember(video?.id) { mutableStateOf(false) }
    var isTempSpeeding by remember(video?.id) { mutableStateOf(false) }
    var nextPromptDismissed by remember(video?.id) { mutableStateOf(false) }
    var interactionVersion by remember { mutableIntStateOf(0) }
    val adjustGestureState = remember { VideoAdjustGestureState() }
    val context = LocalContext.current
    val activityWindow = remember(context) { (context as? Activity)?.window }
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    val brightnessController = remember(activityWindow) { activityWindow?.let { VideoBrightnessController(it) } }
    val volumeController = remember(audioManager) { audioManager?.let { VideoVolumeController(it) } }
    val feedbackScope = rememberCoroutineScope()
    val feedbackJob = remember { AtomicReference<kotlinx.coroutines.Job?>(null) }
    val prePressSpeed = remember { AtomicReference(1f) }

    fun closePanel() {
        activePanel = null
        controlsVisible = true
        interactionVersion++
    }

    fun seekRelative(delta: Int) {
        interactionVersion++
        feedbackJob.get()?.cancel()
        seekFeedback = SeekFeedback(
            deltaSeconds = delta,
            targetPositionSeconds = resolveVideoRelativeSeekPositionSeconds(state.positionSeconds, delta, durationSeconds)
        )
        onSeekRelative(delta)
        feedbackJob.set(feedbackScope.launch {
            delay(1200L)
            seekFeedback = null
        })
    }

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

    fun playNextEpisode() {
        if (!hasNextEpisode) return
        closePanel()
        onPlayNextEpisode()
    }

    LaunchedEffect(controlsVisible, state.isPlaying, scrubPosition, statusTone, activePanel, interactionVersion, isTempSpeeding) {
        if (controlsVisible && state.isPlaying && scrubPosition == null && statusTone == null &&
            activePanel == null && !isTempSpeeding
        ) {
            delay(VIDEO_PLAYER_CONTROLS_AUTO_HIDE_MS)
            controlsVisible = false
        }
    }
    LaunchedEffect(statusTone) {
        if (statusTone != null) controlsVisible = true
    }
    DisposableEffect(video?.id) {
        onDispose { feedbackJob.get()?.cancel() }
    }
    LaunchedEffect(isInPipMode) {
        if (isInPipMode) {
            activePanel = null
            scrubPosition = null
            seekFeedback = null
            adjustGestureState.reset()
            endTempSpeed()
        } else {
            controlsVisible = true
        }
    }
    BackHandler(enabled = !isInPipMode && isFullscreen) { onToggleFullscreen() }
    BackHandler(enabled = !isInPipMode && gesturesLocked) {
        gesturesLocked = false
        controlsVisible = true
    }

    val showChrome = controlsVisible && activePanel == null
    Box(modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.fillMaxSize().then(if (activePanel != null) Modifier.clearAndSetSemantics {} else Modifier)) {
            // Gestures belong to the video surface, not to an ancestor of the buttons or modal lists.
            Box(
                Modifier.fillMaxSize().videoPlayerGestures(
                    enabled = !isInPipMode && video != null && !gesturesLocked && activePanel == null,
                    isFullscreen = isFullscreen,
                    durationSeconds = durationSeconds,
                    currentPositionSeconds = state.positionSeconds,
                    onToggleControls = { controlsVisible = !controlsVisible },
                    onSeekRelative = ::seekRelative,
                    onScrubChange = { scrubPosition = it },
                    onSeek = onSeek,
                    onCycleAspectRatio = onCycleAspectRatio,
                    onBrightnessDrag = { step ->
                        brightnessController?.let { controller ->
                            controller.adjustByFraction(adjustGestureState.applyStep(VideoGestureSide.Left, step, 0.5f))
                        }
                    },
                    onVolumeDrag = { step ->
                        volumeController?.let { controller ->
                            controller.adjustByFraction(adjustGestureState.applyStep(VideoGestureSide.Right, step, controller.volumeFraction))
                        }
                    },
                    onGestureEnd = { adjustGestureState.reset() },
                    onLongPressStart = ::startTempSpeed,
                    onLongPressEnd = ::endTempSpeed
                )
            ) {
                VideoPlayerSurface(
                    aspectRatioMode = if (isInPipMode) AspectRatioMode.FIT else state.aspectRatioMode,
                    videoAspectRatio = videoAspectRatio,
                    onSurfaceReady = surfaceReadyCallback,
                    onSurfaceDisposed = surfaceDisposedCallback,
                    modifier = Modifier.fillMaxSize()
                )
                if (state.selectedSubtitleStream != null) {
                    AndroidView(
                        factory = { context ->
                            androidx.media3.ui.SubtitleView(context, null).apply {
                                setViewType(androidx.media3.ui.SubtitleView.VIEW_TYPE_WEB)
                            }
                        },
                        update = { view -> onAttachSubtitleView(view) },
                        onRelease = { onAttachSubtitleView(null) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            if (!isInPipMode) {
                AnimatedVisibility(visible = showChrome,
                    enter = fadeIn(tween(VIDEO_PLAYER_CHROME_FADE_MS, easing = NordicMotion.easingStandard)),
                    exit = fadeOut(tween(VIDEO_PLAYER_CHROME_FADE_MS, easing = NordicMotion.easingStandard))) {
                    VideoPlayerScrim()
                }
                if (video == null) {
                    VideoPlayerCenterMessage("暂无视频", "从媒体库选择一个视频开始播放")
                } else if (errorMessage != null) {
                    VideoPlayerCenterMessage("播放异常", errorMessage, onCloseAnyway)
                } else if (state.isBuffering) {
                    VideoPlayerCenterMessage("缓冲中", "正在准备视频流")
                }
                seekFeedback?.let { feedback ->
                    VideoPlayerSeekFeedbackOverlay(feedback, colorScheme, Modifier.align(Alignment.Center))
                }
                if (isTempSpeeding) VideoPlayerTempSpeedChip(Modifier.align(Alignment.Center))
                if (adjustGestureState.visible) VideoAdjustGestureOverlay(
                    adjustGestureState.side, adjustGestureState.progress, Modifier.align(Alignment.Center)
                )
                if (gesturesLocked && !showChrome && activePanel == null) {
                    VideoPlayerLockOverlay(
                        onUnlock = { gesturesLocked = false; controlsVisible = true },
                        modifier = Modifier.align(Alignment.CenterStart)
                            .windowInsetsPadding(WindowInsets.safeDrawing).padding(NordicSpacing.lg)
                    )
                }
                AnimatedVisibility(visible = showChrome,
                    enter = fadeIn(tween(VIDEO_PLAYER_CHROME_FADE_MS, easing = NordicMotion.easingStandard)),
                    exit = fadeOut(tween(VIDEO_PLAYER_CHROME_FADE_MS, easing = NordicMotion.easingStandard))) {
                    VideoPlayerChrome(
                        state = state, colorScheme = colorScheme, subtitle = playerSubtitle,
                        durationSeconds = durationSeconds, scrubPosition = scrubPosition,
                        isFullscreen = isFullscreen, hasEpisodes = episodes.isNotEmpty(),
                        hasNextEpisode = hasNextEpisode, hasPlaybackStatus = statusTone != null,
                        gesturesLocked = gesturesLocked,
                        onClose = { endTempSpeed(); onClose() },
                        onToggleLock = { gesturesLocked = !gesturesLocked; interactionVersion++ },
                        onPanel = { panel -> activePanel = panel; interactionVersion++ },
                        onPlayPause = { onPlayPause(); interactionVersion++ },
                        onSeekRelative = ::seekRelative,
                        onCycleAspectRatio = { onCycleAspectRatio(); interactionVersion++ },
                        onPlayNextEpisode = ::playNextEpisode,
                        onToggleFullscreen = { onToggleFullscreen(); interactionVersion++ },
                        onScrubChange = { scrubPosition = it },
                        onScrubFinished = {
                            scrubPosition?.let { onSeek(it.roundToInt()) }
                            scrubPosition = null
                            interactionVersion++
                        },
                        onScrubCanceled = { scrubPosition = null }
                    )
                }
                if (shouldShowVideoNextEpisodePrompt(
                        hasNextEpisode, durationSeconds, state.positionSeconds, controlsVisible,
                        activePanel != null, gesturesLocked, statusTone != null, nextPromptDismissed
                    )
                ) {
                    VideoPlayerNextEpisodeOverlay(
                        episodeTitle = nextEpisode?.title.orEmpty(), onClick = ::playNextEpisode,
                        onDismiss = { nextPromptDismissed = true },
                        modifier = Modifier.align(Alignment.TopEnd)
                            .windowInsetsPadding(WindowInsets.safeDrawing).padding(NordicSpacing.lg)
                    )
                }
                if (shouldShowVideoSkipIntroButton(
                        state.introRange, state.positionSeconds, activePanel != null,
                        gesturesLocked, statusTone != null
                    )
                ) {
                    VideoPlayerSkipIntroButton(
                        colorScheme = colorScheme,
                        modifier = Modifier.align(Alignment.BottomEnd)
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(end = NordicSpacing.lg, bottom = 96.dp)
                    ) {
                        onSkipIntro()
                        interactionVersion++
                    }
                }
            }
        }
        if (!isInPipMode) {
            AnimatedContent(
                modifier = Modifier.fillMaxSize(),
                targetState = activePanel,
                transitionSpec = {
                    fadeIn(tween(NordicMotion.durationShort)) togetherWith fadeOut(tween(NordicMotion.durationShort))
                },
                label = "video-player-panel"
            ) { panel ->
                if (panel != null) VideoPlayerPanelHost(
                    panel = panel, state = state, episodes = episodes, nextEpisode = nextEpisode,
                    isFullscreen = isFullscreen, pipEnabled = pipEnabled,
                    autoSkipIntro = autoSkipIntro,
                    onPanelChange = { activePanel = it }, onDismiss = ::closePanel,
                    onSetPlaybackSpeed = { speed -> onSetPlaybackSpeed(speed); closePanel() },
                    onCycleAspectRatio = onCycleAspectRatio,
                    onSetPreferredTextTrack = { stream ->
                        onSetPreferredTextTrack(stream)
                        if (stream == null) closePanel()
                    },
                    onSetPreferredAudioTrack = { stream ->
                        onSetPreferredAudioTrack(stream)
                        closePanel()
                    },
                    onTogglePip = onTogglePip,
                    onToggleAutoSkipIntro = onToggleAutoSkipIntro,
                    onSeekTo = { position ->
                        closePanel()
                        onSeek(position)
                    },
                    onPlayEpisode = { selected ->
                        closePanel()
                        if (shouldPlaySelectedVideoEpisode(video, selected)) onPlayEpisode(selected)
                    },
                    onPlayNextEpisode = ::playNextEpisode
                )
            }
        }
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

internal typealias VideoPlayerTimeline = PlayerTimeline

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

internal fun resolveVideoPlayerTimeline(positionSeconds: Int, durationSeconds: Int): VideoPlayerTimeline =
    resolvePlayerTimeline(positionSeconds, durationSeconds)

internal fun formatVideoPlayerDurationLabel(durationSeconds: Int): String = formatKnownDuration(durationSeconds)

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
                    Icons.AutoMirrored.Filled.VolumeUp
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
        modifier = modifier.size(48.dp)
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
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.Black.copy(alpha = 0.72f),
        contentColor = Color.White,
        shape = NordicShapes.md,
        modifier = modifier.widthIn(max = 300.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.weight(1f).clickable(role = Role.Button, onClick = onClick)
                    .padding(NordicSpacing.md),
                verticalArrangement = Arrangement.spacedBy(NordicSpacing.xs)
            ) {
                Text("播放下一集", style = MaterialTheme.typography.labelLarge, color = Color.White)
                Text(episodeTitle, style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.68f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            VideoPlayerChromeButton(Icons.Filled.Close, description = "暂不播放下一集", onClick = onDismiss)
        }
    }
}

/** Floating manual "跳过片头" action; visible only inside the intro range. */
@Composable
private fun VideoPlayerSkipIntroButton(
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        color = colorScheme.surface.copy(alpha = 0.94f),
        contentColor = colorScheme.onSurface,
        shape = NordicShapes.full,
        border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.24f)),
        modifier = modifier
    ) {
        Row(
            Modifier.clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = NordicSpacing.lg, vertical = NordicSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "跳过片头",
                style = MaterialTheme.typography.labelLarge,
                color = colorScheme.primary
            )
        }
    }
}
