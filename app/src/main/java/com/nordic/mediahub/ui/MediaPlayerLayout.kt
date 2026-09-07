package com.nordic.mediahub.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nordic.mediahub.ui.theme.NordicControlSizes
import com.nordic.mediahub.ui.theme.NordicSpacing

internal data class PlayerTimeline(val positionSeconds: Int, val sliderMaxSeconds: Int)

internal fun resolvePlayerTimeline(positionSeconds: Int, durationSeconds: Int): PlayerTimeline {
    val position = positionSeconds.coerceAtLeast(0)
    return PlayerTimeline(position, maxOf(position, durationSeconds, 1))
}

internal enum class MediaPlayerBodyMode { Portrait, ScrollablePortrait, SideBySide }
internal enum class MediaTransportMode { Full, Compact, Stacked }

internal fun resolveMediaPlayerBodyMode(width: Dp, height: Dp, fontScale: Float): MediaPlayerBodyMode = when {
    width >= 600.dp && width > height -> MediaPlayerBodyMode.SideBySide
    height < 480.dp || (fontScale > 1.3f && height < 620.dp) -> MediaPlayerBodyMode.ScrollablePortrait
    else -> MediaPlayerBodyMode.Portrait
}

internal fun resolveMediaTransportMode(width: Dp): MediaTransportMode = when {
    width >= NordicControlSizes.touchTarget * 4 + 72.dp + NordicSpacing.xs * 4 -> MediaTransportMode.Full
    width >= NordicControlSizes.touchTarget * 2 + 72.dp + NordicSpacing.xs * 2 -> MediaTransportMode.Compact
    else -> MediaTransportMode.Stacked
}
