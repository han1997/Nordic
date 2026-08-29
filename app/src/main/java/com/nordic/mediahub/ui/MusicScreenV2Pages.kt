package com.nordic.mediahub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
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
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = NordicSpacing.lg,
                end = NordicSpacing.lg,
                bottom = NordicSpacing.xxl
            ),
            verticalArrangement = Arrangement.spacedBy(
                if (isHome) NordicSpacing.lg else NordicSpacing.md
            )
        ) {
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
    onOpenArtistDetail: (NavidromeArtist) -> Unit
) {
    MusicPageList(isHome = true) {
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
                    onClick = { onOpenAlbumDetail(albums.first()) }
                )
            }
        }

        if (recentlyAddedSongs.isNotEmpty()) {
            item {
                MusicSectionHeader(
                    title = "最近添加",
                    subtitle = "新同步到曲库的曲目，点一下直接播放",
                    colorScheme = colorScheme,
                    actionLabel = "全部",
                    onAction = onNavigateToSongs
                )
            }
            item {
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

        if (albums.isNotEmpty()) {
            item {
                MusicSectionHeader(
                    title = "最近专辑",
                    subtitle = "按最近添加展示，进入全部后可切换排序",
                    colorScheme = colorScheme,
                    actionLabel = "全部",
                    onAction = onOpenAlbumLibrary
                )
            }
            item {
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

        if (artists.isNotEmpty()) {
            item {
                MusicSectionHeader(
                    title = "常听歌手",
                    subtitle = "从熟悉的声音继续展开",
                    colorScheme = colorScheme,
                    actionLabel = "全部",
                    onAction = onNavigateToArtists
                )
            }
            item {
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

@Composable
internal fun MusicAlbumsPage(
    sortedAlbums: List<NavidromeAlbum>,
    isLoadingAlbumList: Boolean,
    albumSort: NavidromeAlbumSort,
    colorScheme: ColorScheme,
    onOpenAlbumDetail: (NavidromeAlbum) -> Unit,
    onLoadAlbumList: (NavidromeAlbumSort) -> Unit
) {
    MusicPageList(isHome = false) {
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
                MediaLoadingCard(
                    title = "正在加载专辑",
                    subtitle = "按${albumSort.displayLabel()}从 Navidrome 拉取专辑列表。"
                )
            }
        } else if (sortedAlbums.isEmpty()) {
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
    onSongSortChange: (MusicSongSort) -> Unit
) {
    MusicPageList(isHome = false) {
        if (songs.isEmpty()) {
            item {
                MusicDetailEmptyState(
                    title = "暂无歌曲",
                    subtitle = "刷新音乐库后，Navidrome 中的全部歌曲会显示在这里。",
                )
            }
        } else {
            item {
                OutlinedTextField(
                    value = songFilterQuery,
                    onValueChange = onSongFilterChange,
                    placeholder = {
                        Text(
                            "筛选标题、歌手或专辑",
                            color = colorScheme.onSurface.copy(alpha = NordicAlpha.faint)
                        )
                    },
                    trailingIcon = if (songFilterQuery.isNotBlank()) {
                        {
                            IconButton(onClick = onSongFilterClear) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "清除歌曲筛选",
                                    tint = colorScheme.onSurface.copy(alpha = NordicAlpha.medium)
                                )
                            }
                        }
                    } else {
                        null
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colorScheme.primary,
                        unfocusedBorderColor = colorScheme.onSurface.copy(alpha = 0.2f)
                    ),
                    shape = NordicShapes.md,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
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
    onOpenArtistDetail: (NavidromeArtist) -> Unit
) {
    MusicPageList(isHome = false) {
        if (artists.isEmpty()) {
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
    onPlayArtistAll: () -> Unit
) {
    MusicPageList(isHome = false) {
        if (artist == null) {
            item {
                MusicDetailEmptyState(
                    title = "未选择歌手",
                    subtitle = "返回首页选择一位歌手。",
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
                            shape = NordicShapes.full,
                            modifier = Modifier
                                .height(34.dp)
                                .clickable { onPlayArtistAll() }
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = NordicSpacing.lg),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "播放全部",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }
            if (isLoadingArtistDetail) {
                item {
                    MediaLoadingCard(
                        title = "正在加载歌手专辑",
                        subtitle = "从 Navidrome 拉取该歌手的专辑列表。"
                    )
                }
            } else if (artistAlbums.isEmpty()) {
                item {
                    MusicDetailEmptyState(
                        title = "暂无专辑",
                        subtitle = "该歌手暂无可用专辑。",
                    )
                }
            } else {
                items(artistAlbums, key = { it.id }, contentType = { "artist-album-row" }) { album ->
                    AlbumListRow(
                        album = album,
                        colorScheme = colorScheme,
                        onClick = { onOpenAlbumDetail(album) }
                    )
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
    onPlayAlbumAll: () -> Unit
) {
    MusicPageList(isHome = false) {
        if (album == null) {
            item {
                MusicDetailEmptyState(
                    title = "未选择专辑",
                    subtitle = "返回首页选择一张专辑。",
                )
            }
        } else if (isLoadingAlbumDetail) {
            item {
                MediaLoadingCard(
                    title = "正在加载专辑曲目",
                    subtitle = "从 Navidrome 拉取这张专辑的歌曲列表。"
                )
            }
        } else {
            item {
                AlbumDetailHeader(
                    album = album,
                    colorScheme = colorScheme,
                    onPlayAll = onPlayAlbumAll
                )
            }
            itemsIndexed(
                items = albumDetailSongs,
                key = { index, song -> "album-song-${song.id}-$index" },
                contentType = { _, _ -> "album-song-row" }
            ) { index, song ->
                SongListRow(
                    song = song,
                    colorScheme = colorScheme,
                    onClick = {
                        onSongSelected(
                            albumDetailSongs,
                            index,
                            DIRECT_SELECTION_ALLOW_UNPLAYABLE_START_FALLBACK
                        )
                    }
                )
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
    onClearSearch: () -> Unit
) {
    MusicPageList(isHome = false) {
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text(
                        "搜索歌曲、专辑、歌手...",
                        color = colorScheme.onSurface.copy(alpha = NordicAlpha.faint)
                    )
                },
                trailingIcon = if (shouldShowMusicSearchClearAction(searchQuery)) {
                    {
                        IconButton(onClick = onClearSearch) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "清除搜索关键词",
                                tint = colorScheme.onSurface.copy(alpha = NordicAlpha.medium)
                            )
                        }
                    }
                } else {
                    null
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colorScheme.primary,
                    unfocusedBorderColor = colorScheme.onSurface.copy(alpha = 0.2f)
                ),
                shape = NordicShapes.md,
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

@Composable
internal fun MusicPlaylistsPage(
    isLoadingPlaylists: Boolean,
    playlists: List<NavidromePlaylist>,
    colorScheme: ColorScheme,
    onOpenPlaylistDetail: (NavidromePlaylist) -> Unit,
    onCreatePlaylist: () -> Unit
) {
    MusicPageList(isHome = false) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Surface(
                    color = colorScheme.primary,
                    contentColor = colorScheme.onPrimary,
                    shape = NordicShapes.full,
                    modifier = Modifier
                        .height(36.dp)
                        .clickable { onCreatePlaylist() }
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = NordicSpacing.lg),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("新建歌单", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        if (isLoadingPlaylists) {
            item {
                MediaLoadingCard(
                    title = "正在加载歌单",
                    subtitle = "从 Navidrome 拉取你的歌单列表。"
                )
            }
        } else if (playlists.isEmpty()) {
            item {
                MusicDetailEmptyState(
                    title = "暂无歌单",
                    subtitle = "Navidrome 中的歌单会显示在这里。",
                )
            }
        } else {
            items(playlists, key = { it.id }, contentType = { "playlist-row" }) { playlist ->
                PlaylistListRow(
                    playlist = playlist,
                    colorScheme = colorScheme,
                    onClick = { onOpenPlaylistDetail(playlist) }
                )
            }
        }
    }
}

@Composable
internal fun MusicPlaylistDetailPage(
    playlist: NavidromePlaylist?,
    isLoadingPlaylistDetail: Boolean,
    playlistSongs: List<NavidromeSong>,
    colorScheme: ColorScheme,
    onSongSelected: (List<NavidromeSong>, Int, Boolean) -> Unit,
    onPlayAll: () -> Unit,
    onRenamePlaylist: () -> Unit,
    onDeletePlaylist: () -> Unit
) {
    MusicPageList(isHome = false) {
        if (playlist == null) {
            item {
                MusicDetailEmptyState(
                    title = "未选择歌单",
                    subtitle = "返回歌单列表选择一个歌单。",
                )
            }
        } else if (isLoadingPlaylistDetail) {
            item {
                MediaLoadingCard(
                    title = "正在加载歌单曲目",
                    subtitle = "从 Navidrome 拉取这个歌单的歌曲列表。"
                )
            }
        } else {
            item {
                PlaylistDetailHeader(
                    playlist = playlist,
                    songCount = playlistSongs.size,
                    colorScheme = colorScheme,
                    onPlayAll = onPlayAll
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(NordicSpacing.sm)
                ) {
                    Surface(
                        color = colorScheme.surfaceVariant.copy(alpha = 0.56f),
                        contentColor = colorScheme.onSurface,
                        shape = NordicShapes.full,
                        modifier = Modifier
                            .height(34.dp)
                            .clickable { onRenamePlaylist() }
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = NordicSpacing.lg),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("重命名", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    Surface(
                        color = colorScheme.error.copy(alpha = 0.1f),
                        contentColor = colorScheme.error,
                        shape = NordicShapes.full,
                        modifier = Modifier
                            .height(34.dp)
                            .clickable { onDeletePlaylist() }
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = NordicSpacing.lg),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("删除歌单", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            if (playlistSongs.isEmpty()) {
                item {
                    MusicDetailEmptyState(
                        title = "暂无曲目",
                        subtitle = "这个歌单暂时没有可播放曲目。",
                    )
                }
            } else {
                itemsIndexed(
                    items = playlistSongs,
                    key = { index, song -> "playlist-song-${song.id}-$index" },
                    contentType = { _, _ -> "playlist-song-row" }
                ) { index, song ->
                    SongListRow(
                        song = song,
                        colorScheme = colorScheme,
                        onClick = {
                            onSongSelected(
                                playlistSongs,
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
