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
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    playAction: VideoDetailPlayAction,
    colorScheme: ColorScheme,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onPlayFromStart: () -> Unit,
    onPlayEpisode: (VideoItem) -> Unit
) {
    var episodeFilter by remember { mutableStateOf(VideoEpisodeFilter.All) }
    val filteredEpisodes = remember(relatedEpisodes, episodeFilter) {
        when (episodeFilter) {
            VideoEpisodeFilter.All -> relatedEpisodes
            VideoEpisodeFilter.Unwatched -> relatedEpisodes.filter { !it.isPlayed }
        }
    }

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
                playAction = playAction,
                colorScheme = colorScheme,
                onBack = onBack,
                onPlay = onPlay,
                onPlayFromStart = onPlayFromStart
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
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(NordicSpacing.md)
                ) {
                    Text(
                        "分集",
                        style = MaterialTheme.typography.headlineMedium,
                        color = colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    VideoEpisodeFilterRow(
                        selectedFilter = episodeFilter,
                        colorScheme = colorScheme,
                        onSelect = { episodeFilter = it }
                    )
                }
            }

            if (filteredEpisodes.isEmpty()) {
                item {
                    Text(
                        "没有未看的分集",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurface.copy(alpha = NordicAlpha.subtle)
                    )
                }
            } else {
                items(
                    items = filteredEpisodes,
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
}

@Composable
private fun VideoEpisodeFilterRow(
    selectedFilter: VideoEpisodeFilter,
    colorScheme: ColorScheme,
    onSelect: (VideoEpisodeFilter) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(NordicSpacing.sm)
    ) {
        items(
            items = VideoEpisodeFilter.values().toList(),
            key = { it.name },
            contentType = { "video-episode-filter" }
        ) { filter ->
            val selected = filter == selectedFilter
            Surface(
                color = if (selected) colorScheme.primary.copy(alpha = 0.16f) else colorScheme.surfaceVariant.copy(alpha = 0.50f),
                contentColor = if (selected) colorScheme.primary else colorScheme.onSurface,
                shape = NordicShapes.full,
                border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.06f)),
                modifier = Modifier.clickable { onSelect(filter) }
            ) {
                Text(
                    text = filter.label,
                    modifier = Modifier.padding(horizontal = NordicSpacing.md, vertical = NordicSpacing.sm),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun VideoDetailHero(
    video: VideoItem,
    playAction: VideoDetailPlayAction,
    colorScheme: ColorScheme,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onPlayFromStart: () -> Unit
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

            val playEnabled = !video.streamUrl.isNullOrBlank()
            Column(verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)) {
                PrimaryActionButton(
                    text = playAction.primaryLabel,
                    colorScheme = colorScheme,
                    enabled = playEnabled,
                    onClick = onPlay,
                    icon = Icons.Filled.PlayArrow
                )
                if (playAction.secondaryLabel != null) {
                    SecondaryActionButton(
                        text = playAction.secondaryLabel,
                        colorScheme = colorScheme,
                        enabled = playEnabled,
                        onClick = onPlayFromStart
                    )
                }
            }
        }
    }
}

@Composable
private fun SecondaryActionButton(
    text: String,
    colorScheme: ColorScheme,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = rememberPressScale(
        interactionSource = interactionSource,
        pressedScale = 0.985f,
        enabled = enabled
    )
    Surface(
        color = if (enabled) colorScheme.primary.copy(alpha = 0.18f) else colorScheme.primary.copy(alpha = 0.10f),
        contentColor = colorScheme.primary,
        shape = NordicShapes.full,
        border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.22f)),
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .scale(scale)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text,
                style = MaterialTheme.typography.titleSmall,
                color = colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
