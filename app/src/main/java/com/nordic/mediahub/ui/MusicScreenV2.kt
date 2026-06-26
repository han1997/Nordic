package com.nordic.mediahub.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.nordic.mediahub.api.NavidromeAlbum
import com.nordic.mediahub.api.NavidromeArtist
import com.nordic.mediahub.api.NavidromePlaylist
import com.nordic.mediahub.api.NavidromeSong
import com.nordic.mediahub.data.ConfigRepository
import com.nordic.mediahub.data.DownloadState
import com.nordic.mediahub.data.DownloadStateEntry
import com.nordic.mediahub.data.MusicDownloadManager
import com.nordic.mediahub.data.NavidromeAlbumSort
import com.nordic.mediahub.data.NavidromeConfig
import com.nordic.mediahub.data.NavidromeMusicCacheRepository
import com.nordic.mediahub.data.NavidromeRepository
import com.nordic.mediahub.data.PlayHistoryEntry
import com.nordic.mediahub.data.SearchMusicResult
import com.nordic.mediahub.data.StarredContent
import com.nordic.mediahub.data.isReadyForMusicSync
import com.nordic.mediahub.data.loadNavidromeMusicRefresh
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

private enum class MusicLibraryPage {
    Home,
    Albums,
    Songs,
    Artists,
    ArtistDetail,
    AlbumDetail,
    Search,
    Playlists,
    PlaylistDetail
}

private enum class MusicSongSort {
    Default,
    Added,
    Title,
    Artist,
    Album,
    Duration
}

@Composable
fun MusicScreenV2(
    isDark: Boolean,
    onThemeToggle: (Boolean) -> Unit,
    onSongSelected: (List<NavidromeSong>, Int) -> Unit = { _, _ -> },
    downloadManager: MusicDownloadManager = MusicDownloadManager(LocalContext.current),
    onAddToQueue: ((NavidromeSong) -> Unit)? = null,
    currentPlayingSong: NavidromeSong? = null,
    isMusicPlaying: Boolean = false,
    onPlayPause: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    onOpenPlayer: (() -> Unit)? = null,
    playHistoryEntries: List<PlayHistoryEntry> = emptyList()
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val repository = remember { ConfigRepository(context) }
    val cacheRepository = remember { NavidromeMusicCacheRepository(context) }
    val downloadStatesMap by downloadManager.downloadStates.collectAsStateWithLifecycle(emptyMap())
    val savedConfig by repository.navidromeConfig.collectAsStateWithLifecycle(NavidromeConfig())
    val navidromeRepository = remember(savedConfig) {
        if (savedConfig.isReadyForMusicSync()) NavidromeRepository(savedConfig) else null
    }
    var config by remember { mutableStateOf(NavidromeConfig()) }
    var showConfig by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }
    var libraryPage by remember { mutableStateOf(MusicLibraryPage.Home) }
    var albums by remember { mutableStateOf(emptyList<NavidromeAlbum>()) }
    var sortedAlbums by remember { mutableStateOf(emptyList<NavidromeAlbum>()) }
    var albumSort by remember { mutableStateOf(NavidromeAlbumSort.RecentlyAdded) }
    var songSort by remember { mutableStateOf(MusicSongSort.Default) }
    var songs by remember { mutableStateOf(emptyList<NavidromeSong>()) }
    var recentlyAddedSongs by remember { mutableStateOf(emptyList<NavidromeSong>()) }
    var artists by remember { mutableStateOf(emptyList<NavidromeArtist>()) }
    var playlists by remember { mutableStateOf(emptyList<NavidromePlaylist>()) }
    var isLoading by remember { mutableStateOf(false) }
    var isLoadingAlbumList by remember { mutableStateOf(false) }
    var isLoadingPlaylists by remember { mutableStateOf(false) }
    var loadingAlbumId by remember { mutableStateOf<String?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var selectedAlbum by remember { mutableStateOf<NavidromeAlbum?>(null) }
    var albumDetailSongs by remember { mutableStateOf(emptyList<NavidromeSong>()) }
    var isLoadingAlbumDetail by remember { mutableStateOf(false) }
    var selectedArtist by remember { mutableStateOf<NavidromeArtist?>(null) }
    var artistAlbums by remember { mutableStateOf(emptyList<NavidromeAlbum>()) }
    var isLoadingArtistDetail by remember { mutableStateOf(false) }
    var selectedPlaylist by remember { mutableStateOf<NavidromePlaylist?>(null) }
    var playlistSongs by remember { mutableStateOf(emptyList<NavidromeSong>()) }
    var isLoadingPlaylistDetail by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResult by remember { mutableStateOf<SearchMusicResult?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    val searchJob = remember { AtomicReference<Job?>(null) }
    var cacheUpdatedAtMillis by remember { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()

    // Starred content state
    var starredContent by remember { mutableStateOf(StarredContent()) }
    var isLoadingStarred by remember { mutableStateOf(false) }

    // Playlist management dialog state
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var createPlaylistName by remember { mutableStateOf("") }
    var showRenamePlaylistDialog by remember { mutableStateOf(false) }
    var renamePlaylistTarget by remember { mutableStateOf<NavidromePlaylist?>(null) }
    var renamePlaylistName by remember { mutableStateOf("") }
    var showDeletePlaylistConfirm by remember { mutableStateOf(false) }
    var deletePlaylistTarget by remember { mutableStateOf<NavidromePlaylist?>(null) }

    // Add to playlist dialog state
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var addToPlaylistSongId by remember { mutableStateOf<String?>(null) }

    suspend fun applyCachedMusicData(targetConfig: NavidromeConfig): Boolean {
        val cached = cacheRepository.load(targetConfig)
        if (cached == null) {
            albums = emptyList()
            songs = emptyList()
            recentlyAddedSongs = emptyList()
            artists = emptyList()
            cacheUpdatedAtMillis = null
            return false
        }

        albums = cached.albums
        songs = cached.songs
        recentlyAddedSongs = cached.recentlyAddedSongs
        artists = cached.artists
        cacheUpdatedAtMillis = cached.updatedAtMillis
        errorMsg = null
        return true
    }

    suspend fun refreshMusicData(targetConfig: NavidromeConfig): Boolean {
        if (!targetConfig.isReadyForMusicSync() || isLoading) return false

        isLoading = true
        errorMsg = null
        return try {
            val freshData = loadNavidromeMusicRefresh(
                targetConfig = targetConfig,
                savedConfig = savedConfig,
                savedRepository = navidromeRepository
            ) ?: return false
            val freshCache = cacheRepository.buildCache(
                config = targetConfig,
                albums = freshData.albums,
                songs = freshData.songs,
                recentlyAddedSongs = freshData.recentlyAddedSongs,
                artists = freshData.artists
            )

            albums = freshData.albums
            songs = freshData.songs
            recentlyAddedSongs = freshData.recentlyAddedSongs
            artists = freshData.artists
            cacheUpdatedAtMillis = freshCache.updatedAtMillis
            cacheRepository.save(targetConfig, freshCache)
            true
        } catch (e: Exception) {
            val hasCachedContent = albums.isNotEmpty() || songs.isNotEmpty() || artists.isNotEmpty()
            errorMsg = if (hasCachedContent) {
                "正在显示上次缓存：${e.message}"
            } else {
                "连接失败: ${e.message}"
            }
            false
        } finally {
            isLoading = false
        }
    }

    suspend fun loadAlbumList(sort: NavidromeAlbumSort): Boolean {
        if (isLoadingAlbumList) return false
        val repo = navidromeRepository
        if (repo == null) {
            errorMsg = "请先保存 Navidrome 配置"
            return false
        }

        albumSort = sort
        isLoadingAlbumList = true
        errorMsg = null
        return try {
            sortedAlbums = repo.getAlbums(sort)
            true
        } catch (e: Exception) {
            errorMsg = "获取专辑列表失败: ${e.message}"
            false
        } finally {
            isLoadingAlbumList = false
        }
    }

    fun openAlbumLibrary() {
        libraryPage = MusicLibraryPage.Albums
        if (sortedAlbums.isEmpty()) {
            scope.launch { loadAlbumList(albumSort) }
        }
    }

    suspend fun loadPlaylists(): Boolean {
        if (isLoadingPlaylists) return false
        val repo = navidromeRepository
        if (repo == null) {
            errorMsg = "请先保存 Navidrome 配置"
            return false
        }

        isLoadingPlaylists = true
        errorMsg = null
        return try {
            playlists = repo.getPlaylists()
            true
        } catch (e: Exception) {
            errorMsg = "获取歌单失败: ${e.message}"
            false
        } finally {
            isLoadingPlaylists = false
        }
    }

    fun openPlaylistLibrary() {
        selectedTab = 2
        libraryPage = MusicLibraryPage.Playlists
        if (playlists.isEmpty()) {
            scope.launch { loadPlaylists() }
        }
    }

    suspend fun loadStarredContent(): Boolean {
        if (isLoadingStarred) return false
        val repo = navidromeRepository
        if (repo == null) return false

        isLoadingStarred = true
        return try {
            starredContent = repo.getStarred2()
            true
        } catch (_: Exception) {
            false
        } finally {
            isLoadingStarred = false
        }
    }

    fun toggleSongStar(song: NavidromeSong) {
        val repo = navidromeRepository ?: return
        scope.launch {
            try {
                if (song.starred != null) {
                    repo.unstar(id = song.id)
                } else {
                    repo.star(id = song.id)
                }
                // Update local state
                val updatedSong = song.copy(starred = if (song.starred != null) null else "now")
                songs = songs.map { if (it.id == song.id) updatedSong else it }
                recentlyAddedSongs = recentlyAddedSongs.map { if (it.id == song.id) updatedSong else it }
                albumDetailSongs = albumDetailSongs.map { if (it.id == song.id) updatedSong else it }
                playlistSongs = playlistSongs.map { if (it.id == song.id) updatedSong else it }
                loadStarredContent()
            } catch (_: Exception) {}
        }
    }

    fun toggleAlbumStar(album: NavidromeAlbum) {
        val repo = navidromeRepository ?: return
        scope.launch {
            try {
                if (album.starred != null) {
                    repo.unstar(albumId = album.id)
                } else {
                    repo.star(albumId = album.id)
                }
                val updatedAlbum = album.copy(starred = if (album.starred != null) null else "now")
                albums = albums.map { if (it.id == album.id) updatedAlbum else it }
                sortedAlbums = sortedAlbums.map { if (it.id == album.id) updatedAlbum else it }
                artistAlbums = artistAlbums.map { if (it.id == album.id) updatedAlbum else it }
                if (selectedAlbum?.id == album.id) {
                    selectedAlbum = updatedAlbum
                }
                loadStarredContent()
            } catch (_: Exception) {}
        }
    }

    fun toggleArtistStar(artist: NavidromeArtist) {
        val repo = navidromeRepository ?: return
        scope.launch {
            try {
                if (artist.starred != null) {
                    repo.unstar(artistId = artist.id)
                } else {
                    repo.star(artistId = artist.id)
                }
                val updatedArtist = artist.copy(starred = if (artist.starred != null) null else "now")
                artists = artists.map { if (it.id == artist.id) updatedArtist else it }
                if (selectedArtist?.id == artist.id) {
                    selectedArtist = updatedArtist
                }
                loadStarredContent()
            } catch (_: Exception) {}
        }
    }

    fun toggleSongDownload(song: NavidromeSong) {
        if (downloadManager.isDownloaded(song.id)) {
            downloadManager.deleteDownload(song.id)
        } else {
            if (savedConfig.isReadyForMusicSync()) {
                downloadManager.downloadSong(song, savedConfig)
            }
        }
    }

    fun openSearch() {
        searchJob.get()?.cancel()
        searchQuery = ""
        searchResult = null
        searchError = null
        isSearching = false
        libraryPage = MusicLibraryPage.Search
    }

    suspend fun playAlbum(album: NavidromeAlbum) {
        if (loadingAlbumId != null) return
        val repo = navidromeRepository
        if (repo == null) {
            errorMsg = "请先保存 Navidrome 配置"
            return
        }

        loadingAlbumId = album.id
        errorMsg = null
        try {
            val albumSongs = repo.getAlbumSongs(album.id)
            val firstPlayableSong = albumSongs.firstOrNull { !it.streamUrl.isNullOrBlank() }
                ?: albumSongs.firstOrNull()
            if (firstPlayableSong == null) {
                errorMsg = "这张专辑没有可播放曲目"
            } else {
                onSongSelected(albumSongs, albumSongs.indexOf(firstPlayableSong).coerceAtLeast(0))
            }
        } catch (e: Exception) {
            errorMsg = "获取专辑曲目失败: ${e.message}"
        } finally {
            loadingAlbumId = null
        }
    }

    fun openAlbumDetail(album: NavidromeAlbum) {
        selectedAlbum = album
        albumDetailSongs = emptyList()
        isLoadingAlbumDetail = true
        libraryPage = MusicLibraryPage.AlbumDetail
        scope.launch {
            try {
                navidromeRepository?.let { repo ->
                    albumDetailSongs = repo.getAlbumSongs(album.id)
                }
            } catch (_: Exception) {}
            isLoadingAlbumDetail = false
        }
    }

    fun openArtistDetail(artist: NavidromeArtist) {
        selectedArtist = artist
        artistAlbums = emptyList()
        isLoadingArtistDetail = true
        libraryPage = MusicLibraryPage.ArtistDetail
        scope.launch {
            try {
                navidromeRepository?.let { repo ->
                    artistAlbums = repo.getArtistAlbums(artist.id)
                }
            } catch (_: Exception) {}
            isLoadingArtistDetail = false
        }
    }

    fun openPlaylistDetail(playlist: NavidromePlaylist) {
        selectedPlaylist = playlist
        playlistSongs = emptyList()
        isLoadingPlaylistDetail = true
        errorMsg = null
        libraryPage = MusicLibraryPage.PlaylistDetail
        scope.launch {
            try {
                navidromeRepository?.let { repo ->
                    playlistSongs = repo.getPlaylistSongs(playlist.id)
                }
            } catch (e: Exception) {
                errorMsg = "获取歌单曲目失败: ${e.message}"
            } finally {
                isLoadingPlaylistDetail = false
            }
        }
    }

    LaunchedEffect(savedConfig) {
        config = savedConfig
        sortedAlbums = emptyList()
        playlists = emptyList()
        playlistSongs = emptyList()
        selectedPlaylist = null
        searchJob.get()?.cancel()
        searchResult = null
        searchError = null
        isSearching = false
        albumSort = NavidromeAlbumSort.RecentlyAdded
        if (savedConfig.isReadyForMusicSync()) {
            applyCachedMusicData(savedConfig)
            refreshMusicData(savedConfig)
        } else {
            albums = emptyList()
            songs = emptyList()
            recentlyAddedSongs = emptyList()
            artists = emptyList()
            sortedAlbums = emptyList()
            playlists = emptyList()
            playlistSongs = emptyList()
            selectedPlaylist = null
            libraryPage = MusicLibraryPage.Home
            cacheUpdatedAtMillis = null
            errorMsg = null
        }
    }

    LaunchedEffect(navidromeRepository) {
        if (navidromeRepository != null) {
            loadStarredContent()
        } else {
            starredContent = StarredContent()
        }
    }

    LaunchedEffect(Unit) {
        downloadManager.restoreDownloadState()
    }

    LaunchedEffect(songs, recentlyAddedSongs) {
        downloadManager.updateSongMetadata(songs + recentlyAddedSongs)
    }

    val hasContent = albums.isNotEmpty() || songs.isNotEmpty() || artists.isNotEmpty() || playlists.isNotEmpty()
    val visibleSongs = remember(songs, songSort) {
        sortMusicSongs(songs, songSort)
    }
    val homeSongs = remember(recentlyAddedSongs) { recentlyAddedSongs.take(12) }
    val homeAlbums = remember(albums) { albums.take(10) }
    val homeArtists = remember(artists) { artists.take(10) }
    val downloadedSongs = remember(downloadStatesMap, songs, recentlyAddedSongs) {
        downloadManager.getDownloadedSongs()
    }
    val historySongs = remember(
        playHistoryEntries,
        songs,
        recentlyAddedSongs,
        downloadedSongs,
        albumDetailSongs,
        playlistSongs,
        searchResult,
        starredContent
    ) {
        val songMap = (
            songs +
                recentlyAddedSongs +
                downloadedSongs +
                albumDetailSongs +
                playlistSongs +
                (searchResult?.songs ?: emptyList()) +
                starredContent.songs
            ).associateBy { it.id }
        playHistoryEntries.take(20).mapNotNull { entry -> songMap[entry.songId] }
    }
    val cacheAgeLabel = remember(cacheUpdatedAtMillis) { formatCacheAge(cacheUpdatedAtMillis) }
    val headerActions = remember(config.isReadyForMusicSync(), isLoading, isDark) {
        buildList {
        if (config.isReadyForMusicSync()) {
            add(
                HeaderAction(
                    icon = if (isLoading) "…" else "↻",
                    enabled = !isLoading,
                    onClick = {
                        scope.launch {
                            if (refreshMusicData(config) && libraryPage == MusicLibraryPage.Albums) {
                                loadAlbumList(albumSort)
                            }
                            if (libraryPage == MusicLibraryPage.Playlists) {
                                loadPlaylists()
                            }
                        }
                    }
                )
            )
        }
        add(HeaderAction(if (isDark) "☀" else "☾") { onThemeToggle(!isDark) })
        add(HeaderAction("⚙") { showConfig = !showConfig })
    }
    }
    val isHomePage = libraryPage == MusicLibraryPage.Home
    val headerTitle = when (libraryPage) {
        MusicLibraryPage.Home -> "音乐库"
        MusicLibraryPage.Albums -> "专辑"
        MusicLibraryPage.Songs -> "歌曲"
        MusicLibraryPage.Artists -> "常听歌手"
        MusicLibraryPage.ArtistDetail -> selectedArtist?.name ?: "歌手"
        MusicLibraryPage.AlbumDetail -> selectedAlbum?.name ?: "专辑"
        MusicLibraryPage.Search -> "搜索"
        MusicLibraryPage.Playlists -> "歌单"
        MusicLibraryPage.PlaylistDetail -> selectedPlaylist?.name ?: "歌单"
    }
    val headerSubtitle = when (libraryPage) {
        MusicLibraryPage.Home -> when {
            isLoading && hasContent -> "正在刷新，先显示本地缓存"
            cacheAgeLabel != null -> "本地缓存，$cacheAgeLabel"
            hasContent -> "最近添加按曲目展示，点一下直接播放"
            else -> "连接 Navidrome 后，这里会自动同步你的内容"
        }
        MusicLibraryPage.Albums -> when {
            isLoadingAlbumList -> "正在按${albumSort.displayLabel()}加载专辑"
            sortedAlbums.isNotEmpty() -> "${sortedAlbums.size} 张专辑 · ${albumSort.displayLabel()}"
            else -> "按${albumSort.displayLabel()}浏览 Navidrome 专辑"
        }
        MusicLibraryPage.Songs -> "共 ${songs.size} 首，点一下直接播放"
        MusicLibraryPage.Artists -> "共 ${artists.size} 位歌手"
        MusicLibraryPage.ArtistDetail -> "${selectedArtist?.albumCount ?: 0} 张专辑"
        MusicLibraryPage.AlbumDetail -> selectedAlbum?.artist ?: ""
        MusicLibraryPage.Search -> "搜索歌曲、专辑、歌手"
        MusicLibraryPage.Playlists -> when {
            isLoadingPlaylists -> "正在加载 Navidrome 歌单"
            playlists.isNotEmpty() -> "共 ${playlists.size} 个歌单"
            else -> "浏览和播放 Navidrome 歌单"
        }
        MusicLibraryPage.PlaylistDetail -> when {
            isLoadingPlaylistDetail -> "正在加载歌单曲目"
            playlistSongs.isNotEmpty() -> "${playlistSongs.size} 首 · 点一下直接播放"
            else -> selectedPlaylist?.comment ?: "歌单曲目"
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, top = 18.dp, end = 18.dp, bottom = if (currentPlayingSong != null && isMusicPlaying) 88.dp else 28.dp),
        verticalArrangement = Arrangement.spacedBy(if (isHomePage) 18.dp else 10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                if (!isHomePage) {
                    MusicBackButton(
                        colorScheme = colorScheme,
                        onClick = {
                            if (libraryPage == MusicLibraryPage.PlaylistDetail) {
                                selectedTab = 2
                                libraryPage = MusicLibraryPage.Playlists
                            } else {
                                selectedTab = 0
                                libraryPage = MusicLibraryPage.Home
                            }
                        }
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        headerTitle,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        headerSubtitle,
                        fontSize = 14.sp,
                        color = colorScheme.onSurface.copy(alpha = 0.62f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                HeaderActionGroup(actions = headerActions)
            }
        }
        item {
            AnimatedVisibility(
                visible = showConfig,
                enter = fadeIn(tween(300, easing = FastOutSlowInEasing)) + expandVertically(),
                exit = fadeOut(tween(200)) + shrinkVertically()
            ) {
                NavidromeConfigCard(
                    config = config,
                    colorScheme = colorScheme,
                    onConfigChange = { config = it },
                    onSave = {
                        scope.launch {
                            val nextConfig = config
                            repository.saveNavidromeConfig(nextConfig)
                            applyCachedMusicData(nextConfig)
                            if (refreshMusicData(nextConfig)) {
                                showConfig = false
                            }
                        }
                    }
                )
            }
        }

        if (isHomePage) {
            item {
                MusicSegmentedTabs(
                    selectedTab = selectedTab,
                    colorScheme = colorScheme,
                    onTabSelected = {
                        selectedTab = it
                        libraryPage = when (it) {
                            1 -> MusicLibraryPage.Songs
                            2 -> {
                                if (playlists.isEmpty()) {
                                    scope.launch { loadPlaylists() }
                                }
                                MusicLibraryPage.Playlists
                            }
                            else -> MusicLibraryPage.Home
                        }
                    },
                    onSearchClick = { openSearch() }
                )
            }
        }

        if (errorMsg != null) {
            item {
                MediaStateCard(
                    title = if (hasContent) "刷新失败" else "连接失败",
                    subtitle = errorMsg.orEmpty(),
                    tone = MediaStateTone.Error
                )
            }
        }

        if (isLoading && !hasContent) {
            item {
                MediaLoadingCard(
                    title = "正在同步 Navidrome",
                    subtitle = "加载专辑、歌曲和歌手..."
                )
            }
        }

        if (!isLoading && !isLoadingPlaylists && !isLoadingPlaylistDetail && errorMsg == null && !hasContent) {
            item {
                MediaStateCard(
                    title = "先接入你的音乐库",
                    subtitle = "填入 Navidrome 地址、用户名和密码后，最近添加的专辑和歌曲会直接出现在这里。",
                    hint = "点右上角设置开始连接"
                )
            }
        }

        when (libraryPage) {
            MusicLibraryPage.Home -> {
                if (albums.isNotEmpty()) {
                    item {
                        MusicSectionHeader(
                            title = "刚刚同步",
                            subtitle = "最新进入曲库的专辑，先从这里开始",
                            colorScheme = colorScheme
                        )
                    }
                    item {
                        MusicHeroBanner(
                            album = albums.first(),
                            colorScheme = colorScheme,
                            onClick = {
                                openAlbumDetail(albums.first())
                            },
                            onToggleStar = { toggleAlbumStar(albums.first()) }
                        )
                    }
                }

                // Starred section
                if (starredContent.albums.isNotEmpty() || starredContent.artists.isNotEmpty()) {
                    item {
                        MusicSectionHeader(
                            title = "我的收藏",
                            subtitle = "你收藏的专辑和歌手",
                            colorScheme = colorScheme
                        )
                    }
                    if (starredContent.albums.isNotEmpty()) {
                        item {
                            val homeStarredAlbums = starredContent.albums.take(10)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(
                                    items = homeStarredAlbums,
                                    key = { "starred-album-${it.id}" },
                                    contentType = { "starred-album-card" }
                                ) { album ->
                                    CompactAlbumShelfCard(
                                        album = album,
                                        colorScheme = colorScheme,
                                        onClick = { openAlbumDetail(album) },
                                        onToggleStar = { toggleAlbumStar(album) }
                                    )
                                }
                            }
                        }
                    }
                    if (starredContent.artists.isNotEmpty()) {
                        item {
                            val homeStarredArtists = starredContent.artists.take(10)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(
                                    items = homeStarredArtists,
                                    key = { "starred-artist-${it.id}" },
                                    contentType = { "starred-artist-card" }
                                ) { artist ->
                                    ArtistShelfCard(
                                        artist = artist,
                                        colorScheme = colorScheme,
                                        onClick = { openArtistDetail(artist) },
                                        onToggleStar = { toggleArtistStar(artist) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Downloaded section
                if (downloadedSongs.isNotEmpty()) {
                    item {
                        MusicSectionHeader(
                            title = "已下载",
                            subtitle = "离线可播放的歌曲",
                            colorScheme = colorScheme
                        )
                    }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            itemsIndexed(
                                items = downloadedSongs,
                                key = { _, song -> "downloaded-song-${song.id}" },
                                contentType = { _, _ -> "downloaded-song-card" }
                            ) { index, song ->
                                SongShelfCard(
                                    song = song,
                                    colorScheme = colorScheme,
                                    onClick = {
                                        onSongSelected(downloadedSongs, index)
                                    },
                                    onToggleStar = { toggleSongStar(song) },
                                    downloadState = downloadStatesMap[song.id]?.state ?: DownloadState.NOT_DOWNLOADED,
                                    downloadProgress = downloadStatesMap[song.id]?.progress ?: 0f,
                                    onToggleDownload = { toggleSongDownload(song) }
                                )
                            }
                        }
                    }
                }

                // Recently played section
                if (historySongs.isNotEmpty()) {
                    item {
                        MusicSectionHeader(
                            title = "最近播放",
                            subtitle = "你最近听过的歌曲",
                            colorScheme = colorScheme
                        )
                    }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            itemsIndexed(
                                items = historySongs,
                                key = { _, song -> "history-song-${song.id}" },
                                contentType = { _, _ -> "history-song-card" }
                            ) { index, song ->
                                SongShelfCard(
                                    song = song,
                                    colorScheme = colorScheme,
                                    onClick = {
                                        onSongSelected(historySongs, index)
                                    },
                                    onToggleStar = { toggleSongStar(song) },
                                    downloadState = downloadStatesMap[song.id]?.state ?: DownloadState.NOT_DOWNLOADED,
                                    downloadProgress = downloadStatesMap[song.id]?.progress ?: 0f,
                                    onToggleDownload = { toggleSongDownload(song) }
                                )
                            }
                        }
                    }
                }

                if (recentlyAddedSongs.isNotEmpty()) {
                    item {
                        MusicSectionHeader(
                            title = "最近添加",
                            subtitle = "新同步到曲库的曲目，点一下直接播放",
                            colorScheme = colorScheme,
                            actionLabel = "全部",
                            onAction = {
                                selectedTab = 1
                                libraryPage = MusicLibraryPage.Songs
                            }
                        )
                    }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            itemsIndexed(
                                items = homeSongs,
                                key = { _, song -> "home-song-${song.id}" },
                                contentType = { _, _ -> "home-song-card" }
                            ) { index, song ->
                                SongShelfCard(
                                    song = song,
                                    colorScheme = colorScheme,
                                    onClick = {
                                        onSongSelected(homeSongs, index)
                                    },
                                    onToggleStar = { toggleSongStar(song) },
                                    downloadState = downloadStatesMap[song.id]?.state ?: DownloadState.NOT_DOWNLOADED,
                                    downloadProgress = downloadStatesMap[song.id]?.progress ?: 0f,
                                    onToggleDownload = { toggleSongDownload(song) }
                                )
                            }
                        }
                    }
                }

                if (albums.isNotEmpty()) {
                    item {
                        MusicSectionHeader(
                            title = "最近专辑",
                            subtitle = "按最近添加展示，进入全部后可切换排序",
                            colorScheme = colorScheme,
                            actionLabel = "全部",
                            onAction = { openAlbumLibrary() }
                        )
                    }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(
                                items = homeAlbums,
                                key = { "home-album-${it.id}" },
                                contentType = { "home-album-card" }
                            ) { album ->
                                CompactAlbumShelfCard(
                                    album = album,
                                    colorScheme = colorScheme,
                                    onClick = { openAlbumDetail(album) },
                                    onToggleStar = { toggleAlbumStar(album) }
                                )
                            }
                        }
                    }
                }

                if (artists.isNotEmpty()) {
                    item {
                        MusicSectionHeader(
                            title = "常听歌手",
                            subtitle = "从熟悉的声音继续展开",
                            colorScheme = colorScheme,
                            actionLabel = "全部",
                            onAction = { libraryPage = MusicLibraryPage.Artists }
                        )
                    }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(
                                items = homeArtists,
                                key = { "home-artist-${it.id}" },
                                contentType = { "home-artist-card" }
                            ) { artist ->
                                ArtistShelfCard(artist = artist, colorScheme = colorScheme, onClick = { openArtistDetail(artist) }, onToggleStar = { toggleArtistStar(artist) })
                            }
                        }
                    }
                }
            }

            MusicLibraryPage.Albums -> {
                item {
                    AlbumSortSegmentedControl(
                        selectedSort = albumSort,
                        colorScheme = colorScheme,
                        onSortSelected = { sort ->
                            if (sort != albumSort) {
                                scope.launch { loadAlbumList(sort) }
                            }
                        }
                    )
                }

                if (isLoadingAlbumList) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("正在加载专辑...", fontSize = 14.sp, color = colorScheme.onSurface.copy(alpha = 0.56f))
                        }
                    }
                } else if (sortedAlbums.isEmpty()) {
                    item {
                        MusicDetailEmptyState(
                            title = "暂无专辑",
                            subtitle = "刷新音乐库后，Navidrome 专辑会显示在这里。",
                            colorScheme = colorScheme
                        )
                    }
                } else {
                    items(sortedAlbums, key = { it.id }, contentType = { "album-row" }) { album ->
                        AlbumListRow(
                            album = album,
                            colorScheme = colorScheme,
                            onClick = { openAlbumDetail(album) },
                            onToggleStar = { toggleAlbumStar(album) }
                        )
                    }
                }
            }

            MusicLibraryPage.Songs -> {
                if (songs.isEmpty()) {
                    item {
                        MusicDetailEmptyState(
                            title = "暂无歌曲",
                            subtitle = "刷新音乐库后，Navidrome 中的全部歌曲会显示在这里。",
                            colorScheme = colorScheme
                        )
                    }
                } else {
                    item {
                        SongSortSegmentedControl(
                            selectedSort = songSort,
                            colorScheme = colorScheme,
                            onSortSelected = { songSort = it }
                        )
                    }
                    items(visibleSongs, key = { it.id }, contentType = { "song-row" }) { song ->
                        SongListRow(
                            song = song,
                            colorScheme = colorScheme,
                            onClick = {
                                val index = visibleSongs.indexOf(song)
                                onSongSelected(visibleSongs, index)
                            },
                            onToggleStar = { toggleSongStar(song) },
                            onAddToPlaylist = {
                                addToPlaylistSongId = song.id
                                showAddToPlaylistDialog = true
                            },
                            onAddToQueue = if (onAddToQueue != null) {{ onAddToQueue(song) }} else null,
                            downloadState = downloadStatesMap[song.id]?.state ?: DownloadState.NOT_DOWNLOADED,
                            downloadProgress = downloadStatesMap[song.id]?.progress ?: 0f,
                            onToggleDownload = { toggleSongDownload(song) }
                        )
                    }
                }
            }

            MusicLibraryPage.Artists -> {
                if (artists.isEmpty()) {
                    item {
                        MusicDetailEmptyState(
                            title = "暂无歌手",
                            subtitle = "同步 Navidrome 后，歌手会按列表展示在这里。",
                            colorScheme = colorScheme
                        )
                    }
                } else {
                    items(artists, key = { it.id }, contentType = { "artist-row" }) { artist ->
                        ArtistListRow(
                            artist = artist,
                            colorScheme = colorScheme,
                            onClick = { openArtistDetail(artist) }
                        )
                    }
                }
            }

            MusicLibraryPage.ArtistDetail -> {
                val artist = selectedArtist
                if (artist == null) {
                    item {
                        MusicDetailEmptyState(
                            title = "未选择歌手",
                            subtitle = "返回首页选择一位歌手。",
                            colorScheme = colorScheme
                        )
                    }
                } else {
                    if (!isLoadingArtistDetail && artistAlbums.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Surface(
                                    color = colorScheme.primary,
                                    contentColor = colorScheme.onPrimary,
                                    shape = RoundedCornerShape(999.dp),
                                    modifier = Modifier
                                        .height(34.dp)
                                        .clickable {
                                            scope.launch {
                                                try {
                                                    val repo = navidromeRepository
                                                    if (repo != null) {
                                                        val allSongs = artistAlbums.flatMap { album ->
                                                            repo.getAlbumSongs(album.id)
                                                        }
                                                        if (allSongs.isNotEmpty()) {
                                                            onSongSelected(allSongs, 0)
                                                        }
                                                    }
                                                } catch (_: Exception) {}
                                            }
                                        }
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "播放全部",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (isLoadingArtistDetail) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("加载专辑...", fontSize = 14.sp, color = colorScheme.onSurface.copy(alpha = 0.56f))
                            }
                        }
                    } else if (artistAlbums.isEmpty()) {
                        item {
                            MusicDetailEmptyState(
                                title = "暂无专辑",
                                subtitle = "该歌手暂无可用专辑。",
                                colorScheme = colorScheme
                            )
                        }
                    } else {
                        items(artistAlbums, key = { it.id }, contentType = { "artist-album-row" }) { album ->
                            AlbumListRow(
                                album = album,
                                colorScheme = colorScheme,
                                onClick = { openAlbumDetail(album) }
                            )
                        }
                    }
                }
            }

            MusicLibraryPage.AlbumDetail -> {
                val album = selectedAlbum
                if (album == null) {
                    item {
                        MusicDetailEmptyState(
                            title = "未选择专辑",
                            subtitle = "返回首页选择一张专辑。",
                            colorScheme = colorScheme
                        )
                    }
                } else if (isLoadingAlbumDetail) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 34.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("加载中...", fontSize = 14.sp, color = colorScheme.onSurface.copy(alpha = 0.56f))
                        }
                    }
                } else {
                    item {
                        AlbumDetailHeader(
                            album = album,
                            colorScheme = colorScheme,
                            onPlayAll = {
                                if (albumDetailSongs.isNotEmpty()) {
                                    onSongSelected(albumDetailSongs, 0)
                                }
                            }
                        )
                    }
                    items(albumDetailSongs, key = { it.id }, contentType = { "album-song-row" }) { song ->
                        SongListRow(
                            song = song,
                            colorScheme = colorScheme,
                            onClick = {
                                val index = albumDetailSongs.indexOf(song)
                                onSongSelected(albumDetailSongs, index)
                            },
                            onToggleStar = { toggleSongStar(song) },
                            onAddToPlaylist = {
                                addToPlaylistSongId = song.id
                                showAddToPlaylistDialog = true
                            },
                            onAddToQueue = if (onAddToQueue != null) {{ onAddToQueue(song) }} else null,
                            downloadState = downloadStatesMap[song.id]?.state ?: DownloadState.NOT_DOWNLOADED,
                            downloadProgress = downloadStatesMap[song.id]?.progress ?: 0f,
                            onToggleDownload = { toggleSongDownload(song) }
                        )
                    }
                }
            }

            MusicLibraryPage.Search -> {
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { newQuery ->
                            searchQuery = newQuery
                            searchJob.get()?.cancel()
                            searchError = null
                            val query = newQuery.trim()
                            if (query.isBlank()) {
                                searchResult = null
                                isSearching = false
                            } else {
                                isSearching = true
                                searchJob.set(scope.launch {
                                    delay(300)
                                    try {
                                        val result = navidromeRepository?.search(query) ?: SearchMusicResult()
                                        if (searchQuery.trim() == query) {
                                            searchResult = result
                                        }
                                    } catch (e: Exception) {
                                        if (searchQuery.trim() == query) {
                                            searchResult = null
                                            searchError = e.message ?: "请稍后重试。"
                                        }
                                    } finally {
                                        if (searchQuery.trim() == query) {
                                            isSearching = false
                                        }
                                    }
                                })
                            }
                        },
                        placeholder = { Text("搜索歌曲、专辑、歌手...", color = colorScheme.onSurface.copy(alpha = 0.4f)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colorScheme.primary,
                            unfocusedBorderColor = colorScheme.onSurface.copy(alpha = 0.2f)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                    )
                }

                if (searchQuery.isBlank()) {
                    item {
                        MusicSearchLanding(
                            albums = albums,
                            songs = recentlyAddedSongs.ifEmpty { songs },
                            artists = artists,
                            colorScheme = colorScheme,
                            onAlbumClick = { album -> openAlbumDetail(album) },
                            onSongClick = { song ->
                                val source = recentlyAddedSongs.ifEmpty { songs }
                                val index = source.indexOf(song).coerceAtLeast(0)
                                if (source.isNotEmpty()) {
                                    onSongSelected(source, index)
                                }
                            },
                            onArtistClick = { artist -> openArtistDetail(artist) },
                            downloadStatesMap = downloadStatesMap,
                            onToggleDownload = { song -> toggleSongDownload(song) }
                        )
                    }
                }

                if (isSearching) {
                    item {
                        MediaLoadingCard(
                            title = "正在搜索",
                            subtitle = "在 Navidrome 中查找匹配的歌曲、专辑和歌手。"
                        )
                    }
                }

                if (!isSearching && searchError != null) {
                    item {
                        MediaStateCard(
                            title = "搜索失败",
                            subtitle = searchError.orEmpty(),
                            tone = MediaStateTone.Error,
                            density = MediaStateDensity.Compact
                        )
                    }
                }

                val result = searchResult
                if (!isSearching && searchError == null && result != null) {
                    if (result.artists.isNotEmpty()) {
                        item {
                            SearchResultSectionHeader(
                                title = "歌手",
                                count = result.artists.size,
                                colorScheme = colorScheme
                            )
                        }
                        items(result.artists, key = { "artist-${it.id}" }, contentType = { "search-artist-row" }) { artist ->
                            ArtistListRow(
                                artist = artist,
                                colorScheme = colorScheme,
                                onClick = { openArtistDetail(artist) }
                            )
                        }
                    }
                    if (result.albums.isNotEmpty()) {
                        item {
                            SearchResultSectionHeader(
                                title = "专辑",
                                count = result.albums.size,
                                colorScheme = colorScheme
                            )
                        }
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(
                                    items = result.albums,
                                    key = { "search-album-${it.id}" },
                                    contentType = { "search-album-card" }
                                ) { album ->
                                    CompactAlbumShelfCard(
                                        album = album,
                                        colorScheme = colorScheme,
                                        onClick = { openAlbumDetail(album) }
                                    )
                                }
                            }
                        }
                    }
                    if (result.songs.isNotEmpty()) {
                        item {
                            SearchResultSectionHeader(
                                title = "歌曲",
                                count = result.songs.size,
                                colorScheme = colorScheme
                            )
                        }
                        items(result.songs, key = { "song-${it.id}" }, contentType = { "search-song-row" }) { song ->
                            SongListRow(
                                song = song,
                                colorScheme = colorScheme,
                                onClick = {
                                    val index = result.songs.indexOf(song)
                                    onSongSelected(result.songs, index)
                                },
                                onToggleStar = { toggleSongStar(song) },
                                onAddToPlaylist = {
                                    addToPlaylistSongId = song.id
                                    showAddToPlaylistDialog = true
                                },
                                onAddToQueue = if (onAddToQueue != null) {{ onAddToQueue(song) }} else null,
                                downloadState = downloadStatesMap[song.id]?.state ?: DownloadState.NOT_DOWNLOADED,
                                downloadProgress = downloadStatesMap[song.id]?.progress ?: 0f,
                                onToggleDownload = { toggleSongDownload(song) }
                            )
                        }
                    }
                    if (result.artists.isEmpty() && result.albums.isEmpty() && result.songs.isEmpty()) {
                        item {
                            MusicDetailEmptyState(
                                title = "没有找到结果",
                                subtitle = "试试其他关键词。",
                                colorScheme = colorScheme
                            )
                        }
                    }
                }
            }

            MusicLibraryPage.Playlists -> {
                if (!isLoadingPlaylists && playlists.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Surface(
                                color = colorScheme.primary,
                                contentColor = colorScheme.onPrimary,
                                shape = RoundedCornerShape(999.dp),
                                modifier = Modifier
                                    .height(34.dp)
                                    .clickable { showCreatePlaylistDialog = true }
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "新建歌单",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
                if (isLoadingPlaylists) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("正在加载歌单...", fontSize = 14.sp, color = colorScheme.onSurface.copy(alpha = 0.56f))
                        }
                    }
                } else if (playlists.isEmpty()) {
                    item {
                        MusicDetailEmptyState(
                            title = "暂无歌单",
                            subtitle = "Navidrome 中的歌单会显示在这里。",
                            colorScheme = colorScheme
                        )
                    }
                } else {
                    items(playlists, key = { it.id }, contentType = { "playlist-row" }) { playlist ->
                        PlaylistListRow(
                            playlist = playlist,
                            colorScheme = colorScheme,
                            onClick = { openPlaylistDetail(playlist) },
                            onRename = {
                                renamePlaylistTarget = playlist
                                renamePlaylistName = playlist.name
                                showRenamePlaylistDialog = true
                            },
                            onDelete = {
                                deletePlaylistTarget = playlist
                                showDeletePlaylistConfirm = true
                            }
                        )
                    }
                }
            }

            MusicLibraryPage.PlaylistDetail -> {
                val playlist = selectedPlaylist
                if (playlist == null) {
                    item {
                        MusicDetailEmptyState(
                            title = "未选择歌单",
                            subtitle = "返回歌单列表选择一个歌单。",
                            colorScheme = colorScheme
                        )
                    }
                } else if (isLoadingPlaylistDetail) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 34.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("加载歌单曲目...", fontSize = 14.sp, color = colorScheme.onSurface.copy(alpha = 0.56f))
                        }
                    }
                } else {
                    item {
                        PlaylistDetailHeader(
                            playlist = playlist,
                            songCount = playlistSongs.size,
                            colorScheme = colorScheme,
                            onPlayAll = {
                                if (playlistSongs.isNotEmpty()) {
                                    onSongSelected(playlistSongs, 0)
                                }
                            }
                        )
                    }
                    if (playlistSongs.isEmpty()) {
                        item {
                            MusicDetailEmptyState(
                                title = "暂无曲目",
                                subtitle = "这个歌单暂时没有可播放曲目。",
                                colorScheme = colorScheme
                            )
                        }
                    } else {
                        items(playlistSongs, key = { it.id }, contentType = { "playlist-song-row" }) { song ->
                            SongListRow(
                                song = song,
                                colorScheme = colorScheme,
                                onClick = {
                                    val index = playlistSongs.indexOf(song)
                                    onSongSelected(playlistSongs, index)
                                },
                                onToggleStar = { toggleSongStar(song) },
                                onAddToPlaylist = {
                                    addToPlaylistSongId = song.id
                                    showAddToPlaylistDialog = true
                                },
                                onAddToQueue = if (onAddToQueue != null) {{ onAddToQueue(song) }} else null,
                                downloadState = downloadStatesMap[song.id]?.state ?: DownloadState.NOT_DOWNLOADED,
                                downloadProgress = downloadStatesMap[song.id]?.progress ?: 0f,
                                onToggleDownload = { toggleSongDownload(song) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Mini now-playing bar
    if (currentPlayingSong != null && isMusicPlaying && onPlayPause != null && onOpenPlayer != null) {
        MiniNowPlayingBar(
            song = currentPlayingSong!!,
            isPlaying = isMusicPlaying,
            colorScheme = colorScheme,
            onPlayPause = onPlayPause!!,
            onNext = onNext ?: {},
            onClick = onOpenPlayer!!,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
    }

    // Create playlist dialog
    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = {
                showCreatePlaylistDialog = false
                createPlaylistName = ""
            },
            title = { Text("新建歌单") },
            text = {
                OutlinedTextField(
                    value = createPlaylistName,
                    onValueChange = { createPlaylistName = it },
                    placeholder = { Text("歌单名称") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colorScheme.primary,
                        unfocusedBorderColor = colorScheme.onSurface.copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = createPlaylistName.trim()
                        if (name.isNotBlank()) {
                            scope.launch {
                                try {
                                    navidromeRepository?.createPlaylist(name)
                                    loadPlaylists()
                                } catch (_: Exception) {}
                            }
                        }
                        showCreatePlaylistDialog = false
                        createPlaylistName = ""
                    },
                    enabled = createPlaylistName.trim().isNotBlank()
                ) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreatePlaylistDialog = false
                    createPlaylistName = ""
                }) { Text("取消") }
            }
        )
    }

    // Rename playlist dialog
    if (showRenamePlaylistDialog) {
        AlertDialog(
            onDismissRequest = {
                showRenamePlaylistDialog = false
                renamePlaylistTarget = null
                renamePlaylistName = ""
            },
            title = { Text("重命名歌单") },
            text = {
                OutlinedTextField(
                    value = renamePlaylistName,
                    onValueChange = { renamePlaylistName = it },
                    placeholder = { Text("歌单名称") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colorScheme.primary,
                        unfocusedBorderColor = colorScheme.onSurface.copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = renamePlaylistTarget
                        val newName = renamePlaylistName.trim()
                        if (target != null && newName.isNotBlank()) {
                            scope.launch {
                                try {
                                    navidromeRepository?.renamePlaylist(target.id, newName)
                                    loadPlaylists()
                                } catch (_: Exception) {}
                            }
                        }
                        showRenamePlaylistDialog = false
                        renamePlaylistTarget = null
                        renamePlaylistName = ""
                    },
                    enabled = renamePlaylistName.trim().isNotBlank()
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRenamePlaylistDialog = false
                    renamePlaylistTarget = null
                    renamePlaylistName = ""
                }) { Text("取消") }
            }
        )
    }

    // Delete playlist confirmation dialog
    if (showDeletePlaylistConfirm) {
        AlertDialog(
            onDismissRequest = {
                showDeletePlaylistConfirm = false
                deletePlaylistTarget = null
            },
            title = { Text("删除歌单") },
            text = { Text("确定删除歌单「${deletePlaylistTarget?.name}」吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = deletePlaylistTarget
                        if (target != null) {
                            scope.launch {
                                try {
                                    navidromeRepository?.deletePlaylist(target.id)
                                    loadPlaylists()
                                } catch (_: Exception) {}
                            }
                        }
                        showDeletePlaylistConfirm = false
                        deletePlaylistTarget = null
                    }
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeletePlaylistConfirm = false
                    deletePlaylistTarget = null
                }) { Text("取消") }
            }
        )
    }

    // Add to playlist dialog
    if (showAddToPlaylistDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddToPlaylistDialog = false
                addToPlaylistSongId = null
            },
            title = { Text("添加到歌单") },
            text = {
                if (playlists.isEmpty()) {
                    Text("暂无歌单，请先创建一个歌单。", color = colorScheme.onSurface.copy(alpha = 0.6f))
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        playlists.forEach { playlist ->
                            Surface(
                                color = colorScheme.surfaceVariant.copy(alpha = 0.42f),
                                contentColor = colorScheme.onSurface,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val songId = addToPlaylistSongId
                                        if (songId != null) {
                                            scope.launch {
                                                try {
                                                    navidromeRepository?.addToPlaylist(playlist.id, songId)
                                                } catch (_: Exception) {}
                                            }
                                        }
                                        showAddToPlaylistDialog = false
                                        addToPlaylistSongId = null
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        playlist.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "${playlist.songCount} 首",
                                        fontSize = 12.sp,
                                        color = colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    showAddToPlaylistDialog = false
                    addToPlaylistSongId = null
                }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun MusicSearchLanding(
    albums: List<NavidromeAlbum>,
    songs: List<NavidromeSong>,
    artists: List<NavidromeArtist>,
    colorScheme: ColorScheme,
    onAlbumClick: (NavidromeAlbum) -> Unit,
    onSongClick: (NavidromeSong) -> Unit,
    onArtistClick: (NavidromeArtist) -> Unit,
    downloadStatesMap: Map<String, DownloadStateEntry> = emptyMap(),
    onToggleDownload: ((NavidromeSong) -> Unit)? = null
) {
    val hasSuggestions = albums.isNotEmpty() || songs.isNotEmpty() || artists.isNotEmpty()
    val suggestedAlbums = remember(albums) { albums.take(8) }
    val suggestedSongs = remember(songs) { songs.take(8) }
    val suggestedArtists = remember(artists) { artists.take(8) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (!hasSuggestions) {
            MediaStateCard(
                title = "输入关键词开始搜索",
                subtitle = "可以搜索 Navidrome 中的歌曲、专辑和歌手。",
                density = MediaStateDensity.Compact
            )
            return@Column
        }

        MusicSectionHeader(
            title = "搜索建议",
            subtitle = "先从最近同步的内容开始",
            colorScheme = colorScheme
        )

        if (albums.isNotEmpty()) {
            SearchResultSectionHeader(
                title = "最近专辑",
                count = albums.size,
                colorScheme = colorScheme
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(
                    items = suggestedAlbums,
                    key = { "search-home-album-${it.id}" },
                    contentType = { "search-home-album-card" }
                ) { album ->
                    CompactAlbumShelfCard(
                        album = album,
                        colorScheme = colorScheme,
                        onClick = { onAlbumClick(album) }
                    )
                }
            }
        }

        if (songs.isNotEmpty()) {
            SearchResultSectionHeader(
                title = "最近歌曲",
                count = songs.size,
                colorScheme = colorScheme
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(
                    items = suggestedSongs,
                    key = { "search-home-song-${it.id}" },
                    contentType = { "search-home-song-card" }
                ) { song ->
                    SongShelfCard(
                        song = song,
                        colorScheme = colorScheme,
                        onClick = { onSongClick(song) },
                        downloadState = downloadStatesMap[song.id]?.state ?: DownloadState.NOT_DOWNLOADED,
                        downloadProgress = downloadStatesMap[song.id]?.progress ?: 0f,
                        onToggleDownload = if (onToggleDownload != null) {{ onToggleDownload(song) }} else null
                    )
                }
            }
        }

        if (artists.isNotEmpty()) {
            SearchResultSectionHeader(
                title = "歌手",
                count = artists.size,
                colorScheme = colorScheme
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(
                    items = suggestedArtists,
                    key = { "search-home-artist-${it.id}" },
                    contentType = { "search-home-artist-card" }
                ) { artist ->
                    ArtistShelfCard(
                        artist = artist,
                        colorScheme = colorScheme,
                        onClick = { onArtistClick(artist) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultSectionHeader(
    title: String,
    count: Int,
    colorScheme: ColorScheme
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            count.toString(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = colorScheme.onSurface.copy(alpha = 0.5f),
            maxLines = 1
        )
    }
}

@Composable
private fun PlaylistListRow(
    playlist: NavidromePlaylist,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onRename: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    Surface(
        color = colorScheme.surfaceVariant.copy(alpha = 0.42f),
        contentColor = colorScheme.onSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.045f)),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                colorScheme.primary.copy(alpha = 0.18f),
                                colorScheme.secondary.copy(alpha = 0.12f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (playlist.coverArt != null) {
                    AsyncImage(
                        model = playlist.coverArt,
                        contentDescription = playlist.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                } else {
                    Text("≡", fontSize = 22.sp, color = colorScheme.primary.copy(alpha = 0.56f))
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    playlist.name,
                    fontSize = 15.sp,
                    color = colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    playlist.comment?.takeIf { it.isNotBlank() } ?: playlist.owner ?: "Navidrome 歌单",
                    fontSize = 13.sp,
                    color = colorScheme.onSurface.copy(alpha = 0.62f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${playlist.songCount} 首  •  ${formatDuration(playlist.duration)}",
                    fontSize = 12.sp,
                    color = colorScheme.onSurface.copy(alpha = 0.46f)
                )
            }
            if (onRename != null || onDelete != null) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (onRename != null) {
                        Surface(
                            color = Color.Transparent,
                            contentColor = colorScheme.onSurface.copy(alpha = 0.46f),
                            onClick = onRename,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Text("✎", fontSize = 16.sp, modifier = Modifier.padding(2.dp))
                        }
                    }
                    if (onDelete != null) {
                        Surface(
                            color = Color.Transparent,
                            contentColor = colorScheme.error.copy(alpha = 0.7f),
                            onClick = onDelete,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Text("✕", fontSize = 14.sp, modifier = Modifier.padding(4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistDetailHeader(
    playlist: NavidromePlaylist,
    songCount: Int,
    colorScheme: ColorScheme,
    onPlayAll: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            colorScheme.primary.copy(alpha = 0.22f),
                            colorScheme.secondary.copy(alpha = 0.16f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            if (playlist.coverArt != null) {
                AsyncImage(
                    model = playlist.coverArt,
                    contentDescription = playlist.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text("≡", fontSize = 38.sp, color = colorScheme.primary.copy(alpha = 0.62f))
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                playlist.name,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            playlist.comment?.takeIf { it.isNotBlank() }?.let { comment ->
                Text(
                    comment,
                    fontSize = 14.sp,
                    color = colorScheme.onSurface.copy(alpha = 0.68f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MusicMetaChip("${songCount} 首", colorScheme)
                MusicMetaChip(formatDuration(playlist.duration), colorScheme)
            }
            Surface(
                color = colorScheme.primary,
                contentColor = colorScheme.onPrimary,
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier
                    .height(36.dp)
                    .clickable(onClick = onPlayAll)
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "播放全部",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun MusicBackButton(
    colorScheme: ColorScheme,
    onClick: () -> Unit
) {
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
            contentAlignment = Alignment.Center
        ) {
            Text(
                "‹",
                fontSize = 26.sp,
                color = colorScheme.onSurface.copy(alpha = 0.74f),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun MusicDetailEmptyState(
    title: String,
    subtitle: String,
    colorScheme: ColorScheme
) {
    MediaStateCard(
        title = title,
        subtitle = subtitle,
        density = MediaStateDensity.Compact
    )
}

@Composable
private fun MusicSegmentedTabs(
    selectedTab: Int,
    colorScheme: ColorScheme,
    onTabSelected: (Int) -> Unit,
    onSearchClick: () -> Unit = {}
) {
    val tabs = listOf("发现", "歌曲", "歌单")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = colorScheme.surfaceVariant.copy(alpha = 0.56f),
            contentColor = colorScheme.onSurface,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.06f)),
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                tabs.forEachIndexed { index, label ->
                    val selected = selectedTab == index
                    val tabColor by animateColorAsState(
                        targetValue = if (selected) colorScheme.surface.copy(alpha = 0.96f) else Color.Transparent,
                        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                    )
                    val textColor by animateColorAsState(
                        targetValue = if (selected) colorScheme.primary else colorScheme.onSurface.copy(alpha = 0.62f),
                        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                    )

                    Surface(
                        color = tabColor,
                        contentColor = textColor,
                        shape = RoundedCornerShape(14.dp),
                        tonalElevation = if (selected) 2.dp else 0.dp,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onTabSelected(index) }
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                color = textColor,
                                fontSize = 14.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
        MusicSearchButton(colorScheme = colorScheme, onClick = onSearchClick)
    }
}

private fun NavidromeAlbumSort.displayLabel(): String {
    return when (this) {
        NavidromeAlbumSort.RecentlyAdded -> "最近添加"
        NavidromeAlbumSort.ReleaseYear -> "发行年份"
        NavidromeAlbumSort.Name -> "名称"
    }
}

private fun MusicSongSort.displayLabel(): String {
    return when (this) {
        MusicSongSort.Default -> "默认"
        MusicSongSort.Added -> "最近添加"
        MusicSongSort.Title -> "标题"
        MusicSongSort.Artist -> "歌手"
        MusicSongSort.Album -> "专辑"
        MusicSongSort.Duration -> "时长"
    }
}

private fun sortMusicSongs(
    songs: List<NavidromeSong>,
    sort: MusicSongSort
): List<NavidromeSong> {
    return when (sort) {
        MusicSongSort.Default -> songs
        MusicSongSort.Added -> songs.sortedWith(
            compareByDescending<NavidromeSong> { it.created.orEmpty() }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        )
        MusicSongSort.Title -> songs.sortedWith(
            compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        )
        MusicSongSort.Artist -> songs.sortedWith(
            compareBy<NavidromeSong, String>(String.CASE_INSENSITIVE_ORDER) { it.artist.orEmpty() }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        )
        MusicSongSort.Album -> songs.sortedWith(
            compareBy<NavidromeSong, String>(String.CASE_INSENSITIVE_ORDER) { it.album.orEmpty() }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        )
        MusicSongSort.Duration -> songs.sortedWith(
            compareBy<NavidromeSong> { it.duration }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        )
    }
}

@Composable
private fun SongSortSegmentedControl(
    selectedSort: MusicSongSort,
    colorScheme: ColorScheme,
    onSortSelected: (MusicSongSort) -> Unit
) {
    val sorts = listOf(
        MusicSongSort.Default,
        MusicSongSort.Added,
        MusicSongSort.Title,
        MusicSongSort.Artist,
        MusicSongSort.Album,
        MusicSongSort.Duration
    )

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(sorts, key = { it.name }) { sort ->
            val selected = selectedSort == sort
            val background by animateColorAsState(
                targetValue = if (selected) colorScheme.surface.copy(alpha = 0.96f) else colorScheme.surfaceVariant.copy(alpha = 0.5f),
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
            )
            val textColor by animateColorAsState(
                targetValue = if (selected) colorScheme.primary else colorScheme.onSurface.copy(alpha = 0.64f),
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
            )

            Surface(
                color = background,
                contentColor = textColor,
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.06f)),
                tonalElevation = if (selected) 2.dp else 0.dp,
                modifier = Modifier
                    .height(38.dp)
                    .clickable { onSortSelected(sort) }
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        sort.displayLabel(),
                        color = textColor,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumSortSegmentedControl(
    selectedSort: NavidromeAlbumSort,
    colorScheme: ColorScheme,
    onSortSelected: (NavidromeAlbumSort) -> Unit
) {
    val sorts = listOf(
        NavidromeAlbumSort.RecentlyAdded,
        NavidromeAlbumSort.ReleaseYear,
        NavidromeAlbumSort.Name
    )

    Surface(
        color = colorScheme.surfaceVariant.copy(alpha = 0.56f),
        contentColor = colorScheme.onSurface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.06f)),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            sorts.forEach { sort ->
                val selected = selectedSort == sort
                val tabColor by animateColorAsState(
                    targetValue = if (selected) colorScheme.surface.copy(alpha = 0.96f) else Color.Transparent,
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                )
                val textColor by animateColorAsState(
                    targetValue = if (selected) colorScheme.primary else colorScheme.onSurface.copy(alpha = 0.62f),
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                )

                Surface(
                    color = tabColor,
                    contentColor = textColor,
                    shape = RoundedCornerShape(14.dp),
                    tonalElevation = if (selected) 2.dp else 0.dp,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onSortSelected(sort) }
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            sort.displayLabel(),
                            color = textColor,
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicSearchButton(
    colorScheme: ColorScheme,
    onClick: () -> Unit = {}
) {
    Surface(
        color = colorScheme.surfaceVariant.copy(alpha = 0.56f),
        contentColor = colorScheme.onSurface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.06f)),
        modifier = Modifier
            .height(48.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .height(48.dp)
                .padding(horizontal = 15.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "⌕",
                fontSize = 20.sp,
                color = colorScheme.onSurface.copy(alpha = 0.72f),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun AlbumListRow(
    album: NavidromeAlbum,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onToggleStar: (() -> Unit)? = null
) {
    Surface(
        color = colorScheme.surfaceVariant.copy(alpha = 0.42f),
        contentColor = colorScheme.onSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.045f)),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                colorScheme.primary.copy(alpha = 0.18f),
                                colorScheme.secondary.copy(alpha = 0.12f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (album.coverArt != null) {
                    AsyncImage(
                        model = album.coverArt,
                        contentDescription = album.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                } else {
                    Text("♪", fontSize = 20.sp, color = colorScheme.primary.copy(alpha = 0.52f))
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    album.name,
                    fontSize = 15.sp,
                    color = colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    album.artist ?: "Unknown artist",
                    fontSize = 13.sp,
                    color = colorScheme.onSurface.copy(alpha = 0.62f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    buildString {
                        append("${album.songCount} tracks")
                        album.year?.let {
                            append("  •  ")
                            append(it)
                        }
                    },
                    fontSize = 12.sp,
                    color = colorScheme.onSurface.copy(alpha = 0.46f)
                )
            }
            if (onToggleStar != null) {
                IconButton(onClick = onToggleStar, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (album.starred != null) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (album.starred != null) "取消收藏" else "收藏",
                        tint = if (album.starred != null) colorScheme.error else colorScheme.onSurface.copy(alpha = 0.46f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumDetailHeader(
    album: NavidromeAlbum,
    colorScheme: ColorScheme,
    onPlayAll: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            colorScheme.primary.copy(alpha = 0.22f),
                            colorScheme.secondary.copy(alpha = 0.16f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            if (album.coverArt != null) {
                AsyncImage(
                    model = album.coverArt,
                    contentDescription = album.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text("♪", fontSize = 36.sp, color = colorScheme.primary.copy(alpha = 0.6f))
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                album.name,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            album.artist?.let { artist ->
                Text(
                    artist,
                    fontSize = 14.sp,
                    color = colorScheme.onSurface.copy(alpha = 0.68f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MusicMetaChip("${album.songCount} tracks", colorScheme)
                album.year?.let { MusicMetaChip(it.toString(), colorScheme) }
            }
            Surface(
                color = colorScheme.primary,
                contentColor = colorScheme.onPrimary,
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier
                    .height(36.dp)
                    .clickable(onClick = onPlayAll)
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "播放全部",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}


private fun formatCacheAge(updatedAtMillis: Long?): String? {
    if (updatedAtMillis == null || updatedAtMillis <= 0L) return null

    val elapsedMillis = (System.currentTimeMillis() - updatedAtMillis).coerceAtLeast(0L)
    val elapsedMinutes = elapsedMillis / 60_000L
    val elapsedHours = elapsedMillis / 3_600_000L
    val elapsedDays = elapsedMillis / 86_400_000L

    return when {
        elapsedMinutes < 1L -> "刚刚更新"
        elapsedMinutes < 60L -> "${elapsedMinutes} 分钟前更新"
        elapsedHours < 24L -> "${elapsedHours} 小时前更新"
        else -> "${elapsedDays} 天前更新"
    }
}

@Composable
private fun MiniNowPlayingBar(
    song: NavidromeSong,
    isPlaying: Boolean,
    colorScheme: ColorScheme,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = colorScheme.surfaceVariant.copy(alpha = 0.82f),
        contentColor = colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.06f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                colorScheme.primary.copy(alpha = 0.2f),
                                colorScheme.secondary.copy(alpha = 0.12f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (song.coverArt != null) {
                    AsyncImage(
                        model = song.coverArt,
                        contentDescription = song.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                } else {
                    Text(
                        "♪",
                        fontSize = 14.sp,
                        color = colorScheme.primary.copy(alpha = 0.68f)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    song.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    song.artist ?: "Unknown",
                    fontSize = 12.sp,
                    color = colorScheme.onSurface.copy(alpha = 0.52f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                color = colorScheme.primary.copy(alpha = 0.12f),
                contentColor = colorScheme.primary,
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.size(34.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.clickable(onClick = onPlayPause)
                ) {
                    Text(
                        if (isPlaying) "Ⅱ" else "▶",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.primary
                    )
                }
            }

            Surface(
                color = colorScheme.surface.copy(alpha = 0.56f),
                contentColor = colorScheme.onSurface,
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.size(34.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.clickable(onClick = onNext)
                ) {
                    Text(
                        "›",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface.copy(alpha = 0.76f)
                    )
                }
            }
        }
    }
}
