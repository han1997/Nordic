package com.nordic.mediahub.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nordic.mediahub.api.NavidromeSong


@Composable
private fun DockPlayPauseButton(
    isPlaying: Boolean,
    colorScheme: ColorScheme,
    onPlayPause: () -> Unit
) {
    Surface(
        color = colorScheme.primary,
        contentColor = colorScheme.onPrimary,
        shape = RoundedCornerShape(999.dp),
        shadowElevation = 2.dp,
        modifier = Modifier.clickable(onClick = onPlayPause)
    ) {
        Box(
            modifier = Modifier.size(38.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isPlaying) {
                Text("⏸", fontSize = 16.sp, color = colorScheme.onPrimary)
            } else {
                Text("▶", fontSize = 16.sp, color = colorScheme.onPrimary)
            }
        }
    }
}
@Composable
fun PolishedPlaybackDock(
    selected: Int,
    colorScheme: ColorScheme,
    currentSong: NavidromeSong?,
    isPlaying: Boolean,
    playbackStatus: String? = null,
    onOpenPlayer: () -> Unit,
    onPlayPause: () -> Unit,
    onSelect: (Int) -> Unit
) {
    Surface(
        color = colorScheme.surface.copy(alpha = 0.94f),
        contentColor = colorScheme.onSurface,
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 6.dp,
        shadowElevation = 12.dp,
        border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.08f)),
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
    ) {
        Column(
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            PolishedNowPlayingBar(
                song = currentSong,
                colorScheme = colorScheme,
                isPlaying = isPlaying,
                playbackStatus = playbackStatus,
                onOpenPlayer = onOpenPlayer,
                onPlayPause = onPlayPause
            )
            Box(
                Modifier
                    .padding(horizontal = 18.dp, vertical = 2.dp)
                    .height(1.dp)
                    .fillMaxWidth()
                    .background(colorScheme.onSurface.copy(alpha = 0.07f))
            )
            PolishedBottomNav(selected, colorScheme, onSelect)
        }
    }
}

@Composable
fun PolishedBottomNav(selected: Int, colorScheme: ColorScheme, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(58.dp)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        PolishedNavItem("♪", "音乐", selected == 0, colorScheme, Modifier.weight(1f)) { onSelect(0) }
        PolishedNavItem("▤", "有声书", selected == 1, colorScheme, Modifier.weight(1f)) { onSelect(1) }
        PolishedNavItem("▶", "视频", selected == 2, colorScheme, Modifier.weight(1f)) { onSelect(2) }
    }
}

@Composable
fun PolishedNavItem(
    icon: String,
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
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)
    )
    val itemColor by animateColorAsState(
        targetValue = if (selected) colorScheme.primary.copy(alpha = 0.13f) else Color.Transparent,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) colorScheme.primary else colorScheme.onSurface.copy(alpha = 0.58f),
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .scale(scale)
            .clip(RoundedCornerShape(18.dp))
            .background(color = itemColor)
            .clickable(
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
            Text(icon, fontSize = 19.sp, color = contentColor, fontWeight = FontWeight.SemiBold)
            Text(
                label,
                fontSize = 11.sp,
                color = contentColor,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
        }
    }
}

@Composable
fun PolishedNowPlayingBar(
    song: NavidromeSong?,
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
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(13.dp))
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
            if (song?.coverArt != null) {
                AsyncImage(
                    model = song.coverArt,
                    contentDescription = song.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            } else {
                Text("♪", fontSize = 22.sp, color = colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            if (song == null) {
                Text(
                    "播放队列",
                    fontSize = 15.sp,
                    color = colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    song.title,
                    fontSize = 15.sp,
                    color = colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (song == null) {
                Text(
                    "等待播放",
                    fontSize = 12.sp,
                    color = colorScheme.onSurface.copy(alpha = 0.56f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    playbackStatus ?: song.artist ?: song.album ?: "Unknown artist",
                    fontSize = 12.sp,
                    color = if (playbackStatus == null) {
                        colorScheme.onSurface.copy(alpha = 0.56f)
                    } else {
                        colorScheme.primary.copy(alpha = 0.78f)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        DockPlayPauseButton(
            isPlaying = isPlaying,
            colorScheme = colorScheme,
            onPlayPause = onPlayPause
        )
    }
}
