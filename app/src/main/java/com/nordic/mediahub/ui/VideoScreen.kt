package com.nordic.mediahub.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nordic.mediahub.data.ConfigRepository
import com.nordic.mediahub.data.EmbyRepository
import com.nordic.mediahub.data.VideoItem
import com.nordic.mediahub.data.VideoLibrary
import com.nordic.mediahub.data.VideoServerConfig
import com.nordic.mediahub.data.isReadyForVideoSync
import com.nordic.mediahub.ui.theme.NordicSpacing
import kotlinx.coroutines.launch

@Composable
fun VideoScreen(
    colorScheme: ColorScheme,
    isDark: Boolean,
    onThemeToggle: (Boolean) -> Unit,
    onPlayVideo: (VideoItem) -> Unit = {},
    onPlayVideoFromStart: (VideoItem) -> Unit = {}
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
    var searchExpanded by remember { mutableStateOf(false) }
    var selectedTypeFilter by remember { mutableStateOf(VideoTypeFilter.All) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var videoConfigStateVersion by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val visibleTypeFilters = remember(videos) {
        visibleVideoTypeFilters(videos)
    }
    val visibleVideos = remember(videos, searchQuery, selectedTypeFilter) {
        visibleBrowseVideos(videos, searchQuery, selectedTypeFilter)
    }
    val browseVideos = remember(videos) {
        browseCatalogVideos(videos)
    }
    val hasActiveBrowserFilter = searchQuery.isNotBlank() || selectedTypeFilter != VideoTypeFilter.All
    val continueWatchingVideos = remember(videos) {
        continueWatchingShelf(videos)
    }
    val topRatedVideos = remember(videos) {
        topRatedVideoShelf(videos)
    }
    val unplayedVideos = remember(videos) {
        unplayedVideoShelf(videos)
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
        searchExpanded = false
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

    fun openVideoDetail(video: VideoItem) {
        showConfig = false
        selectedVideo = video
    }

    BackHandler(enabled = selectedVideo != null) {
        selectedVideo = null
    }

    BackHandler(
        enabled = selectedVideo == null && shouldHandleVideoBrowserBack(
            searchExpanded = searchExpanded,
            searchQuery = searchQuery,
            selectedTypeFilter = selectedTypeFilter
        )
    ) {
        searchQuery = ""
        searchExpanded = false
        selectedTypeFilter = VideoTypeFilter.All
    }

    BackHandler(enabled = selectedVideo == null && showConfig) {
        showConfig = false
    }

    selectedVideo?.let { video ->
        val relatedEpisodes = remember(video, videos) { videos.relatedEpisodesFor(video) }
        val playAction = remember(video) { resolveVideoDetailPlayAction(video) }
        VideoDetailScreen(
            video = video,
            relatedEpisodes = relatedEpisodes,
            playAction = playAction,
            colorScheme = colorScheme,
            onBack = { selectedVideo = null },
            onPlay = { onPlayVideo(video) },
            onPlayFromStart = { onPlayVideoFromStart(video) },
            onPlayEpisode = onPlayVideo
        )
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 156.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(NordicSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(NordicSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(NordicSpacing.md)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            MediaPageHeader(
                title = "视频",
                subtitle = when {
                    isLoading && videos.isNotEmpty() -> "正在刷新，先显示当前 Emby 内容"
                    hasActiveBrowserFilter -> "${visibleVideos.size} / ${browseVideos.size} 个匹配条目"
                    selectedLibraryId != null -> "共 ${browseVideos.size} 个条目，点击海报播放"
                    savedConfig.isReadyForVideoSync() -> "已连接 Emby，选择媒体库浏览内容"
                    else -> "连接 Emby 后显示真实媒体库、海报和视频信息"
                },
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
                },
                colorScheme = colorScheme
            )
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            MediaConfigPanel(visible = showConfig) {
                VideoConfigCard(
                    config = config,
                    colorScheme = colorScheme,
                    onConfigChange = { config = it },
                    onSave = {
                        scope.launch {
                            configRepository.saveVideoConfig(config)
                            showConfig = false
                        }
                    }
                )
            }
        }

        if (errorMessage != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                MediaStateCard(
                    title = "Emby 连接错误",
                    subtitle = errorMessage.orEmpty(),
                    tone = MediaStateTone.Error
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
                        searchExpanded = false
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
                        onVideoSelected = ::openVideoDetail
                    )
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                VideoBrowserControls(
                    searchExpanded = searchExpanded,
                    searchQuery = searchQuery,
                    selectedTypeFilter = selectedTypeFilter,
                    filters = visibleTypeFilters,
                    colorScheme = colorScheme,
                    onToggleSearch = { searchExpanded = true },
                    onSearchChange = { searchQuery = it },
                    onSearchCollapse = {
                        searchQuery = ""
                        searchExpanded = false
                    },
                    onFilterSelected = { selectedTypeFilter = it }
                )
            }

            val catalogCount = if (hasActiveBrowserFilter) visibleVideos.size else browseVideos.size
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "全部 $catalogCount 项",
                    style = MaterialTheme.typography.headlineMedium,
                    color = colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        when {
            isLoading && videos.isEmpty() -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    MediaLoadingCard(
                        title = "正在同步 Emby",
                        subtitle = "加载媒体库、海报和继续观看进度..."
                    )
                }
            }

            !savedConfig.isReadyForVideoSync() -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    MediaStateCard(
                        title = "先接入你的 Emby 服务器",
                        subtitle = "填写服务器地址，并使用 API Key 或用户名密码登录。这里会显示真实媒体库和视频缩略图。",
                        hint = "点右上角设置开始连接"
                    )
                }
            }

            libraries.isEmpty() && !isLoading -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    MediaStateCard(
                        title = "没有可用视频媒体库",
                        subtitle = "Emby 已连接，但当前用户没有可浏览的电影、剧集或家庭视频媒体库。"
                    )
                }
            }

            videos.isEmpty() && !isLoading -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    MediaStateCard(
                        title = "这个媒体库暂时没有内容",
                        subtitle = "切换其他媒体库，或回到 Emby 服务端检查扫描结果和用户权限。"
                    )
                }
            }

            visibleVideos.isEmpty() && !isLoading -> {
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
                        onClick = { openVideoDetail(video) }
                    )
                }
            }
        }
    }
}


