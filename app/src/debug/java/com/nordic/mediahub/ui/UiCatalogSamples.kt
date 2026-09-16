package com.nordic.mediahub.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
    AudiobookBookmarks("ab_bookmarks", "书签")
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
        UiSampleScreen.AudiobookHome, UiSampleScreen.AudiobookDetail, UiSampleScreen.AudiobookPlayer,
        UiSampleScreen.AudiobookChapters, UiSampleScreen.AudiobookSpeed, UiSampleScreen.AudiobookSleep,
        UiSampleScreen.AudiobookBookmarks -> AudiobookCatalogSample(request, onNavigate, onEvent)
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
