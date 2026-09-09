package com.nordic.mediahub.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nordic.mediahub.playback.AspectRatioMode
import com.nordic.mediahub.ui.theme.NordicSpacing

internal enum class VideoPlayerPanel(val title: String) {
    Settings("播放设置"), Speed("播放速度"), Info("影片信息"), Episodes("选集"), Tracks("字幕与音轨"),
    Chapters("章节")
}

internal data class VideoPlayerToolLayout(
    val showCenterTransport: Boolean,
    val showInlineAspectRatio: Boolean,
    val showInlineNextEpisode: Boolean,
    val speedButtonWidth: Dp,
    val occupiedWidth: Dp
)

/** Reserve real touch targets first; move optional actions into Settings instead of shrinking them. */
internal fun resolveVideoPlayerToolLayout(
    availableWidth: Dp,
    availableHeight: Dp,
    hasEpisodes: Boolean,
    hasNextEpisode: Boolean,
    fontScale: Float = 1f,
    hasPlaybackStatus: Boolean = false
): VideoPlayerToolLayout {
    val showCenterTransport = availableHeight >= 280.dp && !hasPlaybackStatus
    val speedWidth = (56.dp * fontScale.coerceIn(1f, 2f)).coerceAtMost(96.dp)
    val mandatoryIcons = 1 + (if (hasEpisodes) 1 else 0) + (if (showCenterTransport) 0 else 1)
    var occupiedWidth = speedWidth + (48.dp + NordicSpacing.xs) * mandatoryIcons
    val optionalWidth = 48.dp + NordicSpacing.xs
    val showNext = hasNextEpisode && availableWidth >= occupiedWidth + optionalWidth
    if (showNext) occupiedWidth += optionalWidth
    val showAspect = availableWidth >= occupiedWidth + optionalWidth
    if (showAspect) occupiedWidth += optionalWidth
    return VideoPlayerToolLayout(showCenterTransport, showAspect, showNext, speedWidth, occupiedWidth)
}

internal fun useVideoPlayerSidePanel(isFullscreen: Boolean, width: Dp, height: Dp): Boolean =
    isFullscreen && width >= 600.dp && width > height

internal fun videoPlayerAspectRatioLabel(mode: AspectRatioMode): String = when (mode) {
    AspectRatioMode.FIT -> "适应画面"
    AspectRatioMode.CROP -> "裁剪填满"
    AspectRatioMode.FILL -> "拉伸填满"
}

internal fun shouldShowVideoNextEpisodePrompt(
    hasNextEpisode: Boolean,
    durationSeconds: Int,
    positionSeconds: Int,
    controlsVisible: Boolean,
    panelOpen: Boolean,
    gesturesLocked: Boolean,
    hasPlaybackStatus: Boolean,
    dismissed: Boolean
): Boolean = hasNextEpisode && durationSeconds > 0 &&
    positionSeconds >= (durationSeconds - VIDEO_NEXT_EPISODE_OVERLAY_LEAD_SECONDS).coerceAtLeast(0) &&
    !controlsVisible && !panelOpen && !gesturesLocked && !hasPlaybackStatus && !dismissed

/**
 * Manual "跳过片头" button visibility: inside the intro range with no blocking
 * UI state. Shown regardless of chrome visibility so it is reachable while the
 * controls are hidden.
 */
internal fun shouldShowVideoSkipIntroButton(
    introRange: com.nordic.mediahub.data.VideoIntroRange?,
    positionSeconds: Int,
    panelOpen: Boolean,
    gesturesLocked: Boolean,
    hasPlaybackStatus: Boolean
): Boolean = introRange != null && !panelOpen && !gesturesLocked && !hasPlaybackStatus &&
    positionSeconds >= introRange.startSeconds && positionSeconds < introRange.endSeconds
