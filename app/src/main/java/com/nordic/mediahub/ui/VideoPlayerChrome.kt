package com.nordic.mediahub.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nordic.mediahub.playback.VideoPlaybackState
import com.nordic.mediahub.playback.resolvePlaybackSpeedLabel
import com.nordic.mediahub.ui.theme.NordicShapes
import com.nordic.mediahub.ui.theme.NordicSpacing
import kotlin.math.roundToInt

@Composable
internal fun VideoPlayerChrome(
    state: VideoPlaybackState,
    colorScheme: ColorScheme,
    subtitle: String?,
    durationSeconds: Int,
    scrubPosition: Float?,
    isFullscreen: Boolean,
    hasEpisodes: Boolean,
    hasNextEpisode: Boolean,
    hasPlaybackStatus: Boolean,
    gesturesLocked: Boolean,
    onClose: () -> Unit,
    onToggleLock: () -> Unit,
    onPanel: (VideoPlayerPanel) -> Unit,
    onPlayPause: () -> Unit,
    onSeekRelative: (Int) -> Unit,
    onCycleAspectRatio: () -> Unit,
    onPlayNextEpisode: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onScrubChange: (Float) -> Unit,
    onScrubFinished: () -> Unit,
    onScrubCanceled: () -> Unit
) {
    val hasVideo = state.video != null
    BoxWithConstraints(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = NordicSpacing.lg, vertical = NordicSpacing.sm)
    ) {
        val layout = resolveVideoPlayerToolLayout(
            maxWidth, maxHeight, hasEpisodes, hasNextEpisode, LocalDensity.current.fontScale,
            hasPlaybackStatus
        )
        val sideLock = maxWidth >= 600.dp
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NordicSpacing.xs)
            ) {
                VideoPlayerChromeButton(Icons.Filled.Close, description = "关闭播放器", onClick = onClose)
                Column(Modifier.weight(1f)) {
                    Text(state.video?.title ?: "视频播放器", style = MaterialTheme.typography.titleMedium,
                        color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.68f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (!sideLock) VideoPlayerGestureLockButton(gesturesLocked, hasVideo, onToggleLock)
                VideoPlayerChromeButton(Icons.Filled.MoreHoriz, description = "更多播放设置",
                    enabled = hasVideo, onClick = { onPanel(VideoPlayerPanel.Settings) })
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (sideLock) Box(Modifier.align(Alignment.CenterStart)) {
                    VideoPlayerGestureLockButton(gesturesLocked, hasVideo, onToggleLock)
                }
                if (layout.showCenterTransport) {
                    Row(
                        Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(NordicSpacing.lg),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        VideoPlayerChromeButton(Icons.Filled.Replay10, description = "后退 10 秒",
                            enabled = hasVideo,
                            onClick = { onSeekRelative(-VIDEO_GESTURE_SKIP_BACK_SECONDS) })
                        VideoPlayerChromeButton(
                            if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            description = if (state.isPlaying) "暂停" else "播放",
                            primary = true, enabled = hasVideo, size = 72.dp, onClick = onPlayPause
                        )
                        VideoPlayerChromeButton(Icons.Filled.Forward30, description = "前进 30 秒",
                            enabled = hasVideo,
                            onClick = { onSeekRelative(VIDEO_GESTURE_SKIP_FORWARD_SECONDS) })
                    }
                }
            }
            val timeline = resolveVideoPlayerTimeline(state.positionSeconds, durationSeconds)
            val visiblePosition = (scrubPosition ?: timeline.positionSeconds.toFloat())
                .coerceIn(0f, timeline.sliderMaxSeconds.toFloat())
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${formatDuration(visiblePosition.roundToInt())} / ${formatVideoPlayerDurationLabel(durationSeconds)}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (scrubPosition != null) "松开跳转" else formatVideoPlayerRemainingLabel(durationSeconds, visiblePosition.roundToInt()),
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = Color.White.copy(alpha = 0.68f), maxLines = 1,
                    modifier = Modifier.padding(start = NordicSpacing.md)
                )
            }
            PlayerThinSlider(
                position = visiblePosition, duration = timeline.sliderMaxSeconds,
                colorScheme = colorScheme, enabled = hasVideo,
                activeColor = colorScheme.primary, inactiveColor = Color.White.copy(alpha = 0.22f),
                thumbColor = Color.White, bufferedPosition = state.bufferedPositionSeconds.toFloat(),
                bufferColor = Color.White.copy(alpha = 0.3f),
                onPositionChange = onScrubChange, onPositionChangeFinished = onScrubFinished,
                onPositionChangeCanceled = onScrubCanceled,
                modifier = Modifier.heightIn(min = 48.dp)
            )
            Row(
                Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!layout.showCenterTransport) VideoPlayerChromeButton(
                    if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    description = if (state.isPlaying) "暂停" else "播放",
                    enabled = hasVideo, onClick = onPlayPause
                )
                VideoPlayerChromeButton(text = resolvePlaybackSpeedLabel(state.playbackSpeed),
                    description = "播放速度 ${resolvePlaybackSpeedLabel(state.playbackSpeed)}",
                    enabled = hasVideo, width = layout.speedButtonWidth,
                    onClick = { onPanel(VideoPlayerPanel.Speed) })
                if (layout.showInlineAspectRatio) VideoPlayerChromeButton(Icons.Filled.AspectRatio,
                    description = "画面比例：${videoPlayerAspectRatioLabel(state.aspectRatioMode)}",
                    enabled = hasVideo, onClick = onCycleAspectRatio)
                if (hasEpisodes) VideoPlayerChromeButton(Icons.AutoMirrored.Filled.PlaylistPlay, description = "选集",
                    onClick = { onPanel(VideoPlayerPanel.Episodes) })
                if (layout.showInlineNextEpisode) VideoPlayerChromeButton(Icons.Filled.SkipNext,
                    description = "播放下一集", onClick = onPlayNextEpisode)
                VideoPlayerChromeButton(
                    if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                    description = if (isFullscreen) "退出全屏" else "进入全屏",
                    enabled = hasVideo, onClick = onToggleFullscreen
                )
            }
        }
    }
}

@Composable
private fun VideoPlayerGestureLockButton(locked: Boolean, enabled: Boolean, onClick: () -> Unit) {
    VideoPlayerChromeButton(
        if (locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
        description = if (locked) "解除手势锁" else "锁定播放手势",
        enabled = enabled, onClick = onClick
    )
}

@Composable
internal fun VideoPlayerChromeButton(
    icon: ImageVector? = null,
    text: String? = null,
    description: String,
    primary: Boolean = false,
    enabled: Boolean = true,
    size: Dp = 48.dp,
    width: Dp = size,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val contentColor = if (primary) Color.Black else Color.White
    Surface(
        color = if (primary) Color.White.copy(alpha = if (enabled) 0.92f else 0.3f) else Color.Transparent,
        shape = NordicShapes.full,
        modifier = Modifier.width(width).heightIn(min = size)
            .pressScale(interactionSource, pressedScale = 0.94f, enabled = enabled)
            .semantics { contentDescription = description }
            .clickable(enabled = enabled, role = Role.Button,
                interactionSource = interactionSource, indication = null, onClick = onClick)
    ) {
        Box(Modifier.size(width, size), contentAlignment = Alignment.Center) {
            if (icon != null) Icon(icon, null,
                tint = contentColor.copy(alpha = if (enabled) 1f else 0.38f),
                modifier = Modifier.size(if (primary) 36.dp else 24.dp))
            else if (text != null) Text(text, color = contentColor.copy(alpha = if (enabled) 1f else 0.38f),
                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
