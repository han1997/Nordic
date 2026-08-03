package com.nordic.mediahub.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(NordicSpacing.md)
    ) {
        items(libraries, key = { it.id }, contentType = { "video-library-chip" }) { library ->
            val selected = library.id == selectedLibraryId
            Surface(
                color = if (selected) colorScheme.primary.copy(alpha = 0.16f) else colorScheme.surfaceVariant.copy(alpha = 0.56f),
                contentColor = if (selected) colorScheme.primary else colorScheme.onSurface,
                shape = NordicShapes.md,
                border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.06f)),
                modifier = Modifier.clickable { onSelect(library.id) }
            ) {
                Text(
                    text = library.name,
                    modifier = Modifier.padding(horizontal = NordicSpacing.md, vertical = NordicSpacing.md),
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
internal fun VideoCard(
    video: VideoItem,
    colorScheme: ColorScheme,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interactionSource)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
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
                    Box(modifier = Modifier.width(132.dp)) {
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
    val scale = rememberPressScale(interactionSource)
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
            .scale(scale)
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
                        progress = progressFraction,
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
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = {
                        Text(
                            "搜索标题、简介、年份",
                            color = colorScheme.onSurface.copy(alpha = NordicAlpha.faint)
                        )
                    },
                    shape = NordicShapes.md,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = colorScheme.surfaceVariant.copy(alpha = 0.42f),
                        unfocusedContainerColor = colorScheme.surfaceVariant.copy(alpha = 0.42f),
                        disabledContainerColor = colorScheme.surfaceVariant.copy(alpha = 0.28f)
                    )
                )
                Surface(
                    color = colorScheme.surfaceVariant.copy(alpha = 0.56f),
                    contentColor = colorScheme.onSurface,
                    shape = NordicShapes.full,
                    border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.06f)),
                    modifier = Modifier
                        .size(48.dp)
                        .clickable { onSearchCollapse() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "收起搜索",
                            tint = colorScheme.onSurface.copy(alpha = NordicAlpha.medium),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        } else {
            Surface(
                color = colorScheme.surfaceVariant.copy(alpha = 0.42f),
                contentColor = colorScheme.onSurface,
                shape = NordicShapes.full,
                border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.06f)),
                modifier = Modifier.clickable { onToggleSearch() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = NordicSpacing.lg, vertical = NordicSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(NordicSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "搜索",
                        tint = colorScheme.onSurface.copy(alpha = NordicAlpha.medium),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "搜索",
                        style = MaterialTheme.typography.labelLarge,
                        color = colorScheme.onSurface.copy(alpha = NordicAlpha.medium),
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(NordicSpacing.sm)
        ) {
            items(filters, key = { it.name }, contentType = { "video-type-filter" }) { filter ->
                val selected = filter == selectedTypeFilter
                Surface(
                    color = if (selected) colorScheme.primary.copy(alpha = 0.16f) else colorScheme.surfaceVariant.copy(alpha = 0.50f),
                    contentColor = if (selected) colorScheme.primary else colorScheme.onSurface,
                    shape = NordicShapes.full,
                    border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.06f)),
                    modifier = Modifier.clickable { onFilterSelected(filter) }
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
}

@Composable
internal fun VideoMessageCard(
    title: String,
    subtitle: String,
    isError: Boolean = false
) {
    MediaStateCard(
        title = title,
        subtitle = subtitle,
        tone = if (isError) MediaStateTone.Error else MediaStateTone.Neutral
    )
}

@Composable
internal fun VideoLoadingCard(index: Int, colorScheme: ColorScheme) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(index * 50L)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300))
    ) {
        Surface(
            color = colorScheme.surfaceVariant.copy(alpha = 0.76f),
            shape = NordicShapes.md,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .background(colorScheme.surface.copy(alpha = 0.34f))
                )
                Column(
                    modifier = Modifier.padding(NordicSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .clip(NordicShapes.full)
                            .background(colorScheme.onSurface.copy(alpha = 0.12f))
                    )
                    Box(
                        modifier = Modifier
                            .width(96.dp)
                            .height(12.dp)
                            .clip(NordicShapes.full)
                            .background(colorScheme.onSurface.copy(alpha = 0.08f))
                    )
                }
            }
        }
    }
}
