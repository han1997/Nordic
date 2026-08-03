package com.nordic.mediahub.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nordic.mediahub.data.VideoItem
import com.nordic.mediahub.ui.theme.NordicAlpha
import com.nordic.mediahub.ui.theme.NordicShapes
import com.nordic.mediahub.ui.theme.NordicSpacing

@Composable
internal fun VideoDetailScreen(
    video: VideoItem,
    relatedEpisodes: List<VideoItem>,
    colorScheme: ColorScheme,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onPlayEpisode: (VideoItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(NordicSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(NordicSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            VideoDetailHero(
                video = video,
                colorScheme = colorScheme,
                onBack = onBack,
                onPlay = onPlay
            )
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)
            ) {
                Text(
                    "简介",
                    style = MaterialTheme.typography.titleMedium,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    video.overview.ifBlank { "暂无简介" },
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 21.sp,
                    color = colorScheme.onSurface.copy(alpha = NordicAlpha.medium)
                )
            }
        }

        if (relatedEpisodes.isNotEmpty()) {
            item {
                Text(
                    "分集",
                    style = MaterialTheme.typography.headlineMedium,
                    color = colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            items(
                items = relatedEpisodes,
                key = { episode -> "video-episode-${episode.id}" },
                contentType = { "video-episode-row" }
            ) { episode ->
                VideoEpisodeRow(
                    episode = episode,
                    colorScheme = colorScheme,
                    onClick = { onPlayEpisode(episode) }
                )
            }
        }
    }
}

@Composable
private fun VideoDetailHero(
    video: VideoItem,
    colorScheme: ColorScheme,
    onBack: () -> Unit,
    onPlay: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
    ) {
        if (!video.backdropImageUrl.isNullOrBlank()) {
            CoverArt(
                imageUrl = video.backdropImageUrl,
                contentDescription = video.title,
                colorScheme = colorScheme,
                modifier = Modifier.fillMaxSize(),
                shape = NordicShapes.md,
                fallbackText = video.title
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(NordicShapes.md)
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to colorScheme.primary.copy(alpha = 0.18f),
                                0.5f to colorScheme.secondary.copy(alpha = 0.10f),
                                1f to colorScheme.surfaceVariant.copy(alpha = 0.82f)
                            )
                        )
                    )
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(NordicShapes.md)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Black.copy(alpha = 0.40f),
                            0.5f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.70f)
                        )
                    )
                )
        )

        ScreenBackButton(
            colorScheme = colorScheme,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(NordicSpacing.md),
            onClick = onBack
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(NordicSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(NordicSpacing.md)
        ) {
            Text(
                video.title,
                style = MaterialTheme.typography.displaySmall,
                lineHeight = 32.sp,
                color = Color.White,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            val chips = remember(video) { video.detailChips() }
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NordicSpacing.sm)
            ) {
                items(chips, key = { it }, contentType = { "video-detail-chip" }) { chip ->
                    MetaChip(text = chip, colorScheme = colorScheme)
                }
            }

            PrimaryActionButton(
                text = "播放",
                colorScheme = colorScheme,
                enabled = !video.streamUrl.isNullOrBlank(),
                onClick = onPlay,
                icon = Icons.Filled.PlayArrow
            )
        }
    }
}

@Composable
internal fun VideoEpisodeRow(
    episode: VideoItem,
    colorScheme: ColorScheme,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = rememberPressScale(
        interactionSource = interactionSource,
        pressedScale = 0.985f,
        enabled = !episode.streamUrl.isNullOrBlank()
    )
    val progressFraction = remember(episode) {
        if (episode.durationSeconds > 0 && episode.playbackPositionSeconds > 0 && !episode.isPlayed) {
            (episode.playbackPositionSeconds.toFloat() / episode.durationSeconds).coerceIn(0f, 1f)
        } else {
            0f
        }
    }
    val showProgress = episode.playbackPositionSeconds > 0 &&
        !episode.isPlayed &&
        episode.durationSeconds > 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                enabled = !episode.streamUrl.isNullOrBlank(),
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        horizontalArrangement = Arrangement.spacedBy(NordicSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = colorScheme.surfaceVariant.copy(alpha = 0.42f),
            shape = NordicShapes.sm,
            border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.05f)),
            modifier = Modifier.width(116.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            ) {
                CoverArt(
                    imageUrl = episode.imageUrl,
                    contentDescription = episode.title,
                    colorScheme = colorScheme,
                    modifier = Modifier.fillMaxSize(),
                    shape = NordicShapes.md,
                    fallbackText = "VIDEO"
                )

                if (episode.isPlayed) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "已播放",
                        tint = colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(NordicSpacing.xs)
                            .size(18.dp)
                    )
                } else if (showProgress) {
                    LinearProgressIndicator(
                        progress = progressFraction,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(),
                        color = colorScheme.primary,
                        trackColor = Color.White.copy(alpha = 0.22f)
                    )
                }
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(NordicSpacing.xs)
        ) {
            val label = remember(episode) { episode.episodeLabel() }
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurface.copy(alpha = NordicAlpha.subtle),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                episode.title,
                style = MaterialTheme.typography.titleMedium,
                lineHeight = 19.sp,
                color = colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val meta = remember(episode) { episode.metaText() }
            if (meta.isNotBlank()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Normal,
                    color = colorScheme.onSurface.copy(alpha = NordicAlpha.subtle),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
