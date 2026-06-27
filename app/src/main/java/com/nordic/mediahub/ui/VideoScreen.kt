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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.nordic.mediahub.data.VideoItemFilter
import com.nordic.mediahub.data.VideoItemQuery
import com.nordic.mediahub.data.VideoLibrary
import com.nordic.mediahub.data.VideoProgress
import com.nordic.mediahub.data.VideoServerConfig
import com.nordic.mediahub.data.VideoSortOption
import com.nordic.mediahub.data.isReadyForVideoSync
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun VideoScreen(
    colorScheme: ColorScheme,
    isDark: Boolean,
    onThemeToggle: (Boolean) -> Unit,
    onShowVideoDetail: (VideoItem) -> Unit = {}
) {
    val context = LocalContext.current
    val configRepository = remember { ConfigRepository(context) }
    val savedConfig by configRepository.videoConfig.collectAsStateWithLifecycle(VideoServerConfig())
    var config by remember { mutableStateOf(VideoServerConfig()) }
    var showConfig by remember { mutableStateOf(false) }
    var libraries by remember { mutableStateOf(emptyList<VideoLibrary>()) }
    var selectedLibraryId by remember { mutableStateOf<String?>(null) }
    var videos by remember { mutableStateOf(emptyList<VideoItem>()) }
    var resumeItems by remember { mutableStateOf(emptyList<VideoItem>()) }
    var nextUpItems by remember { mutableStateOf(emptyList<VideoItem>()) }
    var itemQuery by remember { mutableStateOf(VideoItemQuery()) }
    var searchTerm by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf(emptyList<VideoItem>()) }
    var isSearching by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var searchError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val loadingCardIndexes = remember { List(3) { it } }

    val embyRepository = remember(savedConfig) {
        if (savedConfig.isReadyForVideoSync()) EmbyRepository(savedConfig) else null
    }

    fun updateItemEverywhere(itemId: String, transform: (VideoItem) -> VideoItem) {
        videos = videos.map { item -> if (item.id == itemId) transform(item) else item }
        resumeItems = resumeItems.map { item -> if (item.id == itemId) transform(item) else item }
        nextUpItems = nextUpItems.map { item -> if (item.id == itemId) transform(item) else item }
        searchResults = searchResults.map { item -> if (item.id == itemId) transform(item) else item }
    }

    fun toggleFavorite(item: VideoItem) {
        val repo = embyRepository ?: return
        val nextFavorite = !item.isFavorite
        scope.launch {
            runCatching { repo.setItemFavorite(item.id, nextFavorite) }
                .onSuccess {
                    updateItemEverywhere(item.id) { current -> current.copy(isFavorite = nextFavorite) }
                }
                .onFailure { error -> errorMessage = error.message ?: "Update favorite failed" }
        }
    }

    fun togglePlayed(item: VideoItem) {
        val repo = embyRepository ?: return
        val nextPlayed = item.progress?.isPlayed != true
        scope.launch {
            runCatching { repo.setItemPlayed(item.id, nextPlayed) }
                .onSuccess {
                    updateItemEverywhere(item.id) { current -> current.withPlayed(nextPlayed) }
                    if (nextPlayed) {
                        resumeItems = resumeItems.filterNot { it.id == item.id }
                    }
                }
                .onFailure { error -> errorMessage = error.message ?: "Update watched state failed" }
        }
    }

    suspend fun refreshVideo(
        targetConfig: VideoServerConfig = savedConfig,
        targetLibraryId: String? = selectedLibraryId,
        targetQuery: VideoItemQuery = itemQuery
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
            val catalog = repo.getCatalog(targetLibraryId, targetQuery)
            libraries = catalog.libraries
            selectedLibraryId = catalog.selectedLibraryId
            videos = catalog.items
            resumeItems = catalog.resumeItems
            nextUpItems = catalog.nextUpItems
        } catch (e: Exception) {
            errorMessage = e.message ?: "Connect to Emby failed"
        } finally {
            isLoading = false
        }
    }

    fun loadLibrary(libraryId: String, query: VideoItemQuery = itemQuery) {
        val repo = embyRepository ?: return
        selectedLibraryId = libraryId
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                videos = repo.getLibraryItems(libraryId, query)
            } catch (e: Exception) {
                errorMessage = e.message ?: "Load video list failed"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(savedConfig) {
        config = savedConfig
        if (savedConfig.isReadyForVideoSync()) {
            refreshVideo(savedConfig, selectedLibraryId, itemQuery)
        } else {
            libraries = emptyList()
            selectedLibraryId = null
            videos = emptyList()
            resumeItems = emptyList()
            nextUpItems = emptyList()
            searchResults = emptyList()
            errorMessage = null
            searchError = null
        }
    }

    LaunchedEffect(searchTerm, embyRepository) {
        val term = searchTerm.trim()
        searchError = null
        if (term.isBlank() || embyRepository == null) {
            searchResults = emptyList()
            isSearching = false
            return@LaunchedEffect
        }

        delay(350)
        isSearching = true
        runCatching { embyRepository?.searchVideos(term).orEmpty() }
            .onSuccess { results -> searchResults = results }
            .onFailure { error ->
                searchResults = emptyList()
                searchError = error.message ?: "Search failed"
            }
        isSearching = false
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
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
                        "Video",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        when {
                            isLoading && videos.isNotEmpty() -> "Refreshing Emby while keeping the current list visible"
                            selectedLibraryId != null -> "${videos.size} items in this library"
                            savedConfig.isReadyForVideoSync() -> "Connected to Emby"
                            else -> "Connect Emby to browse real video libraries"
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
                                    icon = if (isLoading) "..." else "R",
                                    enabled = !isLoading,
                                    onClick = { scope.launch { refreshVideo() } }
                                )
                            )
                        }
                        add(HeaderAction(if (isDark) "L" else "D") { onThemeToggle(!isDark) })
                        add(HeaderAction("Cfg") { showConfig = !showConfig })
                    }
                )
            }
        }

        item {
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
                            refreshVideo(config, selectedLibraryId, itemQuery)
                            if (errorMessage == null && config.isReadyForVideoSync()) {
                                showConfig = false
                            }
                        }
                    }
                )
            }
        }

        if (errorMessage != null) {
            item {
                VideoMessageCard(
                    title = "Emby error",
                    subtitle = errorMessage.orEmpty(),
                    isError = true
                )
            }
        }

        if (savedConfig.isReadyForVideoSync()) {
            item {
                VideoSearchBar(
                    searchTerm = searchTerm,
                    isSearching = isSearching,
                    colorScheme = colorScheme,
                    onSearchTermChange = { searchTerm = it },
                    onClear = {
                        searchTerm = ""
                        searchResults = emptyList()
                        searchError = null
                    }
                )
            }
        }

        if (searchTerm.isNotBlank()) {
            item {
                VideoSearchResultsSection(
                    items = searchResults,
                    isSearching = isSearching,
                    errorMessage = searchError,
                    colorScheme = colorScheme,
                    onItemClick = onShowVideoDetail,
                    onToggleFavorite = ::toggleFavorite,
                    onTogglePlayed = ::togglePlayed
                )
            }
        }

        if (libraries.isNotEmpty()) {
            item {
                VideoLibrarySelector(
                    libraries = libraries,
                    selectedLibraryId = selectedLibraryId,
                    colorScheme = colorScheme,
                    onSelect = { libraryId -> loadLibrary(libraryId, itemQuery) }
                )
            }
            item {
                VideoLibraryControls(
                    query = itemQuery,
                    colorScheme = colorScheme,
                    onQueryChange = { nextQuery ->
                        itemQuery = nextQuery
                        selectedLibraryId?.let { libraryId -> loadLibrary(libraryId, nextQuery) }
                    }
                )
            }
        }

        if (resumeItems.isNotEmpty()) {
            item {
                VideoShelfSection(
                    title = "Continue Watching",
                    subtitle = "${resumeItems.size} unfinished",
                    items = resumeItems,
                    colorScheme = colorScheme,
                    onItemClick = onShowVideoDetail
                )
            }
        }

        if (nextUpItems.isNotEmpty()) {
            item {
                VideoShelfSection(
                    title = "Next Up",
                    subtitle = "${nextUpItems.size} episodes",
                    items = nextUpItems,
                    colorScheme = colorScheme,
                    onItemClick = onShowVideoDetail
                )
            }
        }

        when {
            isLoading && videos.isEmpty() -> {
                itemsIndexed(
                    items = loadingCardIndexes,
                    contentType = { _, _ -> "video-loading-card" }
                ) { index, _ ->
                    VideoLoadingCard(index = index, colorScheme = colorScheme)
                }
            }

            !savedConfig.isReadyForVideoSync() -> {
                item {
                    VideoMessageCard(
                        title = "Connect your Emby server",
                        subtitle = "Add a server URL and API key or username/password to browse video libraries."
                    )
                }
            }

            libraries.isEmpty() && !isLoading -> {
                item {
                    VideoMessageCard(
                        title = "No video libraries",
                        subtitle = "Emby is connected, but this user has no browsable video libraries."
                    )
                }
            }

            videos.isEmpty() && !isLoading -> {
                item {
                    VideoMessageCard(
                        title = "No items here",
                        subtitle = "Try another library, sort mode, or filter."
                    )
                }
            }

            else -> {
                items(videos, key = { it.id }, contentType = { "video-card" }) { video ->
                    VideoCard(
                        video = video,
                        colorScheme = colorScheme,
                        onClick = { onShowVideoDetail(video) },
                        onToggleFavorite = { toggleFavorite(video) },
                        onTogglePlayed = { togglePlayed(video) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoSearchBar(
    searchTerm: String,
    isSearching: Boolean,
    colorScheme: ColorScheme,
    onSearchTermChange: (String) -> Unit,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = searchTerm,
            onValueChange = onSearchTermChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(if (isSearching) "Searching..." else "Search movies, shows, episodes") },
            singleLine = true
        )
        if (searchTerm.isNotBlank()) {
            VideoActionChip(
                text = "Clear search",
                selected = false,
                colorScheme = colorScheme,
                onClick = onClear
            )
        }
    }
}

@Composable
private fun VideoSearchResultsSection(
    items: List<VideoItem>,
    isSearching: Boolean,
    errorMessage: String?,
    colorScheme: ColorScheme,
    onItemClick: (VideoItem) -> Unit,
    onToggleFavorite: (VideoItem) -> Unit,
    onTogglePlayed: (VideoItem) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "Search Results",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onBackground
        )
        when {
            errorMessage != null -> VideoMessageCard("Search failed", errorMessage, isError = true)
            isSearching -> VideoLoadingCard(index = 0, colorScheme = colorScheme)
            items.isEmpty() -> VideoMessageCard("No search results", "Try a different title or episode name.")
            else -> {
                items.groupBy { item -> item.type.ifBlank { "Video" } }.forEach { (type, groupItems) ->
                    Text(
                        type,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface.copy(alpha = 0.62f)
                    )
                    groupItems.forEach { item ->
                        VideoCard(
                            video = item,
                            colorScheme = colorScheme,
                            onClick = { onItemClick(item) },
                            onToggleFavorite = { onToggleFavorite(item) },
                            onTogglePlayed = { onTogglePlayed(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoLibraryControls(
    query: VideoItemQuery,
    colorScheme: ColorScheme,
    onQueryChange: (VideoItemQuery) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(VideoSortOption.values().toList(), key = { it.name }) { sort ->
                VideoActionChip(
                    text = sort.label(),
                    selected = query.sort == sort,
                    colorScheme = colorScheme,
                    onClick = { onQueryChange(query.copy(sort = sort)) }
                )
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(VideoItemFilter.values().toList(), key = { it.name }) { filter ->
                VideoActionChip(
                    text = filter.label(),
                    selected = query.filter == filter,
                    colorScheme = colorScheme,
                    onClick = { onQueryChange(query.copy(filter = filter)) }
                )
            }
        }
    }
}

@Composable
private fun VideoShelfSection(
    title: String,
    subtitle: String,
    items: List<VideoItem>,
    colorScheme: ColorScheme,
    onItemClick: (VideoItem) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onBackground
            )
            Text(
                subtitle,
                fontSize = 12.sp,
                color = colorScheme.onSurface.copy(alpha = 0.52f)
            )
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = items,
                key = { item -> "$title-${item.id}" },
                contentType = { "video-shelf-card" }
            ) { item ->
                VideoResumeCard(
                    video = item,
                    colorScheme = colorScheme,
                    onClick = { onItemClick(item) }
                )
            }
        }
    }
}

@Composable
private fun VideoResumeCard(
    video: VideoItem,
    colorScheme: ColorScheme,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interactionSource)
    val progress = video.progress
    val progressFraction = video.resumeProgressFraction()

    Surface(
        color = colorScheme.surfaceVariant.copy(alpha = 0.46f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.045f)),
        modifier = Modifier
            .width(220.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Column {
            Box {
                VideoThumbnail(
                    imageUrl = video.imageUrl,
                    title = video.title,
                    colorScheme = colorScheme,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                )
                if (progressFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(colorScheme.onSurface.copy(alpha = 0.18f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressFraction)
                                .height(4.dp)
                                .background(colorScheme.primary)
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    video.title,
                    fontSize = 14.sp,
                    color = colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    video.progressLabel(progress),
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
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTogglePlayed: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interactionSource)
    val isPlayed = video.progress?.isPlayed == true

    Surface(
        color = colorScheme.surfaceVariant.copy(alpha = 0.42f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.045f)),
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Column {
            VideoThumbnail(
                imageUrl = video.imageUrl,
                title = video.title,
                colorScheme = colorScheme,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            )
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    video.title,
                    fontSize = 16.sp,
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
                if (video.overview.isNotBlank()) {
                    Text(
                        video.overview,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = colorScheme.onSurface.copy(alpha = 0.62f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VideoActionChip(
                        text = if (video.isFavorite) "Favorited" else "Favorite",
                        selected = video.isFavorite,
                        colorScheme = colorScheme,
                        onClick = onToggleFavorite
                    )
                    VideoActionChip(
                        text = if (isPlayed) "Watched" else "Unwatched",
                        selected = isPlayed,
                        colorScheme = colorScheme,
                        onClick = onTogglePlayed
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoActionChip(
    text: String,
    selected: Boolean,
    colorScheme: ColorScheme,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) colorScheme.primary.copy(alpha = 0.16f) else colorScheme.surfaceVariant.copy(alpha = 0.6f),
        contentColor = if (selected) colorScheme.primary else colorScheme.onSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.06f)),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
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
        delay(index * 50L)
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
                        .aspectRatio(16f / 9f)
                        .background(colorScheme.surface.copy(alpha = 0.34f))
                )
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(180.dp)
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

private fun VideoSortOption.label(): String {
    return when (this) {
        VideoSortOption.DateAdded -> "Date Added"
        VideoSortOption.Name -> "Name"
        VideoSortOption.Year -> "Year"
        VideoSortOption.LastPlayed -> "Last Played"
    }
}

private fun VideoItemFilter.label(): String {
    return when (this) {
        VideoItemFilter.All -> "All"
        VideoItemFilter.Unplayed -> "Unplayed"
        VideoItemFilter.Played -> "Played"
        VideoItemFilter.Favorite -> "Favorites"
    }
}

private fun VideoItem.metaText(): String {
    return buildList {
        type.takeIf { it.isNotBlank() }?.let { add(it) }
        year?.let { add(it.toString()) }
        if (durationSeconds > 0) add(formatVideoDuration(durationSeconds))
        if (isFavorite) add("Favorite")
        if (progress?.isPlayed == true) add("Watched")
    }.joinToString("  /  ")
}

private fun VideoItem.resumeProgressFraction(): Float {
    val progress = progress ?: return 0f
    val percentFraction = (progress.playedPercentage / 100f).takeIf { it > 0f }
    val durationFraction = if (durationSeconds > 0) {
        progress.currentTimeSeconds.toFloat() / durationSeconds.toFloat()
    } else {
        0f
    }
    return (percentFraction ?: durationFraction).coerceIn(0f, 1f)
}

private fun VideoItem.progressLabel(progress: VideoProgress?): String {
    return when {
        (progress?.currentTimeSeconds ?: 0) > 0 -> buildString {
            append("Resume ")
            append(formatDuration(progress?.currentTimeSeconds ?: 0))
            if (durationSeconds > 0) {
                append(" / ")
                append(formatDuration(durationSeconds))
            }
        }
        progress?.isPlayed == true -> "Watched"
        else -> metaText().ifBlank { "Ready to play" }
    }
}

private fun VideoItem.withPlayed(played: Boolean): VideoItem {
    val nextProgress = (progress ?: VideoProgress()).copy(
        currentTimeSeconds = if (played) durationSeconds else 0,
        playedPercentage = if (played) 100f else 0f,
        isPlayed = played
    )
    return copy(progress = nextProgress)
}
