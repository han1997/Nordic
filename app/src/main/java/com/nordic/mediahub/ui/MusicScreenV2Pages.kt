package com.nordic.mediahub.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.nordic.mediahub.data.NavidromeAlbum
import com.nordic.mediahub.data.NavidromeAlbumSort
import com.nordic.mediahub.data.NavidromeArtist
import com.nordic.mediahub.data.NavidromePlaylist
import com.nordic.mediahub.data.NavidromeSong
import com.nordic.mediahub.data.SearchMusicResult
import com.nordic.mediahub.ui.theme.NordicAlpha
import com.nordic.mediahub.ui.theme.NordicShapes
import com.nordic.mediahub.ui.theme.NordicSpacing

/**
 * Shared [LazyColumn] shell for every Music library page. Centralizing the
 * content padding + vertical spacing keeps the per-page composables below
 * focused on their own content and guarantees identical scroll/list chrome
 * across Home / Albums / Songs / Artists / details / search / playlists.
 */
@Composable
private fun MusicPageList(
    isHome: Boolean,
    itemSpacing: Dp = if (isHome) NordicSpacing.xxl else NordicSpacing.sm,
    feedback: MusicLibraryFeedbackState = MusicLibraryFeedbackState(),
    onRetry: (() -> Unit)? = null,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = NordicSpacing.lg,
                top = if (isHome) 0.dp else NordicSpacing.md,
                end = NordicSpacing.lg,
                bottom = NordicSpacing.xxl
            ),
            verticalArrangement = Arrangement.spacedBy(itemSpacing)
        ) {
            if (feedback.isVisible) item(key = "library-feedback") { MusicLibraryFeedback(feedback, onRetry) }
            content()
        }
        MusicScrollbar(
            state = listState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = NordicSpacing.xs)
        )
    }
}

@Composable
internal fun MusicHomePage(
    albums: List<NavidromeAlbum>,
    recentlyAddedSongs: List<NavidromeSong>,
    artists: List<NavidromeArtist>,
    homeSongs: List<NavidromeSong>,
    homePlaybackQueue: List<NavidromeSong>,
    homeAlbums: List<NavidromeAlbum>,
    homeArtists: List<NavidromeArtist>,
    colorScheme: ColorScheme,
    onOpenAlbumDetail: (NavidromeAlbum) -> Unit,
    onSongSelected: (List<NavidromeSong>, Int, Boolean) -> Unit,
    onOpenAlbumLibrary: () -> Unit,
    onNavigateToSongs: () -> Unit,
    onNavigateToArtists: () -> Unit,
    onOpenArtistDetail: (NavidromeArtist) -> Unit,
    feedback: MusicLibraryFeedbackState = MusicLibraryFeedbackState(),
    onRetry: (() -> Unit)? = null
) {
    MusicPageList(isHome = true, feedback = feedback, onRetry = onRetry) {
        if (albums.isEmpty() && recentlyAddedSongs.isEmpty() && artists.isEmpty() && !feedback.suppressesEmptyState) {
            item { MusicDetailEmptyState("音乐库暂无内容", "当前音乐库还没有可展示的专辑、歌曲或歌手。") }
        }
        if (albums.isNotEmpty()) {
            item {
                Column(Modifier.testTag("music-home-hero"), verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)) {
                    MusicSectionHeader(
                        title = "刚刚同步",
                        colorScheme = colorScheme
                    )

                    MusicHeroBanner(
                        album = albums.first(),
                        colorScheme = colorScheme,
                        onClick = { onOpenAlbumDetail(albums.first()) }
                    )
                }
            }
        }

        if (recentlyAddedSongs.isNotEmpty()) {
            item {
                Column(Modifier.testTag("music-home-songs"), verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)) {
                    MusicSectionHeader(
                        title = "最近添加",
                        colorScheme = colorScheme,
                        actionLabel = "全部",
                        onAction = onNavigateToSongs
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(NordicSpacing.md)) {
                        itemsIndexed(
                            items = homeSongs,
                            key = { index, song -> "home-song-${song.id}-$index" },
                            contentType = { _, _ -> "home-song-card" }
                        ) { index, song ->
                            SongShelfCard(
                                song = song,
                                colorScheme = colorScheme,
                                onClick = {
                                    onSongSelected(
                                        homePlaybackQueue,
                                        index,
                                        DIRECT_SELECTION_ALLOW_UNPLAYABLE_START_FALLBACK
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        if (albums.isNotEmpty()) {
            item {
                Column(Modifier.testTag("music-home-albums"), verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)) {
                    MusicSectionHeader(
                        title = "最近专辑",
                        colorScheme = colorScheme,
                        actionLabel = "全部",
                        onAction = onOpenAlbumLibrary
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(NordicSpacing.md)) {
                        items(
                            items = homeAlbums,
                            key = { "home-album-${it.id}" },
                            contentType = { "home-album-card" }
                        ) { album ->
                            CompactAlbumShelfCard(
                                album = album,
                                colorScheme = colorScheme,
                                onClick = { onOpenAlbumDetail(album) }
                            )
                        }
                    }
                }
            }
        }

        if (artists.isNotEmpty()) {
            item {
                Column(Modifier.testTag("music-home-artists"), verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)) {
                    MusicSectionHeader(
                        title = "曲库歌手",
                        colorScheme = colorScheme,
                        actionLabel = "全部",
                        onAction = onNavigateToArtists
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(NordicSpacing.md)) {
                        items(
                            items = homeArtists,
                            key = { "home-artist-${it.id}" },
                            contentType = { "home-artist-card" }
                        ) { artist ->
                            ArtistShelfCard(
                                artist = artist,
                                colorScheme = colorScheme,
                                onClick = { onOpenArtistDetail(artist) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MusicAlbumsPage(
    sortedAlbums: List<NavidromeAlbum>,
    isLoadingAlbumList: Boolean,
    albumSort: NavidromeAlbumSort,
    colorScheme: ColorScheme,
    onOpenAlbumDetail: (NavidromeAlbum) -> Unit,
    onLoadAlbumList: (NavidromeAlbumSort) -> Unit,
    feedback: MusicLibraryFeedbackState = MusicLibraryFeedbackState(),
    onRetry: (() -> Unit)? = null
) {
    MusicPageList(isHome = false, feedback = feedback, onRetry = onRetry) {
        item {
            AlbumSortSegmentedControl(
                selectedSort = albumSort,
                colorScheme = colorScheme,
                onSortSelected = { sort ->
                    if (sort != albumSort) {
                        onLoadAlbumList(sort)
                    }
                }
            )
        }

        if (isLoadingAlbumList) {
            item {
                MusicListLoadingStatus("正在加载专辑", "按${albumSort.displayLabel()}从 Navidrome 拉取专辑列表。",
                    hasContent = sortedAlbums.isNotEmpty())
            }
        }
        if (shouldShowMusicCollectionEmpty(isLoadingAlbumList, sortedAlbums.size, feedback.suppressesEmptyState)) {
            item {
                MusicDetailEmptyState(
                    title = "暂无专辑",
                    subtitle = "刷新音乐库后，Navidrome 专辑会显示在这里。",
                )
            }
        } else {
            items(sortedAlbums, key = { it.id }, contentType = { "album-row" }) { album ->
                AlbumListRow(
                    album = album,
                    colorScheme = colorScheme,
                    onClick = { onOpenAlbumDetail(album) }
                )
            }
        }
    }
}

@Composable
internal fun MusicSongsPage(
    songs: List<NavidromeSong>,
    visibleSongs: List<NavidromeSong>,
    songFilterQuery: String,
    songSort: MusicSongSort,
    colorScheme: ColorScheme,
    onSongSelected: (List<NavidromeSong>, Int, Boolean) -> Unit,
    onSongFilterChange: (String) -> Unit,
    onSongFilterClear: () -> Unit,
    onSongSortChange: (MusicSongSort) -> Unit,
    feedback: MusicLibraryFeedbackState = MusicLibraryFeedbackState(),
    onRetry: (() -> Unit)? = null
) {
    MusicPageList(isHome = false, feedback = feedback, onRetry = onRetry) {
        if (songs.isEmpty()) {
            if (!feedback.suppressesEmptyState) item {
                MusicDetailEmptyState(
                    title = "暂无歌曲",
                    subtitle = "刷新音乐库后，Navidrome 中的全部歌曲会显示在这里。",
                )
            }
        } else {
            item {
                MediaSearchField(
                    value = songFilterQuery, onValueChange = onSongFilterChange,
                    placeholder = "筛选标题、歌手或专辑", clearDescription = "清除歌曲筛选",
                    onClear = onSongFilterClear, colorScheme = colorScheme
                )
            }
            item {
                SongSortSegmentedControl(
                    selectedSort = songSort,
                    colorScheme = colorScheme,
                    onSortSelected = onSongSortChange
                )
            }
            if (visibleSongs.isEmpty()) {
                item {
                    MusicDetailEmptyState(
                        title = "没有匹配歌曲",
                        subtitle = "换一个关键词，或清空筛选后查看全部歌曲。",
                    )
                }
            } else {
                itemsIndexed(
                    items = visibleSongs,
                    key = { index, song -> "song-${song.id}-$index" },
                    contentType = { _, _ -> "song-row" }
                ) { index, song ->
                    SongListRow(
                        song = song,
                        colorScheme = colorScheme,
                        onClick = {
                            onSongSelected(
                                visibleSongs,
                                index,
                                DIRECT_SELECTION_ALLOW_UNPLAYABLE_START_FALLBACK
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun MusicArtistsPage(
    artists: List<NavidromeArtist>,
    colorScheme: ColorScheme,
    onOpenArtistDetail: (NavidromeArtist) -> Unit,
    feedback: MusicLibraryFeedbackState = MusicLibraryFeedbackState(),
    onRetry: (() -> Unit)? = null
) {
    MusicPageList(isHome = false, feedback = feedback, onRetry = onRetry) {
        if (artists.isEmpty() && !feedback.suppressesEmptyState) {
            item {
                MusicDetailEmptyState(
                    title = "暂无歌手",
                    subtitle = "同步 Navidrome 后，歌手会按列表展示在这里。",
                )
            }
        } else {
            items(artists, key = { it.id }, contentType = { "artist-row" }) { artist ->
                ArtistListRow(
                    artist = artist,
                    colorScheme = colorScheme,
                    onClick = { onOpenArtistDetail(artist) }
                )
            }
        }
    }
}

@Composable
internal fun MusicArtistDetailPage(
    artist: NavidromeArtist?,
    isLoadingArtistDetail: Boolean,
    artistAlbums: List<NavidromeAlbum>,
    colorScheme: ColorScheme,
    onOpenAlbumDetail: (NavidromeAlbum) -> Unit,
    onPlayArtistAll: () -> Unit,
    hasVisibleError: Boolean = false,
    feedback: MusicLibraryFeedbackState = MusicLibraryFeedbackState(),
    onRetry: (() -> Unit)? = null
) {
    MusicPageList(isHome = false, feedback = feedback, onRetry = onRetry) {
        if (artist == null) {
            item { MusicDetailEmptyState("未选择歌手", "返回首页选择一位歌手。") }
        } else {
            item {
                MusicCollectionHeader(
                    itemId = artist.id, title = artist.name, subtitle = "",
                    metadata = listOf(musicAlbumCountLabel(resolveMusicCollectionCount(
                        artist.albumCount, artistAlbums.size, isLoadingArtistDetail, hasVisibleError))),
                    artworkUrl = null, fallbackIcon = Icons.Filled.Person, initials = artist.initials,
                    artworkShape = NordicShapes.full, colorScheme = colorScheme,
                    onPlayAll = onPlayArtistAll,
                    playEnabled = !isLoadingArtistDetail && artistAlbums.isNotEmpty()
                )
            }
            if (isLoadingArtistDetail) {
                item { MediaLoadingCard("正在加载歌手专辑", "从 Navidrome 拉取该歌手的专辑列表。") }
            }
            if (shouldShowMusicCollectionEmpty(isLoadingArtistDetail, artistAlbums.size, hasVisibleError || feedback.suppressesEmptyState)) {
                item { MusicDetailEmptyState("暂无专辑", "该歌手暂无可用专辑。") }
            } else {
                items(artistAlbums, key = { it.id }, contentType = { "artist-album-row" }) { album ->
                    AlbumListRow(album, colorScheme, onClick = { onOpenAlbumDetail(album) })
                }
            }
        }
    }
}

@Composable
internal fun MusicAlbumDetailPage(
    album: NavidromeAlbum?,
    isLoadingAlbumDetail: Boolean,
    albumDetailSongs: List<NavidromeSong>,
    colorScheme: ColorScheme,
    onSongSelected: (List<NavidromeSong>, Int, Boolean) -> Unit,
    onPlayAlbumAll: () -> Unit,
    hasVisibleError: Boolean = false,
    feedback: MusicLibraryFeedbackState = MusicLibraryFeedbackState(),
    onRetry: (() -> Unit)? = null
) {
    MusicPageList(isHome = false, feedback = feedback, onRetry = onRetry) {
        if (album == null) {
            item { MusicDetailEmptyState("未选择专辑", "返回首页选择一张专辑。") }
        } else {
            item {
                AlbumDetailHeader(
                    album = album, colorScheme = colorScheme, onPlayAll = onPlayAlbumAll,
                    songCount = resolveMusicCollectionCount(album.songCount, albumDetailSongs.size, isLoadingAlbumDetail, hasVisibleError),
                    playEnabled = !isLoadingAlbumDetail && albumDetailSongs.isNotEmpty()
                )
            }
            if (isLoadingAlbumDetail) {
                item { MediaLoadingCard("正在加载专辑曲目", "从 Navidrome 拉取这张专辑的歌曲列表。") }
            }
            if (shouldShowMusicCollectionEmpty(isLoadingAlbumDetail, albumDetailSongs.size, hasVisibleError || feedback.suppressesEmptyState)) {
                item { MusicDetailEmptyState("暂无曲目", "这张专辑暂时没有曲目。") }
            } else {
                itemsIndexed(albumDetailSongs, key = { index, song -> "album-song-${song.id}-$index" },
                    contentType = { _, _ -> "album-song-row" }) { index, song ->
                    SongListRow(song, colorScheme, showAlbum = false, onClick = {
                        onSongSelected(albumDetailSongs, index, DIRECT_SELECTION_ALLOW_UNPLAYABLE_START_FALLBACK)
                    })
                }
            }
        }
    }
}

@Composable
internal fun MusicSearchPage(
    searchQuery: String,
    searchResult: SearchMusicResult?,
    isSearching: Boolean,
    searchError: String?,
    albums: List<NavidromeAlbum>,
    recentlyAddedSongs: List<NavidromeSong>,
    songs: List<NavidromeSong>,
    artists: List<NavidromeArtist>,
    colorScheme: ColorScheme,
    onSongSelected: (List<NavidromeSong>, Int, Boolean) -> Unit,
    onOpenAlbumDetail: (NavidromeAlbum) -> Unit,
    onOpenArtistDetail: (NavidromeArtist) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    feedback: MusicLibraryFeedbackState = MusicLibraryFeedbackState(),
    onRetry: (() -> Unit)? = null
) {
    MusicPageList(isHome = false, feedback = feedback, onRetry = onRetry) {
        item {
            MediaSearchField(
                value = searchQuery, onValueChange = onSearchQueryChange,
                placeholder = "搜索歌曲、专辑、歌手", clearDescription = "清除搜索关键词",
                onClear = onClearSearch, colorScheme = colorScheme,
                showClear = shouldShowMusicSearchClearAction(searchQuery)
            )
        }

        if (searchQuery.isBlank()) {
            item {
                MusicSearchLanding(
                    albums = albums,
                    songs = recentlyAddedSongs.ifEmpty { songs },
                    artists = artists,
                    colorScheme = colorScheme,
                    onAlbumClick = { album -> onOpenAlbumDetail(album) },
                    onSongClick = { index ->
                        val source = recentlyAddedSongs.ifEmpty { songs }
                        if (source.isNotEmpty()) {
                            onSongSelected(
                                source,
                                index,
                                DIRECT_SELECTION_ALLOW_UNPLAYABLE_START_FALLBACK
                            )
                        }
                    },
                    onArtistClick = { artist -> onOpenArtistDetail(artist) }
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
            item { SecondaryActionButton("重试搜索", colorScheme, onClick = { onSearchQueryChange(searchQuery) }) }
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
                        onClick = { onOpenArtistDetail(artist) }
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
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(NordicSpacing.md)) {
                        items(
                            items = result.albums,
                            key = { "search-album-${it.id}" },
                            contentType = { "search-album-card" }
                        ) { album ->
                            CompactAlbumShelfCard(
                                album = album,
                                colorScheme = colorScheme,
                                onClick = { onOpenAlbumDetail(album) }
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
                itemsIndexed(
                    items = result.songs,
                    key = { index, song -> "search-song-${song.id}-$index" },
                    contentType = { _, _ -> "search-song-row" }
                ) { index, song ->
                    SongListRow(
                        song = song,
                        colorScheme = colorScheme,
                        onClick = {
                            onSongSelected(
                                result.songs,
                                index,
                                DIRECT_SELECTION_ALLOW_UNPLAYABLE_START_FALLBACK
                            )
                        }
                    )
                }
            }
            if (result.artists.isEmpty() && result.albums.isEmpty() && result.songs.isEmpty()) {
                item {
                    MusicDetailEmptyState(
                        title = "没有找到结果",
                        subtitle = "试试其他关键词。",
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MusicPlaylistsPage(
    isLoadingPlaylists: Boolean,
    playlists: List<NavidromePlaylist>,
    colorScheme: ColorScheme,
    onOpenPlaylistDetail: (NavidromePlaylist) -> Unit,
    onCreatePlaylist: () -> Unit,
    feedback: MusicLibraryFeedbackState = MusicLibraryFeedbackState(),
    onRetry: (() -> Unit)? = null
) {
    MusicPageList(isHome = false, feedback = feedback, onRetry = onRetry) {
        item {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)) {
                Text(when {
                    isLoadingPlaylists -> "正在更新歌单"
                    playlists.isEmpty() && feedback.suppressesEmptyState -> "我的歌单"
                    else -> "${playlists.size} 个歌单"
                }, style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically))
                MusicCollectionAction("新建歌单", Icons.Filled.Add, colorScheme, onCreatePlaylist)
            }
        }
        if (isLoadingPlaylists) {
            item { MusicListLoadingStatus("正在加载歌单", "从 Navidrome 拉取你的歌单列表。", playlists.isNotEmpty()) }
        }
        if (shouldShowMusicCollectionEmpty(isLoadingPlaylists, playlists.size, feedback.suppressesEmptyState)) {
            item { MusicDetailEmptyState("暂无歌单", "Navidrome 中的歌单会显示在这里，也可以新建歌单。") }
        } else {
            items(playlists, key = { it.id }, contentType = { "playlist-row" }) { playlist ->
                PlaylistListRow(playlist, colorScheme, onClick = { onOpenPlaylistDetail(playlist) })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MusicPlaylistDetailPage(
    playlist: NavidromePlaylist?,
    isLoadingPlaylistDetail: Boolean,
    playlistSongs: List<NavidromeSong>,
    colorScheme: ColorScheme,
    onSongSelected: (List<NavidromeSong>, Int, Boolean) -> Unit,
    onPlayAll: () -> Unit,
    onRenamePlaylist: () -> Unit,
    onDeletePlaylist: () -> Unit,
    hasVisibleError: Boolean = false,
    feedback: MusicLibraryFeedbackState = MusicLibraryFeedbackState(),
    onRetry: (() -> Unit)? = null
) {
    MusicPageList(isHome = false, feedback = feedback, onRetry = onRetry) {
        if (playlist == null) {
            item { MusicDetailEmptyState("未选择歌单", "返回歌单列表选择一个歌单。") }
        } else {
            item {
                PlaylistDetailHeader(
                    playlist = playlist,
                    songCount = resolveMusicCollectionCount(playlist.songCount, playlistSongs.size, isLoadingPlaylistDetail, hasVisibleError),
                    colorScheme = colorScheme, onPlayAll = onPlayAll,
                    playEnabled = !isLoadingPlaylistDetail && playlistSongs.isNotEmpty()
                )
            }
            if (!isLoadingPlaylistDetail) item {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NordicSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)) {
                    MusicCollectionAction("重命名", Icons.Filled.Edit, colorScheme, onRenamePlaylist)
                    MusicCollectionAction("删除歌单", Icons.Filled.Delete, colorScheme, onDeletePlaylist, destructive = true)
                }
            }
            if (isLoadingPlaylistDetail) {
                item { MediaLoadingCard("正在加载歌单曲目", "从 Navidrome 拉取这个歌单的歌曲列表。") }
            }
            if (shouldShowMusicCollectionEmpty(isLoadingPlaylistDetail, playlistSongs.size, hasVisibleError || feedback.suppressesEmptyState)) {
                item { MusicDetailEmptyState("暂无曲目", "这个歌单暂时没有曲目。") }
            } else {
                itemsIndexed(playlistSongs, key = { index, song -> "playlist-song-${song.id}-$index" },
                    contentType = { _, _ -> "playlist-song-row" }) { index, song ->
                    SongListRow(song, colorScheme, onClick = {
                        onSongSelected(playlistSongs, index, DIRECT_SELECTION_ALLOW_UNPLAYABLE_START_FALLBACK)
                    })
                }
            }
        }
    }
}

@Composable
private fun MusicListLoadingStatus(title: String, subtitle: String, hasContent: Boolean) {
    if (!hasContent) {
        MediaLoadingCard(title, subtitle)
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)) {
            Text("正在更新，保留现有内容", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}
