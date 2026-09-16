package com.nordic.mediahub.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nordic.mediahub.data.*
import com.nordic.mediahub.ui.theme.NordicSpacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class DataConfirmation(val title: String, val message: String, val action: suspend () -> Unit)

@Composable
internal fun StorageSettingsPage(sources: MediaSourceState, onDownloads: (String, String) -> Unit) {
    val context = LocalContext.current
    val repository = remember { AppStorageRepository(context) }
    var summary by remember { mutableStateOf<StorageSummary?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<DataConfirmation?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(sources, revision) {
        try { summary = repository.inspect(sources); loadError = false }
        catch (e: Exception) { if (e is CancellationException) throw e; loadError = true }
    }
    Column {
        if (summary == null || busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (loadError) MediaStateCard("读取存储信息失败", "请稍后重试,不会删除任何数据。", tone = MediaStateTone.Error, density = MediaStateDensity.Compact)
        SettingsSectionTitle("可清理缓存")
        SettingsRow("图片缓存", "清理后会在需要时重新加载封面。", value = summary?.let { formatMediaBytes(it.imageBytes) },
            enabled = !busy, icon = Icons.Filled.Image, onClick = {
                confirmation = DataConfirmation("清理图片缓存？", "不会删除连接、下载或观看进度。") { repository.clearImages() }
            })
        SettingsRow("媒体目录数据", "包含来源目录列表，不含收藏文件夹与观看进度。", value = summary?.let { "约 ${formatMediaBytes(it.catalogBytes)}" },
            enabled = !busy, icon = Icons.Filled.Cached, onClick = {
                confirmation = DataConfirmation("清理媒体目录缓存？", "下次浏览时重新获取目录，已下载音乐不会被删除。") { repository.clearCatalogs() }
            })
        SettingsSectionTitle("已下载音乐 · 按来源管理")
        summary?.downloads?.forEach { source ->
            SettingsRow(source.name, "${source.count} 首 · ${formatMediaBytes(source.bytes)}", icon = Icons.Filled.Download,
                onClick = { onDownloads(source.sourceId, source.name) })
        }
        if (summary?.downloads?.isEmpty() == true) SettingsRow("暂无下载", "播放音乐时，可从播放器菜单下载当前曲目。")
        SettingsRow("刷新占用信息", icon = Icons.Filled.Refresh, onClick = { revision++ })
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    confirmation?.let { action -> AlertDialog(onDismissRequest = { confirmation = null }, title = { Text(action.title) }, text = { Text(action.message) },
        confirmButton = { TextButton(onClick = {
            confirmation = null; busy = true; error = null
            scope.launch { try { action.action(); revision++ } catch (e: Exception) { if (e is CancellationException) throw e; error = e.message ?: "清理失败" } finally { busy = false } }
        }) { Text("清理") } }, dismissButton = { TextButton(onClick = { confirmation = null }) { Text("取消") } }) }
}

@Composable
internal fun DownloadedMusicScreen(sourceId: String, name: String, onBack: () -> Unit, onPlay: (NavidromeSong) -> Unit) {
    val context = LocalContext.current
    val manager = remember(sourceId) { MusicDownloadManagers.get(context, sourceId) }
    val states by manager.downloadStates.collectAsStateWithLifecycle()
    val source = LocalSourceActions.current?.state?.sources?.firstOrNull { it.id == sourceId }
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var deletion by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(manager) { withContext(Dispatchers.IO) { manager.restoreDownloadState() } }
    val rows = remember(states) { states.entries.sortedBy { it.value.song?.title ?: it.key } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(NordicSpacing.content)) {
        item { MediaPageHeader("已下载音乐", name, emptyList(), MaterialTheme.colorScheme, showBack = true, onBack = onBack) }
        error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
        if (rows.isEmpty()) item { MediaStateCard("暂无下载", "下载的音乐会按来源保存在这里。", density = MediaStateDensity.Compact) }
        items(rows, key = { it.key }) { (id, entry) ->
            SettingsRow(entry.song?.title ?: id, subtitle = when (entry.state) {
                DownloadState.DOWNLOADING -> "正在下载 ${(entry.progress * 100).toInt()}%"
                DownloadState.DOWNLOADED -> "已下载 · 点击播放"
                else -> entry.errorMessage ?: "下载未完成"
            }, icon = Icons.Filled.MusicNote, onClick = if (entry.state == DownloadState.DOWNLOADED) ({
                scope.launch {
                    val path = withContext(Dispatchers.IO) { manager.getLocalFilePath(id) }
                    if (path != null) onPlay((entry.song ?: NavidromeSong(id, id)).copy(sourceId = sourceId, streamUrl = "file://$path"))
                    else error = "下载文件已不存在，请刷新列表"
                }
            }) else null)
            Row(horizontalArrangement = Arrangement.spacedBy(NordicSpacing.sm)) {
                if (entry.state == DownloadState.DOWNLOADING) TextButton(onClick = { manager.cancelDownload(id) }) { Text("取消下载") }
                else TextButton(onClick = { deletion = id }) { Text("删除下载") }
                if (entry.state == DownloadState.NOT_DOWNLOADED && source != null && entry.song != null) TextButton(onClick = {
                    manager.downloadSong(entry.song, source.navidromeConfig())
                }) { Text("重试") }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
    deletion?.let { id -> AlertDialog(onDismissRequest = { deletion = null }, title = { Text("删除这首下载？") },
        text = { Text("仅删除本机下载文件，不删除服务器上的音乐。") },
        confirmButton = { TextButton(onClick = { deletion = null; scope.launch {
            try { withContext(Dispatchers.IO) { manager.deleteDownload(id) } }
            catch (e: Exception) { if (e is CancellationException) throw e; error = "删除下载失败" }
        } }) { Text("删除") } }, dismissButton = { TextButton(onClick = { deletion = null }) { Text("取消") } }) }
}

@Composable
internal fun PrivacySettingsPage(sources: MediaSourceState, onLegacy: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { AppStorageRepository(context) }
    val configRepository = remember { ConfigRepository(context) }
    val scope = rememberCoroutineScope()
    var confirmation by remember { mutableStateOf<DataConfirmation?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    Column {
        SettingsRow("凭证加密存储", "服务器密码与密钥保存在 Android 加密存储中，不随系统备份导出。")
        SettingsRow("媒体请求与隐私", "媒体请求直接发送到你配置的服务器。本应用不提供账号云同步或使用行为统计服务。")
        SettingsSectionTitle("清理本机记录")
        sources.sources.forEach { source ->
            val label = when (source.domain) {
                MediaDomain.MUSIC -> "播放历史"
                MediaDomain.AUDIOBOOK -> "本机书签"
                MediaDomain.VIDEO -> if (source.kind == MediaSourceKind.WEBDAV) "观看进度" else "服务器进度不在本机删除"
            }
            if (source.kind != MediaSourceKind.EMBY) SettingsRow(source.name, label, enabled = !busy, destructive = true, onClick = {
                confirmation = DataConfirmation("清理 ${source.name} 的$label？", "只清理本机数据，不操作服务器历史。正在播放的内容仍会继续记录进度。") { repository.clearLocalRecords(source) }
            })
        }
        SettingsRow("待归属旧数据", "旧版本无法可靠判断来源的数据由你手动归属。", icon = Icons.Filled.FolderOpen, onClick = onLegacy)
        SettingsSectionTitle("恢复设置")
        SettingsRow("恢复偏好默认值", "保留服务器、下载、书签和观看进度。", enabled = !busy, destructive = true, onClick = {
            confirmation = DataConfirmation("恢复默认偏好？", "主题、启动页和播放偏好将恢复默认；不会删除任何服务器或下载。") { configRepository.resetPreferences() }
        })
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
    confirmation?.let { action -> AlertDialog(onDismissRequest = { confirmation = null }, title = { Text(action.title) }, text = { Text(action.message) },
        confirmButton = { TextButton(onClick = { confirmation = null; busy = true; scope.launch {
            try { action.action(); message = "操作完成" }
            catch (e: Exception) { if (e is CancellationException) throw e; message = e.message ?: "操作失败" }
            finally { busy = false }
        } }) { Text("确认") } }, dismissButton = { TextButton(onClick = { confirmation = null }) { Text("取消") } }) }
}

@Composable
internal fun LegacyDataPage(sources: MediaSourceState) {
    val context = LocalContext.current
    val repository = remember { AppStorageRepository(context) }
    var summary by remember { mutableStateOf<LegacyDataSummary?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    var confirmation by remember { mutableStateOf<DataConfirmation?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(revision) { try { summary = repository.legacySummary() } catch (e: Exception) { if (e is CancellationException) throw e; message = "读取旧数据失败" } }
    Column {
        SettingsRow("旧数据不会自动关联账号", "归属操作由你确认，已存在的下载不会被覆盖；冲突文件继续保留在旧下载中。")
        summary?.let { SettingsRow("待归属内容", "音乐历史 ${it.musicHistory} 条 · 有声书书签 ${it.bookmarks} 条 · 音乐下载 ${it.downloads} 首") }
        sources.sources.filter { it.domain != MediaDomain.VIDEO }.forEach { source ->
            SettingsRow("归属到 ${source.name}", source.domain.label, enabled = !busy, onClick = {
                confirmation = DataConfirmation("确认旧数据属于 ${source.name}？", "此操作会关联对应媒体的旧本机记录。音乐来源还会接收未归属的旧下载，不会覆盖已有文件。") { repository.assignLegacy(source) }
            })
        }
        SettingsRow("清理未归属旧记录", "只清理旧播放历史和旧书签，保留下载文件。", enabled = !busy, destructive = true, onClick = {
            confirmation = DataConfirmation("清理旧记录？", "旧播放历史与旧书签将被删除，下载文件保留。") { repository.clearLegacyRecords() }
        })
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let { Text(it) }
    }
    confirmation?.let { action -> AlertDialog(onDismissRequest = { confirmation = null }, title = { Text(action.title) }, text = { Text(action.message) },
        confirmButton = { TextButton(onClick = { confirmation = null; busy = true; scope.launch {
            try { action.action(); revision++; message = "操作完成" }
            catch (e: Exception) { if (e is CancellationException) throw e; message = e.message ?: "操作失败，未移动的旧数据已保留" }
            finally { busy = false }
        } }) { Text("确认") } }, dismissButton = { TextButton(onClick = { confirmation = null }) { Text("取消") } }) }
}