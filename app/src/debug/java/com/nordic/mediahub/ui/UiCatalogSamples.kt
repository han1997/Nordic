package com.nordic.mediahub.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nordic.mediahub.R
import com.nordic.mediahub.data.*
import com.nordic.mediahub.playback.MusicLyricsUiState
import com.nordic.mediahub.playback.resolvePlayNextTargetIndex
import com.nordic.mediahub.ui.theme.NordicSpacing
import kotlinx.coroutines.flow.MutableStateFlow

internal enum class UiSampleScreen(val id: String, val label: String) {
    Catalog("catalog", "UI 样板目录"), Songs("songs", "歌曲列表"), Album("album", "专辑详情"),
    Player("player", "音乐播放器"), Lyrics("lyrics", "歌词"), Queue("queue", "播放队列"), Speed("speed", "播放速度"),
    Modules("modules", "模块显示"), Server("server", "服务器编辑"),
    ServerEmby("server_emby", "Emby 连接表单"), ServerWebdav("server_webdav", "WebDAV 连接表单"), SettingsRows("settings_rows", "设置组件"),
    Home("home", "音乐发现"), Albums("albums", "专辑列表"), Artists("artists", "歌手列表"),
    Artist("artist", "歌手详情"), Search("search", "搜索结果"), SearchLanding("search_landing", "搜索建议"),
    Playlists("playlists", "歌单列表"), Playlist("playlist", "歌单详情"),
    PlaylistCreate("playlist_create", "新建歌单"), PlaylistRename("playlist_rename", "重命名歌单"),
    PlaylistDelete("playlist_delete", "删除歌单"), Equalizer("equalizer", "均衡器组件 · 未接入"),
    MusicActions("music_actions", "音乐下载操作"),
    AudiobookHome("ab_home", "有声书书库"), AudiobookDetail("ab_detail", "有声书详情"),
    AudiobookPlayer("ab_player", "有声书播放器"), AudiobookChapters("ab_chapters", "章节面板"),
    AudiobookSpeed("ab_speed", "有声书倍速"), AudiobookSleep("ab_sleep", "睡眠定时"),
    AudiobookBookmarks("ab_bookmarks", "书签"),
    VideoHome("video_home", "视频媒体库"), VideoSearch("video_search", "视频搜索"),
    VideoDetail("video_detail", "视频详情"), VideoSeries("video_series", "剧集详情"),
    SettingsHome("settings_home", "设置主页"), SettingsServers("settings_servers", "服务器列表"),
    SettingsPrefs("settings_prefs", "偏好设置页"), SettingsData("settings_data", "数据与隐私页")
}
internal enum class UiSampleState(val id: String, val label: String) {
    Normal("normal", "正常"), LongText("long", "长文本"), Empty("empty", "空白"), Loading("loading", "加载中"),
    Error("error", "错误"), NoArtwork("no_art", "无封面"), Disabled("disabled", "禁用"), Refreshing("refreshing", "缓存刷新"), CachedError("cached_error", "缓存失败"), LibraryEmpty("library_empty", "已配置空曲库")
}
internal data class UiSampleRequest(val screen: UiSampleScreen = UiSampleScreen.Catalog, val state: UiSampleState = UiSampleState.Normal,
    val dark: Boolean = false, val fontScale: Float = 1f)

@Composable
internal fun UiCatalogContent(
    request: UiSampleRequest,
    onNavigate: (UiSampleScreen) -> Unit,
    onOptionsChange: (Boolean, Float, UiSampleState) -> Unit,
    onEvent: (String) -> Unit
) {
    val context = LocalContext.current
    val covers = listOf(R.drawable.ui_sample_cover_a, R.drawable.ui_sample_cover_b, R.drawable.ui_sample_cover_c)
        .map { "android.resource://${context.packageName}/$it" }
    val songs = remember(request.state) {
        listOf("九万字", "深空尽头", "沿途的风", "留白 · Acoustic Session", "远山", "一个人的日落", "向着海岸线", "夜航").mapIndexed { index, title ->
            NavidromeSong(id = "sample-$index", title = if (request.state == UiSampleState.LongText && index == 0)
                "在漫长的旅途中听见远方的回声 · Live Acoustic Session" else title,
                artist = if (index == 5) null else "张靓颖", album = "深空尽头", duration = if (index == 5) 0 else 234 + index * 17,
                coverArt = if (request.state == UiSampleState.NoArtwork) null else covers[index % covers.size],
                streamUrl = if (request.state == UiSampleState.Disabled) null else "preview://song/$index", sourceId = "ui-samples")
        }
    }
    BackHandler(enabled = request.screen != UiSampleScreen.Catalog) { onNavigate(UiSampleScreen.Catalog) }
    when (request.screen) {
        UiSampleScreen.Catalog -> LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(),
            contentPadding = PaddingValues(NordicSpacing.content), verticalArrangement = Arrangement.spacedBy(NordicSpacing.md)) {
            item { Text("UI 样板目录", style = MaterialTheme.typography.headlineMedium) }
            item { Text("仅调试样例 · 不连接账号、不保存设置、不播放媒体。全量界面将在样板确认后逐页推进。", style = MaterialTheme.typography.bodyMedium) }
            item { SettingsRow("深色主题", checked = request.dark,
                onCheckedChange = { onOptionsChange(it, request.fontScale, request.state) }) }
            item { MediaSegmentedControl(listOf(1f, 1.5f, 2f), request.fontScale,
                label = { "字号 ${it}×" }, optionKey = { it.toString() }, colorScheme = MaterialTheme.colorScheme,
                onOptionSelected = { onOptionsChange(request.dark, it, request.state) }) }
            item { MediaSegmentedControl(UiSampleState.entries, request.state, label = { it.label }, optionKey = { it.id },
                colorScheme = MaterialTheme.colorScheme, onOptionSelected = { onOptionsChange(request.dark, request.fontScale, it) }) }
            item { Text("状态只作用于适用的样板；交互结果为本地模拟，不代表真实服务调用。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(UiSampleScreen.entries.filter { it != UiSampleScreen.Catalog }, key = { it.id }) { screen ->
                SettingsRow(screen.label, onClick = { onNavigate(screen) }, id = "sample-${screen.id}")
            }
        }
        UiSampleScreen.Songs, UiSampleScreen.Album -> LibrarySample(request, songs, onNavigate, onEvent)
        UiSampleScreen.Player, UiSampleScreen.Lyrics, UiSampleScreen.Queue, UiSampleScreen.Speed -> PlayerSample(request, songs, onNavigate, onEvent)
        UiSampleScreen.Modules -> ModuleSample(request, onNavigate, onEvent)
        UiSampleScreen.Server, UiSampleScreen.ServerEmby, UiSampleScreen.ServerWebdav -> ServerSample(request, onNavigate, onEvent)
        UiSampleScreen.SettingsRows -> SettingsRowSample(request, onNavigate, onEvent)
        UiSampleScreen.SettingsHome, UiSampleScreen.SettingsServers, UiSampleScreen.SettingsPrefs,
        UiSampleScreen.SettingsData -> SettingsSample(request, onNavigate, onEvent)
        UiSampleScreen.AudiobookHome, UiSampleScreen.AudiobookDetail, UiSampleScreen.AudiobookPlayer,
        UiSampleScreen.AudiobookChapters, UiSampleScreen.AudiobookSpeed, UiSampleScreen.AudiobookSleep,
        UiSampleScreen.AudiobookBookmarks -> AudiobookCatalogSample(request, onNavigate, onEvent)
        UiSampleScreen.VideoHome, UiSampleScreen.VideoSearch, UiSampleScreen.VideoDetail,
        UiSampleScreen.VideoSeries -> VideoCatalogSample(request, onNavigate, onEvent)
        else -> MusicCatalogSample(request, songs, onNavigate, onEvent)
    }
}

@Composable
private fun LibrarySample(request: UiSampleRequest, songs: List<NavidromeSong>, onNavigate: (UiSampleScreen) -> Unit, onEvent: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(MusicSongSort.Default) }
    var playing by remember { mutableStateOf(false) }
    var currentSong by remember { mutableStateOf(songs.first()) }
    val album = request.screen == UiSampleScreen.Album
    val sourceSongs = if (request.state == UiSampleState.Empty) emptyList() else songs
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        MediaPageHeader(if (album) "专辑" else "音乐", if (album) "音乐资料库" else "${sourceSongs.size} 首歌曲",
            if (album) emptyList() else listOf(HeaderAction(Icons.Filled.Settings, "打开设置", fixed = true) { onNavigate(UiSampleScreen.Modules) }),
            colors, showBack = album, onBack = { onNavigate(UiSampleScreen.Catalog) },
            modifier = Modifier.padding(horizontal = NordicSpacing.content))
        if (!album) Box(Modifier.padding(horizontal = NordicSpacing.content, vertical = NordicSpacing.sm)) {
            MusicSegmentedTabs(1, colors, onTabSelected = { onEvent("music-tab:$it") }, onSearchClick = { onEvent("search") })
        }
        Box(Modifier.weight(1f)) {
            when {
                request.state == UiSampleState.Loading && !album -> Box(Modifier.padding(NordicSpacing.content)) {
                    MediaLoadingCard("正在加载音乐", "正在读取曲库，请稍候。")
                }
                request.state == UiSampleState.Error && !album -> Box(Modifier.padding(NordicSpacing.content)) {
                    MediaStateCard("暂时无法加载音乐", "连接暂时不可用，已有数据会保留。", tone = MediaStateTone.Error)
                }
                album -> Column {
                    if (request.state == UiSampleState.Error) Box(Modifier.padding(NordicSpacing.content)) {
                        MediaStateCard("曲目加载失败", "稍后重试，不会清除已保存的专辑信息。", tone = MediaStateTone.Error)
                    }
                    MusicAlbumDetailPage(
                        NavidromeAlbum("sample-album", if (request.state == UiSampleState.LongText) "深空尽头 · 在漫长旅途中寻找属于自己的声音 Live Sessions" else "深空尽头",
                            "张靓颖", songs[1].coverArt, 8, 2026), request.state == UiSampleState.Loading,
                        if (request.state in listOf(UiSampleState.Empty, UiSampleState.Loading, UiSampleState.Error)) emptyList() else songs,
                        colors, onSongSelected = { list, index, _ -> currentSong = list[index]; playing = true; onEvent("song:${list[index].id}") },
                        onPlayAlbumAll = { playing = true; onEvent("play-album") }, hasVisibleError = request.state == UiSampleState.Error
                    )
                }
                else -> MusicSongsPage(sourceSongs, sortMusicSongs(filterMusicSongs(sourceSongs, query), sort),
                    query, sort, colors, onSongSelected = { list, index, _ -> currentSong = list[index]; playing = true; onEvent("song:${list[index].id}") },
                    onSongFilterChange = { query = it }, onSongFilterClear = { query = "" }, onSongSortChange = { sort = it; onEvent("sort:$it") })
            }
        }
        PolishedPlaybackDock(0, colors, DockNowPlayingContent.Music(currentSong), playing,
            playbackStatus = when (request.state) {
                UiSampleState.Error -> "连接暂时不可用"
                UiSampleState.Loading -> "正在缓冲"
                else -> null
            },
            statusIsError = request.state == UiSampleState.Error,
            onOpenPlayer = { onNavigate(UiSampleScreen.Player) }, onPlayPause = { playing = !playing; onEvent("dock-play") },
            onSelect = { onEvent("domain:$it"); if (it != 0) onNavigate(UiSampleScreen.Catalog) })
    }
}

@Composable
internal fun PlayerSample(request: UiSampleRequest, initialSongs: List<NavidromeSong>, onNavigate: (UiSampleScreen) -> Unit, onEvent: (String) -> Unit) {
    var songs by remember { mutableStateOf(if (request.screen == UiSampleScreen.Queue && request.state == UiSampleState.Empty) emptyList() else initialSongs) }
    var index by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(request.screen == UiSampleScreen.Lyrics) }
    var showQueue by remember { mutableStateOf(request.screen == UiSampleScreen.Queue) }
    var showSpeed by remember { mutableStateOf(request.screen == UiSampleScreen.Speed) }
    var speed by remember { mutableFloatStateOf(1f) }
    var repeat by remember { mutableIntStateOf(0) }
    var shuffle by remember { mutableStateOf(false) }
    var revision by remember { mutableLongStateOf(0) }
    val initialPosition = if (request.state == UiSampleState.Empty) 0 else 76
    var position by remember { mutableIntStateOf(initialPosition) }
    val positionMillis = remember { MutableStateFlow(initialPosition * 1000L) }
    val song = if (request.state == UiSampleState.Empty) null else songs.getOrNull(index)
    fun seek(value: Int) { position = value; positionMillis.value = value * 1000L; revision++; onEvent("seek:$value") }
    val lyrics = song?.let { current ->
        when (request.state) {
            UiSampleState.Loading -> MusicLyricsUiState.Loading(current.id)
            UiSampleState.Error -> MusicLyricsUiState.Error(current.id, "歌词暂时无法加载", true)
            else -> MusicLyricsUiState.Content(current.id, index + 1L, MusicLyrics(listOf(
                MusicLyricsLine(0, "这一段文字仅用于界面预览"),
                MusicLyricsLine(30_000, "让每一次停留，都有清晰的节奏"),
                MusicLyricsLine(60_000, "在漫长的旅途中，听见远方的回声"),
                MusicLyricsLine(90_000, "Across the quiet sky, we find our way"),
                MusicLyricsLine(120_000, "留下一点空白，给下一段旋律"),
                MusicLyricsLine(160_000, "在光与影之间，慢慢听见自己")
            ), synced = true))
        }
    } ?: MusicLyricsUiState.Idle
    MusicPlayerContent(song, MaterialTheme.colorScheme, playing, request.state == UiSampleState.Loading,
        if (request.state == UiSampleState.Error) "播放暂时不可用，请稍后重试" else null,
        position, positionMillis, song?.duration ?: 0, bufferedPositionSeconds = 155,
        lyricsState = lyrics, showLyrics = showLyrics, lyricsSeekRevision = revision,
        onToggleLyrics = { showLyrics = !showLyrics; onEvent("lyrics") }, onRetryLyrics = { onEvent("retry-lyrics") },
        repeatMode = repeat, shuffleModeEnabled = shuffle, playbackSpeed = speed, onSeek = ::seek,
        onPlayPause = { playing = !playing; onEvent("player-play") }, onClose = { onNavigate(UiSampleScreen.Catalog) },
        onSeekToNext = { index = (index + 1).coerceAtMost(songs.lastIndex); seek(0) }, onSeekToPrevious = { index = (index - 1).coerceAtLeast(0); seek(0) },
        onToggleRepeat = { repeat = (repeat + 1) % 3 }, onToggleShuffle = { shuffle = !shuffle }, onOpenQueue = { showQueue = true },
        onToggleFavorite = { id, starred -> songs = songs.map { if (it.id == id) it.copy(starred = if (starred) "fixture" else null) else it }; onEvent("favorite") },
        onSetPlaybackSpeed = { speed = it; onEvent("speed:$it") }, onDownloadSong = { onEvent("download-preview") })
    if (showQueue) MusicQueueSheet(songs, index, MaterialTheme.colorScheme,
        onSeekToIndex = { index = it; showQueue = false; onEvent("queue:$it") }, onPlayNext = { item ->
            resolvePlayNextTargetIndex(item, index, songs.size)?.let { target ->
                val current = songs[index].id
                songs = songs.toMutableList().apply { add(target, removeAt(item)) }
                index = songs.indexOfFirst { it.id == current }
            }
            onEvent("play-next:$item")
        },
        onRemoveFromQueue = { remove -> if (songs.size > 1) {
            songs = songs.filterIndexed { item, _ -> item != remove }
            index = (if (remove < index) index - 1 else index).coerceAtMost(songs.lastIndex)
            onEvent("queue-remove:$remove")
        } },
        onClearUpcoming = { songs = songs.take(index + 1) },
        onMoveQueueItem = { from, to -> onEvent("queue-move:$from:$to"); val current = songs.getOrNull(index)?.id; songs = songs.toMutableList().apply { add(to, removeAt(from)) }; index = songs.indexOfFirst { it.id == current }.coerceAtLeast(0) },
        onDismiss = { showQueue = false })
    if (showSpeed) MusicPlaybackSpeedSheet(speed, MaterialTheme.colorScheme, onSelect = { speed = it; showSpeed = false; onEvent("speed:$it") }, onDismiss = { showSpeed = false })
}

@Composable
private fun ModuleSample(request: UiSampleRequest, onNavigate: (UiSampleScreen) -> Unit, onEvent: (String) -> Unit) {
    var preferences by remember { mutableStateOf(if (request.state == UiSampleState.Disabled) AppPreferences(showAudiobook = false, showVideo = false) else AppPreferences()) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(NordicSpacing.content)) {
        MediaPageHeader("模块显示", "设置", emptyList(), MaterialTheme.colorScheme, showBack = true, onBack = { onNavigate(UiSampleScreen.Catalog) })
        Spacer(Modifier.height(NordicSpacing.md))
        ModuleVisibilityPage(preferences, isModuleActive = { false }, onHideModule = { _, stopped, _ -> stopped() }) { transform ->
            val next = transform(preferences)
            error = when {
                request.state == UiSampleState.Error -> "保存设置失败，请重试"
                !next.showMusic && !next.showAudiobook && !next.showVideo -> "至少保留一个媒体模块"
                else -> null
            }
            if (error == null) preferences = next
            onEvent("module-change")
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun ServerSample(request: UiSampleRequest, onNavigate: (UiSampleScreen) -> Unit, onEvent: (String) -> Unit) {
    val kind = when (request.screen) {
        UiSampleScreen.ServerEmby -> MediaSourceKind.EMBY
        UiSampleScreen.ServerWebdav -> MediaSourceKind.WEBDAV
        else -> MediaSourceKind.NAVIDROME
    }
    var draft by remember { mutableStateOf(MediaSource(id = "sample-server", name = if (request.state == UiSampleState.LongText) "我的家庭媒体服务器 · 长名称与混合语言检查" else "家庭音乐库",
        kind = kind, serverUrl = if (kind == MediaSourceKind.WEBDAV) "https://files.example.test/dav/" else "https://music.example.test", username = "listener")) }
    var check by remember { mutableStateOf<ConnectionCheck?>(null) }
    var error by remember { mutableStateOf<String?>(if (request.state == UiSampleState.Error) "连接暂时不可用，请检查地址后重试" else null) }
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        ServerEditorContent(draft, false, request.state == UiSampleState.Disabled, request.state == UiSampleState.Loading,
            check, error, onChange = { draft = it; error = null; check = null; onEvent("server-change") }, onBack = { onNavigate(UiSampleScreen.Catalog) },
            onTestConnection = { check = ConnectionCheck("调试样例：连接测试成功，未发送网络请求", true, 0); onEvent("test-connection-preview") },
            onSave = { check = ConnectionCheck("调试样例：已确认输入，未写入存储", true, 0); onEvent("save-preview") })
    }
}

@Composable
private fun SettingsRowSample(request: UiSampleRequest, onNavigate: (UiSampleScreen) -> Unit, onEvent: (String) -> Unit) {
    var enabled by remember { mutableStateOf(true) }
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(NordicSpacing.content)) {
        MediaPageHeader("设置组件", "选项、状态与长文本", emptyList(), MaterialTheme.colorScheme, showBack = true, onBack = { onNavigate(UiSampleScreen.Catalog) })
        SettingsSectionTitle("外观与启动")
        SettingsRow("应用主题", value = "跟随系统", onClick = { onEvent("theme") })
        SettingsRow("默认打开的媒体页面", value = "上次访问的页面", onClick = { onEvent("startup") }, id = "long-setting-value")
        SettingsRow("保留播放状态", "返回应用后继续使用上次的播放设置。", checked = enabled, onCheckedChange = { enabled = it })
        SettingsSectionTitle("存储与下载")
        SettingsRow("下载目录", value = "/storage/emulated/0/Android/data/fun.han1997.nordic/files/Music/", onClick = { onEvent("directory") })
        SettingsRow("清理缓存", "不会删除已下载的音乐和播放进度。", enabled = request.state != UiSampleState.Disabled, destructive = true, onClick = { onEvent("clear-preview") })
    }
}

@Composable
private fun SettingsSample(request: UiSampleRequest, onNavigate: (UiSampleScreen) -> Unit, onEvent: (String) -> Unit) {
    when (request.screen) {
        UiSampleScreen.SettingsHome -> SettingsHomeSample(request, onNavigate, onEvent)
        UiSampleScreen.SettingsServers -> SettingsServersSample(request, onNavigate, onEvent)
        UiSampleScreen.SettingsPrefs -> SettingsPrefsSample(request, onNavigate, onEvent)
        UiSampleScreen.SettingsData -> SettingsDataSample(request, onNavigate, onEvent)
        else -> Unit
    }
}

@Composable
private fun SettingsHomeSample(request: UiSampleRequest, onNavigate: (UiSampleScreen) -> Unit, onEvent: (String) -> Unit) {
    val preferences = when (request.state) {
        UiSampleState.Disabled -> AppPreferences(showAudiobook = false)
        else -> AppPreferences()
    }
    val sources = MediaSourceState(
        sources = if (request.state != UiSampleState.Empty) listOf(
            MediaSource(id = "sample-music", name = "家庭音乐库", kind = MediaSourceKind.NAVIDROME, serverUrl = "https://music.example.test"),
            MediaSource(id = "sample-book", name = "我的有声书", kind = MediaSourceKind.AUDIOBOOKSHELF, serverUrl = "https://books.example.test"),
            MediaSource(id = "sample-video", name = "影院", kind = MediaSourceKind.EMBY, serverUrl = "https://video.example.test")
        ) else emptyList(),
        activeMusicId = if (request.state == UiSampleState.Empty) null else "sample-music",
        activeAudiobookId = if (request.state == UiSampleState.Empty) null else "sample-book",
        activeVideoId = if (request.state == UiSampleState.Empty) null else "sample-video"
    )
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        MediaPageHeader("设置", when {
            request.state == UiSampleState.Empty -> "尚未添加任何服务器"
            else -> "${sources.sources.size} 个已保存来源"
        }, listOf(HeaderAction(Icons.Filled.Search, "搜索设置", onClick = { onEvent("settings-search") })),
            colors, showBack = true, onBack = { onNavigate(UiSampleScreen.Catalog) }, modifier = Modifier.padding(horizontal = NordicSpacing.content))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(NordicSpacing.content),
            verticalArrangement = Arrangement.spacedBy(NordicSpacing.xs)) {
            if (request.state == UiSampleState.Error) item {
                MediaStateCard("配置读取提示", "设置无法读取,原数据未被修改。", tone = MediaStateTone.Error, density = MediaStateDensity.Compact)
            }
            SETTINGS_HOME_PAGES.filter { it !in hiddenModulePages(preferences) }.forEach { destination ->
                if (destination == SettingsPage.SERVERS) item { SettingsSectionTitle("连接") }
                if (destination == SettingsPage.APPEARANCE) item { SettingsSectionTitle("体验与播放") }
                if (destination == SettingsPage.STORAGE) item { SettingsSectionTitle("数据与应用") }
                item {
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
                    SettingsRow(destination.title, summary, icon = icon, onClick = { onEvent("settings:${destination.name}") })
                    HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
private fun SettingsServersSample(request: UiSampleRequest, onNavigate: (UiSampleScreen) -> Unit, onEvent: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val sources = if (request.state == UiSampleState.Empty) {
        MediaSourceState()
    } else MediaSourceState(
        sources = listOf(
            MediaSource(id = "sample-music", name = if (request.state == UiSampleState.LongText) "我的家庭音乐服务器 · 长名称与混合语言检查" else "家庭音乐库",
                kind = MediaSourceKind.NAVIDROME, serverUrl = "https://music.example.test"),
            MediaSource(id = "sample-book", name = "我的有声书", kind = MediaSourceKind.AUDIOBOOKSHELF, serverUrl = "https://books.example.test"),
            MediaSource(id = "sample-video", name = "NAS 影院", kind = MediaSourceKind.EMBY, serverUrl = "https://video.example.test")
        ),
        activeMusicId = "sample-music", activeAudiobookId = "sample-book", activeVideoId = "sample-video"
    )
    var menuFor by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(), contentPadding = PaddingValues(NordicSpacing.content),
        verticalArrangement = Arrangement.spacedBy(NordicSpacing.xs)) {
        item {
            MediaPageHeader("媒体服务器", "音乐、有声书和视频分别选择来源", emptyList(), colors, showBack = true,
                onBack = { onNavigate(UiSampleScreen.Catalog) })
        }
        item { SettingsRow("添加服务器", "支持 Navidrome、AudiobookShelf、Emby 与 WebDAV", icon = Icons.Filled.Add,
            onClick = { onEvent("settings:add-server") }) }
        MediaDomain.entries.forEach { domain ->
            item { SettingsSectionTitle(domain.label) }
            val entries = sources.sources.filter { it.domain == domain }
            if (entries.isEmpty()) item {
                MediaStateCard("尚未添加${domain.label}服务器", "本域的播放与浏览将暂不可用。", density = MediaStateDensity.Compact)
            }
            items(entries, key = { it.id }) { source ->
                Row(Modifier.fillMaxWidth().padding(vertical = NordicSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(sources.activeId(domain) == source.id, enabled = request.state != UiSampleState.Disabled,
                        onClick = { onEvent("settings:select") })
                    Column(Modifier.weight(1f)) {
                        Text(source.name, style = MaterialTheme.typography.titleMedium)
                        Text("${source.kind.label} · ${source.serverUrl}", maxLines = 2, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        Text(if (request.state == UiSampleState.Error)
                            "上次测试:连接未通过" else "上次测试:连接正常",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (request.state == UiSampleState.Error) colors.error else colors.onSurfaceVariant)
                    }
                    Box {
                        IconButton(onClick = { menuFor = source.id }, enabled = request.state != UiSampleState.Disabled) { Icon(Icons.Filled.MoreVert, "管理 ${source.name}") }
                        DropdownMenu(menuFor == source.id, onDismissRequest = { menuFor = null }) {
                            DropdownMenuItem(text = { Text("编辑连接") }, onClick = { menuFor = null; onEvent("settings:edit") })
                            DropdownMenuItem(text = { Text("测试连接") }, onClick = { menuFor = null; onEvent("settings:test") })
                            DropdownMenuItem(text = { Text("删除连接") }, onClick = { menuFor = null; onEvent("settings:delete") })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsPrefsSample(request: UiSampleRequest, onNavigate: (UiSampleScreen) -> Unit, onEvent: (String) -> Unit) {
    var preferences by remember {
        mutableStateOf(when (request.state) {
            UiSampleState.Empty -> AppPreferences(musicSpeed = 1f)
            else -> AppPreferences()
        })
    }
    var choice by remember { mutableStateOf<SettingsChoiceRequest?>(null) }
    val page = if (request.state == UiSampleState.LongText) SettingsPage.VIDEO else SettingsPage.APPEARANCE
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        MediaPageHeader(page.title, "偏好即时持久化(样板仅演示布局)", emptyList(), MaterialTheme.colorScheme,
            showBack = true, onBack = { onNavigate(UiSampleScreen.Catalog) })
        PreferenceSettingsPage(page, preferences, videoSource = null, highlight = null,
            update = { transform -> preferences = transform(preferences); onEvent("prefs-update") },
            choice = { selection -> choice = selection })
    }
    choice?.let { request -> SettingsChoiceDialog(request) { choice = null } }
}

@Composable
private fun SettingsDataSample(request: UiSampleRequest, onNavigate: (UiSampleScreen) -> Unit, onEvent: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(NordicSpacing.content)) {
        MediaPageHeader("存储与下载", "缓存与已下载音乐",
            emptyList(), colors, showBack = true, onBack = { onNavigate(UiSampleScreen.Catalog) })
        if (request.state == UiSampleState.Loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        SettingsSectionTitle("可清理缓存", topPadding = NordicSpacing.xs)
        SettingsRow("图片缓存", "清理后会在需要时重新加载封面。", value = "12.4 MB", icon = Icons.Filled.Image,
            enabled = request.state != UiSampleState.Disabled, onClick = { onEvent("settings:clear-images") })
        SettingsRow("媒体目录数据", "包含来源目录列表,不含收藏文件夹与观看进度。", value = "约 2.1 MB", icon = Icons.Filled.Cached,
            enabled = request.state != UiSampleState.Disabled, onClick = { onEvent("settings:clear-catalogs") })
        SettingsRow("刷新占用信息", icon = Icons.Filled.Refresh, onClick = { onEvent("settings:refresh") })
        if (request.state == UiSampleState.Empty) {
            SettingsSectionTitle("已下载音乐 · 按来源管理")
            MediaStateCard("暂无下载", "播放音乐时,可从播放器菜单下载当前曲目。", density = MediaStateDensity.Compact)
            SettingsSectionTitle("隐私与数据")
            SettingsRow("凭证加密存储", "服务器密码与密钥保存在 Android 加密存储中,不随系统备份导出。")
            SettingsRow("媒体请求与隐私", "媒体请求直接发送到你配置的服务器。本应用不提供账号云同步或使用行为统计服务。")
            SettingsRow("恢复偏好默认值", "保留服务器、下载、书签和观看进度。", enabled = request.state != UiSampleState.Disabled,
                destructive = true, onClick = { onEvent("settings:reset-prefs") })
        } else {
            SettingsSectionTitle("已下载音乐 · 按来源管理")
            SettingsRow("家庭音乐库", "3 首 · 24.1 MB", icon = Icons.Filled.Download, onClick = { onEvent("settings:downloads") })
            SettingsRow("NAS 影院", "0 首 · 0 B", icon = Icons.Filled.Download, onClick = { onEvent("settings:downloads") })
            SettingsSectionTitle("隐私与数据")
            SettingsRow("凭证加密存储", "服务器密码与密钥保存在 Android 加密存储中,不随系统备份导出。")
            SettingsRow("媒体请求与隐私", "媒体请求直接发送到你配置的服务器。本应用不提供账号云同步或使用行为统计服务。")
            if (request.state == UiSampleState.Error) MediaStateCard("清理失败", "请稍后重试,不会删除你的下载。", tone = MediaStateTone.Error, density = MediaStateDensity.Compact)
            SettingsRow("恢复偏好默认值", "保留服务器、下载、书签和观看进度。", enabled = request.state != UiSampleState.Disabled,
                destructive = true, onClick = { onEvent("settings:reset-prefs") })
        }
    }
}
