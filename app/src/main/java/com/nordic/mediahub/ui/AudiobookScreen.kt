package com.nordic.mediahub.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.nordic.mediahub.data.AudiobookChapter
import com.nordic.mediahub.data.AudiobookShelfConfig
import com.nordic.mediahub.data.AudiobookShelfRepository
import com.nordic.mediahub.data.AudiobookItemDetail
import com.nordic.mediahub.data.AudiobookItemSummary
import com.nordic.mediahub.data.AudiobookLibrarySummary
import com.nordic.mediahub.data.ConfigRepository
import com.nordic.mediahub.data.isReadyForAudiobookSync
import kotlinx.coroutines.launch

private enum class AudiobookLibraryPage {
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

@Composable
fun AudiobookScreen(
    colorScheme: ColorScheme,
    isDark: Boolean,
    onThemeToggle: (Boolean) -> Unit,
    onPlayAudiobook: (AudiobookItemSummary) -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { ConfigRepository(context) }
    val savedConfig by repository.audiobookConfig.collectAsStateWithLifecycle(AudiobookShelfConfig())
    var config by remember { mutableStateOf(AudiobookShelfConfig()) }
    var showConfig by remember { mutableStateOf(false) }
    var libraryPage by remember { mutableStateOf(AudiobookLibraryPage.Home) }
    var libraries by remember { mutableStateOf(emptyList<AudiobookLibrarySummary>()) }
    var selectedLibraryId by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf(emptyList<AudiobookItemSummary>()) }
    var selectedItem by remember { mutableStateOf<AudiobookItemDetail?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val audiobookRepository = remember(savedConfig) {
        if (savedConfig.isReadyForAudiobookSync()) {
            AudiobookShelfRepository(savedConfig)
        } else {
            null
        }
    }

    LaunchedEffect(savedConfig) { config = savedConfig }

    suspend fun refreshAudiobooks(targetConfig: AudiobookShelfConfig = config) {
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
            libraries = loadedLibraries
            val resolvedLibraryId = resolveAudiobookSelectedLibraryId(selectedLibraryId, loadedLibraries)
            selectedLibraryId = resolvedLibraryId
            items = if (resolvedLibraryId == null) emptyList() else repo.getLibraryItems(resolvedLibraryId)
        } catch (e: Exception) {
            errorMessage = e.message ?: "连接 AudiobookShelf 失败"
        } finally {
            isLoading = false
        }
    }

    fun openItemDetail(item: AudiobookItemSummary) {
        val repo = audiobookRepository ?: return
        libraryPage = AudiobookLibraryPage.Detail
        selectedItem = null
        errorMessage = null
        scope.launch {
            isLoading = true
            try {
                selectedItem = repo.getLibraryItem(item.id)
            } catch (e: Exception) {
                errorMessage = e.message ?: "加载详情失败"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(savedConfig.serverUrl, savedConfig.username, savedConfig.password) {
        if (savedConfig.isReadyForAudiobookSync()) {
            refreshAudiobooks(savedConfig)
        } else {
            libraries = emptyList()
            items = emptyList()
            selectedLibraryId = null
            selectedItem = null
            libraryPage = AudiobookLibraryPage.Home
            errorMessage = null
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                if (libraryPage != AudiobookLibraryPage.Home) {
                    AudiobookBackButton(colorScheme) {
                        libraryPage = AudiobookLibraryPage.Home
                        errorMessage = null
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        if (libraryPage == AudiobookLibraryPage.Home) "有声书" else selectedItem?.title ?: "详情",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        when (libraryPage) {
                            AudiobookLibraryPage.Home -> when {
                                isLoading && items.isNotEmpty() -> "正在刷新，先显示当前书库"
                                selectedLibraryId != null -> "共 ${items.size} 本，点开查看章节和续播进度"
                                else -> "连接 AudiobookShelf 后自动加载书库"
                            }
                            AudiobookLibraryPage.Detail -> selectedItem?.authors?.joinToString(" / ").orEmpty()
                        },
                        fontSize = 14.sp,
                        color = colorScheme.onSurface.copy(alpha = 0.62f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                HeaderActionGroup(
                    actions = buildList {
                        if (config.isReadyForAudiobookSync()) {
                            add(
                                HeaderAction(
                                    icon = if (isLoading) "…" else "↻",
                                    enabled = !isLoading,
                                    onClick = { scope.launch { refreshAudiobooks() } }
                                )
                            )
                        }
                        add(HeaderAction(if (isDark) "☀" else "☾") { onThemeToggle(!isDark) })
                        add(HeaderAction("⚙") { showConfig = !showConfig })
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
                AudiobookConfigCard(config, colorScheme,
                    onConfigChange = { config = it },
                    onSave = {
                        scope.launch {
                            repository.saveAudiobookConfig(config)
                            refreshAudiobooks(config)
                            if (errorMessage == null) {
                                showConfig = false
                            }
                        }
                    }
                )
            }
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
                            scope.launch {
                                isLoading = true
                                errorMessage = null
                                try {
                                    items = repo.getLibraryItems(libraryId)
                                } catch (e: Exception) {
                                    errorMessage = e.message ?: "加载书库失败"
                                } finally {
                                    isLoading = false
                                }
                            }
                        }
                    )
                }
            }

            if (isLoading && items.isEmpty()) {
                item {
                    AudiobookLoadingCard(
                        title = "正在同步 AudiobookShelf",
                        subtitle = "加载书库、封面和续播进度..."
                    )
                }
            } else if (!config.isReadyForAudiobookSync()) {
                item {
                    AudiobookEmptyState(
                        title = "先接入你的有声书书库",
                        subtitle = "填入 AudiobookShelf 地址、用户名和密码后，这里会显示真实书目、章节和续播进度。",
                        hint = "点右上角设置开始连接",
                    )
                }
            } else if (libraries.isEmpty() && !isLoading) {
                item {
                    AudiobookEmptyState(
                        title = "没有可用书库",
                        subtitle = "已连接 AudiobookShelf，但当前账号下没有可访问的 audiobook library。",
                        hint = "检查服务器权限或书库类型",
                    )
                }
            } else if (items.isEmpty() && !isLoading) {
                item {
                    AudiobookEmptyState(
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
                        AudiobookLoadingCard(
                            title = "正在加载详情",
                            subtitle = "同步章节、简介和续播进度..."
                        )
                    }
                }
                item == null -> {
                    item {
                        AudiobookEmptyState(
                            title = "未选中条目",
                            subtitle = "返回列表选择一本有声书。",
                            hint = "",
                        )
                    }
                }
                else -> {
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
                    if (item.chapters.isNotEmpty()) {
                        item {
                            Text(
                                "章节",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onBackground
                            )
                        }
                        items(item.chapters, key = { it.id }, contentType = { "audiobook-chapter-row" }) { chapter ->
                            AudiobookChapterRow(chapter, colorScheme)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudiobookBackButton(colorScheme: ColorScheme, onClick: () -> Unit) {
    Surface(
        color = colorScheme.surfaceVariant.copy(alpha = 0.56f),
        contentColor = colorScheme.onSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.06f)),
        modifier = Modifier
            .height(42.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 13.dp),
            contentAlignment = Center
        ) {
            Text("‹", fontSize = 26.sp, color = colorScheme.onSurface.copy(alpha = 0.74f), fontWeight = FontWeight.SemiBold)
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
        horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.06f)),
                modifier = Modifier.clickable { onSelect(library.id) }
            ) {
                Text(
                    library.name,
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
private fun AudiobookSummaryCard(
    item: AudiobookItemSummary,
    colorScheme: ColorScheme,
    onOpen: () -> Unit,
    onPlay: () -> Unit
) {
    Surface(
        color = colorScheme.surfaceVariant.copy(alpha = 0.42f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.045f)),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AudiobookCover(
                coverUrl = item.coverUrl,
                contentDescription = item.title,
                colorScheme = colorScheme,
                modifier = Modifier.size(72.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    item.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.author.isNotBlank()) {
                    Text(item.author, fontSize = 13.sp, color = colorScheme.onSurface.copy(alpha = 0.66f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val meta = buildList {
                    if (item.narrator.isNotBlank()) add("播讲 ${item.narrator}")
                    if (item.chapterCount > 0) add("${item.chapterCount} 章")
                    if (item.durationSeconds > 0) add(formatDuration(item.durationSeconds))
                }.joinToString("  •  ")
                if (meta.isNotBlank()) {
                    Text(meta, fontSize = 12.sp, color = colorScheme.onSurface.copy(alpha = 0.5f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Surface(
                color = colorScheme.primary,
                contentColor = colorScheme.onPrimary,
                shape = CircleShape,
                modifier = Modifier.size(38.dp).clickable(onClick = onPlay)
            ) {
                Box(contentAlignment = Center) {
                    Text("▶", fontSize = 16.sp, color = colorScheme.onPrimary)
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
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            AudiobookCover(
                coverUrl = item.coverUrl,
                contentDescription = item.title,
                colorScheme = colorScheme,
                modifier = Modifier.size(128.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(item.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colorScheme.onSurface)
                if (item.subtitle.isNotBlank()) {
                    Text(item.subtitle, fontSize = 14.sp, color = colorScheme.onSurface.copy(alpha = 0.64f))
                }
                if (item.authors.isNotEmpty()) {
                    Text(item.authors.joinToString(" / "), fontSize = 14.sp, color = colorScheme.onSurface.copy(alpha = 0.68f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AudiobookMetaChip("${item.chapters.size} 章", colorScheme)
                    AudiobookMetaChip(formatDuration(item.durationSeconds), colorScheme)
                }
                item.progress?.let { progress ->
                    AudiobookMetaChip("续播 ${formatDuration(progress.currentTimeSeconds)}", colorScheme)
                }
                Surface(
                    color = colorScheme.primary,
                    contentColor = colorScheme.onPrimary,
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.height(36.dp).clickable(onClick = onPlay)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        contentAlignment = Center
                    ) {
                        Text("继续播放", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        if (item.description.isNotBlank()) {
            Surface(
                color = colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.05f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("简介", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colorScheme.onSurface)
                    Text(
                        item.description,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = colorScheme.onSurface.copy(alpha = 0.66f)
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
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.045f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(chapter.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colorScheme.onSurface)
            Text(
                "${formatDuration(chapter.startSeconds)} - ${formatDuration(chapter.endSeconds)}",
                fontSize = 12.sp,
                color = colorScheme.onSurface.copy(alpha = 0.54f)
            )
        }
    }
}

@Composable
private fun AudiobookEmptyState(
    title: String,
    subtitle: String,
    hint: String
) {
    MediaStateCard(
        title = title,
        subtitle = subtitle,
        hint = hint
    )
}

@Composable
private fun AudiobookLoadingCard(title: String, subtitle: String) {
    MediaLoadingCard(title = title, subtitle = subtitle)
}

@Composable
private fun AudiobookCover(
    coverUrl: String?,
    contentDescription: String,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier
) {
    var imageFailed by remember(coverUrl) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        colorScheme.primary.copy(alpha = 0.22f),
                        colorScheme.secondary.copy(alpha = 0.16f)
                    )
                )
            ),
        contentAlignment = Center
    ) {
        if (coverUrl != null && !imageFailed) {
            AsyncImage(
                model = coverUrl,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                onError = { imageFailed = true }
            )
        } else {
            Text("▤", fontSize = 24.sp, color = colorScheme.primary.copy(alpha = 0.72f))
        }
    }
}

@Composable
private fun AudiobookMetaChip(text: String, colorScheme: ColorScheme) {
    Surface(
        color = colorScheme.surfaceVariant.copy(alpha = 0.62f),
        contentColor = colorScheme.onSurface,
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            fontSize = 11.sp,
            color = colorScheme.onSurface.copy(alpha = 0.62f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
