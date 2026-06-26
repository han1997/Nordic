package com.nordic.mediahub.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.nordic.mediahub.playback.VideoPlaybackState

@Composable
fun VideoPlayerScreen(
    state: VideoPlaybackState,
    player: Player,
    colorScheme: ColorScheme,
    onSeek: (Int) -> Unit,
    onPlayPause: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playbackInfo = state.playbackInfo
    val duration = maxOf(state.durationSeconds, playbackInfo?.durationSeconds ?: 0, 1)
    var scrubPosition by remember(playbackInfo?.itemId) { mutableStateOf<Float?>(null) }
    val visiblePosition = scrubPosition ?: state.positionSeconds.toFloat()
    val statusText = when {
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
                isError = state.errorMessage != null,
                colorScheme = colorScheme,
                onClose = onClose
            )

            VideoPlayerControls(
                title = playbackInfo?.title ?: "等待播放",
                overview = playbackInfo?.overview.orEmpty(),
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
                onPlayPause = onPlayPause
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
    isPlaying: Boolean,
    position: Float,
    duration: Int,
    colorScheme: ColorScheme,
    onPositionChange: (Float) -> Unit,
    onPositionChangeFinished: () -> Unit,
    onPlayPause: () -> Unit
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
            Text(
                formatDuration(duration),
                color = Color.White.copy(alpha = 0.64f),
                fontSize = 12.sp
            )
        }
    }
}
