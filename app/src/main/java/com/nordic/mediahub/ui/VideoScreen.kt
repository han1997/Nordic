package com.nordic.mediahub.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nordic.mediahub.data.ConfigRepository
import com.nordic.mediahub.data.EmbyRepository
import com.nordic.mediahub.data.EmbyVideoCacheRepository
import com.nordic.mediahub.data.VideoItem
import com.nordic.mediahub.data.VideoLibrary
import com.nordic.mediahub.data.VideoServerConfig
import com.nordic.mediahub.data.cacheKey
import com.nordic.mediahub.data.formatCacheAge
import com.nordic.mediahub.data.isCacheFresh
import com.nordic.mediahub.data.isReadyForVideoSync
import com.nordic.mediahub.ui.theme.NordicSpacing
import kotlinx.coroutines.launch

@Composable
fun VideoScreen(
    colorScheme: ColorScheme,
    isDark: Boolean,
    onThemeToggle: (Boolean) -> Unit,
    onPlayVideo: (VideoItem) -> Unit = {},
    onPlayVideoFromStart: (VideoItem) -> Unit = {},
    onCatalogChanged: (List<VideoItem>) -> Unit = {}
) {
    val context = LocalContext.current
    val configRepository = remember { ConfigRepository(context) }
    val cacheRepository = remember { EmbyVideoCacheRepository(context) }
    val savedConfig by configRepository.videoConfig.collectAsStateWithLifecycle(VideoServerConfig())
    var libraries by remember { mutableStateOf(emptyList<VideoLibrary>()) }
    var selectedLibraryId by remember { mutableStateOf<String?>(null) }
    var videos by remember { mutableStateOf(emptyList<VideoItem>()) }
    var selectedVideo by remember { mutableStateOf<VideoItem?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    var selectedTypeFilter by remember { mutableStateOf(VideoTypeFilter.All) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var cacheUpdatedAtMillis by remember { mutableStateOf<Long?>(null) }
    // Server-side continue-watching list (Items/Resume). Null = not fetched
    // yet; empty list = server says nothing to resume. The list is persisted
    // in the video cache as raw server data, so cold starts restore the last
    // known server rows (cache-then-network). There is no local-derivation
    // fallback: the shelf only ever shows server data.
    var resumeVideos by remember { mutableStateOf<List<VideoItem>?>(null) }
    var videoConfigStateVersion by remember { mutableStateOf(0) }
    var previousVideoConfig by remember { mutableStateOf<VideoServerConfig?>(null) }
    var videoResetNotice by remember { mutableStateOf<String?>(null) }
    var videoDetailInvalidationNotice by remember { mutableStateOf<String?>(null) }
    var videoLibraryRequestVersion by remember { mutableStateOf(0) }
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
    val continueWatchingVideos = remember(resumeVideos, videos) {
        // Server Resume list is the only source: fresh from the network, or
        // the persisted server rows restored from the cache. No local
        // derivation — a locally derived shelf would show stale progress.
        val serverList = resumeVideos
        if (serverList != null) {
            mergeResumeItemsWithCatalog(serverList, videos)
        } else {
            emptyList()
        }
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
        videoResetNotice = null
        videoDetailInvalidationNotice = null
        videoLibraryRequestVersion += 1
        libraries = emptyList()
        selectedLibraryId = null
        videos = emptyList()
        resumeVideos = null
        selectedVideo = resolveVideoSelectionAfterConfigChange(selectedVideo)
        searchQuery = ""
        searchExpanded = false
        selectedTypeFilter = resolveVideoTypeFilterAfterConfigChange(selectedTypeFilter)
        isLoading = false
        errorMessage = null
        cacheUpdatedAtMillis = null
    }

    suspend fun applyCachedVideo(
        targetConfig: VideoServerConfig,
        requestVersion: Int? = null
    ): Boolean {
        val cached = cacheRepository.load(targetConfig)
        if (!isCurrentVideoConfigRequest(requestVersion)) {
            return false
        }

        if (cached == null) {
            libraries = emptyList()
            selectedLibraryId = null
            videos = emptyList()
            selectedVideo = null
            cacheUpdatedAtMillis = null
            return false
        }

        libraries = cached.libraries
        selectedLibraryId = cached.selectedLibraryId
        videos = cached.videos
        selectedVideo = null
        // Restore the persisted server Resume rows so the continue-watching
        // shelf renders the last known server data before the silent refresh.
        resumeVideos = cached.resumeVideos.ifEmpty { null }
        cacheUpdatedAtMillis = cached.updatedAtMillis
        errorMessage = null
        return true
    }

    /**
     * Pulls the server continue-watching list after a successful catalog
     * refresh. Success updates the UI state and persists the raw server rows
     * to the cache. Failures keep the current value: the shelf continues to
     * show the last known server data instead of collapsing to null.
     */
    suspend fun refreshResumeItems(repo: EmbyRepository, targetConfig: VideoServerConfig, requestVersion: Int?) {
        try {
            val items = repo.getResumeItems()
            if (isCurrentVideoConfigRequest(requestVersion)) {
                resumeVideos = items
                cacheRepository.saveResumeItems(config = targetConfig, items = items)
            }
        } catch (e: Exception) {
            // Keep the existing resume list (cache-restored or previously
            // fetched); do not null it out on a transient failure.
        }
    }

    suspend fun refreshVideo(
        targetConfig: VideoServerConfig = savedConfig,
        targetLibraryId: String? = selectedLibraryId,
        requestVersion: Int? = videoConfigStateVersion
    ) {
        if (!targetConfig.isReadyForVideoSync() || isLoading) return

        // Capture the library request identity at refresh start. If the user
        // switches libraries while this network round-trip is in flight, the
        // stale catalog response must not write selectedLibraryId/videos/cache
        // back over the newer chip selection.
        val libraryRequestVersion = videoLibraryRequestVersion
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
            val isCurrentLibraryRequest = videoLibraryRequestVersion == libraryRequestVersion

            val previousSelectedVideo = selectedVideo
            val refreshedSelectedVideo = resolveVideoSelectionAfterCatalogRefresh(
                selectedVideo = previousSelectedVideo,
                selectedLibraryId = catalog.selectedLibraryId,
                videos = catalog.items
            )
            libraries = catalog.libraries
            if (isCurrentLibraryRequest) {
                selectedLibraryId = catalog.selectedLibraryId
            }
            videos = catalog.items
            selectedTypeFilter = resolveVideoTypeFilterAfterCatalogRefresh(
                selectedTypeFilter = selectedTypeFilter,
                videos = catalog.items
            )
            selectedVideo = refreshedSelectedVideo
            if (shouldShowVideoDetailInvalidationNotice(previousSelectedVideo, refreshedSelectedVideo)) {
                videoDetailInvalidationNotice = "这个视频已不在刷新后的媒体库中，已返回视频列表。"
            }
            if (isCurrentLibraryRequest) {
                val freshCache = cacheRepository.buildCache(
                    config = targetConfig,
                    libraries = catalog.libraries,
                    videos = catalog.items,
                    selectedLibraryId = catalog.selectedLibraryId
                )
                cacheUpdatedAtMillis = freshCache.updatedAtMillis
                cacheRepository.save(targetConfig, freshCache)
            }
            refreshResumeItems(repo, targetConfig, requestVersion)
        } catch (e: Exception) {
            if (isCurrentVideoConfigRequest(requestVersion)) {
                val hasCachedContent = libraries.isNotEmpty() || videos.isNotEmpty()
                errorMessage = if (hasCachedContent) {
                    "正在显示上次缓存：${e.message ?: "未知错误"}"
                } else {
                    "连接失败: ${e.message ?: "未知错误"}"
                }
            }
        } finally {
            if (isCurrentVideoConfigRequest(requestVersion)) {
                isLoading = false
            }
        }
    }

    LaunchedEffect(savedConfig) {
        val previousConfig = previousVideoConfig
        val previousConfigChanged = previousConfig != null && previousConfig.cacheKey() != savedConfig.cacheKey()
        val shouldShowConfigResetNotice = shouldShowVideoConfigResetNotice(
            previousConfigChanged = previousConfigChanged,
            selectedVideo = selectedVideo,
            searchQuery = searchQuery,
            searchExpanded = searchExpanded,
            selectedTypeFilter = selectedTypeFilter
        )
        previousVideoConfig = savedConfig
        resetVideoStateAfterConfigChange()
        if (shouldShowConfigResetNotice) {
            videoResetNotice = "视频配置已更新，已回到视频首页。"
        }
        val requestVersion = videoConfigStateVersion
        // Clear the previous config's persisted cache so switching Emby accounts/servers
        // does not leave dead cache JSON in DataStore.
        if (previousConfigChanged) {
            previousConfig?.let { cacheRepository.clear(it) }
        }
        if (savedConfig.isReadyForVideoSync()) {
            applyCachedVideo(savedConfig, requestVersion)
            // Launch-path refresh is TTL-gated; manual refresh ( bypasses TTL.
            if (!isCacheFresh(cacheUpdatedAtMillis)) {
                refreshVideo(savedConfig, targetLibraryId = null, requestVersion = requestVersion)
            }
        }
    }

    // Re-entering the video screen always re-pulls the latest playback records
    // (positions / played flags may have changed on another device or in a
    // previous session). Cache-then-network: cached data renders immediately,
    // the silent refresh updates in place. `refreshVideo` self-guards with
    // `isLoading`, so this cannot stack with a manual refresh in flight.
    // A library switch during the round-trip bumps `videoLibraryRequestVersion`,
    // which makes refreshVideo skip the selectedLibraryId/cache write-back so
    // the user's chip selection is not swallowed by the stale response.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (savedConfig.isReadyForVideoSync()) {
            scope.launch {
                refreshVideo(
                    targetConfig = savedConfig,
                    targetLibraryId = selectedLibraryId,
                    requestVersion = videoConfigStateVersion
                )
            }
        }
    }

    fun openVideoDetail(video: VideoItem) {
        videoResetNotice = null
        videoDetailInvalidationNotice = null
        selectedVideo = video
    }

    // Publish the browse catalog so the video player can resolve the
    // "next episode" target when playback starts from this screen.
    LaunchedEffect(videos) {
        onCatalogChanged(videos)
    }

    BackHandler(enabled = selectedVideo != null) {
        videoResetNotice = null
        videoDetailInvalidationNotice = null
        selectedVideo = null
    }

    BackHandler(
        enabled = selectedVideo == null && shouldHandleVideoBrowserBack(
            searchExpanded = searchExpanded,
            searchQuery = searchQuery,
            selectedTypeFilter = selectedTypeFilter
        )
    ) {
        videoResetNotice = null
        videoDetailInvalidationNotice = null
        searchQuery = ""
        searchExpanded = false
        selectedTypeFilter = VideoTypeFilter.All
    }

    selectedVideo?.let { video ->
        val relatedEpisodes = remember(video, videos) { videos.relatedEpisodesFor(video) }
        val playAction = remember(video) { resolveVideoDetailPlayAction(video) }
        VideoDetailScreen(
            video = video,
            relatedEpisodes = relatedEpisodes,
            playAction = playAction,
            colorScheme = colorScheme,
            onBack = {
                videoResetNotice = null
                selectedVideo = null
            },
            onPlay = { onPlayVideo(video) },
            onPlayFromStart = { onPlayVideoFromStart(video) },
            onPlayEpisode = onPlayVideo
        )
        return
    }

    val cacheAgeLabel = formatCacheAge(cacheUpdatedAtMillis)
    val hasVideoContent = libraries.isNotEmpty() || videos.isNotEmpty()
    val refreshErrorSubtitle = mediaRefreshErrorSubtitle(errorMessage, hasVideoContent)
    val standaloneError = standaloneMediaError(errorMessage, hasVideoContent)

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
                    refreshErrorSubtitle != null -> refreshErrorSubtitle
                    isLoading && videos.isNotEmpty() -> "正在刷新，先显示本地缓存"
                    hasActiveBrowserFilter -> "${visibleVideos.size} / ${browseVideos.size} 个匹配条目"
                    cacheAgeLabel != null -> "本地缓存，$cacheAgeLabel"
                    selectedLibraryId != null -> "共 ${browseVideos.size} 个条目"
                    savedConfig.isReadyForVideoSync() -> "已连接 Emby"
                    else -> "连接 Emby 后显示真实媒体库、海报和视频信息"
                },
                actions = buildList {
                    if (savedConfig.isReadyForVideoSync()) {
                        add(
                            HeaderAction(
                                icon = Icons.Filled.Refresh,
                                contentDescription = "刷新视频",
                                enabled = !isLoading,
                                onClick = { scope.launch { refreshVideo() } }
                            )
                        )
                    }
                    add(
                        HeaderAction(
                            icon = if (isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = if (isDark) "切换到浅色模式" else "切换到深色模式",
                            onClick = { onThemeToggle(!isDark) }
                        )
                    )
                },
                colorScheme = colorScheme
            )
        }

        if (standaloneError != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                MediaStateCard(
                    title = "Emby 连接错误",
                    subtitle = standaloneError,
                    hint = "检查配置或点击刷新重试",
                    tone = MediaStateTone.Error
                )
            }
        }

        if (videoResetNotice != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                MediaStateCard(
                    title = "已应用新的视频配置",
                    subtitle = videoResetNotice.orEmpty(),
                    density = MediaStateDensity.Compact
                )
            }
        }

        if (videoDetailInvalidationNotice != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                MediaStateCard(
                    title = "详情已更新",
                    subtitle = videoDetailInvalidationNotice.orEmpty(),
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
                    onSelect = { libraryId ->
                        videoResetNotice = null
                        videoDetailInvalidationNotice = null
                        videoLibraryRequestVersion += 1
                        val libraryRequestVersion = videoLibraryRequestVersion
                        selectedLibraryId = libraryId
                        selectedVideo = null
                        videos = emptyList()
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
                                if (videoConfigStateVersion == requestVersion &&
                                    videoLibraryRequestVersion == libraryRequestVersion &&
                                    selectedLibraryId == libraryId
                                ) {
                                    videos = loadedVideos
                                    selectedTypeFilter = resolveVideoTypeFilterAfterCatalogRefresh(
                                        selectedTypeFilter = selectedTypeFilter,
                                        videos = loadedVideos
                                    )
                                    val updatedCache = cacheRepository.buildCache(
                                        config = savedConfig,
                                        libraries = libraries,
                                        videos = loadedVideos,
                                        selectedLibraryId = libraryId
                                    )
                                    cacheUpdatedAtMillis = updatedCache.updatedAtMillis
                                    cacheRepository.save(savedConfig, updatedCache)
                                }
                            } catch (e: Exception) {
                                if (videoConfigStateVersion == requestVersion &&
                                    videoLibraryRequestVersion == libraryRequestVersion &&
                                    selectedLibraryId == libraryId
                                ) {
                                    errorMessage = "加载视频列表失败: ${e.message ?: "未知错误"}"
                                }
                            } finally {
                                if (videoConfigStateVersion == requestVersion &&
                                    videoLibraryRequestVersion == libraryRequestVersion &&
                                    selectedLibraryId == libraryId
                                ) {
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
                    onToggleSearch = {
                        videoResetNotice = null
                        searchExpanded = true
                    },
                    onSearchChange = {
                        videoResetNotice = null
                        searchQuery = it
                    },
                    onSearchCollapse = {
                        videoResetNotice = null
                        searchQuery = ""
                        searchExpanded = false
                    },
                    onFilterSelected = {
                        videoResetNotice = null
                        selectedTypeFilter = it
                    }
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


