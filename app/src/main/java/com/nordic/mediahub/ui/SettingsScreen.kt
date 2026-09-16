package com.nordic.mediahub.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nordic.mediahub.data.*
import com.nordic.mediahub.ui.theme.NordicSpacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun SettingsScreen(
    openServersRequest: Int = 0,
    onPlaySong: (NavidromeSong) -> Unit = {},
    onClose: () -> Unit = {},
    isModuleActive: (MediaDomain) -> Boolean = { false },
    onHideModule: (MediaDomain, () -> Unit, (String) -> Unit) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val repository = remember { ConfigRepository(context) }
    val localStorageError by repository.storageError.collectAsStateWithLifecycle()
    val storageError = LocalConfigurationError.current ?: localStorageError
    val actions = LocalSourceActions.current
    val sources = actions?.state ?: MediaSourceState()
    val preferences = LocalAppPreferences.current
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var pageName by rememberSaveable { mutableStateOf(SettingsPage.HOME.name) }
    val page = SettingsPage.valueOf(pageName)
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var highlight by rememberSaveable { mutableStateOf<String?>(null) }
    var editor by remember { mutableStateOf<MediaSource?>(null) }
    var isNew by remember { mutableStateOf(false) }
    var downloadsId by rememberSaveable { mutableStateOf("") }
    var downloadsName by rememberSaveable { mutableStateOf("") }
    var choice by remember { mutableStateOf<SettingsChoiceRequest?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val checks = remember { mutableStateMapOf<String, ConnectionCheck>() }
    val testing = remember { mutableStateMapOf<String, Boolean>() }
    val pageStates = rememberSaveableStateHolder()
    fun navigate(next: SettingsPage) { pageName = next.name; searchOpen = false; query = ""; highlight = null }
    fun back() {
        if (searchOpen) { searchOpen = false; query = ""; return }
        if (page == SettingsPage.HOME) { onClose(); return }
        navigate(when (page) {
            SettingsPage.EDIT_SERVER, SettingsPage.ADD_SERVER -> SettingsPage.SERVERS
            SettingsPage.DOWNLOADS -> SettingsPage.STORAGE
            SettingsPage.LEGACY -> SettingsPage.PRIVACY
            SettingsPage.HELP, SettingsPage.LICENSES -> SettingsPage.ABOUT
            else -> SettingsPage.HOME
        })
    }
    LaunchedEffect(openServersRequest) { if (openServersRequest > 0) navigate(SettingsPage.SERVERS) }
    LaunchedEffect(page) { if (page == SettingsPage.EDIT_SERVER && editor == null) navigate(SettingsPage.SERVERS) }
    BackHandler(enabled = true, onBack = ::back)
    fun update(change: (AppPreferences) -> AppPreferences) {
        scope.launch { try { repository.updatePreferences(change) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "设置保存失败" } }
    }
    fun test(source: MediaSource) {
        if (testing[source.id] == true) return
        testing[source.id] = true
        scope.launch {
            try { checks[source.id] = ConnectionCheck(testMediaSourceConnection(source), true) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { checks[source.id] = ConnectionCheck(e.message ?: "连接失败", false) }
            finally { testing[source.id] = false }
        }
    }
    CompositionLocalProvider(LocalSourceDomain provides null) {
        pageStates.SaveableStateProvider(pageName) {
            when {
                page == SettingsPage.EDIT_SERVER && editor != null -> ServerEditorScreen(editor!!, isNew, ::back) { saved, checked ->
                    checked?.let { checks[saved.id] = it }
                    editor = null
                    navigate(SettingsPage.SERVERS)
                }
                page == SettingsPage.DOWNLOADS -> DownloadedMusicScreen(downloadsId, downloadsName, ::back, onPlaySong)
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(NordicSpacing.content),
                    verticalArrangement = Arrangement.spacedBy(NordicSpacing.xs)) {
                    item(key = "header") {
                        MediaPageHeader(page.title,
                            if (page == SettingsPage.HOME) "${sources.sources.size} 个已保存来源" else when (page) {
                                SettingsPage.SERVERS -> "音乐、有声书和视频分别选择来源"
                                SettingsPage.VIDEO -> sources.active(MediaDomain.VIDEO)?.name ?: "尚未选择视频来源"
                                else -> ""
                            },
                            if (page == SettingsPage.HOME) listOf(HeaderAction(Icons.Filled.Search, "搜索设置", onClick = { searchOpen = !searchOpen })) else emptyList(),
                            colors, showBack = page != SettingsPage.HOME, onBack = ::back)
                    }
                    if (storageError != null) item { MediaStateCard("配置读取提示", storageError!!, tone = MediaStateTone.Error, density = MediaStateDensity.Compact) }
                    if (searchOpen) {
                        item { MediaSearchField(query, { query = it }, "搜索设置", "清空设置搜索", { query = "" }, colors) }
                        val results = searchSettings(query, preferences)
                        if (query.isNotBlank() && results.isEmpty()) item { MediaStateCard("没有匹配设置", "试试“字幕”“缓存”或“WebDAV”。", density = MediaStateDensity.Compact) }
                        items(results, key = { it.id }) { result ->
                            SettingsRow(result.title, "设置 / ${result.page.title}", onClick = {
                                navigate(result.page); highlight = result.id
                            })
                        }
                    } else when (page) {
                        SettingsPage.HOME -> {
                            val hiddenPages = hiddenModulePages(preferences)
                            SETTINGS_HOME_PAGES.filter { it !in hiddenPages }.forEach { destination ->
                                if (destination == SettingsPage.SERVERS) item { SettingsSectionTitle("连接") }
                                if (destination == SettingsPage.APPEARANCE) item { SettingsSectionTitle("体验与播放") }
                                if (destination == SettingsPage.STORAGE) item { SettingsSectionTitle("数据与应用") }
                                item(key = destination.name) {
                                    val summary = when (destination) {
                                        SettingsPage.SERVERS -> "音乐 ${sources.sources.count { it.domain == MediaDomain.MUSIC }} · 有声书 ${sources.sources.count { it.domain == MediaDomain.AUDIOBOOK }} · 视频 ${sources.sources.count { it.domain == MediaDomain.VIDEO }}"
                                        SettingsPage.APPEARANCE -> "${preferences.theme.label} · 启动${preferences.startupPage.label}"
                                        SettingsPage.MODULES -> "音乐、有声书、视频的显示开关"
                                        SettingsPage.MUSIC -> "${preferences.musicSpeed}× · ${preferences.musicDefaultView.label}"
                                        SettingsPage.AUDIOBOOK -> "${preferences.audiobookSpeed}× · 后退 ${preferences.audiobookSkipBack} 秒"
                                        SettingsPage.VIDEO -> "${preferences.videoSpeed}× · ${if (preferences.videoPip) "画中画开启" else "画中画关闭"}"
                                        SettingsPage.STORAGE -> "缓存占用与已下载音乐"
                                        SettingsPage.PRIVACY -> "本机记录、旧数据与默认设置"
                                        else -> "版本、开源声明与连接指南"
                                    }
                                    val icon = when (destination) {
                                        SettingsPage.SERVERS -> Icons.Filled.Dns
                                        SettingsPage.APPEARANCE -> Icons.Filled.Palette
                                        SettingsPage.MODULES -> Icons.Filled.ViewModule
                                        SettingsPage.MUSIC -> Icons.Filled.MusicNote
                                        SettingsPage.AUDIOBOOK -> Icons.AutoMirrored.Filled.MenuBook
                                        SettingsPage.VIDEO -> Icons.Filled.Movie
                                        SettingsPage.STORAGE -> Icons.Filled.Storage
                                        SettingsPage.PRIVACY -> Icons.Filled.Security
                                        else -> Icons.Filled.Info
                                    }
                                    SettingsRow(destination.title, summary, icon = icon, onClick = { navigate(destination) })
                                    HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.5f))
                                }
                            }
                        }
                        SettingsPage.SERVERS -> {
                            item { SettingsRow("添加服务器", "支持 Navidrome、AudiobookShelf、Emby 与 WebDAV", icon = Icons.Filled.Add, onClick = { navigate(SettingsPage.ADD_SERVER) }) }
                            MediaDomain.entries.forEach { domain ->
                                item { SettingsSectionTitle(domain.label) }
                                val entries = sources.sources.filter { it.domain == domain }
                                if (entries.isEmpty()) item { MediaStateCard("尚未添加${domain.label}服务器", "本域的播放与浏览将暂不可用。", density = MediaStateDensity.Compact) }
                                items(entries, key = { it.id }) { source ->
                                    var menu by remember(source.id) { mutableStateOf(false) }
                                    Row(Modifier.fillMaxWidth().padding(vertical = NordicSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(sources.activeId(domain) == source.id, enabled = actions?.busy != true,
                                            onClick = { actions?.select?.invoke(domain, source.id) })
                                        Column(Modifier.weight(1f)) {
                                            Text(source.name, style = MaterialTheme.typography.titleMedium)
                                            Text("${source.kind.label} · ${source.serverUrl}", maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                                            val check = checks[source.id]
                                            Text(if (testing[source.id] == true) "正在测试连接…" else check?.let { "上次测试：${it.message}" } ?: "未验证",
                                                style = MaterialTheme.typography.bodySmall, color = if (check?.success == false) colors.error else colors.onSurfaceVariant)
                                        }
                                        Box {
                                            IconButton(onClick = { menu = true }, enabled = actions?.busy != true) { Icon(Icons.Filled.MoreVert, "管理 ${source.name}") }
                                            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                                                DropdownMenuItem(text = { Text("编辑连接") }, onClick = { menu = false; editor = source; isNew = false; navigate(SettingsPage.EDIT_SERVER) })
                                                DropdownMenuItem(text = { Text("测试连接") }, onClick = { menu = false; test(source) })
                                                DropdownMenuItem(text = { Text("删除连接") }, onClick = { menu = false; actions?.delete?.invoke(source) { it.onFailure { e -> error = e.message } } })
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        SettingsPage.ADD_SERVER -> items(MediaSourceKind.entries, key = { it.name }) { kind ->
                            SettingsRow(kind.label, kind.domain.label, icon = Icons.Filled.Add, onClick = {
                                editor = MediaSource(name = kind.label, kind = kind); isNew = true; navigate(SettingsPage.EDIT_SERVER)
                            })
                        }
                        SettingsPage.APPEARANCE, SettingsPage.MUSIC, SettingsPage.AUDIOBOOK, SettingsPage.VIDEO -> item {
                            PreferenceSettingsPage(page, preferences, sources.active(MediaDomain.VIDEO), highlight, ::update) { choice = it }
                        }
                        SettingsPage.MODULES -> item {
                            ModuleVisibilityPage(preferences, isModuleActive, onHideModule, ::update)
                        }
                        SettingsPage.STORAGE -> item { StorageSettingsPage(sources) { id, name -> downloadsId = id; downloadsName = name; navigate(SettingsPage.DOWNLOADS) } }
                        SettingsPage.PRIVACY -> item { PrivacySettingsPage(sources) { navigate(SettingsPage.LEGACY) } }
                        SettingsPage.LEGACY -> item { LegacyDataPage(sources) }
                        SettingsPage.ABOUT -> item {
                            val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }
                            SettingsRow("Nordic", "自托管媒体客户端", value = version)
                            SettingsRow("连接与播放帮助", icon = Icons.AutoMirrored.Filled.HelpOutline, onClick = { navigate(SettingsPage.HELP) })
                            SettingsRow("开源声明", icon = Icons.Filled.Code, onClick = { navigate(SettingsPage.LICENSES) })
                            SettingsRow("隐私说明", "配置和播放偏好保存在本机。服务器会接收你主动发起的媒体访问与必要的播放进度同步。")
                        }
                        SettingsPage.HELP -> item {
                            SettingsRow("AList / OpenList", "WebDAV 地址通常以 /dav/ 结尾。使用实例的 WebDAV 账号，确保目录与文件读取权限已开启。")
                            SettingsRow("NAS / Nextcloud", "填写完整 WebDAV 地址和端口。Nextcloud 常用 /remote.php/dav/files/用户名/；启用双重验证的服务可能需要应用专用密码。")
                            SettingsRow("证书与 HTTP", "推荐 HTTPS 和有效证书。本应用不绕过证书校验；HTTP 需在连接表单中明确确认。")
                            SettingsRow("拖动与续播", "服务器和网盘直链需支持 HTTP Range。无法定位时可从头播放；WebDAV 进度只保存在本机。")
                            SettingsRow("字幕与格式", "支持内嵌音轨/字幕和同目录同名 SRT、ASS、VTT 字幕。格式兼容性取决于 Media3 和设备解码能力，不保证所有编码可播放。")
                            SettingsRow("字幕命名示例", "电影.mkv 可搭配 电影.srt、电影.zh.srt 或 电影.en.vtt。默认字幕策略可在视频播放设置中修改。")
                            SettingsRow("切换服务器", "每种媒体分别选择当前来源，不合并媒体库。切换正在播放的同类来源前会提示结束旧会话。")
                        }
                        SettingsPage.LICENSES -> item {
                            SettingsRow("主要开源组件", "Kotlin、AndroidX / Compose / Media3、OkHttp、Retrofit、Coil、Gson。")
                            SettingsRow("Apache License 2.0", "上述主要组件采用 Apache License 2.0。第三方项目名称与商标归各自所有者；具体版权声明以各组件分发材料为准。")
                        }
                        else -> Unit
                    }
                }
            }
        }
    }
    choice?.let { request -> SettingsChoiceDialog(request) { choice = null } }
    error?.let { message -> AlertDialog(onDismissRequest = { error = null }, title = { Text("操作未完成") }, text = { Text(message) },
        confirmButton = { TextButton(onClick = { error = null }) { Text("知道了") } }) }
}