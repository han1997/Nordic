package com.nordic.mediahub.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nordic.mediahub.data.AudiobookChapter
import com.nordic.mediahub.data.AudiobookCacheRepository
import com.nordic.mediahub.data.AudiobookShelfConfig
import com.nordic.mediahub.data.AudiobookShelfRepository
import com.nordic.mediahub.data.AudiobookItemDetail
import com.nordic.mediahub.data.AudiobookItemSummary
import com.nordic.mediahub.data.AudiobookLibrarySummary
import com.nordic.mediahub.data.ConfigRepository
import com.nordic.mediahub.data.cacheKey
import com.nordic.mediahub.data.formatCacheAge
import com.nordic.mediahub.data.isCacheFresh
import com.nordic.mediahub.data.isReadyForAudiobookSync
import com.nordic.mediahub.ui.theme.NordicAlpha
import com.nordic.mediahub.ui.theme.NordicShapes
import com.nordic.mediahub.ui.theme.NordicSpacing
import kotlinx.coroutines.launch

internal enum class AudiobookLibraryPage {
    Home,
    Detail
}

internal fun resolveAudiobookSelectedLibraryId(
    currentLibraryId: String?,
    libraries: List<AudiobookLibrarySummary>
): String? {
    return currentLibraryId
        ?.takeIf { selectedId -> libraries.any { library -> library.id == selectedId } }
        ?: libraries.firstOrNull()?.id
}

internal fun resolveAudiobookSelectedItemAfterLibraryRefresh(
    selectedItem: AudiobookItemDetail?,
    items: List<AudiobookItemSummary>
): AudiobookItemDetail? {
    val currentSelection = selectedItem ?: return null
    return currentSelection.takeIf { item ->
        items.any { summary -> summary.id == item.id }
    }
}

internal fun resolveAudiobookLibraryPageAfterRefresh(
    currentPage: AudiobookLibraryPage,
    previousSelectedItem: AudiobookItemDetail?,
    refreshedSelectedItem: AudiobookItemDetail?
): AudiobookLibraryPage {
    return if (
        currentPage == AudiobookLibraryPage.Detail &&
        previousSelectedItem != null &&
        refreshedSelectedItem == null
    ) {
        AudiobookLibraryPage.Home
    } else {
        currentPage
    }
}

internal fun resolveAudiobookLibraryPageAfterConfigChange(
    currentPage: AudiobookLibraryPage
): AudiobookLibraryPage {
    return when (currentPage) {
        AudiobookLibraryPage.Home,
        AudiobookLibraryPage.Detail -> AudiobookLibraryPage.Home
    }
}

internal fun sortAudiobookDetailChapters(chapters: List<AudiobookChapter>): List<AudiobookChapter> {
    return chapters
        .withIndex()
        .sortedWith(
            compareBy<IndexedValue<AudiobookChapter>> { indexed -> indexed.value.startSeconds }
                .thenBy { indexed -> indexed.index }
        )
        .map { indexed -> indexed.value }
}

@Composable
fun AudiobookScreen(
    colorScheme: ColorScheme,
    isDark: Boolean,
    onThemeToggle: (Boolean) -> Unit,
    onPlayAudiobook: (AudiobookItemSummary) -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { ConfigRepository(context) }
    val cacheRepository = remember { AudiobookCacheRepository(context) }
    val savedConfig by repository.audiobookConfig.collectAsStateWithLifecycle(AudiobookShelfConfig())
    var libraryPage by remember { mutableStateOf(AudiobookLibraryPage.Home) }
    var libraries by remember { mutableStateOf(emptyList<AudiobookLibrarySummary>()) }
    var selectedLibraryId by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf(emptyList<AudiobookItemSummary>()) }
    var selectedItem by remember { mutableStateOf<AudiobookItemDetail?>(null) }
    var loadingItemDetailId by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var cacheUpdatedAtMillis by remember { mutableStateOf<Long?>(null) }
    var audiobookConfigStateVersion by remember { mutableStateOf(0) }
    var previousAudiobookConfig by remember { mutableStateOf<AudiobookShelfConfig?>(null) }
    val scope = rememberCoroutineScope()

    val audiobookRepository = remember(savedConfig) {
        if (savedConfig.isReadyForAudiobookSync()) {
            AudiobookShelfRepository(savedConfig)
        } else {
            null
        }
    }

    fun isCurrentAudiobookConfigRequest(requestVersion: Int?): Boolean {
        return requestVersion == null || audiobookConfigStateVersion == requestVersion
    }

    fun resetAudiobookStateAfterConfigChange() {
        audiobookConfigStateVersion += 1
        libraryPage = resolveAudiobookLibraryPageAfterConfigChange(libraryPage)
        libraries = emptyList()
        selectedLibraryId = null
        items = emptyList()
        selectedItem = null
        loadingItemDetailId = null
        isLoading = false
        errorMessage = null
        cacheUpdatedAtMillis = null
    }

    suspend fun applyCachedAudiobooks(
        targetConfig: AudiobookShelfConfig,
        requestVersion: Int? = null
    ): Boolean {
        val cached = cacheRepository.load(targetConfig)
        if (!isCurrentAudiobookConfigRequest(requestVersion)) {
            return false
        }

        if (cached == null) {
            libraries = emptyList()
            selectedLibraryId = null
            items = emptyList()
            selectedItem = null
            cacheUpdatedAtMillis = null
            return false
        }

        libraries = cached.libraries
        selectedLibraryId = cached.selectedLibraryId
            ?: resolveAudiobookSelectedLibraryId(null, cached.libraries)
        items = cached.items
        selectedItem = null
        cacheUpdatedAtMillis = cached.updatedAtMillis
        errorMessage = null
        return true
    }

    suspend fun refreshAudiobooks(
        targetConfig: AudiobookShelfConfig = savedConfig,
        requestVersion: Int? = audiobookConfigStateVersion
    ) {
        if (!targetConfig.isReadyForAudiobookSync() || isLoading) return

        isLoading = true
        errorMessage = null
        try {
            val repo = if (targetConfig == savedConfig) {
                audiobookRepository ?: AudiobookShelfRepository(targetConfig)
            } else {
                AudiobookShelfRepository(targetConfig)
            }
            val loadedLibraries = repo.getLibraries()
            val resolvedLibraryId = resolveAudiobookSelectedLibraryId(selectedLibraryId, loadedLibraries)
            val refreshedItems = if (resolvedLibraryId == null) emptyList() else repo.getLibraryItems(resolvedLibraryId)
            if (!isCurrentAudiobookConfigRequest(requestVersion)) {
                return
            }

            val previousSelectedItem = selectedItem
            val refreshedSelectedItem = resolveAudiobookSelectedItemAfterLibraryRefresh(
                selectedItem = previousSelectedItem,
                items = refreshedItems
            )
            libraries = loadedLibraries
            selectedLibraryId = resolvedLibraryId
            items = refreshedItems
            selectedItem = refreshedSelectedItem
            loadingItemDetailId = null
            libraryPage = resolveAudiobookLibraryPageAfterRefresh(
                currentPage = libraryPage,
                previousSelectedItem = previousSelectedItem,
                refreshedSelectedItem = refreshedSelectedItem
            )
            val freshCache = cacheRepository.buildCache(
                config = targetConfig,
                libraries = loadedLibraries,
                items = refreshedItems,
                selectedLibraryId = resolvedLibraryId
            )
            cacheUpdatedAtMillis = freshCache.updatedAtMillis
            cacheRepository.save(targetConfig, freshCache)
        } catch (e: Exception) {
            if (isCurrentAudiobookConfigRequest(requestVersion)) {
                val hasCachedContent = libraries.isNotEmpty() || items.isNotEmpty()
                errorMessage = if (hasCachedContent) {
                    "正在显示上次缓存：${e.message ?: "未知错误"}"
                } else {
                    "连接失败: ${e.message ?: "未知错误"}"
                }
            }
        } finally {
            if (isCurrentAudiobookConfigRequest(requestVersion)) {
                isLoading = false
            }
        }
    }

    fun openItemDetail(item: AudiobookItemSummary) {
        val repo = audiobookRepository ?: return
        val requestVersion = audiobookConfigStateVersion
        libraryPage = AudiobookLibraryPage.Detail
        selectedItem = null
        loadingItemDetailId = item.id
        errorMessage = null
        isLoading = true
        scope.launch {
            // Cache-then-refresh: show cached detail first, then refresh in the
            // background and write the fresh detail back to the cache. Detail
            // caches have no TTL — opening always refreshes.
            val cachedDetail = cacheRepository.loadItemDetail(savedConfig, item.id)
            if (audiobookConfigStateVersion == requestVersion && loadingItemDetailId == item.id) {
                selectedItem = cachedDetail
            }
            try {
                val detail = repo.getLibraryItem(item.id)
                if (audiobookConfigStateVersion == requestVersion && loadingItemDetailId == item.id) {
                    selectedItem = detail
                    cacheRepository.saveItemDetail(savedConfig, item.id, detail)
                }
            } catch (e: Exception) {
                if (audiobookConfigStateVersion == requestVersion && loadingItemDetailId == item.id) {
                    errorMessage = if (cachedDetail != null) {
                        "正在显示上次缓存：${e.message ?: "未知错误"}"
                    } else {
                        e.message ?: "加载详情失败"
                    }
                }
            } finally {
                if (audiobookConfigStateVersion == requestVersion && loadingItemDetailId == item.id) {
                    isLoading = false
                    loadingItemDetailId = null
                }
            }
        }
    }

    LaunchedEffect(savedConfig) {
        val previousConfig = previousAudiobookConfig
        previousAudiobookConfig = savedConfig
        resetAudiobookStateAfterConfigChange()
        val requestVersion = audiobookConfigStateVersion
        // Clear the previous config's persisted cache so switching AudiobookShelf
        // accounts/servers does not leave dead cache JSON in DataStore.
        if (previousConfig != null && previousConfig.cacheKey() != savedConfig.cacheKey()) {
            cacheRepository.clear(previousConfig)
        }
        if (savedConfig.isReadyForAudiobookSync()) {
            applyCachedAudiobooks(savedConfig, requestVersion)
            // Launch-path refresh is TTL-gated; manual refresh (↻) bypasses TTL.
            if (!isCacheFresh(cacheUpdatedAtMillis)) {
                refreshAudiobooks(savedConfig, requestVersion)
            }
        }
    }

    fun navigateBackFromAudiobookPage() {
        libraryPage = AudiobookLibraryPage.Home
        errorMessage = null
    }

    BackHandler(enabled = libraryPage != AudiobookLibraryPage.Home) {
        navigateBackFromAudiobookPage()
    }

    val cacheAgeLabel = formatCacheAge(cacheUpdatedAtMillis)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(NordicSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(NordicSpacing.lg)
    ) {
        item {
            MediaPageHeader(
                title = if (libraryPage == AudiobookLibraryPage.Home) "有声书" else selectedItem?.title ?: "详情",
                subtitle = when (libraryPage) {
                    AudiobookLibraryPage.Home -> when {
                        isLoading && items.isNotEmpty() -> "正在刷新，先显示本地缓存"
                        cacheAgeLabel != null -> "本地缓存，$cacheAgeLabel"
                        selectedLibraryId != null -> "共 ${items.size} 本，点开查看章节和续播进度"
                        else -> "连接 AudiobookShelf 后自动加载书库"
                    }
                    AudiobookLibraryPage.Detail -> selectedItem?.authors?.joinToString(" / ").orEmpty()
                },
                actions = buildList {
                    if (savedConfig.isReadyForAudiobookSync()) {
                        add(
                            HeaderAction(
                                icon = Icons.Filled.Refresh,
                                contentDescription = "刷新有声书",
                                enabled = !isLoading,
                                onClick = { scope.launch { refreshAudiobooks() } }
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
                colorScheme = colorScheme,
                showBack = libraryPage != AudiobookLibraryPage.Home,
                onBack = ::navigateBackFromAudiobookPage
            )
        }
        if (errorMessage != null) {
            item {
                MediaStateCard(
                    title = "AudiobookShelf 错误",
                    subtitle = errorMessage.orEmpty(),
                    tone = MediaStateTone.Error
                )
            }
        }

        if (libraryPage == AudiobookLibraryPage.Home) {
            if (libraries.isNotEmpty()) {
                item {
                    AudiobookLibrarySelector(
                        libraries = libraries,
                        selectedLibraryId = selectedLibraryId,
                        colorScheme = colorScheme,
                        onSelect = { libraryId ->
                            selectedLibraryId = libraryId
                            val repo = audiobookRepository ?: return@AudiobookLibrarySelector
                            val requestVersion = audiobookConfigStateVersion
                            scope.launch {
                                isLoading = true
                                errorMessage = null
                                try {
                                    val loadedItems = repo.getLibraryItems(libraryId)
                                    if (audiobookConfigStateVersion == requestVersion && selectedLibraryId == libraryId) {
                                        items = loadedItems
                                        selectedItem = null
                                        libraryPage = AudiobookLibraryPage.Home
                                        loadingItemDetailId = null
                                    }
                                } catch (e: Exception) {
                                    if (audiobookConfigStateVersion == requestVersion && selectedLibraryId == libraryId) {
                                        errorMessage = e.message ?: "加载书库失败"
                                    }
                                } finally {
                                    if (audiobookConfigStateVersion == requestVersion && selectedLibraryId == libraryId) {
                                        isLoading = false
                                    }
                                }
                            }
                        }
                    )
                }
            }

            if (isLoading && items.isEmpty()) {
                item {
                    MediaLoadingCard(
                        title = "正在同步 AudiobookShelf",
                        subtitle = "加载书库、封面和续播进度..."
                    )
                }
            } else if (!savedConfig.isReadyForAudiobookSync()) {
                item {
                    MediaStateCard(
                        title = "先接入你的有声书书库",
                        subtitle = "填入 AudiobookShelf 地址、用户名和密码后，这里会显示真实书目、章节和续播进度。",
                        hint = "前往配置 tab 开始连接",
                    )
                }
            } else if (libraries.isEmpty() && !isLoading) {
                item {
                    MediaStateCard(
                        title = "没有可用书库",
                        subtitle = "已连接 AudiobookShelf，但当前账号下没有可访问的 audiobook library。",
                        hint = "检查服务器权限或书库类型",
                    )
                }
            } else if (items.isEmpty() && !isLoading) {
                item {
                    MediaStateCard(
                        title = "这个书库还没有内容",
                        subtitle = "已连接 AudiobookShelf，但当前书库里没有可展示的有声书条目。",
                        hint = "切换其他书库或回到服务端检查扫描结果",
                    )
                }
            } else {
                items(items, key = { it.id }, contentType = { "audiobook-summary-card" }) { item ->
                    AudiobookSummaryCard(
                        item = item,
                        colorScheme = colorScheme,
                        onOpen = { openItemDetail(item) },
                        onPlay = { onPlayAudiobook(item) }
                    )
                }
            }
        } else {
            val item = selectedItem
            when {
                isLoading && item == null -> {
                    item {
                        MediaLoadingCard(
                            title = "正在加载详情",
                            subtitle = "同步章节、简介和续播进度..."
                        )
                    }
                }
                item == null -> {
                    item {
                        MediaStateCard(
                            title = "未选中条目",
                            subtitle = "返回列表选择一本有声书。",
                            hint = "",
                            density = MediaStateDensity.Compact
                        )
                    }
                }
                else -> {
                    val detailChapters = sortAudiobookDetailChapters(item.chapters)
                    item {
                        AudiobookDetailHeader(
                            item = item,
                            colorScheme = colorScheme,
                            onPlay = {
                                val summary = items.firstOrNull { summary -> summary.id == item.id }
                                if (summary != null) {
                                    onPlayAudiobook(summary)
                                }
                            }
                        )
                    }
                    if (detailChapters.isNotEmpty()) {
                    item {
                        Text(
                            "章节",
                            style = MaterialTheme.typography.titleMedium,
                            color = colorScheme.onBackground
                        )
                    }
                        items(detailChapters, key = { it.id }, contentType = { "audiobook-chapter-row" }) { chapter ->
                            AudiobookChapterRow(chapter, colorScheme)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudiobookLibrarySelector(
    libraries: List<AudiobookLibrarySummary>,
    selectedLibraryId: String?,
    colorScheme: ColorScheme,
    onSelect: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(NordicSpacing.md)
    ) {
        items(
            items = libraries,
            key = { it.id },
            contentType = { "audiobook-library-chip" }
        ) { library ->
            val selected = library.id == selectedLibraryId
            Surface(
                color = if (selected) colorScheme.primary.copy(alpha = 0.16f) else colorScheme.surfaceVariant.copy(alpha = 0.56f),
                contentColor = if (selected) colorScheme.primary else colorScheme.onSurface,
                shape = NordicShapes.md,
                border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.06f)),
                modifier = Modifier.clickable { onSelect(library.id) }
            ) {
                Text(
                    library.name,
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
private fun AudiobookSummaryCard(
    item: AudiobookItemSummary,
    colorScheme: ColorScheme,
    onOpen: () -> Unit,
    onPlay: () -> Unit
) {
    Surface(
        color = colorScheme.surfaceVariant.copy(alpha = 0.42f),
        shape = NordicShapes.md,
        border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.045f)),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)
    ) {
        Row(
            modifier = Modifier.padding(NordicSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(NordicSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CoverArt(
                imageUrl = item.coverUrl,
                contentDescription = item.title,
                colorScheme = colorScheme,
                modifier = Modifier.size(72.dp),
                shape = NordicShapes.md,
                fallbackIcon = Icons.AutoMirrored.Filled.MenuBook
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(NordicSpacing.xs)
            ) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.author.isNotBlank()) {
                    Text(item.author, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Normal, color = colorScheme.onSurface.copy(alpha = NordicAlpha.medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val meta = remember(item) {
                    buildList {
                        if (item.narrator.isNotBlank()) add("播讲 ${item.narrator}")
                        if (item.chapterCount > 0) add("${item.chapterCount} 章")
                        if (item.durationSeconds > 0) add(formatDuration(item.durationSeconds))
                    }.joinToString("  •  ")
                }
                if (meta.isNotBlank()) {
                    Text(meta, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Normal, color = colorScheme.onSurface.copy(alpha = NordicAlpha.subtle), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Surface(
                color = colorScheme.primary,
                contentColor = colorScheme.onPrimary,
                shape = NordicShapes.full,
                modifier = Modifier.size(38.dp).clickable(onClick = onPlay)
            ) {
                Box(contentAlignment = Center) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "播放有声书",
                        tint = colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AudiobookDetailHeader(
    item: AudiobookItemDetail,
    colorScheme: ColorScheme,
    onPlay: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(NordicSpacing.lg)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(NordicSpacing.lg),
            verticalAlignment = Alignment.Top
        ) {
            CoverArt(
                imageUrl = item.coverUrl,
                contentDescription = item.title,
                colorScheme = colorScheme,
                modifier = Modifier.size(128.dp),
                shape = NordicShapes.md,
                fallbackIcon = Icons.AutoMirrored.Filled.MenuBook
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)
            ) {
                Text(item.title, style = MaterialTheme.typography.headlineMedium, color = colorScheme.onSurface)
                if (item.subtitle.isNotBlank()) {
                    Text(item.subtitle, style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurface.copy(alpha = NordicAlpha.medium))
                }
                if (item.authors.isNotEmpty()) {
                    Text(item.authors.joinToString(" / "), style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurface.copy(alpha = NordicAlpha.medium))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(NordicSpacing.sm)) {
                    MetaChip("${item.chapters.size} 章", colorScheme)
                    MetaChip(formatDuration(item.durationSeconds), colorScheme)
                }
                item.progress?.let { progress ->
                    MetaChip("续播 ${formatDuration(progress.currentTimeSeconds)}", colorScheme)
                }
                Surface(
                    color = colorScheme.primary,
                    contentColor = colorScheme.onPrimary,
                    shape = NordicShapes.full,
                    modifier = Modifier.height(36.dp).clickable(onClick = onPlay)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = NordicSpacing.lg),
                        contentAlignment = Center
                    ) {
                        Text("继续播放", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        if (item.description.isNotBlank()) {
            Surface(
                color = colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = NordicShapes.lg,
                border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.05f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(NordicSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)
                ) {
                    Text("简介", style = MaterialTheme.typography.titleMedium, color = colorScheme.onSurface)
                    Text(
                        item.description,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Normal,
                        lineHeight = 19.sp,
                        color = colorScheme.onSurface.copy(alpha = NordicAlpha.medium)
                    )
                }
            }
        }
    }
}

@Composable
private fun AudiobookChapterRow(chapter: AudiobookChapter, colorScheme: ColorScheme) {
    Surface(
        color = colorScheme.surfaceVariant.copy(alpha = 0.42f),
        shape = NordicShapes.md,
        border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.045f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = NordicSpacing.md, vertical = NordicSpacing.md),
            verticalArrangement = Arrangement.spacedBy(NordicSpacing.xs)
        ) {
            Text(chapter.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = colorScheme.onSurface)
            Text(
                "${formatDuration(chapter.startSeconds)} - ${formatDuration(chapter.endSeconds)}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Normal,
                color = colorScheme.onSurface.copy(alpha = NordicAlpha.subtle)
            )
        }
    }
}

