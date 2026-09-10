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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nordic.mediahub.data.VideoItem
import com.nordic.mediahub.data.VideoLibrary
import com.nordic.mediahub.ui.theme.NordicAlpha
import com.nordic.mediahub.ui.theme.NordicShapes
import com.nordic.mediahub.ui.theme.NordicSpacing

@Composable
internal fun VideoLibrarySelector(
    libraries: List<VideoLibrary>,
    selectedLibraryId: String?,
    colorScheme: ColorScheme,
    onSelect: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(NordicSpacing.md)
    ) {
        items(libraries, key = { it.id }, contentType = { "video-library-chip" }) { library ->
            MediaChoiceChip(
                text = library.name,
                selected = library.id == selectedLibraryId,
                colorScheme = colorScheme,
                onClick = { onSelect(library.id) }
            )
        }
    }
}

@Composable
internal fun VideoCard(
    video: VideoItem,
    colorScheme: ColorScheme,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)
    ) {
        Surface(
            color = colorScheme.surfaceVariant.copy(alpha = 0.42f),
            shape = NordicShapes.md,
            border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.045f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            CoverArt(
                imageUrl = video.imageUrl,
                contentDescription = video.title,
                colorScheme = colorScheme,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f),
                shape = NordicShapes.md,
                fallbackText = "VIDEO"
            )
        }

        Column {
            Text(
                video.title,
                style = MaterialTheme.typography.titleSmall,
                lineHeight = 18.sp,
                color = colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val meta = remember(video) { video.metaText() }
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

@Composable
internal fun VideoSpotlightSections(
    continueWatching: List<VideoItem>,
    topRated: List<VideoItem>,
    unplayed: List<VideoItem>,
    colorScheme: ColorScheme,
    onVideoSelected: (VideoItem) -> Unit
) {
    if (continueWatching.isEmpty() && topRated.isEmpty() && unplayed.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = NordicSpacing.sm),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "暂无推荐",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Normal,
                color = colorScheme.onSurface.copy(alpha = NordicAlpha.subtle)
            )
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(NordicSpacing.lg)
    ) {
        VideoSpotlightRow(
            title = "继续观看",
            videos = continueWatching,
            keyPrefix = "continue",
            colorScheme = colorScheme,
            onVideoSelected = onVideoSelected
        )
        VideoSpotlightRow(
            title = "最受好评",
            videos = topRated,
            keyPrefix = "rated",
            colorScheme = colorScheme,
            onVideoSelected = onVideoSelected
        )
        VideoSpotlightRow(
            title = "未播放的",
            videos = unplayed,
            keyPrefix = "unplayed",
            colorScheme = colorScheme,
            onVideoSelected = onVideoSelected
        )
    }
}

@Composable
internal fun VideoSpotlightRow(
    title: String,
    videos: List<VideoItem>,
    keyPrefix: String,
    colorScheme: ColorScheme,
    onVideoSelected: (VideoItem) -> Unit
) {
    if (videos.isEmpty()) return
    val isContinueWatching = keyPrefix == "continue"

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(NordicSpacing.md)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            color = colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(NordicSpacing.md)
        ) {
            items(
                items = videos,
                key = { video -> "$keyPrefix-${video.libraryId}:${video.id}" },
                contentType = { if (isContinueWatching) "video-continue-card" else "video-spotlight-card" }
            ) { video ->
                if (isContinueWatching) {
                    ContinueWatchingCard(
                        video = video,
                        colorScheme = colorScheme,
                        onClick = { onVideoSelected(video) }
                    )
                } else {
                    Box(modifier = Modifier.width(videoShelfCardSize(LocalDensity.current.fontScale))) {
                        VideoCard(
                            video = video,
                            colorScheme = colorScheme,
                            onClick = { onVideoSelected(video) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ContinueWatchingCard(
    video: VideoItem,
    colorScheme: ColorScheme,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val progressFraction = remember(video) {
        if (video.durationSeconds > 0) {
            (video.playbackPositionSeconds.toFloat() / video.durationSeconds).coerceIn(0f, 1f)
        } else {
            0f
        }
    }

    Surface(
        color = colorScheme.surfaceVariant.copy(alpha = 0.42f),
        shape = NordicShapes.md,
        border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.045f)),
        modifier = Modifier
            .width(240.dp)
            .pressScale(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            CoverArt(
                imageUrl = video.imageUrl,
                contentDescription = video.title,
                colorScheme = colorScheme,
                modifier = Modifier.fillMaxSize(),
                shape = NordicShapes.md,
                fallbackText = video.title
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                0.55f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.72f)
                            )
                        )
                    )
            )

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.46f),
                    contentColor = Color.White,
                    shape = NordicShapes.full,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(NordicSpacing.md),
                verticalArrangement = Arrangement.spacedBy(NordicSpacing.xs)
            ) {
                Text(
                    video.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (video.durationSeconds > 0) {
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier.fillMaxWidth(),
                        color = colorScheme.primary,
                        trackColor = Color.White.copy(alpha = 0.22f)
                    )
                }
            }
        }
    }
}

@Composable
internal fun VideoBrowserControls(
    searchExpanded: Boolean,
    searchQuery: String,
    selectedTypeFilter: VideoTypeFilter,
    filters: List<VideoTypeFilter>,
    colorScheme: ColorScheme,
    onToggleSearch: () -> Unit,
    onSearchChange: (String) -> Unit,
    onSearchCollapse: () -> Unit,
    onFilterSelected: (VideoTypeFilter) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(NordicSpacing.md)
    ) {
        if (searchExpanded) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NordicSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MediaSearchField(
                    value = searchQuery, onValueChange = onSearchChange,
                    placeholder = "搜索标题、简介、年份", clearDescription = "清除视频搜索关键词",
                    onClear = { onSearchChange("") }, colorScheme = colorScheme,
                    modifier = Modifier.weight(1f)
                )
                AnimatedIconButton(Icons.Filled.Close, "收起搜索", onSearchCollapse, colorScheme = colorScheme)
            }
        } else {
            AnimatedIconButton(Icons.Filled.Search, "搜索视频", onToggleSearch, colorScheme = colorScheme)
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(NordicSpacing.sm)
        ) {
            items(filters, key = { it.name }, contentType = { "video-type-filter" }) { filter ->
                MediaChoiceChip(filter.label, filter == selectedTypeFilter, colorScheme,
                    onClick = { onFilterSelected(filter) })
            }
        }
    }
}
