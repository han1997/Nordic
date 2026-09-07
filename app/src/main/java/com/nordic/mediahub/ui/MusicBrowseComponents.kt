package com.nordic.mediahub.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nordic.mediahub.data.NavidromeAlbum
import com.nordic.mediahub.data.NavidromeAlbumSort
import com.nordic.mediahub.data.NavidromeArtist
import com.nordic.mediahub.data.NavidromePlaylist
import com.nordic.mediahub.data.NavidromeSong
import com.nordic.mediahub.ui.theme.NordicAlpha
import com.nordic.mediahub.ui.theme.NordicMotion
import com.nordic.mediahub.ui.theme.NordicShapes
import com.nordic.mediahub.ui.theme.NordicSpacing

@Composable
internal fun MusicSearchLanding(
    albums: List<NavidromeAlbum>,
    songs: List<NavidromeSong>,
    artists: List<NavidromeArtist>,
    colorScheme: ColorScheme,
    onAlbumClick: (NavidromeAlbum) -> Unit,
    onSongClick: (Int) -> Unit,
    onArtistClick: (NavidromeArtist) -> Unit
) {
    val hasSuggestions = albums.isNotEmpty() || songs.isNotEmpty() || artists.isNotEmpty()
    val suggestedAlbums = remember(albums) { albums.take(8) }
    val suggestedSongs = remember(songs) { songs.take(8) }
    val suggestedArtists = remember(artists) { artists.take(8) }

    Column(verticalArrangement = Arrangement.spacedBy(NordicSpacing.lg)) {
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
            LazyRow(horizontalArrangement = Arrangement.spacedBy(NordicSpacing.md)) {
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
            LazyRow(horizontalArrangement = Arrangement.spacedBy(NordicSpacing.md)) {
                itemsIndexed(
                    items = suggestedSongs,
                    key = { index, song -> "search-home-song-${song.id}-$index" },
                    contentType = { _, _ -> "search-home-song-card" }
                ) { index, song ->
                    SongShelfCard(
                        song = song,
                        colorScheme = colorScheme,
                        onClick = { onSongClick(index) }
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
            LazyRow(horizontalArrangement = Arrangement.spacedBy(NordicSpacing.md)) {
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
internal fun SearchResultSectionHeader(
    title: String,
    count: Int,
    colorScheme: ColorScheme
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(NordicSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f, fill = false).semantics { heading() },
            color = colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            count.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
internal fun PlaylistListRow(
    playlist: NavidromePlaylist,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    MusicLibraryRow(
        title = playlist.name,
        subtitle = playlist.comment?.trim()?.takeIf { it.isNotEmpty() }
            ?: playlist.owner?.trim()?.takeIf { it.isNotEmpty() } ?: "Navidrome 歌单",
        metadata = listOfNotNull(musicSongCountLabel(playlist.songCount),
            playlist.duration.takeIf { it > 0 }?.let(::formatDuration)).joinToString(" · "),
        colorScheme = colorScheme, modifier = modifier,
        clickLabel = "打开歌单", onClick = onClick,
        artwork = { CoverArt(playlist.coverArt, playlist.name, colorScheme,
            fallbackIcon = Icons.AutoMirrored.Filled.QueueMusic) }
    )
}

@Composable
internal fun PlaylistDetailHeader(
    playlist: NavidromePlaylist,
    songCount: Int,
    colorScheme: ColorScheme,
    onPlayAll: () -> Unit,
    playEnabled: Boolean = true
) {
    MusicCollectionHeader(
        itemId = playlist.id, title = playlist.name,
        subtitle = playlist.owner?.trim()?.takeIf { it.isNotEmpty() }?.let { "创建者：$it" } ?: "Navidrome 歌单",
        metadata = listOfNotNull(musicSongCountLabel(songCount), playlist.duration.takeIf { it > 0 }?.let(::formatDuration)),
        artworkUrl = playlist.coverArt, fallbackIcon = Icons.AutoMirrored.Filled.QueueMusic,
        colorScheme = colorScheme, onPlayAll = onPlayAll, playEnabled = playEnabled,
        description = playlist.comment
    )
}

@Composable
internal fun MusicDetailEmptyState(
    title: String,
    subtitle: String
) {
    MediaStateCard(
        title = title,
        subtitle = subtitle,
        density = MediaStateDensity.Compact
    )
}

@Composable
internal fun MusicSegmentedTabs(
    selectedTab: Int,
    colorScheme: ColorScheme,
    onTabSelected: (Int) -> Unit,
    onSearchClick: () -> Unit = {}
) {
    val tabs = listOf("发现", "歌曲", "歌单")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(NordicSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MediaSegmentedControl(
            options = tabs.indices.toList(), selectedOption = selectedTab,
            label = { tabs[it] }, optionKey = { "music-tab-$it" }, colorScheme = colorScheme,
            onOptionSelected = onTabSelected, modifier = Modifier.weight(1f)
        )
        MusicSearchButton(colorScheme, onSearchClick)
    }
}

@Composable
internal fun MusicSearchButton(colorScheme: ColorScheme, onClick: () -> Unit = {}) {
    AnimatedIconButton(Icons.Filled.Search, "搜索音乐", onClick, colorScheme = colorScheme)
}

@Composable
internal fun SongSortSegmentedControl(
    selectedSort: MusicSongSort,
    colorScheme: ColorScheme,
    onSortSelected: (MusicSongSort) -> Unit
) {
    MediaSegmentedControl(
        options = MusicSongSort.values().toList(), selectedOption = selectedSort,
        label = { it.displayLabel() }, optionKey = { it.name }, colorScheme = colorScheme,
        onOptionSelected = onSortSelected
    )
}

@Composable
internal fun AlbumSortSegmentedControl(
    selectedSort: NavidromeAlbumSort,
    colorScheme: ColorScheme,
    onSortSelected: (NavidromeAlbumSort) -> Unit
) {
    MediaSegmentedControl(
        options = listOf(NavidromeAlbumSort.RecentlyAdded, NavidromeAlbumSort.ReleaseYear, NavidromeAlbumSort.Name),
        selectedOption = selectedSort,
        label = { it.displayLabel() }, optionKey = { it.name }, colorScheme = colorScheme,
        onOptionSelected = onSortSelected
    )
}

@Composable
internal fun AlbumListRow(
    album: NavidromeAlbum,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    MusicLibraryRow(
        title = album.name, subtitle = musicArtistLabel(album.artist),
        metadata = listOfNotNull(musicSongCountLabel(album.songCount), album.year?.takeIf { it > 0 }?.toString()).joinToString(" · "),
        colorScheme = colorScheme, modifier = modifier,
        clickLabel = "打开专辑", onClick = onClick,
        artwork = { CoverArt(album.coverArt, album.name, colorScheme, fallbackIcon = Icons.Filled.Album) }
    )
}

@Composable
internal fun AlbumDetailHeader(
    album: NavidromeAlbum,
    colorScheme: ColorScheme,
    onPlayAll: () -> Unit,
    songCount: Int = album.songCount,
    playEnabled: Boolean = true
) {
    MusicCollectionHeader(
        itemId = album.id, title = album.name, subtitle = musicArtistLabel(album.artist),
        metadata = listOfNotNull(musicSongCountLabel(songCount), album.year?.takeIf { it > 0 }?.toString()),
        artworkUrl = album.coverArt, fallbackIcon = Icons.Filled.Album,
        colorScheme = colorScheme, onPlayAll = onPlayAll, playEnabled = playEnabled
    )
}
