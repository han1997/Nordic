package com.nordic.mediahub.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ColorScheme
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.nordic.mediahub.data.ConfigRepository
import com.nordic.mediahub.data.EmbyRepository
import com.nordic.mediahub.data.VideoItem
import com.nordic.mediahub.data.VideoLibrary
import com.nordic.mediahub.data.VideoServerConfig
import com.nordic.mediahub.data.isReadyForVideoSync
import kotlinx.coroutines.launch

@Composable
fun VideoScreen(
    colorScheme: ColorScheme,
    isDark: Boolean,
    onThemeToggle: (Boolean) -> Unit,
    onPlayVideo: (VideoItem) -> Unit = {}
) {
    val context = LocalContext.current
    val configRepository = remember { ConfigRepository(context) }
    val savedConfig by configRepository.videoConfig.collectAsStateWithLifecycle(VideoServerConfig())
    var config by remember { mutableStateOf(VideoServerConfig()) }
    var showConfig by remember { mutableStateOf(false) }
    var libraries by remember { mutableStateOf(emptyList<VideoLibrary>()) }
    var selectedLibraryId by remember { mutableStateOf<String?>(null) }
    var videos by remember { mutableStateOf(emptyList<VideoItem>()) }
    var selectedVideo by remember { mutableStateOf<VideoItem?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTypeFilter by remember { mutableStateOf(VideoTypeFilter.All) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var videoConfigStateVersion by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val loadingCardIndexes = remember { List(3) { it } }
    val visibleTypeFilters = remember(videos) {
        VideoTypeFilter.values().filter { filter ->
            filter == VideoTypeFilter.All || videos.any(filter::matches)
        }
    }
    val visibleVideos = remember(videos, searchQuery, selectedTypeFilter) {
        videos.filter { video ->
            selectedTypeFilter.matches(video) && video.matchesSearch(searchQuery)
        }
    }
    val hasActiveBrowserFilter = searchQuery.isNotBlank() || selectedTypeFilter != VideoTypeFilter.All
    val continueWatchingVideos = remember(videos) {
        continueWatchingShelf(videos)
    }
    val topRatedVideos = remember(videos) {
        videos
            .filter { video -> (video.communityRating ?: 0f) > 0f }
            .sortedByDescending { video -> video.communityRating ?: 0f }
            .take(12)
    }
    val unplayedVideos = remember(videos) {
        videos
            .filter { video -> !video.isPlayed && video.playbackPositionSeconds <= 0 }
            .take(12)
    }

    val embyRepository = remember(savedConfig) {
        if (savedConfig.isReadyForVideoSync()) EmbyRepository(savedConfig) else null
    }

    fun isCurrentVideoConfigRequest(requestVersion: Int?): Boolean {
        return requestVersion == null || videoConfigStateVersion == requestVersion
    }

    fun resetVideoStateAfterConfigChange() {
        videoConfigStateVersion += 1
        libraries = emptyList()
        selectedLibraryId = null
        videos = emptyList()
        selectedVideo = resolveVideoSelectionAfterConfigChange(selectedVideo)
        searchQuery = ""
        selectedTypeFilter = resolveVideoTypeFilterAfterConfigChange(selectedTypeFilter)
        isLoading = false
        errorMessage = null
    }

    suspend fun refreshVideo(
        targetConfig: VideoServerConfig = savedConfig,
        targetLibraryId: String? = selectedLibraryId,
        requestVersion: Int? = videoConfigStateVersion
    ) {
        if (!targetConfig.isReadyForVideoSync() || isLoading) return

        isLoading = true
        errorMessage = null
        try {
            val repo = if (targetConfig == savedConfig) {
                embyRepository ?: EmbyRepository(targetConfig)
            } else {
                EmbyRepository(targetConfig)
            }
            val catalog = repo.getCatalog(targetLibraryId)
            if (!isCurrentVideoConfigRequest(requestVersion)) {
                return
            }

            libraries = catalog.libraries
            selectedLibraryId = catalog.selectedLibraryId
            videos = catalog.items
            selectedTypeFilter = resolveVideoTypeFilterAfterCatalogRefresh(
                selectedTypeFilter = selectedTypeFilter,
                videos = catalog.items
            )
            selectedVideo = resolveVideoSelectionAfterCatalogRefresh(
                selectedVideo = selectedVideo,
                selectedLibraryId = catalog.selectedLibraryId,
                videos = catalog.items
            )
        } catch (e: Exception) {
            if (isCurrentVideoConfigRequest(requestVersion)) {
                errorMessage = e.message ?: "连接 Emby 失败"
            }
        } finally {
            if (isCurrentVideoConfigRequest(requestVersion)) {
                isLoading = false
            }
        }
    }

    LaunchedEffect(savedConfig) {
        config = savedConfig
        resetVideoStateAfterConfigChange()
        val requestVersion = videoConfigStateVersion
        if (savedConfig.isReadyForVideoSync()) {
            refreshVideo(savedConfig, targetLibraryId = null, requestVersion = requestVersion)
        }
    }

    BackHandler(enabled = selectedVideo != null) {
        selectedVideo = null
    }

    BackHandler(enabled = showConfig) {
        showConfig = false
    }

    selectedVideo?.let { video ->
        val relatedEpisodes = remember(video, videos) { videos.relatedEpisodesFor(video) }
        VideoDetailScreen(
            video = video,
            relatedEpisodes = relatedEpisodes,
            colorScheme = colorScheme,
            onBack = { selectedVideo = null },
            onPlay = { onPlayVideo(video) },
            onPlayEpisode = onPlayVideo
        )
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 156.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "视频",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        when {
                            isLoading && videos.isNotEmpty() -> "正在刷新，先显示当前 Emby 内容"
                            hasActiveBrowserFilter -> "${visibleVideos.size} / ${videos.size} 个匹配条目"
                            selectedLibraryId != null -> "共 ${videos.size} 个条目，点击海报播放"
                            savedConfig.isReadyForVideoSync() -> "已连接 Emby，选择媒体库浏览内容"
                            else -> "连接 Emby 后显示真实媒体库、海报和视频信息"
                        },
                        fontSize = 14.sp,
                        color = colorScheme.onSurface.copy(alpha = 0.62f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                HeaderActionGroup(
                    actions = buildList {
                        if (savedConfig.isReadyForVideoSync()) {
                            add(
                                HeaderAction(
                                    icon = if (isLoading) "…" else "↻",
                                    enabled = !isLoading,
                                    onClick = { scope.launch { refreshVideo() } }
                                )
                            )
                        }
                        add(HeaderAction(if (isDark) "☀" else "☾") { onThemeToggle(!isDark) })
                        add(HeaderAction("⚙") { showConfig = !showConfig })
                    }
                )
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            AnimatedVisibility(
                visible = showConfig,
                enter = fadeIn(tween(300, easing = FastOutSlowInEasing)) + expandVertically(),
                exit = fadeOut(tween(200)) + shrinkVertically()
            ) {
                VideoConfigCard(
                    config = config,
                    colorScheme = colorScheme,
                    onConfigChange = { config = it },
                    onSave = {
                        scope.launch {
                            configRepository.saveVideoConfig(config)
                            refreshVideo(config, selectedLibraryId, requestVersion = null)
                            if (errorMessage == null && config.isReadyForVideoSync()) {
                                showConfig = false
                            }
                        }
                    }
                )
            }
        }

        if (errorMessage != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                VideoMessageCard(
                    title = "Emby 连接错误",
                    subtitle = errorMessage.orEmpty(),
                    isError = true
                )
            }
        }

        if (libraries.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                VideoLibrarySelector(
                    libraries = libraries,
                    selectedLibraryId = selectedLibraryId,
                    colorScheme = colorScheme,
                    onSelect = { libraryId ->
                        selectedLibraryId = libraryId
                        selectedVideo = null
                        searchQuery = ""
                        selectedTypeFilter = VideoTypeFilter.All
                        val repo = embyRepository ?: return@VideoLibrarySelector
                        val requestVersion = videoConfigStateVersion
                        scope.launch {
                            isLoading = true
                            errorMessage = null
                            try {
                                val loadedVideos = repo.getLibraryItems(libraryId)
                                if (videoConfigStateVersion == requestVersion && selectedLibraryId == libraryId) {
                                    videos = loadedVideos
                                }
                            } catch (e: Exception) {
                                if (videoConfigStateVersion == requestVersion && selectedLibraryId == libraryId) {
                                    errorMessage = e.message ?: "加载视频列表失败"
                                }
                            } finally {
                                if (videoConfigStateVersion == requestVersion && selectedLibraryId == libraryId) {
                                    isLoading = false
                                }
                            }
                        }
                    }
                )
            }
        }

        if (videos.isNotEmpty()) {
            if (!hasActiveBrowserFilter) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    VideoSpotlightSections(
                        continueWatching = continueWatchingVideos,
                        topRated = topRatedVideos,
                        unplayed = unplayedVideos,
                        colorScheme = colorScheme,
                        onVideoSelected = { selectedVideo = it }
                    )
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                VideoBrowserControls(
                    searchQuery = searchQuery,
                    selectedTypeFilter = selectedTypeFilter,
                    filters = visibleTypeFilters,
                    colorScheme = colorScheme,
                    onSearchChange = { searchQuery = it },
                    onFilterSelected = { selectedTypeFilter = it }
                )
            }
        }

        when {
            isLoading && videos.isEmpty() -> {
                gridItemsIndexed(
                    items = loadingCardIndexes,
                    contentType = { _, _ -> "video-loading-card" }
                ) { index, _ ->
                    VideoLoadingCard(index = index, colorScheme = colorScheme)
                }
            }

            !savedConfig.isReadyForVideoSync() -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    VideoMessageCard(
                        title = "先接入你的 Emby 服务器",
                        subtitle = "填写服务器地址，并使用 API Key 或用户名密码登录。这里会显示真实媒体库和视频缩略图。"
                    )
                }
            }

            libraries.isEmpty() && !isLoading -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    VideoMessageCard(
                        title = "没有可用视频媒体库",
                        subtitle = "Emby 已连接，但当前用户没有可浏览的电影、剧集或家庭视频媒体库。"
                    )
                }
            }

            videos.isEmpty() && !isLoading -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    VideoMessageCard(
                        title = "这个媒体库暂时没有内容",
                        subtitle = "切换其他媒体库，或回到 Emby 服务端检查扫描结果和用户权限。"
                    )
                }
            }

            visibleVideos.isEmpty() && !isLoading -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    VideoMessageCard(
                        title = "没有匹配的视频",
                        subtitle = "换一个关键词，或切换类型筛选查看这个媒体库中的其他内容。"
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
                        onClick = { selectedVideo = video }
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoLibrarySelector(
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
private fun VideoCard(
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
            VideoThumbnail(
                imageUrl = video.imageUrl,
                title = video.title,
                colorScheme = colorScheme,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
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
            val meta = video.metaText()
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
private fun VideoSpotlightSections(
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
private fun VideoSpotlightRow(
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
private fun VideoDetailScreen(
    video: VideoItem,
    relatedEpisodes: List<VideoItem>,
    colorScheme: ColorScheme,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onPlayEpisode: (VideoItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = colorScheme.surfaceVariant.copy(alpha = 0.56f),
                contentColor = colorScheme.onSurface,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.06f)),
                modifier = Modifier
                    .size(42.dp)
                    .clickable(onClick = onBack)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("‹", fontSize = 26.sp, color = colorScheme.onSurface.copy(alpha = 0.78f))
                }
            }
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

        Surface(
            color = colorScheme.surfaceVariant.copy(alpha = 0.42f),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.05f)),
            shadowElevation = 10.dp,
            modifier = Modifier.fillMaxWidth(0.72f)
        ) {
            VideoThumbnail(
                imageUrl = video.imageUrl,
                title = video.title,
                colorScheme = colorScheme,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
            )
        }

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

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(video.detailChips(), key = { it }, contentType = { "video-detail-chip" }) { chip ->
                    VideoDetailMetaChip(text = chip, colorScheme = colorScheme)
                }
            }

            VideoDetailPlayButton(
                enabled = !video.streamUrl.isNullOrBlank(),
                colorScheme = colorScheme,
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

            if (relatedEpisodes.isNotEmpty()) {
                VideoEpisodeSection(
                    episodes = relatedEpisodes,
                    colorScheme = colorScheme,
                    onPlayEpisode = onPlayEpisode
                )
            }
        }
    }
}

@Composable
private fun VideoEpisodeSection(
    episodes: List<VideoItem>,
    colorScheme: ColorScheme,
    onPlayEpisode: (VideoItem) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "分集",
            fontSize = 20.sp,
            color = colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        episodes.forEach { episode ->
            VideoEpisodeRow(
                episode = episode,
                colorScheme = colorScheme,
                onClick = { onPlayEpisode(episode) }
            )
        }
    }
}

@Composable
private fun VideoEpisodeRow(
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
            VideoThumbnail(
                imageUrl = episode.imageUrl,
                title = episode.title,
                colorScheme = colorScheme,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                episode.episodeLabel(),
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
            val meta = episode.metaText()
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
private fun VideoDetailMetaChip(text: String, colorScheme: ColorScheme) {
    Surface(
        color = colorScheme.surfaceVariant.copy(alpha = 0.56f),
        contentColor = colorScheme.onSurface,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.05f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            fontSize = 12.sp,
            color = colorScheme.onSurface.copy(alpha = 0.68f),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun VideoDetailPlayButton(
    enabled: Boolean,
    colorScheme: ColorScheme,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = rememberPressScale(
        interactionSource = interactionSource,
        pressedScale = 0.985f,
        enabled = enabled
    )

    Surface(
        color = if (enabled) colorScheme.primary else colorScheme.primary.copy(alpha = 0.32f),
        contentColor = colorScheme.onPrimary,
        shape = RoundedCornerShape(999.dp),
        shadowElevation = if (enabled) 4.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .scale(scale)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "▶  播放",
                fontSize = 16.sp,
                color = colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun VideoBrowserControls(
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
private fun VideoThumbnail(
    imageUrl: String?,
    title: String,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier
) {
    var imageFailed by remember(imageUrl) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        colorScheme.primary.copy(alpha = 0.22f),
                        colorScheme.secondary.copy(alpha = 0.14f),
                        colorScheme.surfaceVariant.copy(alpha = 0.82f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (imageUrl != null && !imageFailed) {
            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = { imageFailed = true }
            )
        } else {
            Text(
                "VIDEO",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface.copy(alpha = 0.48f)
            )
        }
    }
}

@Composable
private fun VideoMessageCard(
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
private fun VideoLoadingCard(index: Int, colorScheme: ColorScheme) {
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

private fun VideoItem.metaText(): String {
    return buildList {
        type.takeIf { it.isNotBlank() }?.let { add(it) }
        year?.let { add(it.toString()) }
        if (durationSeconds > 0) add(formatVideoDuration(durationSeconds))
        if (playbackPositionSeconds > 0 && !isPlayed) add("看到 ${formatVideoDuration(playbackPositionSeconds)}")
        if (isPlayed) add("已播放")
    }.joinToString("  /  ")
}

private fun VideoItem.detailChips(): List<String> {
    return buildList {
        type.takeIf { it.isNotBlank() }?.let { add(it) }
        year?.let { add(it.toString()) }
        if (durationSeconds > 0) add(formatVideoDuration(durationSeconds))
        if (playbackPositionSeconds > 0 && !isPlayed) add("续看 ${formatVideoDuration(playbackPositionSeconds)}")
        if (isPlayed) add("已播放")
        communityRating?.takeIf { it > 0f }?.let { add("评分 ${"%.1f".format(it)}") }
    }.ifEmpty { listOf("视频") }
}

internal fun continueWatchingShelf(videos: List<VideoItem>, limit: Int = 12): List<VideoItem> {
    return videos
        .filter { video -> video.isContinueWatchingCandidate() }
        .sortedWith(
            compareByDescending<VideoItem> { it.lastPlayedDate.orEmpty() }
                .thenByDescending { it.playbackPositionSeconds }
                .thenBy { it.title }
        )
        .take(limit)
}

internal fun resolveVideoSelectionAfterCatalogRefresh(
    selectedVideo: VideoItem?,
    selectedLibraryId: String?,
    videos: List<VideoItem>
): VideoItem? {
    val currentSelection = selectedVideo ?: return null
    val currentLibraryId = selectedLibraryId ?: return null
    if (currentSelection.libraryId != currentLibraryId) return null

    return videos.firstOrNull { video ->
        video.id == currentSelection.id && video.libraryId == currentLibraryId
    }
}

internal fun resolveVideoTypeFilterAfterCatalogRefresh(
    selectedTypeFilter: VideoTypeFilter,
    videos: List<VideoItem>
): VideoTypeFilter {
    return selectedTypeFilter.takeIf { filter ->
        filter == VideoTypeFilter.All || videos.any(filter::matches)
    } ?: VideoTypeFilter.All
}

internal fun resolveVideoSelectionAfterConfigChange(selectedVideo: VideoItem?): VideoItem? {
    return when (selectedVideo) {
        null -> null
        else -> null
    }
}

internal fun resolveVideoTypeFilterAfterConfigChange(
    selectedTypeFilter: VideoTypeFilter
): VideoTypeFilter {
    return when (selectedTypeFilter) {
        VideoTypeFilter.All,
        VideoTypeFilter.Movies,
        VideoTypeFilter.Series,
        VideoTypeFilter.Episodes,
        VideoTypeFilter.Videos -> VideoTypeFilter.All
    }
}

private fun VideoItem.isContinueWatchingCandidate(): Boolean {
    if (playbackPositionSeconds <= 0 || isPlayed) return false

    val knownDuration = durationSeconds.coerceAtLeast(0)
    return knownDuration == 0 || playbackPositionSeconds < knownDuration
}

internal fun List<VideoItem>.relatedEpisodesFor(series: VideoItem): List<VideoItem> {
    if (!series.type.equals("Series", ignoreCase = true)) return emptyList()

    return filter { item ->
        item.type.equals("Episode", ignoreCase = true) &&
            (
                item.seriesId == series.id ||
                    (
                        item.seriesId.isNullOrBlank() &&
                            !item.seriesName.isNullOrBlank() &&
                            item.seriesName.equals(series.title, ignoreCase = true)
                    )
            )
    }.sortedWith(
        compareBy<VideoItem> { it.seasonNumber ?: Int.MAX_VALUE }
            .thenBy { it.episodeNumber ?: Int.MAX_VALUE }
            .thenBy { it.title }
    )
}

private fun VideoItem.episodeLabel(): String {
    val season = seasonNumber
    val episode = episodeNumber
    return when {
        season != null && episode != null -> "S$season E$episode"
        episode != null -> "第 $episode 集"
        season != null -> "第 $season 季"
        else -> type.ifBlank { "Episode" }
    }
}

private fun formatVideoDuration(durationSeconds: Int): String {
    val safeSeconds = durationSeconds.coerceAtLeast(0)
    val hours = safeSeconds / 3600
    val minutes = (safeSeconds % 3600) / 60
    return if (hours > 0) {
        "${hours}h ${minutes}m"
    } else {
        "${minutes}m"
    }
}

internal enum class VideoTypeFilter(val label: String) {
    All("全部"),
    Movies("电影"),
    Series("剧集"),
    Episodes("单集"),
    Videos("视频");

    fun matches(video: VideoItem): Boolean {
        return when (this) {
            All -> true
            Movies -> video.type.equals("Movie", ignoreCase = true)
            Series -> video.type.equals("Series", ignoreCase = true)
            Episodes -> video.type.equals("Episode", ignoreCase = true)
            Videos -> video.type.equals("Video", ignoreCase = true)
        }
    }
}

internal fun videoMatchesSearch(video: VideoItem, query: String): Boolean {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isBlank()) return true

    val searchableTerms = buildList {
        add(video.title)
        add(video.overview)
        add(video.type)
        video.year?.let { year -> add(year.toString()) }
        video.seriesName?.takeIf { it.isNotBlank() }?.let { seriesName -> add(seriesName) }

        val seasonNumber = video.seasonNumber?.takeIf { it > 0 }
        val episodeNumber = video.episodeNumber?.takeIf { it > 0 }
        val seasonTokens = seasonNumber?.let { number -> videoNumberVariants(number) }.orEmpty()
        val episodeTokens = episodeNumber?.let { number -> videoNumberVariants(number) }.orEmpty()

        seasonTokens.forEach { season -> add("S$season") }
        episodeTokens.forEach { episode -> add("E$episode") }
        seasonNumber?.let { season -> add("Season $season") }
        episodeNumber?.let { episode -> add("Episode $episode") }

        seasonTokens.forEach { season ->
            episodeTokens.forEach { episode ->
                add("S${season}E${episode}")
                add("S$season E$episode")
            }
        }
        if (seasonNumber != null && episodeNumber != null) {
            add("Season $seasonNumber Episode $episodeNumber")
        }
    }

    return searchableTerms.any { term -> term.contains(normalizedQuery, ignoreCase = true) }
}

private fun VideoItem.matchesSearch(query: String): Boolean {
    return videoMatchesSearch(this, query)
}

private fun videoNumberVariants(number: Int): List<String> {
    val raw = number.toString()
    val padded = raw.padStart(2, '0')
    return if (raw == padded) listOf(raw) else listOf(raw, padded)
}
