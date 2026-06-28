package com.nordic.mediahub.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
    var searchQuery by remember { mutableStateOf("") }
    var selectedTypeFilter by remember { mutableStateOf(VideoTypeFilter.All) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
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

    val embyRepository = remember(savedConfig) {
        if (savedConfig.isReadyForVideoSync()) EmbyRepository(savedConfig) else null
    }

    suspend fun refreshVideo(
        targetConfig: VideoServerConfig = savedConfig,
        targetLibraryId: String? = selectedLibraryId
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
            libraries = catalog.libraries
            selectedLibraryId = catalog.selectedLibraryId
            videos = catalog.items
        } catch (e: Exception) {
            errorMessage = e.message ?: "连接 Emby 失败"
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(savedConfig) {
        config = savedConfig
        if (savedConfig.isReadyForVideoSync()) {
            refreshVideo(savedConfig, selectedLibraryId)
        } else {
                            libraries = emptyList()
            selectedLibraryId = null
            videos = emptyList()
            searchQuery = ""
            selectedTypeFilter = VideoTypeFilter.All
            errorMessage = null
        }
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
                            refreshVideo(config, selectedLibraryId)
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
                    colorScheme = colorScheme,
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
                        searchQuery = ""
                        selectedTypeFilter = VideoTypeFilter.All
                        val repo = embyRepository ?: return@VideoLibrarySelector
                        scope.launch {
                            isLoading = true
                            errorMessage = null
                            try {
                                videos = repo.getLibraryItems(libraryId)
                            } catch (e: Exception) {
                                errorMessage = e.message ?: "加载视频列表失败"
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                )
            }
        }

        if (videos.isNotEmpty()) {
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
                        subtitle = "填写服务器地址，并使用 API Key 或用户名密码登录。这里会显示真实媒体库和视频缩略图。",
                        colorScheme = colorScheme
                    )
                }
            }

            libraries.isEmpty() && !isLoading -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    VideoMessageCard(
                        title = "没有可用视频媒体库",
                        subtitle = "Emby 已连接，但当前用户没有可浏览的电影、剧集或家庭视频媒体库。",
                        colorScheme = colorScheme
                    )
                }
            }

            videos.isEmpty() && !isLoading -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    VideoMessageCard(
                        title = "这个媒体库暂时没有内容",
                        subtitle = "切换其他媒体库，或回到 Emby 服务端检查扫描结果和用户权限。",
                        colorScheme = colorScheme
                    )
                }
            }

            visibleVideos.isEmpty() && !isLoading -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    VideoMessageCard(
                        title = "没有匹配的视频",
                        subtitle = "换一个关键词，或切换类型筛选查看这个媒体库中的其他内容。",
                        colorScheme = colorScheme
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
                        onClick = { onPlayVideo(video) }
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
    colorScheme: ColorScheme,
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
    }.joinToString("  /  ")
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

private enum class VideoTypeFilter(val label: String) {
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

private fun VideoItem.matchesSearch(query: String): Boolean {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isBlank()) return true

    return title.contains(normalizedQuery, ignoreCase = true) ||
        overview.contains(normalizedQuery, ignoreCase = true) ||
        type.contains(normalizedQuery, ignoreCase = true) ||
        year?.toString()?.contains(normalizedQuery) == true
}
