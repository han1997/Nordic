package com.nordic.mediahub.ui

import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.nordic.mediahub.playback.VideoPlaybackState
import kotlin.math.roundToInt

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
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val video = state.video
    val durationSeconds = state.durationSeconds.coerceAtLeast(video?.durationSeconds ?: 0)
    val safeDuration = durationSeconds.coerceAtLeast(1)
    val positionSeconds = state.positionSeconds.coerceIn(0, safeDuration)
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            VideoPlayerTopBar(
                title = video?.title ?: "视频播放",
                subtitle = when {
                    state.errorMessage != null -> state.errorMessage
                    state.isBuffering -> "正在缓冲"
                    video != null -> video.metaTextForPlayer()
                    else -> "没有正在播放的视频"
                },
                colorScheme = colorScheme,
                onClose = onClose
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { context ->
                        SurfaceView(context).also { view ->
                            attachedSurface = view
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (video == null) {
                    Text(
                        "选择一个视频开始播放",
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Surface(
                color = colorScheme.surface.copy(alpha = 0.94f),
                contentColor = colorScheme.onSurface,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Slider(
                        value = positionSeconds.toFloat(),
                        onValueChange = { value -> onSeek(value.roundToInt()) },
                        valueRange = 0f..safeDuration.toFloat(),
                        enabled = video != null
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${formatDuration(positionSeconds)} / ${formatDuration(durationSeconds)}",
                            fontSize = 12.sp,
                            color = colorScheme.onSurface.copy(alpha = 0.58f),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        VideoPlayerControlButton(
                            text = "-10",
                            colorScheme = colorScheme,
                            enabled = video != null,
                            onClick = onSeekBack
                        )
                        VideoPlayerControlButton(
                            text = if (state.isPlaying) "Ⅱ" else "▶",
                            colorScheme = colorScheme,
                            enabled = video != null,
                            onClick = onPlayPause
                        )
                        VideoPlayerControlButton(
                            text = "+30",
                            colorScheme = colorScheme,
                            enabled = video != null,
                            onClick = onSeekForward
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoPlayerTopBar(
    title: String,
    subtitle: String?,
    colorScheme: ColorScheme,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        VideoPlayerControlButton(
            text = "×",
            colorScheme = colorScheme,
            primary = false,
            onClick = onClose
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                title,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.62f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun VideoPlayerControlButton(
    text: String,
    colorScheme: ColorScheme,
    primary: Boolean = true,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = rememberPressScale(
        interactionSource = interactionSource,
        pressedScale = 0.94f,
        enabled = enabled
    )

    Surface(
        color = if (primary) {
            colorScheme.primary.copy(alpha = if (enabled) 1f else 0.32f)
        } else {
            Color.White.copy(alpha = 0.14f)
        },
        contentColor = if (primary) colorScheme.onPrimary else Color.White,
        shape = RoundedCornerShape(999.dp),
        modifier = Modifier
            .size(44.dp)
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
                fontSize = if (text.length > 2) 14.sp else 20.sp,
                fontWeight = FontWeight.Bold,
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
