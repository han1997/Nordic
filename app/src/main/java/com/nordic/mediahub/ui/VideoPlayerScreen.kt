package com.nordic.mediahub.ui

import android.view.SurfaceView
import android.widget.FrameLayout
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import com.nordic.mediahub.playback.AspectRatioMode
import com.nordic.mediahub.playback.VideoPlaybackState
import kotlin.math.roundToInt

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    state: VideoPlaybackState,
    colorScheme: ColorScheme,
    onSurfaceReady: (SurfaceView) -> Unit,
    onSurfaceDisposed: (SurfaceView) -> Unit,
    onSeek: (Int) -> Unit,
    onSeekBack: () -> Unit = {},
    onSeekForward: () -> Unit = {},
    onPlayPause: () -> Unit,
    onCycleAspectRatio: () -> Unit = {},
    onToggleFullscreen: () -> Unit = {},
    isFullscreen: Boolean = false,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val video = state.video
    val durationSeconds = state.durationSeconds.coerceAtLeast(video?.durationSeconds ?: 0)
    val timeline = resolveVideoPlayerTimeline(
        positionSeconds = state.positionSeconds,
        durationSeconds = durationSeconds
    )
    var scrubPosition by remember(video?.id) { mutableStateOf<Float?>(null) }
    val visiblePosition = (scrubPosition ?: timeline.positionSeconds.toFloat())
        .coerceIn(0f, timeline.sliderMaxSeconds.toFloat())
    val statusText = videoPlayerStatusText(
        hasVideo = video != null,
        isBuffering = state.isBuffering,
        errorMessage = state.errorMessage
    )
    val videoAspectRatio = state.videoAspectRatio.takeIf { it > 0f } ?: 16f / 9f
    val currentOnSurfaceReady by rememberUpdatedState(onSurfaceReady)
    val currentOnSurfaceDisposed by rememberUpdatedState(onSurfaceDisposed)
    val surfaceReadyCallback = remember {
        { surface: SurfaceView -> currentOnSurfaceReady(surface) }
    }
    val surfaceDisposedCallback = remember {
        { surface: SurfaceView -> currentOnSurfaceDisposed(surface) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        VideoPlayerSurface(
            aspectRatioMode = state.aspectRatioMode,
            videoAspectRatio = videoAspectRatio,
            onSurfaceReady = surfaceReadyCallback,
            onSurfaceDisposed = surfaceDisposedCallback,
            modifier = Modifier.fillMaxSize()
        )

        VideoPlayerScrim()

        if (video == null) {
            VideoPlayerCenterMessage(
                title = "No video loaded",
                subtitle = "Select a video from the library to start playback."
            )
        } else if (state.errorMessage != null) {
            VideoPlayerCenterMessage(
                title = "Playback issue",
                subtitle = state.errorMessage
            )
        } else if (state.isBuffering) {
            VideoPlayerCenterMessage(
                title = "Buffering",
                subtitle = "Preparing the stream."
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (isFullscreen) Modifier else Modifier.statusBarsPadding())
                .then(if (isFullscreen) Modifier else Modifier.navigationBarsPadding())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            VideoPlayerTopBar(
                title = video?.title ?: "Video player",
                subtitle = video?.metaTextForPlayer(),
                statusText = statusText,
                colorScheme = colorScheme,
                onClose = onClose
            )

            VideoPlayerControls(
                visiblePosition = visiblePosition,
                durationSeconds = durationSeconds,
                timeline = timeline,
                scrubPosition = scrubPosition,
                isPlaying = state.isPlaying,
                hasVideo = video != null,
                aspectRatioMode = state.aspectRatioMode,
                isFullscreen = isFullscreen,
                colorScheme = colorScheme,
                onScrubChange = { scrubPosition = it },
                onScrubFinished = {
                    val target = scrubPosition ?: visiblePosition
                    onSeek(target.roundToInt())
                    scrubPosition = null
                },
                onSeekBack = onSeekBack,
                onPlayPause = onPlayPause,
                onSeekForward = onSeekForward,
                onCycleAspectRatio = onCycleAspectRatio,
                onToggleFullscreen = onToggleFullscreen
            )
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
private fun VideoPlayerTopBar(
    title: String,
    subtitle: String?,
    statusText: String?,
    colorScheme: ColorScheme,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        VideoPlayerChromeButton(
            text = "X",
            colorScheme = colorScheme,
            primary = false,
            onClick = onClose
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                title,
                fontSize = 18.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.66f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (!statusText.isNullOrBlank()) {
            VideoPlayerStatusPill(text = statusText, colorScheme = colorScheme)
        }
    }
}

@Composable
private fun VideoPlayerStatusPill(text: String, colorScheme: ColorScheme) {
    Surface(
        color = if (text == "Buffering") {
            colorScheme.primary.copy(alpha = 0.18f)
        } else {
            Color.Black.copy(alpha = 0.42f)
        },
        contentColor = Color.White,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.86f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BoxScope.VideoPlayerCenterMessage(title: String, subtitle: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp)
            .align(Alignment.Center),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            title,
            color = Color.White.copy(alpha = 0.84f),
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle,
                color = Color.White.copy(alpha = 0.60f),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun VideoPlayerControls(
    visiblePosition: Float,
    durationSeconds: Int,
    timeline: VideoPlayerTimeline,
    scrubPosition: Float?,
    isPlaying: Boolean,
    hasVideo: Boolean,
    aspectRatioMode: AspectRatioMode,
    isFullscreen: Boolean,
    colorScheme: ColorScheme,
    onScrubChange: (Float) -> Unit,
    onScrubFinished: () -> Unit,
    onSeekBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekForward: () -> Unit,
    onCycleAspectRatio: () -> Unit,
    onToggleFullscreen: () -> Unit
) {
    Surface(
        color = Color.Black.copy(alpha = 0.56f),
        contentColor = Color.White,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Slider(
                value = visiblePosition,
                onValueChange = onScrubChange,
                onValueChangeFinished = onScrubFinished,
                valueRange = 0f..timeline.sliderMaxSeconds.toFloat(),
                enabled = hasVideo,
                colors = SliderDefaults.colors(
                    thumbColor = colorScheme.primary,
                    activeTrackColor = colorScheme.primary,
                    inactiveTrackColor = Color.White.copy(alpha = 0.22f),
                    disabledThumbColor = Color.White.copy(alpha = 0.28f),
                    disabledActiveTrackColor = Color.White.copy(alpha = 0.20f),
                    disabledInactiveTrackColor = Color.White.copy(alpha = 0.12f)
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    formatDuration(visiblePosition.roundToInt()),
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.68f),
                    maxLines = 1
                )
                Text(
                    formatVideoPlayerDurationLabel(durationSeconds),
                    fontSize = 12.sp,
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
                    text = aspectRatioMode.label,
                    colorScheme = colorScheme,
                    enabled = hasVideo,
                    size = 44.dp,
                    onClick = onCycleAspectRatio
                )
                Spacer(modifier = Modifier.width(12.dp))
                VideoPlayerChromeButton(
                    text = "-10",
                    colorScheme = colorScheme,
                    enabled = hasVideo,
                    size = 48.dp,
                    onClick = onSeekBack
                )
                Spacer(modifier = Modifier.width(18.dp))
                VideoPlayerChromeButton(
                    text = if (isPlaying) "||" else ">",
                    colorScheme = colorScheme,
                    primary = true,
                    enabled = hasVideo,
                    size = 58.dp,
                    onClick = onPlayPause
                )
                Spacer(modifier = Modifier.width(18.dp))
                VideoPlayerChromeButton(
                    text = "+30",
                    colorScheme = colorScheme,
                    enabled = hasVideo,
                    size = 48.dp,
                    onClick = onSeekForward
                )
                Spacer(modifier = Modifier.width(12.dp))
                VideoPlayerChromeButton(
                    text = if (isFullscreen) "Exit" else "Full",
                    colorScheme = colorScheme,
                    enabled = hasVideo,
                    size = 44.dp,
                    onClick = onToggleFullscreen
                )
            }

            if (scrubPosition != null) {
                Text(
                    "Release to seek to ${formatDuration(scrubPosition.roundToInt())}",
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 12.sp,
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
    text: String,
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
        shape = RoundedCornerShape(999.dp),
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

private fun com.nordic.mediahub.data.VideoItem.metaTextForPlayer(): String {
    return buildList {
        type.takeIf { it.isNotBlank() }?.let { add(it) }
        year?.let { add(it.toString()) }
        if (durationSeconds > 0) add(formatDuration(durationSeconds))
    }.joinToString("  /  ")
}

internal data class VideoPlayerTimeline(
    val positionSeconds: Int,
    val sliderMaxSeconds: Int
)

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

internal fun videoPlayerStatusText(
    hasVideo: Boolean,
    isBuffering: Boolean,
    errorMessage: String?
): String? {
    return when {
        !errorMessage.isNullOrBlank() -> "Issue"
        isBuffering -> "Buffering"
        !hasVideo -> "Idle"
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
