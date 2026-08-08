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
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
            color = colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            count.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurface.copy(alpha = NordicAlpha.subtle),
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
    Surface(
        color = colorScheme.surfaceVariant.copy(alpha = 0.42f),
        contentColor = colorScheme.onSurface,
        shape = NordicShapes.md,
        border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.045f)),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = NordicSpacing.md, vertical = NordicSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(NordicSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(NordicShapes.md)
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
                    AuthedAsyncImage(
                        url = playlist.coverArt,
                        contentDescription = playlist.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                } else {
                    Text("≡", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Normal, color = colorScheme.primary.copy(alpha = NordicAlpha.subtle))
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(NordicSpacing.xs)
            ) {
                Text(
                    playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    playlist.comment?.takeIf { it.isNotBlank() } ?: playlist.owner ?: "Navidrome 歌单",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Normal,
                    color = colorScheme.onSurface.copy(alpha = NordicAlpha.subtle),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${playlist.songCount} 首  •  ${formatDuration(playlist.duration)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Normal,
                    color = colorScheme.onSurface.copy(alpha = NordicAlpha.subtle)
                )
            }
        }
    }
}

@Composable
internal fun PlaylistDetailHeader(
    playlist: NavidromePlaylist,
    songCount: Int,
    colorScheme: ColorScheme,
    onPlayAll: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(NordicSpacing.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(NordicShapes.lg)
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
                AuthedAsyncImage(
                    url = playlist.coverArt,
                    contentDescription = playlist.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text("≡", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Normal, color = colorScheme.primary.copy(alpha = NordicAlpha.subtle))
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)
        ) {
            Text(
                playlist.name,
                style = MaterialTheme.typography.headlineMedium,
                color = colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            playlist.comment?.takeIf { it.isNotBlank() }?.let { comment ->
                Text(
                    comment,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurface.copy(alpha = NordicAlpha.medium),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(NordicSpacing.sm)) {
                MetaChip("${songCount} 首", colorScheme)
                MetaChip(formatDuration(playlist.duration), colorScheme)
            }
            Surface(
                color = colorScheme.primary,
                contentColor = colorScheme.onPrimary,
                shape = NordicShapes.full,
                modifier = Modifier
                    .height(36.dp)
                    .clickable(onClick = onPlayAll)
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
        Surface(
            color = colorScheme.surfaceVariant.copy(alpha = 0.56f),
            contentColor = colorScheme.onSurface,
            shape = NordicShapes.md,
            border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.06f)),
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(NordicSpacing.xs),
                horizontalArrangement = Arrangement.spacedBy(NordicSpacing.xs)
            ) {
                tabs.forEachIndexed { index, label ->
                    val selected = selectedTab == index
                    val tabColor by animateColorAsState(
                        targetValue = if (selected) colorScheme.surface.copy(alpha = 0.96f) else Color.Transparent,
                        animationSpec = tween(durationMillis = NordicMotion.durationMicro, easing = NordicMotion.easingStandard)
                    )
                    val textColor by animateColorAsState(
                        targetValue = if (selected) colorScheme.primary else colorScheme.onSurface.copy(alpha = NordicAlpha.subtle),
                        animationSpec = tween(durationMillis = NordicMotion.durationMicro, easing = NordicMotion.easingStandard)
                    )

                    Surface(
                        color = tabColor,
                        contentColor = textColor,
                        shape = NordicShapes.md,
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
                                style = MaterialTheme.typography.titleSmall,
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

@Composable
internal fun MusicSearchButton(
    colorScheme: ColorScheme,
    onClick: () -> Unit = {}
) {
    Surface(
        color = colorScheme.surfaceVariant.copy(alpha = 0.56f),
        contentColor = colorScheme.onSurface,
        shape = NordicShapes.md,
        border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.06f)),
        modifier = Modifier
            .height(48.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .height(48.dp)
                .padding(horizontal = NordicSpacing.lg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "⌕",
                style = MaterialTheme.typography.headlineMedium,
                color = colorScheme.onSurface.copy(alpha = NordicAlpha.medium),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
internal fun SongSortSegmentedControl(
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

    LazyRow(horizontalArrangement = Arrangement.spacedBy(NordicSpacing.sm)) {
        items(sorts, key = { it.name }) { sort ->
            val selected = selectedSort == sort
            val background by animateColorAsState(
                targetValue = if (selected) colorScheme.surface.copy(alpha = 0.96f) else colorScheme.surfaceVariant.copy(alpha = 0.5f),
                animationSpec = tween(durationMillis = NordicMotion.durationMicro, easing = NordicMotion.easingStandard)
            )
            val textColor by animateColorAsState(
                targetValue = if (selected) colorScheme.primary else colorScheme.onSurface.copy(alpha = NordicAlpha.medium),
                animationSpec = tween(durationMillis = NordicMotion.durationMicro, easing = NordicMotion.easingStandard)
            )

            Surface(
                color = background,
                contentColor = textColor,
                shape = NordicShapes.full,
                border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.06f)),
                tonalElevation = if (selected) 2.dp else 0.dp,
                modifier = Modifier
                    .height(38.dp)
                    .clickable { onSortSelected(sort) }
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = NordicSpacing.md),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        sort.displayLabel(),
                        color = textColor,
                        style = MaterialTheme.typography.labelLarge,
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
internal fun AlbumSortSegmentedControl(
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
        shape = NordicShapes.md,
        border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.06f)),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(NordicSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(NordicSpacing.xs)
        ) {
            sorts.forEach { sort ->
                val selected = selectedSort == sort
                val tabColor by animateColorAsState(
                    targetValue = if (selected) colorScheme.surface.copy(alpha = 0.96f) else Color.Transparent,
                    animationSpec = tween(durationMillis = NordicMotion.durationMicro, easing = NordicMotion.easingStandard)
                )
                val textColor by animateColorAsState(
                    targetValue = if (selected) colorScheme.primary else colorScheme.onSurface.copy(alpha = NordicAlpha.subtle),
                    animationSpec = tween(durationMillis = NordicMotion.durationMicro, easing = NordicMotion.easingStandard)
                )

                Surface(
                    color = tabColor,
                    contentColor = textColor,
                    shape = NordicShapes.md,
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
                            style = MaterialTheme.typography.titleSmall,
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
internal fun AlbumListRow(
    album: NavidromeAlbum,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Surface(
        color = colorScheme.surfaceVariant.copy(alpha = 0.42f),
        contentColor = colorScheme.onSurface,
        shape = NordicShapes.md,
        border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.045f)),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = NordicSpacing.md, vertical = NordicSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(NordicSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(NordicShapes.md)
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
                    AuthedAsyncImage(
                        url = album.coverArt,
                        contentDescription = album.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                } else {
                    Text("♪", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Normal, color = colorScheme.primary.copy(alpha = NordicAlpha.subtle))
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(NordicSpacing.xs)
            ) {
                Text(
                    album.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    album.artist ?: "Unknown artist",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Normal,
                    color = colorScheme.onSurface.copy(alpha = NordicAlpha.subtle),
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
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Normal,
                    color = colorScheme.onSurface.copy(alpha = NordicAlpha.subtle)
                )
            }
        }
    }
}

@Composable
internal fun AlbumDetailHeader(
    album: NavidromeAlbum,
    colorScheme: ColorScheme,
    onPlayAll: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(NordicSpacing.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(NordicShapes.lg)
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
                AuthedAsyncImage(
                    url = album.coverArt,
                    contentDescription = album.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text("♪", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Normal, color = colorScheme.primary.copy(alpha = NordicAlpha.subtle))
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)
        ) {
            Text(
                album.name,
                style = MaterialTheme.typography.headlineMedium,
                color = colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            album.artist?.let { artist ->
                Text(
                    artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurface.copy(alpha = NordicAlpha.medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(NordicSpacing.sm)) {
                MetaChip("${album.songCount} tracks", colorScheme)
                album.year?.let { MetaChip(it.toString(), colorScheme) }
            }
            Surface(
                color = colorScheme.primary,
                contentColor = colorScheme.onPrimary,
                shape = NordicShapes.full,
                modifier = Modifier
                    .height(36.dp)
                    .clickable(onClick = onPlayAll)
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
