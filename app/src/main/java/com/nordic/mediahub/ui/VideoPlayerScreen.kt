package com.nordic.mediahub.ui

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.nordic.mediahub.data.VideoMediaTrack
import com.nordic.mediahub.playback.VideoPlaybackState
import kotlin.math.abs

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    state: VideoPlaybackState,
    player: Player,
    colorScheme: ColorScheme,
    onSeek: (Int) -> Unit,
    onPlayPause: () -> Unit,
    onClose: () -> Unit,
    onSpeedChange: (Float) -> Unit = {},
    onSelectAudioTrack: (Int?) -> Unit = {},
    onSelectSubtitleTrack: (Int?) -> Unit = {},
    onSubtitleScaleChange: (Float) -> Unit = {},
    hasNextEpisode: Boolean = false,
    isLoadingNextEpisode: Boolean = false,
    externalError: String? = null,
    onPlayNextEpisode: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val playbackInfo = state.playbackInfo
    val duration = maxOf(state.durationSeconds, playbackInfo?.durationSeconds ?: 0, 1)
    var scrubPosition by remember(playbackInfo?.itemId) { mutableStateOf<Float?>(null) }
    val visiblePosition = scrubPosition ?: state.positionSeconds.toFloat()
    val statusText = when {
        externalError != null -> externalError
        state.errorMessage != null -> state.errorMessage
        state.isBuffering -> "正在缓冲"
        state.isPlaying -> "正在播放"
        playbackInfo != null -> "已暂停"
        else -> "等待播放"
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(playbackInfo?.itemId, state.positionSeconds, duration) {
                var dragStartX = 0f
                var totalDragX = 0f
                var totalDragY = 0f
                detectDragGestures(
                    onDragStart = { offset ->
                        dragStartX = offset.x
                        totalDragX = 0f
                        totalDragY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDragX += dragAmount.x
                        totalDragY += dragAmount.y
                    },
                    onDragEnd = {
                        if (abs(totalDragX) > abs(totalDragY) && abs(totalDragX) > 48f) {
                            val deltaSeconds = (totalDragX / 8f).toInt()
                            onSeek((state.positionSeconds + deltaSeconds).coerceIn(0, duration))
                        } else if (abs(totalDragY) > 48f) {
                            if (dragStartX > size.width / 2f) {
                                adjustVideoVolume(context, -totalDragY)
                            } else {
                                adjustWindowBrightness(context, -totalDragY / size.height.coerceAtLeast(1))
                            }
                        }
                    }
                )
            }
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    this.player = player
                }
            },
            update = { view ->
                view.player = player
                view.subtitleView?.setFractionalTextSize(0.0533f * state.subtitleScale)
            },
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.78f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.88f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(
                    start = 18.dp,
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp,
                    end = 18.dp,
                    bottom = 16.dp
                ),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            VideoPlayerTopBar(
                title = playbackInfo?.title ?: "视频播放",
                statusText = statusText,
                isError = externalError != null || state.errorMessage != null,
                colorScheme = colorScheme,
                onClose = onClose
            )

            VideoPlayerControls(
                title = playbackInfo?.title ?: "等待播放",
                overview = playbackInfo?.overview.orEmpty(),
                audioTracks = playbackInfo?.audioTracks.orEmpty(),
                subtitleTracks = playbackInfo?.subtitleTracks.orEmpty(),
                isPlaying = state.isPlaying,
                position = visiblePosition.coerceIn(0f, duration.toFloat()),
                duration = duration,
                colorScheme = colorScheme,
                onPositionChange = { scrubPosition = it },
                onPositionChangeFinished = {
                    val target = scrubPosition ?: visiblePosition
                    onSeek(target.toInt())
                    scrubPosition = null
                },
                onPlayPause = onPlayPause,
                speed = state.speed,
                onSpeedChange = onSpeedChange,
                selectedAudioTrackIndex = state.selectedAudioTrackIndex,
                selectedSubtitleTrackIndex = state.selectedSubtitleTrackIndex,
                subtitleScale = state.subtitleScale,
                onSelectAudioTrack = onSelectAudioTrack,
                onSelectSubtitleTrack = onSelectSubtitleTrack,
                onSubtitleScaleChange = onSubtitleScaleChange,
                hasNextEpisode = hasNextEpisode,
                isLoadingNextEpisode = isLoadingNextEpisode,
                onPlayNextEpisode = onPlayNextEpisode
            )
        }
    }
}

@Composable
private fun VideoPlayerTopBar(
    title: String,
    statusText: String,
    isError: Boolean,
    colorScheme: ColorScheme,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = Color.White.copy(alpha = 0.12f),
            contentColor = Color.White,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp),
            onClick = onClose
        ) {
            Text(
                "×",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                statusText,
                color = if (isError) colorScheme.error else Color.White.copy(alpha = 0.68f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun VideoPlayerControls(
    title: String,
    overview: String,
    audioTracks: List<VideoMediaTrack>,
    subtitleTracks: List<VideoMediaTrack>,
    isPlaying: Boolean,
    position: Float,
    duration: Int,
    speed: Float,
    selectedAudioTrackIndex: Int?,
    selectedSubtitleTrackIndex: Int?,
    subtitleScale: Float,
    colorScheme: ColorScheme,
    onPositionChange: (Float) -> Unit,
    onPositionChangeFinished: () -> Unit,
    onPlayPause: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSelectAudioTrack: (Int?) -> Unit,
    onSelectSubtitleTrack: (Int?) -> Unit,
    onSubtitleScaleChange: (Float) -> Unit,
    hasNextEpisode: Boolean,
    isLoadingNextEpisode: Boolean,
    onPlayNextEpisode: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            title,
            color = Color.White,
            fontSize = 21.sp,
            lineHeight = 25.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (overview.isNotBlank()) {
            Text(
                overview,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Slider(
            value = position,
            onValueChange = onPositionChange,
            onValueChangeFinished = onPositionChangeFinished,
            valueRange = 0f..duration.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = colorScheme.primary,
                activeTrackColor = colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                formatDuration(position.toInt()),
                color = Color.White.copy(alpha = 0.64f),
                fontSize = 12.sp
            )
            Surface(
                color = colorScheme.primary,
                contentColor = colorScheme.onPrimary,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp),
                onClick = onPlayPause
            ) {
                Text(
                    if (isPlaying) "暂停" else "播放",
                    modifier = Modifier.padding(horizontal = 26.dp, vertical = 12.dp),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            VideoControlChip(
                text = if (isLoadingNextEpisode) "加载中" else "下一集",
                active = hasNextEpisode,
                enabled = hasNextEpisode && !isLoadingNextEpisode,
                colorScheme = colorScheme,
                onClick = onPlayNextEpisode
            )
            Text(
                formatDuration(duration),
                color = Color.White.copy(alpha = 0.64f),
                fontSize = 12.sp
            )
        }
        val speedOptions = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            speedOptions.forEach { spd ->
                val active = speed == spd
                Surface(
                    color = if (active) colorScheme.primary else Color.White.copy(alpha = 0.12f),
                    contentColor = if (active) colorScheme.onPrimary else Color.White.copy(alpha = 0.7f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    onClick = { onSpeedChange(spd) }
                ) {
                    Text(
                        if (spd == spd.toInt().toFloat()) "${spd.toInt()}x" else "${spd}x",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        fontSize = 12.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium
                    )
                }
            }
        }
        VideoTrackControls(
            title = "音轨",
            options = listOf(null to "自动") + audioTracks.map { it.index to it.label },
            selectedIndex = selectedAudioTrackIndex,
            colorScheme = colorScheme,
            onSelect = onSelectAudioTrack
        )
        VideoTrackControls(
            title = "字幕",
            options = listOf(null to "关闭") + subtitleTracks.map { it.index to it.label },
            selectedIndex = selectedSubtitleTrackIndex,
            colorScheme = colorScheme,
            onSelect = onSelectSubtitleTrack
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VideoControlChip("A-", active = false, colorScheme = colorScheme) {
                onSubtitleScaleChange((subtitleScale - 0.1f).coerceIn(0.75f, 1.75f))
            }
            VideoControlChip("${(subtitleScale * 100).toInt()}%", active = subtitleScale != 1f, colorScheme = colorScheme)
            VideoControlChip("A+", active = false, colorScheme = colorScheme) {
                onSubtitleScaleChange((subtitleScale + 0.1f).coerceIn(0.75f, 1.75f))
            }
        }
    }
}

@Composable
private fun VideoTrackControls(
    title: String,
    options: List<Pair<Int?, String>>,
    selectedIndex: Int?,
    colorScheme: ColorScheme,
    onSelect: (Int?) -> Unit
) {
    if (options.size <= 1) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = Color.White.copy(alpha = 0.62f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.take(4).forEach { (index, label) ->
                VideoControlChip(
                    text = label,
                    active = selectedIndex == index,
                    colorScheme = colorScheme,
                    onClick = { onSelect(index) }
                )
            }
        }
    }
}

@Composable
private fun VideoControlChip(
    text: String,
    active: Boolean,
    enabled: Boolean = true,
    colorScheme: ColorScheme,
    onClick: () -> Unit = {}
) {
    Surface(
        color = when {
            active -> colorScheme.primary
            enabled -> Color.White.copy(alpha = 0.12f)
            else -> Color.White.copy(alpha = 0.06f)
        },
        contentColor = when {
            active -> colorScheme.onPrimary
            enabled -> Color.White.copy(alpha = 0.72f)
            else -> Color.White.copy(alpha = 0.36f)
        },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        enabled = enabled,
        onClick = onClick
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun adjustVideoVolume(context: Context, delta: Float) {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    val nextVolume = (currentVolume + (delta / 80f).toInt()).coerceIn(0, maxVolume)
    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, nextVolume, 0)
}

private fun adjustWindowBrightness(context: Context, deltaFraction: Float) {
    val activity = context as? Activity ?: return
    val window = activity.window ?: return
    val params = window.attributes
    val current = params.screenBrightness.takeIf { it >= 0f } ?: 0.5f
    params.screenBrightness = (current + deltaFraction).coerceIn(0.05f, 1f)
    window.attributes = params
}
