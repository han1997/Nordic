package com.nordic.mediahub.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nordic.mediahub.ui.theme.NordicSpacing

internal data class MusicCollectionLayout(val stacked: Boolean, val artworkSize: Dp)

internal fun resolveMusicCollectionLayout(availableWidth: Dp, fontScale: Float): MusicCollectionLayout {
    val width = availableWidth.coerceAtLeast(0.dp)
    val scale = fontScale.takeIf { it.isFinite() && it > 0f }?.coerceIn(1f, 2f) ?: 1f
    val artwork = (if (width >= 600.dp) 160.dp else 128.dp).coerceAtMost(width)
    return MusicCollectionLayout(width < artwork + NordicSpacing.xxl + 200.dp * scale, artwork)
}

internal fun shouldInlineMusicRowTrailing(availableWidth: Dp, trailingWidth: Dp, fontScale: Float): Boolean {
    val scale = fontScale.takeIf { it.isFinite() && it > 0f }?.coerceIn(1f, 2f) ?: 1f
    return availableWidth >= 52.dp + NordicSpacing.md * 2 + 128.dp * scale + trailingWidth.coerceAtLeast(0.dp)
}

internal fun resolveMusicCollectionCount(reported: Int, loaded: Int, isLoading: Boolean, hasVisibleError: Boolean): Int =
    if (isLoading || (hasVisibleError && loaded <= 0)) reported.coerceAtLeast(0) else loaded.coerceAtLeast(0)

internal fun shouldShowMusicCollectionEmpty(isLoading: Boolean, itemCount: Int, hasVisibleError: Boolean): Boolean =
    !isLoading && itemCount <= 0 && !hasVisibleError

internal fun musicArtistLabel(artist: String?): String = artist?.trim()?.takeIf { it.isNotEmpty() } ?: "未知歌手"
internal fun musicSongCountLabel(count: Int): String = "${count.coerceAtLeast(0)} 首歌曲"
internal fun musicAlbumCountLabel(count: Int): String = "${count.coerceAtLeast(0)} 张专辑"
internal fun musicTrackDurationLabel(seconds: Int): String = formatKnownDuration(seconds)

internal fun musicShelfArtworkSize(fontScale: Float): Dp {
    val scale = fontScale.takeIf { it.isFinite() && it > 0f }?.coerceAtLeast(1f) ?: 1f
    return (124.dp * scale).coerceAtMost(160.dp)
}
