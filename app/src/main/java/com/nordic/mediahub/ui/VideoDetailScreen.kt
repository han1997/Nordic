package com.nordic.mediahub.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nordic.mediahub.data.VideoItem

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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScreenBackButton(
                    colorScheme = colorScheme,
                    onClick = onBack
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        "视频详情",
                        fontSize = 13.sp,
                        color = colorScheme.onSurface.copy(alpha = 0.58f),
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        video.title,
                        fontSize = 18.sp,
                        color = colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        item {
            Surface(
                color = colorScheme.surfaceVariant.copy(alpha = 0.42f),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.05f)),
                shadowElevation = 10.dp,
                modifier = Modifier.fillMaxWidth(0.72f)
            ) {
                CoverArt(
                    imageUrl = video.imageUrl,
                    contentDescription = video.title,
                    colorScheme = colorScheme,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f),
                    shape = RoundedCornerShape(18.dp),
                    fallbackText = "VIDEO"
                )
            }
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    video.title,
                    fontSize = 28.sp,
                    lineHeight = 32.sp,
                    color = colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                val chips = remember(video) { video.detailChips() }
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(chips, key = { it }, contentType = { "video-detail-chip" }) { chip ->
                        MetaChip(text = chip, colorScheme = colorScheme)
                    }
                }

                PrimaryActionButton(
                    text = "▶  播放",
                    colorScheme = colorScheme,
                    enabled = !video.streamUrl.isNullOrBlank(),
                    onClick = onPlay
                )

                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(
                        "简介",
                        fontSize = 17.sp,
                        color = colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        video.overview.ifBlank { "暂无简介" },
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        color = colorScheme.onSurface.copy(alpha = 0.68f)
                    )
                }
            }
        }

        if (relatedEpisodes.isNotEmpty()) {
            item {
                Text(
                    "分集",
                    fontSize = 20.sp,
                    color = colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
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
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = colorScheme.surfaceVariant.copy(alpha = 0.42f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.05f)),
            modifier = Modifier.width(116.dp)
        ) {
            CoverArt(
                imageUrl = episode.imageUrl,
                contentDescription = episode.title,
                colorScheme = colorScheme,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                shape = RoundedCornerShape(18.dp),
                fallbackText = "VIDEO"
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val label = remember(episode) { episode.episodeLabel() }
            Text(
                label,
                fontSize = 12.sp,
                color = colorScheme.onSurface.copy(alpha = 0.58f),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                episode.title,
                fontSize = 15.sp,
                lineHeight = 19.sp,
                color = colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val meta = remember(episode) { episode.metaText() }
            if (meta.isNotBlank()) {
                Text(
                    meta,
                    fontSize = 12.sp,
                    color = colorScheme.onSurface.copy(alpha = 0.56f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
