package com.nordic.mediahub.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.nordic.mediahub.data.*
import com.nordic.mediahub.ui.theme.NordicSpacing

/** Deterministic in-memory host. Pages, dialogs and sheets are the production composables. */
@Composable
internal fun MusicCatalogSample(
    request: UiSampleRequest,
    songs: List<NavidromeSong>,
    onNavigate: (UiSampleScreen) -> Unit,
    onEvent: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val long = request.state == UiSampleState.LongText
    val empty = request.state in listOf(UiSampleState.Empty, UiSampleState.LibraryEmpty)
    var loading by remember { mutableStateOf(request.state in listOf(UiSampleState.Loading, UiSampleState.Refreshing)) }
    var error by remember { mutableStateOf(request.state in listOf(UiSampleState.Error, UiSampleState.CachedError)) }
    val hasCachedContent = request.state in listOf(UiSampleState.Refreshing, UiSampleState.CachedError)
    val albums = remember(songs, long) {
        listOf("深空尽头", "沿途的风", "夜航", "留白", "向着海岸线", "远山").mapIndexed { index, title ->
            NavidromeAlbum("round-album-$index", if (long && index == 0) "在漫长的旅途中听见远方的回声 · Live Acoustic Sessions" else title,
                if (index == 4) null else "张靓颖", songs[index % songs.size].coverArt, 8 + index, 2026 - index)
        }
    }
    val artists = remember(long) {
        listOf("张靓颖", "陈粒", "房东的猫", "旅行团", "声音与记忆").mapIndexed { index, name ->
            NavidromeArtist("round-artist-$index", if (long && index == 0) "声音与记忆 · 一个拥有很长名字的独立音乐人" else name,
                4 + index, name.take(1))
        }
    }
    val playlists = remember(songs, long) {
        listOf("沿途听见", "夜航电台", "周末慢下来", "收集一场日落", "在路上", "留给自己的时间").mapIndexed { index, name ->
            NavidromePlaylist("round-playlist-$index", if (long && index == 0) "把漫长旅途里的每一道风景都写进歌里 · Acoustic Collection" else name,
                comment = if (index == 0) "留一点时间给自己，听见生活里的细小回声。\n从清晨到日落，让音乐陪你走过平凡的一天。\n这份歌单收集沿途的风、远方的海和旅途中的故事。\n慢一点，也没有关系。" else null,
                owner = if (index == 3) null else "listener", songCount = songs.size, duration = 2134,
                coverArt = songs[index % songs.size].coverArt)
        }
    }
    val isNameDialog = request.screen in listOf(UiSampleScreen.PlaylistCreate, UiSampleScreen.PlaylistRename)
    var dialog by remember { mutableStateOf(request.screen.takeIf { isNameDialog || it == UiSampleScreen.PlaylistDelete }) }
    var screen by remember { mutableStateOf(when (request.screen) {
        UiSampleScreen.PlaylistCreate -> UiSampleScreen.Playlists
        UiSampleScreen.PlaylistRename, UiSampleScreen.PlaylistDelete -> UiSampleScreen.Playlist
        else -> request.screen
    }) }
    var selectedAlbum by remember { mutableStateOf(albums.first()) }
    var selectedArtist by remember { mutableStateOf(artists.first()) }
    var selectedPlaylist by remember { mutableStateOf(playlists.first()) }
    var name by remember { mutableStateOf(if (empty || request.screen == UiSampleScreen.PlaylistCreate && !long) "" else playlists.first().name) }
    var nameError by remember { mutableStateOf(if (error) "暂时无法同步到音乐服务器。请检查连接后重试，已输入的歌单名称会保留。" else null) }
    val running = request.state in listOf(UiSampleState.Loading, UiSampleState.Disabled)
    var sort by remember { mutableStateOf(NavidromeAlbumSort.RecentlyAdded) }
    var query by remember { mutableStateOf(if (request.screen == UiSampleScreen.SearchLanding) "" else "沿途") }
    var currentSong by remember { mutableStateOf(songs.first()) }
    var playing by remember { mutableStateOf(false) }
    fun navigate(next: UiSampleScreen) { screen = next; onEvent("navigate:${next.id}") }
    val playSong: (List<NavidromeSong>, Int, Boolean) -> Unit = { queue, index, allowFallback ->
        currentSong = queue[index]; playing = true
        onEvent("song:${queue[index].id}:$index:$allowFallback")
    }
    val openAlbum: (NavidromeAlbum) -> Unit = { selectedAlbum = it; navigate(UiSampleScreen.Album) }
    val openArtist: (NavidromeArtist) -> Unit = { selectedArtist = it; navigate(UiSampleScreen.Artist) }
    fun retry() { loading = false; error = false; onEvent("retry:${screen.id}") }
    val hideContent = empty || ((loading || error) && !hasCachedContent)
    val shownAlbums = if (hideContent) emptyList() else albums
    val shownSongs = if (hideContent) emptyList() else songs
    val shownArtists = if (hideContent) emptyList() else artists
    val shownPlaylists = if (hideContent) emptyList() else playlists
    val isSearch = screen in listOf(UiSampleScreen.Search, UiSampleScreen.SearchLanding)
    BackHandler(screen != request.screen) { navigate(UiSampleScreen.Home) }
    val feedback = MusicLibraryFeedbackState(
        error = if (error && !isSearch) "连接暂时不可用，请稍后重试。" else null,
        hasContent = hasCachedContent,
        isInitialLoading = loading && !hasCachedContent && screen in listOf(UiSampleScreen.Home, UiSampleScreen.Artists),
        showSetup = request.state == UiSampleState.Empty && screen == UiSampleScreen.Home
    )

    if (screen in listOf(UiSampleScreen.Equalizer, UiSampleScreen.MusicActions)) {
        PlayerSample(request, songs, onNavigate, onEvent)
        var open by remember { mutableStateOf(true) }
        if (open && screen == UiSampleScreen.Equalizer) {
            var preset by remember { mutableIntStateOf(-1) }
            var levels by remember { mutableStateOf(List<Short>(5) { 0 }) }
            MusicEqualizerContent(
                available = !empty && !error, presetNames = listOf("正常", "古典", "流行", "摇滚", "人声", "爵士"),
                bandCount = 5, bandLevelRange = -1500 to 1500, centerFreqs = listOf(60000, 230000, 910000, 3600000, 14000000),
                bandLevels = levels, selectedPreset = preset, colorScheme = colors, onDismiss = { open = false },
                onSelectPreset = { preset = it; levels = listOf(100, 300, 0, -100, 200).map(Int::toShort); onEvent("eq-preset:$it") },
                onBandLevelChange = { band, level -> levels = levels.toMutableList().also { it[band] = level }; preset = -1; onEvent("eq-band:$band") }
            )
        }
        if (open && screen == UiSampleScreen.MusicActions) {
            val download = when (request.state) {
                UiSampleState.Loading -> DownloadStateEntry(DownloadState.DOWNLOADING, .42f, songs.first())
                UiSampleState.Disabled -> DownloadStateEntry(DownloadState.DOWNLOADED, 1f, songs.first())
                UiSampleState.Error -> DownloadStateEntry(errorMessage = "下载失败，请检查网络或存储空间后重试。")
                else -> null
            }
            MusicActionsSheet(if (empty) null else songs.first(), download, colors,
                onDownloadSong = { onEvent("download-preview") }, onCancelDownload = { onEvent("cancel-download-preview") }, onDismiss = { open = false })
        }
        return
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        MediaPageHeader(
            title = when (screen) {
                UiSampleScreen.Home -> "音乐库"
                UiSampleScreen.Albums, UiSampleScreen.Album -> "专辑"
                UiSampleScreen.Artists, UiSampleScreen.Artist -> "歌手"
                UiSampleScreen.Search, UiSampleScreen.SearchLanding -> "搜索"
                UiSampleScreen.Songs -> "歌曲"
                else -> "歌单"
            },
            subtitle = if (request.state == UiSampleState.CachedError && error) "刷新失败，先显示本地缓存" else "",
            actions = listOf(HeaderAction(Icons.Filled.Settings, "打开设置", fixed = true) { onNavigate(UiSampleScreen.Modules) },
                HeaderAction(Icons.Filled.Refresh, "刷新音乐", enabled = !loading) { retry() }),
            colorScheme = colors, showBack = screen != UiSampleScreen.Home, onBack = { navigate(UiSampleScreen.Home) },
            modifier = Modifier.padding(horizontal = NordicSpacing.content)
        )
        // Match MusicScreenV2: subpages have Back, not another root navigation row.
        if (screen == UiSampleScreen.Home) {
            Box(Modifier.padding(horizontal = NordicSpacing.content, vertical = NordicSpacing.sm)) {
                MusicSegmentedTabs(if (screen == UiSampleScreen.Playlists) 2 else if (screen == UiSampleScreen.Songs) 1 else 0, colors,
                    onTabSelected = { navigate(listOf(UiSampleScreen.Home, UiSampleScreen.Songs, UiSampleScreen.Playlists)[it]) },
                    onSearchClick = { navigate(UiSampleScreen.SearchLanding); query = "" })
            }
        }
        Box(Modifier.weight(1f)) {
            when (screen) {
                UiSampleScreen.Home -> MusicHomePage(shownAlbums, shownSongs, shownArtists, shownSongs, shownSongs, shownAlbums, shownArtists,
                    colors, openAlbum, playSong, { navigate(UiSampleScreen.Albums) }, { navigate(UiSampleScreen.Songs) },
                    { navigate(UiSampleScreen.Artists) }, openArtist, feedback = feedback, onRetry = ::retry)
                UiSampleScreen.Albums -> MusicAlbumsPage(shownAlbums.sortedBy { if (sort == NavidromeAlbumSort.Name) it.name else it.id }, loading, sort, colors,
                    openAlbum, { sort = it; onEvent("album-sort:$it") }, feedback = feedback, onRetry = ::retry)
                UiSampleScreen.Artists -> MusicArtistsPage(shownArtists, colors, openArtist, feedback = feedback, onRetry = ::retry)
                UiSampleScreen.Artist -> MusicArtistDetailPage(selectedArtist, loading, shownAlbums, colors, openAlbum,
                    { playing = true; onEvent("play-artist") }, hasVisibleError = error, feedback = feedback, onRetry = ::retry)
                UiSampleScreen.Album -> MusicAlbumDetailPage(selectedAlbum, loading, shownSongs, colors, playSong,
                    { playing = true; onEvent("play-album") }, hasVisibleError = error, feedback = feedback, onRetry = ::retry)
                UiSampleScreen.Search, UiSampleScreen.SearchLanding -> MusicSearchPage(query,
                    if (query.isBlank()) null else SearchMusicResult(shownArtists.take(2), shownAlbums.take(3), shownSongs), loading,
                    if (error) "搜索暂时不可用，请检查连接后重试。" else null,
                    if (empty) emptyList() else albums, if (empty) emptyList() else songs, if (empty) emptyList() else songs,
                    if (empty) emptyList() else artists, colors, playSong, openAlbum, openArtist,
                    { query = it; error = false; loading = false; onEvent("search:$it") }, { query = ""; onEvent("search-clear") }, feedback = feedback, onRetry = ::retry)
                UiSampleScreen.Playlists -> MusicPlaylistsPage(loading, shownPlaylists, colors,
                    { selectedPlaylist = it; navigate(UiSampleScreen.Playlist) }, { name = ""; nameError = null; dialog = UiSampleScreen.PlaylistCreate }, feedback = feedback, onRetry = ::retry)
                UiSampleScreen.Playlist -> MusicPlaylistDetailPage(selectedPlaylist, loading, shownSongs, colors, playSong,
                    { playing = true; onEvent("play-playlist") },
                    { name = selectedPlaylist.name; nameError = null; dialog = UiSampleScreen.PlaylistRename },
                    { nameError = null; dialog = UiSampleScreen.PlaylistDelete }, hasVisibleError = error, feedback = feedback, onRetry = ::retry)
                UiSampleScreen.Songs -> MusicSongsPage(songs, songs, "", MusicSongSort.Default, colors, playSong, {}, {}, {}, feedback = feedback, onRetry = ::retry)
                else -> Unit
            }
        }
        PolishedPlaybackDock(0, colors, DockNowPlayingContent.Music(currentSong), playing,
            onOpenPlayer = { onNavigate(UiSampleScreen.Player) }, onPlayPause = { playing = !playing; onEvent("dock-play") },
            onSelect = { onEvent("domain:$it") })
    }
    if (dialog in listOf(UiSampleScreen.PlaylistCreate, UiSampleScreen.PlaylistRename)) {
        MusicPlaylistNameDialog(dialog == UiSampleScreen.PlaylistCreate, name, running, nameError, colors,
            onNameChange = { name = it }, onSubmit = {
                if (name.isNotBlank() && !running) {
                    onEvent("playlist-submit:${name.trim()}"); selectedPlaylist = selectedPlaylist.copy(name = name.trim()); dialog = null
                }
            }, onDismiss = { dialog = null; onEvent("playlist-dismiss") })
    }
    if (dialog == UiSampleScreen.PlaylistDelete) {
        MusicPlaylistDeleteDialog(selectedPlaylist.name, running, nameError, colors,
            onConfirm = { if (!running) { dialog = null; navigate(UiSampleScreen.Playlists); onEvent("playlist-delete") } },
            onDismiss = { dialog = null; onEvent("playlist-dismiss") })
    }
}
