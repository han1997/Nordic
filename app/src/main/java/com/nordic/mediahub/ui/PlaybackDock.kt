package com.nordic.mediahub.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nordic.mediahub.data.NavidromeSong
import com.nordic.mediahub.ui.theme.NordicAlpha
import com.nordic.mediahub.ui.theme.NordicControlSizes
import com.nordic.mediahub.ui.theme.NordicMotion
import com.nordic.mediahub.ui.theme.NordicShapes
import com.nordic.mediahub.ui.theme.NordicSpacing

private const val DOCK_SURFACE_ALPHA = 0.94f
private const val DOCK_HANDLE_SURFACE_ALPHA = 0.92f
private const val DOCK_BORDER_ALPHA = 0.08f
private const val DOCK_DIVIDER_ALPHA = 0.07f
private const val DOCK_SELECTED_CONTAINER_ALPHA = 0.13f

/** Default scroll distance (in dp) required to hide or re-show the dock. */
internal val BottomDockScrollThreshold = 24.dp

/** What the dock should do after a scroll gesture segment. */
internal enum class BottomDockScrollIntent { None, Hide, Show }

/**
 * Pure gesture-to-intent mapping for the bottom dock.
 *
 * Sign convention (Compose nested scroll): positive `accumulatedDeltaPx`
 * means the finger moved down (scrolling back toward the top of the content),
 * negative means the finger moved up (scrolling deeper into the content).
 *
 * - Dock visible + enough downward-into-content scroll → [BottomDockScrollIntent.Hide]
 * - Dock hidden (handle) + enough scroll-back-up → [BottomDockScrollIntent.Show]
 * - Everything else stays [BottomDockScrollIntent.None]; callers reset the
 *   accumulator whenever the direction reverses or an intent fires.
 */
internal fun resolveBottomDockScrollIntent(
    accumulatedDeltaPx: Float,
    thresholdPx: Float,
    dockVisible: Boolean
): BottomDockScrollIntent {
    if (thresholdPx <= 0f) return BottomDockScrollIntent.None
    return when {
        dockVisible && accumulatedDeltaPx <= -thresholdPx -> BottomDockScrollIntent.Hide
        !dockVisible && accumulatedDeltaPx >= thresholdPx -> BottomDockScrollIntent.Show
        else -> BottomDockScrollIntent.None
    }
}

@Composable
private fun DockPlayPauseButton(
    isPlaying: Boolean,
    colorScheme: ColorScheme,
    onPlayPause: () -> Unit
) {
    Surface(
        color = colorScheme.primary,
        contentColor = colorScheme.onPrimary,
        shape = NordicShapes.full,
        shadowElevation = 2.dp,
        modifier = Modifier.clickable(onClick = onPlayPause)
    ) {
        Box(
            modifier = Modifier.size(NordicControlSizes.touchTarget),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "暂停" else "播放",
                tint = colorScheme.onPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * Generalized now-playing content shown in the bottom dock. A single active
 * medium renders in the now-playing bar; music, audiobook, and video share the
 * slot so the dock survives closing the full-screen player.
 */
internal sealed interface DockNowPlayingContent {
    data class Music(val song: NavidromeSong) : DockNowPlayingContent
    data class Audiobook(
        val title: String,
        val author: String?,
        val coverUrl: String?
    ) : DockNowPlayingContent
    data class Video(val title: String) : DockNowPlayingContent
}

@Composable
internal fun PolishedPlaybackDock(
    selected: Int,
    colorScheme: ColorScheme,
    nowPlaying: DockNowPlayingContent?,
    isPlaying: Boolean,
    playbackStatus: String? = null,
    onOpenPlayer: () -> Unit,
    onPlayPause: () -> Unit,
    onSelect: (Int) -> Unit
) {
    Surface(
        color = colorScheme.surface.copy(alpha = DOCK_SURFACE_ALPHA),
        contentColor = colorScheme.onSurface,
        shape = NordicShapes.xl,
        tonalElevation = 8.dp,
        shadowElevation = 14.dp,
        border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = DOCK_BORDER_ALPHA)),
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = NordicSpacing.md, end = NordicSpacing.md, bottom = NordicSpacing.md)
    ) {
        Box(
            modifier = Modifier.background(
                Brush.verticalGradient(
                    listOf(
                        colorScheme.onSurface.copy(alpha = 0.075f),
                        colorScheme.surface.copy(alpha = 0.0f),
                        colorScheme.primary.copy(alpha = 0.055f)
                    )
                )
            )
        ) {
            Column(
                modifier = Modifier.padding(top = NordicSpacing.sm, bottom = NordicSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                PolishedNowPlayingBar(
                    nowPlaying = nowPlaying,
                    colorScheme = colorScheme,
                    isPlaying = isPlaying,
                    playbackStatus = playbackStatus,
                    onOpenPlayer = onOpenPlayer,
                    onPlayPause = onPlayPause
                )
                Box(
                    Modifier
                        .padding(horizontal = NordicSpacing.lg, vertical = 2.dp)
                        .height(1.dp)
                        .fillMaxWidth()
                        .background(colorScheme.onSurface.copy(alpha = DOCK_DIVIDER_ALPHA))
                )
                PolishedBottomNav(selected, colorScheme, onSelect)
            }
        }
    }
}
@Composable
internal fun BottomDockHandle(
    colorScheme: ColorScheme,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = NordicSpacing.sm),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = colorScheme.surface.copy(alpha = DOCK_HANDLE_SURFACE_ALPHA),
            contentColor = colorScheme.onSurface,
            shape = NordicShapes.full,
            tonalElevation = 5.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = DOCK_BORDER_ALPHA)),
            modifier = Modifier
                .clip(NordicShapes.full)
                .semantics { contentDescription = "显示底部导航" }
                .clickable(onClick = onClick)
        ) {
            // Visual pill stays compact; the clickable Surface is padded to the
            // 48dp touch-target standard so the small handle is easy to hit.
            Box(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                colorScheme.onSurface.copy(alpha = 0.065f),
                                colorScheme.primary.copy(alpha = 0.035f)
                            )
                        )
                    )
                    .width(64.dp)
                    .heightIn(min = NordicControlSizes.touchTarget)
                    .padding(horizontal = NordicSpacing.lg, vertical = NordicSpacing.xs),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(NordicShapes.full)
                        .background(colorScheme.onSurface.copy(alpha = NordicAlpha.faint))
                )
            }
        }
    }
}

@Composable
internal fun PolishedBottomNav(selected: Int, colorScheme: ColorScheme, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(58.dp)
            .padding(horizontal = NordicSpacing.sm)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(NordicSpacing.xs)
    ) {
        PolishedNavItem(Icons.Filled.LibraryMusic, "音乐", selected == 0, colorScheme, Modifier.weight(1f)) { onSelect(0) }
        PolishedNavItem(Icons.AutoMirrored.Filled.MenuBook, "有声书", selected == 1, colorScheme, Modifier.weight(1f)) { onSelect(1) }
        PolishedNavItem(Icons.Filled.Movie, "视频", selected == 2, colorScheme, Modifier.weight(1f)) { onSelect(2) }
        PolishedNavItem(Icons.Filled.Settings, "配置", selected == 3, colorScheme, Modifier.weight(1f)) { onSelect(3) }
    }
}
@Composable
internal fun PolishedNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = NordicMotion.durationMicro, easing = NordicMotion.easingStandard),
        label = "dock-item-press-scale"
    )
    val itemColor by animateColorAsState(
        targetValue = if (selected) colorScheme.primary.copy(alpha = DOCK_SELECTED_CONTAINER_ALPHA) else Color.Transparent,
        animationSpec = tween(durationMillis = NordicMotion.durationMicro, easing = NordicMotion.easingStandard)
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) colorScheme.primary else colorScheme.onSurface.copy(alpha = NordicAlpha.subtle),
        animationSpec = tween(durationMillis = NordicMotion.durationMicro, easing = NordicMotion.easingStandard)
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(NordicShapes.md)
            .background(color = itemColor)
            .selectable(
                selected = selected,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(21.dp)
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
        }
    }
}
@Composable
internal fun PolishedNowPlayingBar(
    nowPlaying: DockNowPlayingContent?,
    colorScheme: ColorScheme,
    isPlaying: Boolean,
    playbackStatus: String? = null,
    onOpenPlayer: () -> Unit,
    onPlayPause: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(66.dp)
            .clickable(onClick = onOpenPlayer)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val placeholderIcon: ImageVector = when (nowPlaying) {
            is DockNowPlayingContent.Audiobook -> Icons.AutoMirrored.Filled.MenuBook
            is DockNowPlayingContent.Video -> Icons.Filled.Movie
            else -> Icons.Filled.MusicNote
        }
        val coverUrl = when (nowPlaying) {
            is DockNowPlayingContent.Music -> nowPlaying.song.coverArt
            is DockNowPlayingContent.Audiobook -> nowPlaying.coverUrl
            else -> null
        }
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(NordicShapes.sm)
                .background(
                    Brush.linearGradient(
                        listOf(
                            colorScheme.primary.copy(alpha = 0.28f),
                            colorScheme.secondary.copy(alpha = 0.18f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            if (coverUrl != null) {
                AuthedAsyncImage(
                    url = coverUrl,
                    contentDescription = when (nowPlaying) {
                        is DockNowPlayingContent.Music -> nowPlaying.song.title
                        is DockNowPlayingContent.Audiobook -> nowPlaying.title
                        else -> null
                    },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            } else {
                Icon(
                    imageVector = placeholderIcon,
                    contentDescription = null,
                    tint = colorScheme.primary.copy(alpha = 0.72f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(Modifier.width(NordicSpacing.md))
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(NordicSpacing.xs)
        ) {
            val title = when (nowPlaying) {
                is DockNowPlayingContent.Music -> nowPlaying.song.title
                is DockNowPlayingContent.Audiobook -> nowPlaying.title
                is DockNowPlayingContent.Video -> nowPlaying.title
                null -> "播放队列"
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = when (nowPlaying) {
                is DockNowPlayingContent.Music ->
                    playbackStatus ?: nowPlaying.song.artist?.takeIf { it.isNotBlank() } ?: nowPlaying.song.album?.takeIf { it.isNotBlank() } ?: musicArtistLabel(null)
                is DockNowPlayingContent.Audiobook ->
                    playbackStatus ?: nowPlaying.author?.takeIf { it.isNotBlank() } ?: "有声书"
                is DockNowPlayingContent.Video ->
                    playbackStatus ?: "视频"
                null -> "等待播放"
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Normal,
                color = if (playbackStatus == null) {
                    colorScheme.onSurface.copy(alpha = NordicAlpha.subtle)
                } else {
                    colorScheme.primary.copy(alpha = 0.78f)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        DockPlayPauseButton(
            isPlaying = isPlaying,
            colorScheme = colorScheme,
            onPlayPause = onPlayPause
        )
    }
}
