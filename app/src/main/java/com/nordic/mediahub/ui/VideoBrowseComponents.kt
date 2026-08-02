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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nordic.mediahub.data.VideoItem
import com.nordic.mediahub.data.VideoLibrary

@Composable
internal fun VideoLibrarySelector(
    libraries: List<VideoLibrary>,
    selectedLibraryId: String?,
    colorScheme: ColorScheme,
    onSelect: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(libraries, key = { it.id }, contentType = { "video-library-chip" }) { library ->
            val selected = library.id == selectedLibraryId
            Surface(
                color = if (selected) colorScheme.primary.copy(alpha = 0.16f) else colorScheme.surfaceVariant.copy(alpha = 0.56f),
                contentColor = if (selected) colorScheme.primary else colorScheme.onSurface,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.06f)),
                modifier = Modifier.clickable { onSelect(library.id) }
            ) {
                Text(
                    text = library.name,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    fontSize = 13.sp,
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
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            color = colorScheme.surfaceVariant.copy(alpha = 0.42f),
            shape = RoundedCornerShape(18.dp),
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
                shape = RoundedCornerShape(18.dp),
                fallbackText = "VIDEO"
            )
        }

        Column {
            Text(
                video.title,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                color = colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val meta = remember(video) { video.metaText() }
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
        verticalArrangement = Arrangement.spacedBy(18.dp)
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

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = videos,
                key = { video -> "$keyPrefix-${video.libraryId}:${video.id}" },
                contentType = { "video-spotlight-card" }
            ) { video ->
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

@Composable
internal fun VideoBrowserControls(
    searchQuery: String,
    selectedTypeFilter: VideoTypeFilter,
    filters: List<VideoTypeFilter>,
    colorScheme: ColorScheme,
    onSearchChange: (String) -> Unit,
    onFilterSelected: (VideoTypeFilter) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = {
                Text(
                    "搜索标题、简介、年份",
                    color = colorScheme.onSurface.copy(alpha = 0.44f)
                )
            },
            shape = RoundedCornerShape(16.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = colorScheme.surfaceVariant.copy(alpha = 0.42f),
                unfocusedContainerColor = colorScheme.surfaceVariant.copy(alpha = 0.42f),
                disabledContainerColor = colorScheme.surfaceVariant.copy(alpha = 0.28f)
            )
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filters, key = { it.name }, contentType = { "video-type-filter" }) { filter ->
                val selected = filter == selectedTypeFilter
                Surface(
                    color = if (selected) colorScheme.primary.copy(alpha = 0.16f) else colorScheme.surfaceVariant.copy(alpha = 0.50f),
                    contentColor = if (selected) colorScheme.primary else colorScheme.onSurface,
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.06f)),
                    modifier = Modifier.clickable { onFilterSelected(filter) }
                ) {
                    Text(
                        text = filter.label,
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                        fontSize = 13.sp,
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
            shape = RoundedCornerShape(18.dp),
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
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(colorScheme.onSurface.copy(alpha = 0.12f))
                    )
                    Box(
                        modifier = Modifier
                            .width(96.dp)
                            .height(12.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(colorScheme.onSurface.copy(alpha = 0.08f))
                    )
                }
            }
        }
    }
}
