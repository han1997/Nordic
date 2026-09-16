package com.nordic.mediahub.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nordic.mediahub.data.VideoItem
import com.nordic.mediahub.data.VideoLibrary
import com.nordic.mediahub.ui.theme.NordicControlSizes
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
                role = Role.Button,
                onClickLabel = "打开详情",
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
            Box(Modifier.clearAndSetSemantics { }) {
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
                    color = colorScheme.onSurfaceVariant,
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
                color = colorScheme.onSurfaceVariant
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
internal fun VideoHomeContent(
    header: @Composable () -> Unit,
    libraries: List<VideoLibrary>,
    selectedLibraryId: String?,
    videos: List<VideoItem>,
    visibleVideos: List<VideoItem>,
    browseVideos: List<VideoItem>,
    continueWatching: List<VideoItem>,
    topRated: List<VideoItem>,
    unplayed: List<VideoItem>,
    searchExpanded: Boolean,
    searchQuery: String,
    selectedTypeFilter: VideoTypeFilter,
    typeFilters: List<VideoTypeFilter>,
    isLoading: Boolean,
    standaloneError: String?,
    resetNotice: String?,
    detailInvalidationNotice: String?,
    ready: Boolean,
    colorScheme: ColorScheme,
    onSelectLibrary: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onSearchChange: (String) -> Unit,
    onSearchCollapse: () -> Unit,
    onFilterSelected: (VideoTypeFilter) -> Unit,
    onOpenVideo: (VideoItem) -> Unit,
    onRetry: () -> Unit
) {
    val hasActiveBrowserFilter = searchQuery.isNotBlank() || selectedTypeFilter != VideoTypeFilter.All
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 156.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(NordicSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(NordicSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(NordicSpacing.md)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { header() }

        if (standaloneError != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)) {
                    MediaStateCard(
                        title = "Emby 连接错误",
                        subtitle = standaloneError,
                        hint = "检查配置或点击刷新重试",
                        tone = MediaStateTone.Error
                    )
                    SecondaryActionButton("重试", colorScheme, onClick = onRetry)
                }
            }
        }

        if (resetNotice != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                MediaStateCard(
                    title = "已应用新的视频配置",
                    subtitle = resetNotice,
                    density = MediaStateDensity.Compact
                )
            }
        }

        if (detailInvalidationNotice != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                MediaStateCard(
                    title = "详情已更新",
                    subtitle = detailInvalidationNotice,
                    density = MediaStateDensity.Compact
                )
            }
        }

        if (libraries.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                VideoLibrarySelector(
                    libraries = libraries,
                    selectedLibraryId = selectedLibraryId,
                    colorScheme = colorScheme,
                    onSelect = onSelectLibrary
                )
            }
        }

        if (videos.isNotEmpty()) {
            if (!hasActiveBrowserFilter) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    VideoSpotlightSections(
                        continueWatching = continueWatching,
                        topRated = topRated,
                        unplayed = unplayed,
                        colorScheme = colorScheme,
                        onVideoSelected = onOpenVideo
                    )
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                VideoBrowserControls(
                    searchExpanded = searchExpanded,
                    searchQuery = searchQuery,
                    selectedTypeFilter = selectedTypeFilter,
                    filters = typeFilters,
                    colorScheme = colorScheme,
                    onToggleSearch = onToggleSearch,
                    onSearchChange = onSearchChange,
                    onSearchCollapse = onSearchCollapse,
                    onFilterSelected = onFilterSelected
                )
            }

            val catalogCount = if (hasActiveBrowserFilter) visibleVideos.size else browseVideos.size
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "全部 $catalogCount 项",
                    style = MaterialTheme.typography.headlineMedium,
                    color = colorScheme.onBackground,
                    modifier = Modifier.semantics { heading() },
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        when {
            standaloneError != null && videos.isEmpty() -> Unit
            isLoading && videos.isEmpty() -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    MediaLoadingCard(
                        title = "正在同步 Emby",
                        subtitle = "加载媒体库、海报和继续观看进度..."
                    )
                }
            }
            !ready -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    MediaStateCard(
                        title = "先接入你的 Emby 服务器",
                        subtitle = "填写服务器地址，并使用 API Key 或用户名密码登录。这里会显示真实媒体库和视频缩略图。",
                        hint = "前往配置 tab 开始连接"
                    )
                }
            }
            libraries.isEmpty() && !isLoading && standaloneError == null -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    MediaStateCard(
                        title = "没有可用视频媒体库",
                        subtitle = "Emby 已连接，但当前用户没有可浏览的电影、剧集或家庭视频媒体库。"
                    )
                }
            }
            videos.isEmpty() && !isLoading && standaloneError == null -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    MediaStateCard(
                        title = "这个媒体库暂时没有内容",
                        subtitle = "切换其他媒体库，或回到 Emby 服务端检查扫描结果和用户权限。"
                    )
                }
            }
            visibleVideos.isEmpty() && !isLoading && standaloneError == null -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    MediaStateCard(
                        title = "没有匹配的视频",
                        subtitle = "换一个关键词，或切换类型筛选查看这个媒体库中的其他内容。",
                        density = MediaStateDensity.Compact
                    )
                }
            }
            else -> {
                gridItems(
                    items = visibleVideos,
                    key = { video -> "${video.libraryId}:${video.id}" },
                    contentType = { "video-card" }
                ) { video ->
                    VideoCard(
                        video = video,
                        colorScheme = colorScheme,
                        onClick = { onOpenVideo(video) }
                    )
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
            .width(continueWatchingCardWidth(LocalDensity.current.fontScale))
            .pressScale(interactionSource)
            .clickable(
                role = Role.Button,
                onClickLabel = "打开详情",
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            Box(Modifier.clearAndSetSemantics { }) {
                CoverArt(
                    imageUrl = video.imageUrl,
                    contentDescription = video.title,
                    colorScheme = colorScheme,
                    modifier = Modifier.fillMaxSize(),
                    shape = NordicShapes.md,
                    fallbackText = video.title
                )
            }

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
                    modifier = Modifier.size(NordicControlSizes.touchTarget)
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
