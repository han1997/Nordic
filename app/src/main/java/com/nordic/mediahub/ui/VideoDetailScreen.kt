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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nordic.mediahub.data.VideoItem
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
    // A Series has no stream of its own: the primary play button targets the
    // next unwatched episode (or the first one). Null keeps the button disabled.
    val playTarget = remember(video, relatedEpisodes) { resolveVideoDetailPlayTarget(video, relatedEpisodes) }
    val currentEpisode = remember(video, relatedEpisodes) {
        resolveVideoDetailCurrentEpisode(video, relatedEpisodes)
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
                playEnabled = playTarget != null,
                playTargetTitle = playTarget?.takeIf { it.id != video.id }?.title,
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
                    modifier = Modifier.semantics { heading() },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                MusicCollectionDescription(video.id, video.overview.ifBlank { "暂无简介" }, colorScheme)
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
                        modifier = Modifier.semantics { heading() },
                        fontWeight = FontWeight.SemiBold,
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
                    MediaStateCard(
                        title = "没有未看的分集",
                        subtitle = "本季已没有尚未播放的分集。",
                        density = MediaStateDensity.Compact
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
                        isCurrent = episode.id == currentEpisode?.id,
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
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(NordicSpacing.sm)
    ) {
        items(
            items = VideoEpisodeFilter.values().toList(),
            key = { it.name },
            contentType = { "video-episode-filter" }
        ) { filter ->
            MediaChoiceChip(
                text = filter.label,
                selected = filter == selectedFilter,
                colorScheme = colorScheme,
                onClick = { onSelect(filter) }
            )
        }
    }
}

@Composable
private fun VideoDetailHero(
    video: VideoItem,
    playAction: VideoDetailPlayAction,
    playEnabled: Boolean,
    playTargetTitle: String?,
    colorScheme: ColorScheme,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onPlayFromStart: () -> Unit
) {
    Column {
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

            Text(
                video.title,
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(NordicSpacing.lg)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = NordicSpacing.md),
            verticalArrangement = Arrangement.spacedBy(NordicSpacing.md)
        ) {
            val chips = remember(video) { video.detailChips() }
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NordicSpacing.sm)
            ) {
                items(chips, key = { it }, contentType = { "video-detail-chip" }) { chip ->
                    MetaChip(text = chip, colorScheme = colorScheme)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)) {
                PrimaryActionButton(
                    text = if (playTargetTitle != null) "播放 $playTargetTitle" else playAction.primaryLabel,
                    colorScheme = colorScheme,
                    enabled = playEnabled,
                    onClick = onPlay,
                    icon = Icons.Filled.PlayArrow
                )
                if (playAction.secondaryLabel != null && !video.streamUrl.isNullOrBlank()) {
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
internal fun VideoEpisodeRow(
    episode: VideoItem,
    colorScheme: ColorScheme,
    onClick: () -> Unit,
    isCurrent: Boolean = false,
    compact: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
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
    val playable = !episode.streamUrl.isNullOrBlank()
    val labelColor = if (isCurrent) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant
    val titleColor = if (isCurrent) colorScheme.onPrimaryContainer else colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (compact) Modifier else Modifier.heightIn(min = 72.dp))
            .clip(NordicShapes.sm)
            .background(if (isCurrent) colorScheme.primaryContainer else Color.Transparent)
            .semantics { selected = isCurrent }
            .pressScale(
                interactionSource,
                pressedScale = 0.985f,
                enabled = playable
            )
            .clickable(
                enabled = playable,
                role = Role.Button,
                onClickLabel = if (isCurrent) "正在播放" else "播放分集",
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .then(if (compact) Modifier.padding(NordicSpacing.sm) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(NordicSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = colorScheme.surfaceVariant.copy(alpha = 0.42f),
            shape = NordicShapes.sm,
            border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.045f)),
            modifier = Modifier.width(if (compact) 88.dp else 116.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            ) {
                Box(Modifier.clearAndSetSemantics { }) {
                    CoverArt(
                        imageUrl = episode.imageUrl,
                        contentDescription = episode.title,
                        colorScheme = colorScheme,
                        modifier = Modifier.fillMaxSize(),
                        shape = NordicShapes.md,
                        fallbackText = "VIDEO"
                    )
                }

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
                        progress = { progressFraction },
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
                if (isCurrent) "正在播放 · $label" else label,
                style = MaterialTheme.typography.bodySmall,
                color = labelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                episode.title,
                style = MaterialTheme.typography.titleMedium,
                lineHeight = 19.sp,
                color = titleColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val meta = remember(episode) {
                if (episode.streamUrl.isNullOrBlank()) "暂不可播放" else episode.metaText()
            }
            if (meta.isNotBlank()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Normal,
                    color = labelColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
