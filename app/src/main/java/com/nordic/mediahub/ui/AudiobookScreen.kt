package com.nordic.mediahub.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
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
import com.nordic.mediahub.ui.theme.NordicControlSizes
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

internal fun shouldShowAudiobookConfigResetNotice(
    previousConfigChanged: Boolean,
    libraryPage: AudiobookLibraryPage,
    selectedItem: AudiobookItemDetail?
): Boolean {
    return previousConfigChanged && (
        libraryPage != AudiobookLibraryPage.Home ||
            selectedItem != null
        )
}

internal fun shouldShowAudiobookDetailInvalidationNotice(
    currentPage: AudiobookLibraryPage,
    previousSelectedItem: AudiobookItemDetail?,
    refreshedSelectedItem: AudiobookItemDetail?
): Boolean {
    return currentPage == AudiobookLibraryPage.Detail &&
        previousSelectedItem != null &&
        refreshedSelectedItem == null
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

/**
 * Audiobook detail overview shares the music collection layout policy
 * (stacked on narrow/large-font widths, side-by-side with 128/160dp artwork
 * otherwise) by delegating to the tested music resolver.
 */
internal fun resolveAudiobookCollectionLayout(
    availableWidth: Dp,
    fontScale: Float
): MusicCollectionLayout = resolveMusicCollectionLayout(availableWidth, fontScale)

internal fun audiobookAuthorLabel(author: String?): String =
    author?.trim()?.takeIf { it.isNotEmpty() } ?: "未知作者"

@Composable
fun AudiobookScreen(
    colorScheme: ColorScheme,
    isDark: Boolean,
    onThemeToggle: (Boolean) -> Unit,
    onPlayAudiobook: (AudiobookItemSummary) -> Unit = {},
    onOpenSettings: () -> Unit = {}
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
    // Per-library item caches (stale-while-revalidate): switching libraries
    // renders from this map instantly, then a silent refresh updates in place.
    var itemsByLibrary by remember { mutableStateOf<Map<String, List<AudiobookItemSummary>>>(emptyMap()) }
    var libraryFetchedAt by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var audiobookConfigStateVersion by remember { mutableStateOf(0) }
    var previousAudiobookConfig by remember { mutableStateOf<AudiobookShelfConfig?>(null) }
    var audiobookResetNotice by remember { mutableStateOf<String?>(null) }
    var audiobookDetailInvalidationNotice by remember { mutableStateOf<String?>(null) }
    var audiobookLibraryRequestVersion by remember { mutableStateOf(0) }
    var audiobookDetailRequestVersion by remember { mutableStateOf(0) }
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
        audiobookResetNotice = null
        audiobookDetailInvalidationNotice = null
        audiobookLibraryRequestVersion += 1
        audiobookDetailRequestVersion += 1
        libraryPage = resolveAudiobookLibraryPageAfterConfigChange(libraryPage)
        libraries = emptyList()
        selectedLibraryId = null
        items = emptyList()
        selectedItem = null
        loadingItemDetailId = null
        itemsByLibrary = emptyMap()
        libraryFetchedAt = emptyMap()
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
            itemsByLibrary = emptyMap()
            libraryFetchedAt = emptyMap()
            cacheUpdatedAtMillis = null
            return false
        }

        libraries = cached.libraries
        selectedLibraryId = cached.selectedLibraryId
            ?: resolveAudiobookSelectedLibraryId(null, cached.libraries)
        itemsByLibrary = cached.itemsByLibrary
        libraryFetchedAt = cached.libraryFetchedAt
        // Restore the selected library's cached rows; the launch refresh
        // (TTL-gated) or a manual refresh updates them in place.
        items = cached.selectedLibraryId?.let { cached.itemsByLibrary[it] } ?: cached.items
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
            val shouldShowDetailInvalidationNotice = shouldShowAudiobookDetailInvalidationNotice(
                currentPage = libraryPage,
                previousSelectedItem = previousSelectedItem,
                refreshedSelectedItem = refreshedSelectedItem
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
            if (shouldShowDetailInvalidationNotice) {
                audiobookDetailInvalidationNotice = "这本有声书已不在刷新后的书库中，已返回书库列表。"
            }
            val mergedByLibrary = resolvedLibraryId?.let { itemsByLibrary + (it to refreshedItems) } ?: itemsByLibrary
            val mergedStamps = resolvedLibraryId?.let { libraryFetchedAt + (it to System.currentTimeMillis()) } ?: libraryFetchedAt
            val freshCache = cacheRepository.buildCache(
                config = targetConfig,
                libraries = loadedLibraries,
                items = refreshedItems,
                selectedLibraryId = resolvedLibraryId,
                itemsByLibrary = mergedByLibrary,
                libraryFetchedAt = mergedStamps
            )
            itemsByLibrary = mergedByLibrary
            libraryFetchedAt = mergedStamps
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
        audiobookDetailRequestVersion += 1
        val detailRequestVersion = audiobookDetailRequestVersion
        audiobookResetNotice = null
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
            if (audiobookConfigStateVersion == requestVersion &&
                audiobookDetailRequestVersion == detailRequestVersion &&
                loadingItemDetailId == item.id
            ) {
                selectedItem = cachedDetail
            }
            try {
                val detail = repo.getLibraryItem(item.id)
                if (audiobookConfigStateVersion == requestVersion &&
                    audiobookDetailRequestVersion == detailRequestVersion &&
                    loadingItemDetailId == item.id
                ) {
                    selectedItem = detail
                    cacheRepository.saveItemDetail(savedConfig, item.id, detail)
                }
            } catch (e: Exception) {
                if (audiobookConfigStateVersion == requestVersion &&
                    audiobookDetailRequestVersion == detailRequestVersion &&
                    loadingItemDetailId == item.id
                ) {
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
        val previousConfigChanged = previousConfig != null && previousConfig.cacheKey() != savedConfig.cacheKey()
        val shouldShowConfigResetNotice = shouldShowAudiobookConfigResetNotice(
            previousConfigChanged = previousConfigChanged,
            libraryPage = libraryPage,
            selectedItem = selectedItem
        )
        previousAudiobookConfig = savedConfig
        resetAudiobookStateAfterConfigChange()
        if (shouldShowConfigResetNotice) {
            audiobookResetNotice = "有声书配置已更新，已回到有声书首页。"
        }
        val requestVersion = audiobookConfigStateVersion
        // Clear the previous config's persisted cache so switching AudiobookShelf
        // accounts/servers does not leave dead cache JSON in DataStore.
        if (previousConfigChanged) {
            previousConfig?.takeIf { it.sourceId.isBlank() }?.let { cacheRepository.clear(it) }
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
        audiobookResetNotice = null
        audiobookDetailInvalidationNotice = null
        libraryPage = AudiobookLibraryPage.Home
        errorMessage = null
    }

    BackHandler(enabled = libraryPage != AudiobookLibraryPage.Home) {
        navigateBackFromAudiobookPage()
    }

    val cacheAgeLabel = formatCacheAge(cacheUpdatedAtMillis)
    val hasErrorContent = if (libraryPage == AudiobookLibraryPage.Detail) {
        selectedItem != null
    } else {
        items.isNotEmpty()
    }
    val refreshErrorSubtitle = mediaRefreshErrorSubtitle(errorMessage, hasErrorContent)
    val standaloneError = standaloneMediaError(errorMessage, hasErrorContent)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(NordicSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)
    ) {
        item {
            MediaPageHeader(
                title = if (libraryPage == AudiobookLibraryPage.Home) "有声书" else selectedItem?.title ?: "详情",
                subtitle = when (libraryPage) {
                    AudiobookLibraryPage.Home -> when {
                        refreshErrorSubtitle != null -> refreshErrorSubtitle
                        isLoading && items.isNotEmpty() -> "正在刷新，先显示本地缓存"
                        cacheAgeLabel != null -> "本地缓存，$cacheAgeLabel"
                        selectedLibraryId != null -> "共 ${items.size} 本"
                        else -> "连接 AudiobookShelf 后自动加载书库"
                    }
                    AudiobookLibraryPage.Detail -> refreshErrorSubtitle
                        ?: audiobookAuthorLabel(selectedItem?.authors?.joinToString(" / "))
                },
                actions = buildList {
                    add(
                        HeaderAction(
                            icon = Icons.Filled.Settings,
                            contentDescription = "打开设置",
                            fixed = true,
                            onClick = onOpenSettings
                        )
                    )
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
        if (standaloneError != null) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)) {
                    MediaStateCard(
                        title = "AudiobookShelf 错误",
                        subtitle = standaloneError,
                        hint = "检查配置或点击刷新重试",
                        tone = MediaStateTone.Error
                    )
                    // Same refresh path as the header action; the explicit button
                    // keeps retry reachable without hunting for the header icon.
                    SecondaryActionButton("重试", colorScheme, onClick = {
                        scope.launch { refreshAudiobooks() }
                    })
                }
            }
        }

        if (audiobookResetNotice != null) {
            item {
                MediaStateCard(
                    title = "已应用新的有声书配置",
                    subtitle = audiobookResetNotice.orEmpty(),
                    density = MediaStateDensity.Compact
                )
            }
        }

        if (audiobookDetailInvalidationNotice != null) {
            item {
                MediaStateCard(
                    title = "详情已更新",
                    subtitle = audiobookDetailInvalidationNotice.orEmpty(),
                    density = MediaStateDensity.Compact
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
                            audiobookResetNotice = null
                            audiobookDetailInvalidationNotice = null
                            audiobookLibraryRequestVersion += 1
                            val libraryRequestVersion = audiobookLibraryRequestVersion
                            selectedLibraryId = libraryId
                            selectedItem = null
                            loadingItemDetailId = null
                            errorMessage = null
                            // Stale-while-revalidate: render cached rows for
                            // this library instantly; only a library with no
                            // cached rows shows the loading full fetch.
                            val cachedItems = itemsByLibrary[libraryId]
                            if (cachedItems != null) {
                                items = cachedItems
                                libraryPage = AudiobookLibraryPage.Home
                            } else {
                                items = emptyList()
                            }
                            val repo = audiobookRepository ?: return@AudiobookLibrarySelector
                            val requestVersion = audiobookConfigStateVersion
                            scope.launch {
                                if (cachedItems == null) {
                                    isLoading = true
                                }
                                errorMessage = null
                                try {
                                    val loadedItems = repo.getLibraryItems(libraryId)
                                    if (audiobookConfigStateVersion == requestVersion &&
                                        audiobookLibraryRequestVersion == libraryRequestVersion &&
                                        selectedLibraryId == libraryId
                                    ) {
                                        items = loadedItems
                                        selectedItem = null
                                        libraryPage = AudiobookLibraryPage.Home
                                        loadingItemDetailId = null
                                        itemsByLibrary = itemsByLibrary + (libraryId to loadedItems)
                                        libraryFetchedAt = libraryFetchedAt +
                                            (libraryId to System.currentTimeMillis())
                                        cacheRepository.saveLibraryItems(savedConfig, libraryId, loadedItems)
                                        val updatedCache = cacheRepository.buildCache(
                                            config = savedConfig,
                                            libraries = libraries,
                                            items = loadedItems,
                                            selectedLibraryId = libraryId,
                                            itemsByLibrary = itemsByLibrary,
                                            libraryFetchedAt = libraryFetchedAt
                                        )
                                        cacheUpdatedAtMillis = updatedCache.updatedAtMillis
                                        cacheRepository.save(savedConfig, updatedCache)
                                    }
                                } catch (e: Exception) {
                                    if (audiobookConfigStateVersion == requestVersion &&
                                        audiobookLibraryRequestVersion == libraryRequestVersion &&
                                        selectedLibraryId == libraryId
                                    ) {
                                        // Cached rows stay visible; only a library
                                        // with nothing cached surfaces the error.
                                        if (items.isEmpty()) {
                                            errorMessage = "加载书库失败: ${e.message ?: "未知错误"}"
                                        }
                                    }
                                } finally {
                                    if (audiobookConfigStateVersion == requestVersion &&
                                        audiobookLibraryRequestVersion == libraryRequestVersion &&
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
            } else if (libraries.isEmpty() && !isLoading && standaloneError == null) {
                item {
                    MediaStateCard(
                        title = "没有可用书库",
                        subtitle = "已连接 AudiobookShelf，但当前账号下没有可访问的 audiobook library。",
                        hint = "检查服务器权限或书库类型",
                    )
                }
            } else if (items.isEmpty() && !isLoading && standaloneError == null) {
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
                                color = colorScheme.onSurface,
                                modifier = Modifier.semantics { heading() }
                            )
                        }
                        val currentChapter = item.progress?.let { progress ->
                            resolveCurrentAudiobookChapter(detailChapters, progress.currentTimeSeconds)
                        }
                        items(detailChapters, key = { it.id }, contentType = { "audiobook-chapter-row" }) { chapter ->
                            AudiobookChapterRow(chapter, colorScheme, isCurrent = chapter.id == currentChapter?.id)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AudiobookLibrarySelector(
    libraries: List<AudiobookLibrarySummary>,
    selectedLibraryId: String?,
    colorScheme: ColorScheme,
    onSelect: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(NordicSpacing.md)
    ) {
        items(
            items = libraries,
            key = { it.id },
            contentType = { "audiobook-library-chip" }
        ) { library ->
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
internal fun AudiobookSummaryCard(
    item: AudiobookItemSummary,
    colorScheme: ColorScheme,
    onOpen: () -> Unit,
    onPlay: () -> Unit
) {
    // The nested clickable surface consumes pointer events first, so a tap on the
    // play button does not trip the card's open-detail click.
    Surface(
        color = colorScheme.surfaceVariant.copy(alpha = 0.42f),
        shape = NordicShapes.md,
        border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.045f)),
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "打开详情", onClick = onOpen)
    ) {
        Row(
            modifier = Modifier.padding(NordicSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(NordicSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // The adjacent title already names this artwork; avoid duplicate TalkBack announcements.
            Box(Modifier.clearAndSetSemantics { }) {
                CoverArt(
                    imageUrl = item.coverUrl,
                    contentDescription = item.title,
                    colorScheme = colorScheme,
                    modifier = Modifier.size(72.dp),
                    shape = NordicShapes.md,
                    fallbackIcon = Icons.AutoMirrored.Filled.MenuBook
                )
            }
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
                Text(audiobookAuthorLabel(item.author), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Normal, color = colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val meta = remember(item) {
                    buildList {
                        if (item.narrator.isNotBlank()) add("播讲 ${item.narrator}")
                        if (item.chapterCount > 0) add("${item.chapterCount} 章")
                        if (item.durationSeconds > 0) add(formatDuration(item.durationSeconds))
                    }.joinToString("  •  ")
                }
                if (meta.isNotBlank()) {
                    Text(meta, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Normal, color = colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Surface(
                color = colorScheme.primary,
                contentColor = colorScheme.onPrimary,
                shape = NordicShapes.full,
                modifier = Modifier.size(NordicControlSizes.touchTarget)
                    .clickable(role = Role.Button, onClickLabel = "播放有声书", onClick = onPlay)
            ) {
                Box(contentAlignment = Center) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AudiobookDetailHeader(
    item: AudiobookItemDetail,
    colorScheme: ColorScheme,
    onPlay: () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val layout = resolveAudiobookCollectionLayout(maxWidth, LocalDensity.current.fontScale)
        val artwork: @Composable () -> Unit = {
            // Adjacent heading already names this artwork.
            Box(Modifier.clearAndSetSemantics { }) {
                CoverArt(
                    imageUrl = item.coverUrl,
                    contentDescription = item.title,
                    colorScheme = colorScheme,
                    modifier = Modifier.size(layout.artworkSize),
                    shape = NordicShapes.md,
                    fallbackIcon = Icons.AutoMirrored.Filled.MenuBook
                )
            }
        }
        val details: @Composable () -> Unit = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = if (layout.stacked) Alignment.CenterHorizontally else Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)
            ) {
                Text(item.title, style = MaterialTheme.typography.headlineMedium, color = colorScheme.onSurface,
                    textAlign = if (layout.stacked) TextAlign.Center else TextAlign.Start,
                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() })
                if (item.subtitle.isNotBlank()) {
                    Text(item.subtitle, style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant,
                        textAlign = if (layout.stacked) TextAlign.Center else TextAlign.Start,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text(audiobookAuthorLabel(item.authors.joinToString(" / ")),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                    textAlign = if (layout.stacked) TextAlign.Center else TextAlign.Start,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(NordicSpacing.sm,
                        if (layout.stacked) Alignment.CenterHorizontally else Alignment.Start),
                    verticalArrangement = Arrangement.spacedBy(NordicSpacing.xs)
                ) {
                    MetaChip("${item.chapters.size} 章", colorScheme)
                    MetaChip(formatDuration(item.durationSeconds), colorScheme)
                    item.progress?.let { progress ->
                        MetaChip("续播 ${formatDuration(progress.currentTimeSeconds)}", colorScheme)
                    }
                }
                PrimaryActionButton(
                    text = "继续播放",
                    colorScheme = colorScheme,
                    onClick = onPlay,
                    modifier = Modifier.widthIn(max = 360.dp)
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(NordicSpacing.lg)) {
            if (layout.stacked) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(NordicSpacing.lg)) {
                    artwork()
                    details()
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(NordicSpacing.xxl), verticalAlignment = Alignment.Top) {
                    artwork()
                    Box(Modifier.weight(1f)) { details() }
                }
            }
            if (item.description.isNotBlank()) {
                Surface(
                    color = colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = NordicShapes.lg,
                    border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.045f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(NordicSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)
                    ) {
                        Text("简介", style = MaterialTheme.typography.titleMedium, color = colorScheme.onSurface)
                        MusicCollectionDescription(item.id, item.description, colorScheme)
                    }
                }
            }
        }
    }
}

@Composable
internal fun AudiobookChapterRow(
    chapter: AudiobookChapter,
    colorScheme: ColorScheme,
    isCurrent: Boolean = false
) {
    Surface(
        color = if (isCurrent) colorScheme.primaryContainer else colorScheme.surfaceVariant.copy(alpha = 0.42f),
        contentColor = if (isCurrent) colorScheme.onPrimaryContainer else colorScheme.onSurface,
        shape = NordicShapes.md,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = NordicSpacing.md, vertical = NordicSpacing.md),
            verticalArrangement = Arrangement.spacedBy(NordicSpacing.xs)
        ) {
            Text(chapter.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                color = if (isCurrent) colorScheme.onPrimaryContainer else colorScheme.onSurface,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { if (isCurrent) selected = true })
            Text(
                "${formatDuration(chapter.startSeconds)} - ${formatDuration(chapter.endSeconds)}",
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Normal,
                color = if (isCurrent) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant
            )
        }
    }
}

