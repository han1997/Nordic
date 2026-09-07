package com.nordic.mediahub.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nordic.mediahub.data.NavidromeAlbum
import com.nordic.mediahub.data.NavidromeArtist
import com.nordic.mediahub.data.NavidromePlaylist
import com.nordic.mediahub.data.NavidromeSong
import com.nordic.mediahub.data.ConfigRepository
import com.nordic.mediahub.data.NavidromeAlbumSort
import com.nordic.mediahub.data.NavidromeConfig
import com.nordic.mediahub.data.NavidromeMusicCacheRepository
import com.nordic.mediahub.data.NavidromeRepository
import com.nordic.mediahub.data.SearchMusicResult
import com.nordic.mediahub.data.cacheKey
import com.nordic.mediahub.data.formatCacheAge
import com.nordic.mediahub.data.isCacheFresh
import com.nordic.mediahub.data.isReadyForMusicSync
import com.nordic.mediahub.data.loadNavidromeMusicRefresh
import com.nordic.mediahub.ui.theme.NordicMotion
import com.nordic.mediahub.ui.theme.NordicShapes
import com.nordic.mediahub.ui.theme.NordicSpacing
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

internal enum class MusicLibraryPage {
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

private const val BULK_PLAY_ALLOW_UNPLAYABLE_START_FALLBACK = true
internal const val DIRECT_SELECTION_ALLOW_UNPLAYABLE_START_FALLBACK = false

@Composable
fun MusicScreenV2(
    isDark: Boolean,
    onThemeToggle: (Boolean) -> Unit,
    onSongSelected: (List<NavidromeSong>, Int, Boolean) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val repository = remember { ConfigRepository(context) }
    val cacheRepository = remember { NavidromeMusicCacheRepository(context) }
    val savedConfig by repository.navidromeConfig.collectAsStateWithLifecycle(NavidromeConfig())
    val navidromeRepository = remember(savedConfig) {
        if (savedConfig.isReadyForMusicSync()) NavidromeRepository(savedConfig) else null
    }
    var selectedTab by remember { mutableStateOf(0) }
    var libraryPage by remember { mutableStateOf(MusicLibraryPage.Home) }
    var albums by remember { mutableStateOf(emptyList<NavidromeAlbum>()) }
    var sortedAlbums by remember { mutableStateOf(emptyList<NavidromeAlbum>()) }
    var albumSort by remember { mutableStateOf(NavidromeAlbumSort.RecentlyAdded) }
    var songSort by remember { mutableStateOf(MusicSongSort.Default) }
    var songFilterQuery by remember { mutableStateOf("") }
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
    var isCreatingPlaylist by remember { mutableStateOf(false) }
    var renamingPlaylist by remember { mutableStateOf<NavidromePlaylist?>(null) }
    var deletingPlaylist by remember { mutableStateOf<NavidromePlaylist?>(null) }
    var playlistNameDraft by remember { mutableStateOf("") }
    var playlistActionError by remember { mutableStateOf<String?>(null) }
    var isPlaylistActionRunning by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResult by remember { mutableStateOf<SearchMusicResult?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    val searchJob = remember { AtomicReference<Job?>(null) }
    var cacheUpdatedAtMillis by remember { mutableStateOf<Long?>(null) }
    var musicConfigStateVersion by remember { mutableStateOf(0) }
    var previousMusicConfig by remember { mutableStateOf<NavidromeConfig?>(null) }
    var musicBackStack by remember { mutableStateOf(emptyList<MusicLibraryPage>()) }
    var musicResetNotice by remember { mutableStateOf<String?>(null) }
    var musicDetailInvalidationNotice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun isCurrentMusicConfigRequest(requestVersion: Int?): Boolean {
        return requestVersion == null || musicConfigStateVersion == requestVersion
    }

    fun resetMusicStateAfterConfigChange() {
        musicConfigStateVersion += 1
        musicResetNotice = null
        musicDetailInvalidationNotice = null
        selectedTab = 0
        libraryPage = resolveMusicLibraryPageAfterConfigChange(libraryPage)
        musicBackStack = emptyList()
        sortedAlbums = emptyList()
        albums = emptyList()
        songs = emptyList()
        recentlyAddedSongs = emptyList()
        artists = emptyList()
        playlists = emptyList()
        selectedAlbum = null
        albumDetailSongs = emptyList()
        selectedArtist = null
        artistAlbums = emptyList()
        selectedPlaylist = null
        playlistSongs = emptyList()
        isLoading = false
        isLoadingAlbumList = false
        isLoadingPlaylists = false
        isLoadingAlbumDetail = false
        isLoadingArtistDetail = false
        isLoadingPlaylistDetail = false
        loadingAlbumId = null
        searchJob.get()?.cancel()
        searchJob.set(null)
        searchQuery = ""
        searchResult = null
        searchError = null
        isSearching = false
        albumSort = NavidromeAlbumSort.RecentlyAdded
        songSort = MusicSongSort.Default
        songFilterQuery = ""
        isCreatingPlaylist = false
        renamingPlaylist = null
        deletingPlaylist = null
        playlistNameDraft = ""
        playlistActionError = null
        isPlaylistActionRunning = false
        errorMsg = null
        cacheUpdatedAtMillis = null
    }

    fun navigateToMusicPage(page: MusicLibraryPage, pushCurrent: Boolean = true) {
        if (page == libraryPage) return
        musicResetNotice = null
        if (pushCurrent) {
            musicBackStack = (musicBackStack + libraryPage).takeLast(8)
        }
        selectedTab = resolveMusicSelectedTabForPage(page)
        libraryPage = page
    }

    fun reconcileAlbumDetailSelection(refreshedAlbums: List<NavidromeAlbum>, returnPage: MusicLibraryPage) {
        val resolvedAlbum = resolveSelectedAlbumAfterMusicRefresh(selectedAlbum, refreshedAlbums)
        val shouldShowNotice = shouldShowMusicDetailInvalidationNotice(
            MusicLibraryPage.AlbumDetail, libraryPage, selectedAlbum != null, resolvedAlbum != null
        )
        if (selectedAlbum != null && resolvedAlbum == null) {
            selectedAlbum = null
            albumDetailSongs = emptyList()
            isLoadingAlbumDetail = false
            musicBackStack = musicBackStack.filterNot { it == MusicLibraryPage.AlbumDetail }
            if (libraryPage == MusicLibraryPage.AlbumDetail) {
                selectedTab = resolveMusicSelectedTabForPage(returnPage)
                libraryPage = returnPage
            }
            if (shouldShowNotice) musicDetailInvalidationNotice = "这张专辑已不在刷新后的列表中，已返回专辑列表。"
        } else if (resolvedAlbum != null) {
            selectedAlbum = resolvedAlbum
        }
    }

    fun reconcileArtistDetailSelection(refreshedArtists: List<NavidromeArtist>) {
        val resolvedArtist = resolveSelectedArtistAfterMusicRefresh(selectedArtist, refreshedArtists)
        val shouldShowNotice = shouldShowMusicDetailInvalidationNotice(
            MusicLibraryPage.ArtistDetail, libraryPage, selectedArtist != null, resolvedArtist != null
        )
        if (selectedArtist != null && resolvedArtist == null) {
            selectedArtist = null
            artistAlbums = emptyList()
            isLoadingArtistDetail = false
            musicBackStack = musicBackStack.filterNot { it == MusicLibraryPage.ArtistDetail }
            if (libraryPage == MusicLibraryPage.ArtistDetail) {
                selectedTab = 0
                libraryPage = MusicLibraryPage.Artists
            }
            if (shouldShowNotice) musicDetailInvalidationNotice = "这位歌手已不在刷新后的列表中，已返回歌手列表。"
        } else if (resolvedArtist != null) {
            selectedArtist = resolvedArtist
        }
    }

    fun reconcilePlaylistDetailSelection(refreshedPlaylists: List<NavidromePlaylist>) {
        val resolvedPlaylist = resolveSelectedPlaylistAfterMusicRefresh(selectedPlaylist, refreshedPlaylists)
        val shouldShowNotice = shouldShowMusicDetailInvalidationNotice(
            MusicLibraryPage.PlaylistDetail, libraryPage, selectedPlaylist != null, resolvedPlaylist != null
        )
        if (selectedPlaylist != null && resolvedPlaylist == null) {
            selectedPlaylist = null
            playlistSongs = emptyList()
            isLoadingPlaylistDetail = false
            musicBackStack = musicBackStack.filterNot { it == MusicLibraryPage.PlaylistDetail }
            if (libraryPage == MusicLibraryPage.PlaylistDetail) {
                selectedTab = 2
                libraryPage = MusicLibraryPage.Playlists
            }
            if (shouldShowNotice) musicDetailInvalidationNotice = "这个歌单已不在刷新后的列表中，已返回歌单列表。"
        } else if (resolvedPlaylist != null) {
            selectedPlaylist = resolvedPlaylist
        }
    }

    fun clearMusicSearch() {
        searchQuery = ""
        searchJob.getAndSet(null)?.cancel()
        searchResult = null
        searchError = null
        isSearching = false
    }

    suspend fun applyCachedMusicData(targetConfig: NavidromeConfig, requestVersion: Int? = null): Boolean {
        val cached = cacheRepository.load(targetConfig)
        if (!isCurrentMusicConfigRequest(requestVersion)) {
            return false
        }

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

    suspend fun refreshMusicData(
        targetConfig: NavidromeConfig,
        requestVersion: Int? = musicConfigStateVersion
    ): Boolean {
        if (!targetConfig.isReadyForMusicSync() || isLoading) return false

        isLoading = true
        errorMsg = null
        return try {
            val freshData = loadNavidromeMusicRefresh(
                targetConfig = targetConfig,
                savedConfig = savedConfig,
                savedRepository = navidromeRepository
            ) ?: return false
            if (!isCurrentMusicConfigRequest(requestVersion)) {
                return false
            }

            val freshCache = cacheRepository.buildCache(
                config = targetConfig,
                albums = freshData.albums,
                songs = freshData.songs,
                recentlyAddedSongs = freshData.recentlyAddedSongs,
                artists = freshData.artists
            )

            val previousBrowseAlbums = albums
            albums = freshData.albums
            songs = freshData.songs
            recentlyAddedSongs = freshData.recentlyAddedSongs
            artists = freshData.artists
            // The browse refresh only replaces the home/recent album list. Do not
            // invalidate a detail opened from sorted albums, search, or an artist
            // detail merely because that separate source was not refreshed here.
            if (selectedAlbum == null || previousBrowseAlbums.any { it.id == selectedAlbum?.id }) {
                reconcileAlbumDetailSelection(
                    refreshedAlbums = freshData.albums,
                    returnPage = if (musicBackStack.contains(MusicLibraryPage.Albums)) {
                        MusicLibraryPage.Albums
                    } else {
                        MusicLibraryPage.Home
                    }
                )
            }
            reconcileArtistDetailSelection(freshData.artists)
            // The browse refresh does not load playlists. Keep playlist detail
            // state intact; playlist reconciliation belongs to loadPlaylists().
            cacheUpdatedAtMillis = freshCache.updatedAtMillis
            cacheRepository.save(targetConfig, freshCache)
            true
        } catch (e: Exception) {
            if (isCurrentMusicConfigRequest(requestVersion)) {
                val hasCachedContent = albums.isNotEmpty() || songs.isNotEmpty() || artists.isNotEmpty() ||
                    playlists.isNotEmpty() || albumDetailSongs.isNotEmpty() || artistAlbums.isNotEmpty() ||
                    playlistSongs.isNotEmpty()
                errorMsg = if (hasCachedContent) {
                    "正在显示上次缓存：${e.message ?: "未知错误"}"
                } else {
                    "连接失败: ${e.message ?: "未知错误"}"
                }
            }
            false
        } finally {
            if (isCurrentMusicConfigRequest(requestVersion)) {
                isLoading = false
            }
        }
    }

    suspend fun loadAlbumList(sort: NavidromeAlbumSort): Boolean {
        val requestVersion = musicConfigStateVersion
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
            val loadedAlbums = repo.getAlbums(sort)
            if (musicConfigStateVersion == requestVersion) {
                sortedAlbums = loadedAlbums
                reconcileAlbumDetailSelection(loadedAlbums, MusicLibraryPage.Albums)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            if (musicConfigStateVersion == requestVersion) {
                errorMsg = "获取专辑列表失败: ${e.message}"
            }
            false
        } finally {
            if (musicConfigStateVersion == requestVersion) {
                isLoadingAlbumList = false
            }
        }
    }

    fun openAlbumLibrary() {
        navigateToMusicPage(MusicLibraryPage.Albums)
        if (sortedAlbums.isEmpty()) {
            scope.launch { loadAlbumList(albumSort) }
        }
    }

    suspend fun loadPlaylists(): Boolean {
        val requestVersion = musicConfigStateVersion
        if (isLoadingPlaylists) return false
        val repo = navidromeRepository
        if (repo == null) {
            errorMsg = "请先保存 Navidrome 配置"
            return false
        }

        isLoadingPlaylists = true
        errorMsg = null
        return try {
            val loadedPlaylists = repo.getPlaylists()
            if (musicConfigStateVersion == requestVersion) {
                playlists = loadedPlaylists
                reconcilePlaylistDetailSelection(loadedPlaylists)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            if (musicConfigStateVersion == requestVersion) {
                errorMsg = "获取歌单失败: ${e.message}"
            }
            false
        } finally {
            if (musicConfigStateVersion == requestVersion) {
                isLoadingPlaylists = false
            }
        }
    }

    fun openPlaylistLibrary() {
        navigateToMusicPage(MusicLibraryPage.Playlists)
        if (playlists.isEmpty()) {
            scope.launch { loadPlaylists() }
        }
    }

    fun closePlaylistActionDialogs() {
        isCreatingPlaylist = false
        renamingPlaylist = null
        deletingPlaylist = null
        playlistNameDraft = ""
        playlistActionError = null
        isPlaylistActionRunning = false
    }

    fun createPlaylistFromDraft() {
        val name = playlistNameDraft.trim()
        if (name.isBlank() || isPlaylistActionRunning) return
        val repo = navidromeRepository
        if (repo == null) {
            playlistActionError = "请先保存 Navidrome 配置"
            return
        }
        val requestVersion = musicConfigStateVersion
        isPlaylistActionRunning = true
        playlistActionError = null
        scope.launch {
            try {
                val created = repo.createPlaylist(name)
                if (musicConfigStateVersion == requestVersion) {
                    closePlaylistActionDialogs()
                    navigateToMusicPage(MusicLibraryPage.Playlists, pushCurrent = false)
                    loadPlaylists()
                    selectedPlaylist = created
                }
            } catch (error: Exception) {
                if (musicConfigStateVersion == requestVersion) {
                    playlistActionError = "创建歌单失败: ${error.message ?: "未知错误"}"
                    isPlaylistActionRunning = false
                }
            }
        }
    }

    fun renamePlaylistFromDraft() {
        val playlist = renamingPlaylist ?: return
        val name = playlistNameDraft.trim()
        if (name.isBlank() || isPlaylistActionRunning) return
        val repo = navidromeRepository
        if (repo == null) {
            playlistActionError = "请先保存 Navidrome 配置"
            return
        }
        val requestVersion = musicConfigStateVersion
        isPlaylistActionRunning = true
        playlistActionError = null
        scope.launch {
            try {
                repo.renamePlaylist(playlist.id, name)
                if (musicConfigStateVersion == requestVersion) {
                    closePlaylistActionDialogs()
                    playlists = playlists.map { item -> if (item.id == playlist.id) item.copy(name = name) else item }
                    selectedPlaylist = selectedPlaylist?.let { item -> if (item.id == playlist.id) item.copy(name = name) else item }
                    loadPlaylists()
                }
            } catch (error: Exception) {
                if (musicConfigStateVersion == requestVersion) {
                    playlistActionError = "重命名歌单失败: ${error.message ?: "未知错误"}"
                    isPlaylistActionRunning = false
                }
            }
        }
    }

    fun deleteSelectedPlaylist() {
        val playlist = deletingPlaylist ?: return
        if (isPlaylistActionRunning) return
        val repo = navidromeRepository
        if (repo == null) {
            playlistActionError = "请先保存 Navidrome 配置"
            return
        }
        val requestVersion = musicConfigStateVersion
        isPlaylistActionRunning = true
        playlistActionError = null
        scope.launch {
            try {
                repo.deletePlaylist(playlist.id)
                if (musicConfigStateVersion == requestVersion) {
                    closePlaylistActionDialogs()
                    playlists = playlists.filterNot { it.id == playlist.id }
                    if (selectedPlaylist?.id == playlist.id) {
                        selectedPlaylist = null
                        playlistSongs = emptyList()
                        navigateToMusicPage(MusicLibraryPage.Playlists, pushCurrent = false)
                    }
                    loadPlaylists()
                }
            } catch (error: Exception) {
                if (musicConfigStateVersion == requestVersion) {
                    playlistActionError = "删除歌单失败: ${error.message ?: "未知错误"}"
                    isPlaylistActionRunning = false
                }
            }
        }
    }

    fun openSearch() {
        clearMusicSearch()
        navigateToMusicPage(MusicLibraryPage.Search)
    }

    fun playSongList(songs: List<NavidromeSong>, noPlayableMessage: String) {
        val startIndex = firstPlayableSongIndex(songs)
        if (startIndex == null) {
            errorMsg = noPlayableMessage
        } else {
            errorMsg = null
            onSongSelected(songs, startIndex, BULK_PLAY_ALLOW_UNPLAYABLE_START_FALLBACK)
        }
    }

    suspend fun playAlbum(album: NavidromeAlbum) {
        if (loadingAlbumId != null) return
        val repo = navidromeRepository
        if (repo == null) {
            errorMsg = "请先保存 Navidrome 配置"
            return
        }

        val requestVersion = musicConfigStateVersion
        loadingAlbumId = album.id
        errorMsg = null
        try {
            val albumSongs = repo.getAlbumSongs(album.id)
            if (musicConfigStateVersion != requestVersion) return
            val startIndex = firstPlayableSongIndex(albumSongs)
            if (startIndex == null) {
                errorMsg = "这张专辑没有可播放曲目"
            } else {
                onSongSelected(albumSongs, startIndex, BULK_PLAY_ALLOW_UNPLAYABLE_START_FALLBACK)
            }
        } catch (e: Exception) {
            if (musicConfigStateVersion == requestVersion) {
                errorMsg = "获取专辑曲目失败: ${e.message}"
            }
        } finally {
            if (musicConfigStateVersion == requestVersion) {
                loadingAlbumId = null
            }
        }
    }

    fun openAlbumDetail(album: NavidromeAlbum) {
        val requestVersion = musicConfigStateVersion
        selectedAlbum = album
        albumDetailSongs = emptyList()
        isLoadingAlbumDetail = true
        errorMsg = null
        navigateToMusicPage(MusicLibraryPage.AlbumDetail)
        scope.launch {
            // Cache-then-refresh: render the cached songs instantly, then refresh
            // in the background. Detail caches have no TTL — opening always refreshes.
            val cachedSongs = cacheRepository.loadAlbumDetailSongs(savedConfig, album.id)
            if (musicConfigStateVersion == requestVersion && selectedAlbum?.id == album.id && cachedSongs != null) {
                albumDetailSongs = cachedSongs
                isLoadingAlbumDetail = false
            }
            try {
                navidromeRepository?.let { repo ->
                    val loadedSongs = repo.getAlbumSongs(album.id)
                    if (musicConfigStateVersion == requestVersion && selectedAlbum?.id == album.id) {
                        albumDetailSongs = loadedSongs
                        cacheRepository.saveAlbumDetailSongs(savedConfig, album.id, loadedSongs)
                    }
                }
            } catch (e: Exception) {
                if (musicConfigStateVersion == requestVersion && selectedAlbum?.id == album.id) {
                    errorMsg = if (cachedSongs != null) {
                        "正在显示上次缓存：${e.message ?: "未知错误"}"
                    } else {
                        musicAlbumDetailLoadErrorMessage(e)
                    }
                }
            }
            if (musicConfigStateVersion == requestVersion && selectedAlbum?.id == album.id) {
                isLoadingAlbumDetail = false
            }
        }
    }

    fun openArtistDetail(artist: NavidromeArtist) {
        val requestVersion = musicConfigStateVersion
        selectedArtist = artist
        artistAlbums = emptyList()
        isLoadingArtistDetail = true
        errorMsg = null
        navigateToMusicPage(MusicLibraryPage.ArtistDetail)
        scope.launch {
            val cachedAlbums = cacheRepository.loadArtistAlbums(savedConfig, artist.id)
            if (musicConfigStateVersion == requestVersion && selectedArtist?.id == artist.id && cachedAlbums != null) {
                artistAlbums = cachedAlbums
                isLoadingArtistDetail = false
            }
            try {
                navidromeRepository?.let { repo ->
                    val loadedAlbums = repo.getArtistAlbums(artist.id)
                    if (musicConfigStateVersion == requestVersion && selectedArtist?.id == artist.id) {
                        artistAlbums = loadedAlbums
                        cacheRepository.saveArtistAlbums(savedConfig, artist.id, loadedAlbums)
                    }
                }
            } catch (e: Exception) {
                if (musicConfigStateVersion == requestVersion && selectedArtist?.id == artist.id) {
                    errorMsg = if (cachedAlbums != null) {
                        "正在显示上次缓存：${e.message ?: "未知错误"}"
                    } else {
                        musicArtistDetailLoadErrorMessage(e)
                    }
                }
            }
            if (musicConfigStateVersion == requestVersion && selectedArtist?.id == artist.id) {
                isLoadingArtistDetail = false
            }
        }
    }

    fun openPlaylistDetail(playlist: NavidromePlaylist) {
        val requestVersion = musicConfigStateVersion
        selectedPlaylist = playlist
        playlistSongs = emptyList()
        isLoadingPlaylistDetail = true
        errorMsg = null
        navigateToMusicPage(MusicLibraryPage.PlaylistDetail)
        scope.launch {
            val cachedSongs = cacheRepository.loadPlaylistSongs(savedConfig, playlist.id)
            if (musicConfigStateVersion == requestVersion && selectedPlaylist?.id == playlist.id && cachedSongs != null) {
                playlistSongs = cachedSongs
                isLoadingPlaylistDetail = false
            }
            try {
                navidromeRepository?.let { repo ->
                    val loadedSongs = repo.getPlaylistSongs(playlist.id)
                    if (musicConfigStateVersion == requestVersion && selectedPlaylist?.id == playlist.id) {
                        playlistSongs = loadedSongs
                        cacheRepository.savePlaylistSongs(savedConfig, playlist.id, loadedSongs)
                    }
                }
            } catch (e: Exception) {
                if (musicConfigStateVersion == requestVersion && selectedPlaylist?.id == playlist.id) {
                    errorMsg = if (cachedSongs != null) {
                        "正在显示上次缓存：${e.message ?: "未知错误"}"
                    } else {
                        "获取歌单曲目失败: ${e.message ?: "未知错误"}"
                    }
                }
            }
            if (musicConfigStateVersion == requestVersion && selectedPlaylist?.id == playlist.id) {
                isLoadingPlaylistDetail = false
            }
        }
    }

    LaunchedEffect(savedConfig) {
        val previousConfig = previousMusicConfig
        val shouldShowConfigResetNotice = previousConfig != null &&
            previousConfig.cacheKey() != savedConfig.cacheKey() &&
            (
                libraryPage != MusicLibraryPage.Home ||
                    albums.isNotEmpty() ||
                    songs.isNotEmpty() ||
                    recentlyAddedSongs.isNotEmpty() ||
                    artists.isNotEmpty() ||
                    playlists.isNotEmpty() ||
                    searchQuery.isNotBlank()
                )
        previousMusicConfig = savedConfig
        resetMusicStateAfterConfigChange()
        if (shouldShowConfigResetNotice) {
            musicResetNotice = "音乐配置已更新，已回到音乐首页。"
        }
        val requestVersion = musicConfigStateVersion
        // Clear the previous config's persisted cache so switching accounts/servers
        // does not leave dead cache JSON in DataStore. Only clear when the cache key
        // actually changed; never touch the freshly-saved config's own cache.
        if (previousConfig != null && previousConfig.cacheKey() != savedConfig.cacheKey()) {
            cacheRepository.clear(previousConfig)
        }
        if (savedConfig.isReadyForMusicSync()) {
            applyCachedMusicData(savedConfig, requestVersion)
            // Launch-path refresh is TTL-gated: skip the network when the cache is
            // still fresh. Manual refresh (the ↻ button) bypasses TTL by calling
            // refreshMusicData(...) directly.
            if (!isCacheFresh(cacheUpdatedAtMillis)) {
                refreshMusicData(savedConfig, requestVersion)
            }
        } else {
            albums = emptyList()
            songs = emptyList()
            recentlyAddedSongs = emptyList()
            artists = emptyList()
            cacheUpdatedAtMillis = null
            errorMsg = null
        }
    }

    fun navigateBackFromMusicPage() {
        musicResetNotice = null
        musicDetailInvalidationNotice = null
        val result = resolveMusicBackNavigation(
            currentPage = libraryPage,
            backStack = musicBackStack,
            hasSelectedAlbum = selectedAlbum != null,
            hasSelectedArtist = selectedArtist != null,
            hasSelectedPlaylist = selectedPlaylist != null
        )
        musicBackStack = result.backStack
        selectedTab = resolveMusicSelectedTabForPage(result.page)
        libraryPage = result.page
    }

    BackHandler(enabled = libraryPage != MusicLibraryPage.Home) {
        navigateBackFromMusicPage()
    }

    val hasContent = albums.isNotEmpty() || songs.isNotEmpty() || artists.isNotEmpty() || playlists.isNotEmpty()
    val hasErrorContent = when (libraryPage) {
        MusicLibraryPage.Albums -> sortedAlbums.isNotEmpty()
        MusicLibraryPage.ArtistDetail -> artistAlbums.isNotEmpty()
        MusicLibraryPage.AlbumDetail -> albumDetailSongs.isNotEmpty()
        MusicLibraryPage.Playlists -> playlists.isNotEmpty()
        MusicLibraryPage.PlaylistDetail -> playlistSongs.isNotEmpty()
        else -> hasContent
    }
    val visibleSongs = remember(songs, songSort, songFilterQuery) {
        sortMusicSongs(filterMusicSongs(songs, songFilterQuery), songSort)
    }
    val homeSongs = remember(recentlyAddedSongs) { musicHomePreviewSongs(recentlyAddedSongs) }
    val homePlaybackQueue = remember(recentlyAddedSongs) { musicHomePlaybackQueue(recentlyAddedSongs) }
    val homeAlbums = remember(albums) { albums.take(10) }
    val homeArtists = remember(artists) { artists.take(10) }
    val cacheAgeLabel = formatCacheAge(cacheUpdatedAtMillis)
    val refreshErrorSubtitle = mediaRefreshErrorSubtitle(errorMsg, hasErrorContent)
    val standaloneError = standaloneMediaError(errorMsg, hasErrorContent)
    val headerActions = buildList {
        if (savedConfig.isReadyForMusicSync()) {
            add(
                HeaderAction(
                    icon = Icons.Filled.Refresh,
                    contentDescription = "刷新音乐",
                    enabled = !isLoading,
                    onClick = {
                        scope.launch {
                            if (refreshMusicData(savedConfig)) {
                                if (
                                    libraryPage == MusicLibraryPage.Albums ||
                                    (libraryPage == MusicLibraryPage.AlbumDetail && musicBackStack.contains(MusicLibraryPage.Albums))
                                ) {
                                    loadAlbumList(albumSort)
                                }
                                if (libraryPage == MusicLibraryPage.Playlists || libraryPage == MusicLibraryPage.PlaylistDetail) {
                                    loadPlaylists()
                                }
                            }
                        }
                    }
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
    }
    val isHomePage = libraryPage == MusicLibraryPage.Home
    val headerTitle = when (libraryPage) {
        MusicLibraryPage.Home -> "音乐库"
        MusicLibraryPage.Albums -> "专辑"
        MusicLibraryPage.Songs -> "歌曲"
        MusicLibraryPage.Artists -> "歌手"
        MusicLibraryPage.ArtistDetail -> "歌手"
        MusicLibraryPage.AlbumDetail -> "专辑"
        MusicLibraryPage.Search -> "搜索"
        MusicLibraryPage.Playlists -> "歌单"
        MusicLibraryPage.PlaylistDetail -> "歌单"
    }
    val headerSubtitle = when (libraryPage) {
        MusicLibraryPage.Home -> when {
            refreshErrorSubtitle != null -> refreshErrorSubtitle
            isLoading && hasContent -> "正在刷新，先显示本地缓存"
            cacheAgeLabel != null -> "本地缓存，$cacheAgeLabel"
            hasContent -> "最近添加按曲目展示，点一下直接播放"
            else -> "连接 Navidrome 后，这里会自动同步你的内容"
        }
        MusicLibraryPage.Albums -> when {
            refreshErrorSubtitle != null -> refreshErrorSubtitle
            isLoadingAlbumList -> "正在按${albumSort.displayLabel()}加载专辑"
            sortedAlbums.isNotEmpty() -> "${sortedAlbums.size} 张专辑 · ${albumSort.displayLabel()}"
            else -> "按${albumSort.displayLabel()}浏览 Navidrome 专辑"
        }
        MusicLibraryPage.Songs -> refreshErrorSubtitle ?: "共 ${songs.size} 首，点一下直接播放"
        MusicLibraryPage.Artists -> refreshErrorSubtitle ?: "共 ${artists.size} 位歌手"
        MusicLibraryPage.ArtistDetail -> refreshErrorSubtitle.orEmpty()
        MusicLibraryPage.AlbumDetail -> refreshErrorSubtitle.orEmpty()
        MusicLibraryPage.Search -> "搜索歌曲、专辑、歌手"
        MusicLibraryPage.Playlists -> when {
            refreshErrorSubtitle != null -> refreshErrorSubtitle
            isLoadingPlaylists -> "正在加载 Navidrome 歌单"
            playlists.isNotEmpty() -> "共 ${playlists.size} 个歌单"
            else -> "浏览和播放 Navidrome 歌单"
        }
        MusicLibraryPage.PlaylistDetail -> refreshErrorSubtitle.orEmpty()
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        MediaPageHeader(
            title = headerTitle,
            subtitle = headerSubtitle,
            actions = headerActions,
            colorScheme = colorScheme,
            modifier = Modifier.padding(
                start = NordicSpacing.lg,
                top = NordicSpacing.lg,
                end = NordicSpacing.lg
            ),
            showBack = !isHomePage,
            onBack = ::navigateBackFromMusicPage
        )
        if (isHomePage) {
            Box(modifier = Modifier.padding(horizontal = NordicSpacing.lg, vertical = NordicSpacing.md)) {
                MusicSegmentedTabs(
                    selectedTab = selectedTab,
                    colorScheme = colorScheme,
                    onTabSelected = {
                        musicResetNotice = null
                        musicBackStack = emptyList()
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

        if (musicResetNotice != null) {
            MediaStateCard(
                title = "已应用新的音乐配置",
                subtitle = musicResetNotice.orEmpty(),
                density = MediaStateDensity.Compact,
                modifier = Modifier.padding(
                    horizontal = NordicSpacing.lg,
                    vertical = NordicSpacing.md
                )
            )
        }

        if (musicDetailInvalidationNotice != null) {
            MediaStateCard(
                title = "详情已更新",
                subtitle = musicDetailInvalidationNotice.orEmpty(),
                density = MediaStateDensity.Compact,
                modifier = Modifier.padding(horizontal = NordicSpacing.lg, vertical = NordicSpacing.md)
            )
        }

        if (standaloneError != null) {
            MediaStateCard(
                title = "连接失败",
                subtitle = standaloneError,
                hint = "检查配置或点击刷新重试",
                tone = MediaStateTone.Error,
                modifier = Modifier.padding(
                    horizontal = NordicSpacing.lg,
                    vertical = NordicSpacing.md
                )
            )
        }

        if (isLoading && !hasContent) {
            MediaLoadingCard(
                title = "正在同步 Navidrome",
                subtitle = "加载专辑、歌曲和歌手...",
                modifier = Modifier.padding(
                    horizontal = NordicSpacing.lg,
                    vertical = NordicSpacing.md
                )
            )
        }

        if (!isLoading && !isLoadingPlaylists && !isLoadingPlaylistDetail && standaloneError == null && !hasContent) {
            MediaStateCard(
                title = "先接入你的音乐库",
                subtitle = "填入 Navidrome 地址、用户名和密码后,最近添加的专辑和歌曲会直接出现在这里。",
                hint = "前往配置 tab 开始连接",
                modifier = Modifier.padding(
                    horizontal = NordicSpacing.lg,
                    vertical = NordicSpacing.md
                )
            )
        }

        AnimatedContent(
            targetState = libraryPage,
            transitionSpec = {
                NordicMotion.slideDirectionSpec(
                    resolveMusicLibraryPageForward(initialState, targetState)
                )
            },
            label = "music-library-page",
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) { page ->
            when (page) {
                MusicLibraryPage.Home -> MusicHomePage(
                    albums = albums,
                    recentlyAddedSongs = recentlyAddedSongs,
                    artists = artists,
                    homeSongs = homeSongs,
                    homePlaybackQueue = homePlaybackQueue,
                    homeAlbums = homeAlbums,
                    homeArtists = homeArtists,
                    colorScheme = colorScheme,
                    onOpenAlbumDetail = { album -> openAlbumDetail(album) },
                    onSongSelected = onSongSelected,
                    onOpenAlbumLibrary = { openAlbumLibrary() },
                    onNavigateToSongs = { navigateToMusicPage(MusicLibraryPage.Songs) },
                    onNavigateToArtists = { navigateToMusicPage(MusicLibraryPage.Artists) },
                    onOpenArtistDetail = { artist -> openArtistDetail(artist) }
                )
                MusicLibraryPage.Albums -> MusicAlbumsPage(
                    sortedAlbums = sortedAlbums,
                    isLoadingAlbumList = isLoadingAlbumList,
                    albumSort = albumSort,
                    colorScheme = colorScheme,
                    onOpenAlbumDetail = { album -> openAlbumDetail(album) },
                    onLoadAlbumList = { sort -> scope.launch { loadAlbumList(sort) } }
                )
                MusicLibraryPage.Songs -> MusicSongsPage(
                    songs = songs,
                    visibleSongs = visibleSongs,
                    songFilterQuery = songFilterQuery,
                    songSort = songSort,
                    colorScheme = colorScheme,
                    onSongSelected = onSongSelected,
                    onSongFilterChange = { songFilterQuery = it },
                    onSongFilterClear = { songFilterQuery = "" },
                    onSongSortChange = { songSort = it }
                )
                MusicLibraryPage.Artists -> MusicArtistsPage(
                    artists = artists,
                    colorScheme = colorScheme,
                    onOpenArtistDetail = { artist -> openArtistDetail(artist) }
                )
                MusicLibraryPage.ArtistDetail -> MusicArtistDetailPage(
                    artist = selectedArtist,
                    isLoadingArtistDetail = isLoadingArtistDetail,
                    artistAlbums = artistAlbums,
                    colorScheme = colorScheme,
                    hasVisibleError = errorMsg != null,
                    onOpenAlbumDetail = { album -> openAlbumDetail(album) },
                    onPlayArtistAll = {
                        scope.launch {
                            try {
                                val repo = navidromeRepository
                                if (repo != null) {
                                    val allSongs = artistAlbums.flatMap { album -> repo.getAlbumSongs(album.id) }
                                    playSongList(allSongs, "这位歌手没有可播放曲目")
                                }
                            } catch (error: Exception) {
                                errorMsg = "获取歌手曲目失败: ${error.message}"
                            }
                        }
                    }
                )
                MusicLibraryPage.AlbumDetail -> MusicAlbumDetailPage(
                    album = selectedAlbum,
                    isLoadingAlbumDetail = isLoadingAlbumDetail,
                    albumDetailSongs = albumDetailSongs,
                    colorScheme = colorScheme,
                    hasVisibleError = errorMsg != null,
                    onSongSelected = onSongSelected,
                    onPlayAlbumAll = { playSongList(albumDetailSongs, "这张专辑没有可播放曲目") }
                )
                MusicLibraryPage.Search -> MusicSearchPage(
                    searchQuery = searchQuery,
                    searchResult = searchResult,
                    isSearching = isSearching,
                    searchError = searchError,
                    albums = albums,
                    recentlyAddedSongs = recentlyAddedSongs,
                    songs = songs,
                    artists = artists,
                    colorScheme = colorScheme,
                    onSongSelected = onSongSelected,
                    onOpenAlbumDetail = { album -> openAlbumDetail(album) },
                    onOpenArtistDetail = { artist -> openArtistDetail(artist) },
                    onSearchQueryChange = { newQuery ->
                        searchQuery = newQuery
                        searchJob.getAndSet(null)?.cancel()
                        searchError = null
                        val query = newQuery.trim()
                        if (query.isBlank()) {
                            searchResult = null
                            isSearching = false
                        } else {
                            isSearching = true
                            val requestVersion = musicConfigStateVersion
                            searchJob.set(scope.launch {
                                delay(300)
                                try {
                                    val result = navidromeRepository?.search(query) ?: SearchMusicResult()
                                    if (musicConfigStateVersion == requestVersion && searchQuery.trim() == query) {
                                        searchResult = result
                                    }
                                } catch (e: Exception) {
                                    if (musicConfigStateVersion == requestVersion && searchQuery.trim() == query) {
                                        searchResult = null
                                        searchError = e.message ?: "请稍后重试。"
                                    }
                                } finally {
                                    if (musicConfigStateVersion == requestVersion && searchQuery.trim() == query) {
                                        isSearching = false
                                    }
                                }
                            })
                        }
                    },
                    onClearSearch = { clearMusicSearch() }
                )
                MusicLibraryPage.Playlists -> MusicPlaylistsPage(
                    isLoadingPlaylists = isLoadingPlaylists,
                    playlists = playlists,
                    colorScheme = colorScheme,
                    onOpenPlaylistDetail = { playlist -> openPlaylistDetail(playlist) },
                    onCreatePlaylist = {
                        playlistNameDraft = ""
                        playlistActionError = null
                        isCreatingPlaylist = true
                    }
                )
                MusicLibraryPage.PlaylistDetail -> MusicPlaylistDetailPage(
                    playlist = selectedPlaylist,
                    isLoadingPlaylistDetail = isLoadingPlaylistDetail,
                    playlistSongs = playlistSongs,
                    colorScheme = colorScheme,
                    hasVisibleError = errorMsg != null,
                    onSongSelected = onSongSelected,
                    onPlayAll = { playSongList(playlistSongs, "这个歌单没有可播放曲目") },
                    onRenamePlaylist = {
                        renamingPlaylist = selectedPlaylist
                        playlistNameDraft = selectedPlaylist?.name ?: ""
                        playlistActionError = null
                    },
                    onDeletePlaylist = {
                        deletingPlaylist = selectedPlaylist
                        playlistActionError = null
                    }
                )
            }
        }
    }

    fun submitPlaylistNameAction() {
        if (playlistNameDraft.isBlank() || isPlaylistActionRunning) return
        if (isCreatingPlaylist) createPlaylistFromDraft() else renamePlaylistFromDraft()
    }

    if (isCreatingPlaylist || renamingPlaylist != null) {
        val keyboard = LocalSoftwareKeyboardController.current
        AlertDialog(
            onDismissRequest = {
                if (!isPlaylistActionRunning) closePlaylistActionDialogs()
            },
            title = {
                Text(if (isCreatingPlaylist) "新建歌单" else "重命名歌单")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(NordicSpacing.md)) {
                    OutlinedTextField(
                        value = playlistNameDraft,
                        onValueChange = { playlistNameDraft = it },
                        label = { Text("歌单名称") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        enabled = !isPlaylistActionRunning,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colorScheme.primary,
                            unfocusedBorderColor = colorScheme.outline
                        ),
                        shape = NordicShapes.md,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (playlistNameDraft.isNotBlank() && !isPlaylistActionRunning) {
                                keyboard?.hide()
                                submitPlaylistNameAction()
                            }
                        })
                    )
                    playlistActionError?.let { message ->
                        Text(
                            message,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = playlistNameDraft.isNotBlank() && !isPlaylistActionRunning,
                    onClick = ::submitPlaylistNameAction
                ) {
                    Text(if (isPlaylistActionRunning) "处理中" else if (isCreatingPlaylist) "创建" else "保存")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isPlaylistActionRunning,
                    onClick = { closePlaylistActionDialogs() }
                ) {
                    Text("取消")
                }
            }
        )
    }

    deletingPlaylist?.let { playlist ->
        AlertDialog(
            onDismissRequest = {
                if (!isPlaylistActionRunning) closePlaylistActionDialogs()
            },
            title = { Text("删除歌单") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(NordicSpacing.md)) {
                    Text("确定删除“${playlist.name}”？这个操作会同步到 Navidrome。")
                    playlistActionError?.let { message ->
                        Text(
                            message,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isPlaylistActionRunning,
                    colors = ButtonDefaults.textButtonColors(contentColor = colorScheme.error),
                    onClick = { deleteSelectedPlaylist() }
                ) {
                    Text(if (isPlaylistActionRunning) "处理中" else "删除")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isPlaylistActionRunning,
                    onClick = { closePlaylistActionDialogs() }
                ) {
                    Text("取消")
                }
            }
        )
    }
}
