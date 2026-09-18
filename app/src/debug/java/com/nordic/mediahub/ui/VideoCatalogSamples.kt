package com.nordic.mediahub.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.MaterialTheme
import com.nordic.mediahub.R
import com.nordic.mediahub.data.VideoItem
import com.nordic.mediahub.data.VideoLibrary

/** Deterministic in-memory host. Home and detail are the production composables. */
@Composable
internal fun VideoCatalogSample(
    request: UiSampleRequest,
    onNavigate: (UiSampleScreen) -> Unit,
    onEvent: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val covers = listOf(R.drawable.ui_sample_cover_a, R.drawable.ui_sample_cover_b, R.drawable.ui_sample_cover_c)
        .map { "android.resource://${context.packageName}/$it" }
    val long = request.state == UiSampleState.LongText
    val empty = request.state == UiSampleState.Empty
    val libraryEmpty = request.state == UiSampleState.LibraryEmpty
    val loading = request.state == UiSampleState.Loading
    val refreshing = request.state == UiSampleState.Refreshing
    val cachedError = request.state == UiSampleState.CachedError
    val error = request.state == UiSampleState.Error
    val noArt = request.state == UiSampleState.NoArtwork
    val hasCachedContent = refreshing || cachedError
    val ready = !empty || request.screen == UiSampleScreen.VideoSearch
    var screen by remember { mutableStateOf(request.screen) }
    var selectedLibraryId by remember { mutableStateOf("video-library-1") }
    var searchExpanded by remember { mutableStateOf(request.screen == UiSampleScreen.VideoSearch) }
    var searchQuery by remember {
        mutableStateOf(
            when {
                request.screen == UiSampleScreen.VideoSearch && request.state == UiSampleState.Empty -> "没有这部片子"
                request.screen == UiSampleScreen.VideoSearch && long -> "在漫长的旅途中听见远方"
                request.screen == UiSampleScreen.VideoSearch -> "北境"
                else -> ""
            }
        )
    }
    var typeFilter by remember { mutableStateOf(VideoTypeFilter.All) }

    val libraries = remember {
        listOf(
            VideoLibrary("video-library-1", "电影", "movies", 6),
            VideoLibrary("video-library-2", "剧集", "tvshows", 2)
        )
    }
    val shownLibraries = if (libraryEmpty || (empty && request.screen != UiSampleScreen.VideoSearch)) emptyList() else libraries

    val movies = remember(request.state) {
        listOf("北境回声", "深空尽头", "沿途的风", "留白", "远山灯火", "一个人的日落").mapIndexed { index, title ->
            VideoItem(
                id = "video-movie-$index",
                libraryId = "video-library-1",
                title = if (long && index == 0) "在漫长的旅途中听见远方的回声 · 导演剪辑加长版（附评论音轨）" else title,
                type = "Movie",
                overview = if (long && index == 0) {
                    "这份影像记录了漫长旅途里的每一道风景。从清晨的湖面到深夜的车站，从熟悉的城市到陌生的旷野。\n本简介用于检查详情页展开与收起。"
                } else if (index == 3) "" else "关于旅途、记忆与远方的故事。",
                year = 2018 + index,
                durationSeconds = 5400 + index * 240,
                playbackPositionSeconds = if (index == 0) 1260 else 0,
                isPlayed = index == 4,
                communityRating = if (index < 3) 8.4f - index * 0.3f else null,
                imageUrl = if (noArt) null else covers[index % covers.size],
                backdropImageUrl = if (noArt) null else covers[index % covers.size],
                streamUrl = "preview://video/$index",
                sourceId = "ui-samples"
            )
        }
    }
    val series = remember(request.state) {
        VideoItem(
            id = "video-series-1",
            libraryId = "video-library-1",
            title = if (long) "漫长季节 · 全季导演剪辑合集" else "漫长季节",
            type = "Series",
            overview = if (long) "一座北方小城的记忆被重新打开。本简介用于检查剧集详情的长文本展开。" else "一座北方小城的记忆。",
            year = 2023,
            imageUrl = if (noArt) null else covers[1],
            backdropImageUrl = if (noArt) null else covers[1],
            sourceId = "ui-samples"
        )
    }
    val seriesAllWatched = request.screen == UiSampleScreen.VideoSeries && request.state == UiSampleState.Empty
    val episodes = remember(request.state) {
        listOf("序章", "回声", "长夜", "远山", "空白", "归途").mapIndexed { index, name ->
            VideoItem(
                id = "video-ep-$index",
                libraryId = "video-library-1",
                title = if (long && index == 1) "一个拥有很长名字的分集标题用来检查两行省略" else name,
                type = "Episode",
                overview = "分集简介",
                durationSeconds = 2700,
                playbackPositionSeconds = if (index == 1) 840 else 0,
                isPlayed = seriesAllWatched || index == 0,
                seriesId = "video-series-1",
                seriesName = series.title,
                seasonNumber = 1,
                episodeNumber = index + 1,
                imageUrl = if (noArt) null else covers[index % covers.size],
                streamUrl = if (index == 5) null else "preview://episode/$index",
                sourceId = "ui-samples"
            )
        }
    }
    val catalog = remember(movies, series, request.state) { movies + series }
    val shownCatalog = when {
        (empty && request.screen != UiSampleScreen.VideoSearch) || libraryEmpty -> emptyList()
        loading && !hasCachedContent -> emptyList()
        error && !hasCachedContent -> emptyList()
        else -> catalog
    }
    val browseVideos = remember(shownCatalog) { browseCatalogVideos(shownCatalog) }
    val visibleVideos = remember(shownCatalog, searchQuery, typeFilter) {
        visibleBrowseVideos(shownCatalog, searchQuery, typeFilter)
    }
    val continueWatching = remember(shownCatalog) {
        shownCatalog.filter { it.playbackPositionSeconds > 0 && !it.isPlayed }
    }
    val topRated = remember(shownCatalog) { topRatedVideoShelf(shownCatalog) }
    val unplayed = remember(shownCatalog) { unplayedVideoShelf(shownCatalog) }
    val typeFilters = remember(shownCatalog) { visibleVideoTypeFilters(shownCatalog) }
    val standaloneError = when {
        error && !hasCachedContent -> "连接失败: 样例网络错误"
        else -> null
    }
    val running = loading || refreshing
    val detailVideo = when (screen) {
        UiSampleScreen.VideoSeries -> series
        UiSampleScreen.VideoDetail -> movies.first().let { movie ->
            if (request.state == UiSampleState.Empty) movie.copy(overview = "") else movie
        }
        else -> null
    }
    val related = if (screen == UiSampleScreen.VideoSeries) episodes else emptyList()

    BackHandler(enabled = screen != request.screen) {
        screen = request.screen
        onEvent("video-back")
    }

    if (detailVideo != null) {
        VideoDetailScreen(
            video = detailVideo,
            relatedEpisodes = related,
            playAction = resolveVideoDetailPlayAction(detailVideo),
            colorScheme = colors,
            onBack = { onNavigate(UiSampleScreen.Catalog); onEvent("video-back-home") },
            onPlay = { onEvent("video-play:${detailVideo.id}") },
            onPlayFromStart = { onEvent("video-play-start:${detailVideo.id}") },
            onPlayEpisode = { onEvent("video-play-episode:${it.id}") }
        )
        return
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        VideoHomeContent(
            header = {
                MediaPageHeader(
                    title = "视频",
                    subtitle = when {
                        error && hasCachedContent -> "刷新失败，先显示本地缓存"
                        refreshing -> "正在刷新，先显示本地缓存"
                        empty && request.screen != UiSampleScreen.VideoSearch -> "连接 Emby 后显示真实媒体库、海报和视频信息"
                        libraryEmpty -> "已连接 Emby"
                        searchQuery.isNotBlank() || typeFilter != VideoTypeFilter.All ->
                            "${visibleVideos.size} / ${browseVideos.size} 个匹配条目"
                        else -> "共 ${browseVideos.size} 个条目"
                    },
                    actions = buildList {
                        add(HeaderAction(Icons.Filled.Settings, "打开设置", fixed = true) {
                            onNavigate(UiSampleScreen.Modules)
                        })
                        if (ready) add(HeaderAction(Icons.Filled.Refresh, "刷新视频", enabled = !running) {
                            onEvent("video-refresh")
                        })
                    },
                    colorScheme = colors
                )
            },
            libraries = shownLibraries,
            selectedLibraryId = selectedLibraryId,
            videos = shownCatalog,
            visibleVideos = visibleVideos,
            browseVideos = browseVideos,
            continueWatching = continueWatching,
            topRated = topRated,
            unplayed = unplayed,
            searchExpanded = searchExpanded,
            searchQuery = searchQuery,
            selectedTypeFilter = typeFilter,
            typeFilters = typeFilters,
            isLoading = loading && !hasCachedContent,
            standaloneError = standaloneError,
            resetNotice = null,
            detailInvalidationNotice = null,
            ready = ready,
            colorScheme = colors,
            onSelectLibrary = { selectedLibraryId = it; onEvent("video-library:$it") },
            onToggleSearch = { searchExpanded = true; onEvent("video-search-open") },
            onSearchChange = { searchQuery = it; onEvent("video-search:$it") },
            onSearchCollapse = { searchQuery = ""; searchExpanded = false; onEvent("video-search-clear") },
            onFilterSelected = { typeFilter = it; onEvent("video-filter:${it.name}") },
            onOpenVideo = { item ->
                onEvent("video-open:${item.id}")
                screen = if (item.type.equals("Series", ignoreCase = true)) {
                    UiSampleScreen.VideoSeries
                } else {
                    UiSampleScreen.VideoDetail
                }
            },
            onRetry = { onEvent("retry:video_home") }
        )
    }
}
