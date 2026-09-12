package com.nordic.mediahub.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nordic.mediahub.data.*
import com.nordic.mediahub.ui.theme.NordicControlSizes
import com.nordic.mediahub.ui.theme.NordicShapes
import com.nordic.mediahub.ui.theme.NordicSpacing
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WebDavScreen(config: VideoServerConfig, onPlay: (VideoItem) -> Unit, onPlayFromStart: (VideoItem) -> Unit, onEpisodeContext: (List<VideoItem>) -> Unit = {}) {
    val model: WebDavBrowserViewModel = viewModel(key = "webdav-${config.sourceId}")
    val state by model.state.collectAsStateWithLifecycle()
    val rows by model.visibleEntries.collectAsStateWithLifecycle()
    val query by model.query.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val preferences = LocalAppPreferences.current
    val context = LocalContext.current
    val repository = remember { ConfigRepository(context) }
    val scope = rememberCoroutineScope()
    var search by rememberSaveable { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<WebDavEntry?>(null) }
    var settingError by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val positions = remember { mutableMapOf<String, Pair<Int, Int>>() }
    val currentOnPlay by rememberUpdatedState(onPlay)
    val currentOnPlayFromStart by rememberUpdatedState(onPlayFromStart)
    val currentOnEpisodeContext by rememberUpdatedState(onEpisodeContext)
    LaunchedEffect(config) { model.configure(config) }
    DisposableEffect(model) { onDispose { model.cancelRequests() } }
    LaunchedEffect(state.path) { positions[state.path]?.let { listState.scrollToItem(it.first, it.second) } ?: listState.scrollToItem(0) }
    fun open(path: String) { positions[state.path] = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset; model.open(path) }
    fun play(entry: WebDavEntry, fromStart: Boolean = false) {
        model.play(entry, fromStart) { video, start, episodeContext ->
            currentOnEpisodeContext(episodeContext)
            if (start) currentOnPlayFromStart(video) else currentOnPlay(video)
        }
    }
    fun preference(change: (AppPreferences) -> AppPreferences) {
        scope.launch { runCatching { repository.updatePreferences(change) }.onFailure { settingError = "目录显示设置保存失败" } }
    }
    BackHandler(search || state.path != "/") {
        if (search) { search = false; model.query.value = "" } else open(webDavParent(state.path))
    }
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(NordicSpacing.content),
        verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)) {
        item(key = "header") {
            MediaPageHeader("视频", if (state.loading) "正在读取目录" else "${rows.size} 个项目 · ${formatCacheAge(state.fetchedAt) ?: "尚未刷新"}",
                listOf(HeaderAction(Icons.Filled.Search, "搜索当前目录", onClick = { search = !search }),
                    HeaderAction(Icons.Filled.Refresh, "刷新目录", onClick = model::refresh),
                    HeaderAction(Icons.AutoMirrored.Filled.Sort, "目录排序与显示", onClick = { showSort = true })), colors)
        }
        item(key = "path") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { open(if (state.path == "/") model.initialPath else webDavParent(state.path)) }) {
                    Icon(if (state.path == "/") Icons.Filled.Home else Icons.AutoMirrored.Filled.ArrowBack, if (state.path == "/") "打开起始目录" else "返回上层")
                }
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { open("/") }) { Text("根目录") }
                    val segments = state.path.trim('/').split('/').filter { it.isNotEmpty() }
                    segments.forEachIndexed { index, _ ->
                        val path = "/" + segments.take(index + 1).joinToString("/") + "/"
                        Text("/", color = colors.onSurfaceVariant)
                        TextButton(onClick = { open(path) }) { Text(model.displayPath(path).substringAfterLast('/')) }
                    }
                }
                IconButton(onClick = { model.favorite(state.path, model.displayPath(state.path).ifBlank { "根目录" }) }) {
                    Icon(if (state.favorites.any { it.path == state.path }) Icons.Filled.Star else Icons.Filled.StarBorder, "收藏当前目录", tint = colors.primary)
                }
            }
        }
        if (search) item(key = "search") {
            MediaSearchField(query, { model.query.value = it }, "搜索当前目录", "清空目录搜索", { model.query.value = "" }, colors)
        }
        if (state.loading || state.preparing) item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        val error = state.error ?: settingError
        if (error != null) item(key = "error") {
            MediaStateCard("读取提示", error, tone = MediaStateTone.Error, density = MediaStateDensity.Compact)
            TextButton(onClick = { settingError = null; model.refresh() }) { Text("重试") }
        }
        if (state.path == model.initialPath && query.isBlank()) {
            val continuing = state.progress.filter { !it.completed && it.positionSeconds > 0 }
            if (continuing.isNotEmpty()) {
                item(key = "resume-title") { SettingsSectionTitle("继续观看 · 仅本机") }
                item(key = "resume") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(NordicSpacing.md)) {
                        items(continuing, key = { it.path }) { record ->
                            Surface(Modifier.width(220.dp).clickable(role = Role.Button, enabled = !state.preparing) {
                                play(WebDavEntry(record.path, record.title, false))
                            }, shape = NordicShapes.md, color = colors.surfaceVariant.copy(alpha = 0.45f)) {
                                Column(Modifier.padding(NordicSpacing.lg), verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)) {
                                    Icon(Icons.Filled.PlayCircle, null, tint = colors.primary)
                                    Text(record.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                                    Text("${formatDuration(record.positionSeconds)} / ${formatDuration(record.durationSeconds)}", style = MaterialTheme.typography.bodySmall)
                                    TextButton(onClick = { model.removeProgress(record.path) }) { Text("移除进度") }
                                }
                            }
                        }
                    }
                }
            }
            if (state.favorites.isNotEmpty()) {
                item(key = "favorites-title") { SettingsSectionTitle("收藏文件夹") }
                items(state.favorites, key = { "favorite:${it.path}" }) { folder ->
                    SettingsRow(folder.name, icon = Icons.Filled.FolderSpecial, onClick = { open(folder.path) })
                }
            }
        }
        item(key = "files-title") { SettingsSectionTitle("文件与文件夹") }
        if (showWebDavEmptyState(state.loading, state.error, rows.size)) item(key = "empty") {
            MediaStateCard(if (query.isNotBlank()) "没有匹配项目" else "这里还没有视频", "仅显示当前目录内容，可在显示选项中查看其他文件。", density = MediaStateDensity.Compact)
        }
        items(rows, key = { "file:${it.path}" }) { entry ->
            var menu by remember(entry.path) { mutableStateOf(false) }
            val progress = state.progress.firstOrNull { it.path == entry.path && !it.completed }
            Row(Modifier.fillMaxWidth().heightIn(min = 72.dp)
                .clickable(enabled = !state.preparing, role = Role.Button) { if (entry.directory) open(entry.path) else if (entry.isVideo) play(entry) else detail = entry },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NordicSpacing.md)) {
                Icon(if (entry.directory) Icons.Filled.Folder else if (entry.isVideo) Icons.Filled.Movie else Icons.AutoMirrored.Filled.InsertDriveFile,
                    null, Modifier.size(32.dp), tint = colors.primary)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NordicSpacing.xs)) {
                    Text(entry.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(if (entry.directory) "文件夹" else listOfNotNull(entry.extension.uppercase(), formatMediaBytes(entry.size),
                        progress?.let { "已看 ${formatDuration(it.positionSeconds)}" }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "${entry.name}的操作") }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        if (entry.isVideo) DropdownMenuItem(text = { Text("从头播放") }, onClick = { menu = false; play(entry, true) })
                        if (entry.directory) DropdownMenuItem(text = { Text(if (state.favorites.any { it.path == entry.path }) "取消收藏" else "收藏文件夹") },
                            onClick = { menu = false; model.favorite(entry.path, entry.name) })
                        if (progress != null) DropdownMenuItem(text = { Text("清除本机进度") }, onClick = { menu = false; model.removeProgress(entry.path) })
                        DropdownMenuItem(text = { Text("文件信息") }, onClick = { menu = false; detail = entry })
                    }
                }
            }
            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.4f))
        }
    }
    if (showSort) ModalBottomSheet(onDismissRequest = { showSort = false }) {
        Column(Modifier.padding(horizontal = NordicSpacing.content).padding(bottom = NordicSpacing.xxl)) {
            Text("目录排序与显示", style = MaterialTheme.typography.headlineMedium)
            Column(Modifier.selectableGroup()) {
                WebDavSort.entries.forEach { sort ->
                    MediaPlayerChoiceRow(sort.label, preferences.webDavSort == sort, colors,
                        onClick = { preference { it.copy(webDavSort = sort) } })
                }
            }
            SettingsRow("倒序排列", checked = preferences.webDavSortDescending, onCheckedChange = { checked -> preference { it.copy(webDavSortDescending = checked) } })
            SettingsRow("显示隐藏文件", checked = preferences.webDavShowHidden, onCheckedChange = { checked -> preference { it.copy(webDavShowHidden = checked) } })
            SettingsRow("仅显示视频与文件夹", checked = preferences.webDavOnlyVideos, onCheckedChange = { checked -> preference { it.copy(webDavOnlyVideos = checked) } })
        }
    }
    detail?.let { entry ->
        AlertDialog(onDismissRequest = { detail = null }, title = { Text(entry.name) },
            text = { Text("路径：${model.displayPath(entry.path)}\n大小：${formatMediaBytes(entry.size)}\n修改时间：${entry.modifiedAtMillis?.let { java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it)) } ?: "未知"}") },
            confirmButton = { TextButton(onClick = { detail = null }) { Text("关闭") } })
    }
}