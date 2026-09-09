package com.nordic.mediahub.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nordic.mediahub.data.NavidromeAlbum
import com.nordic.mediahub.data.NavidromeArtist
import com.nordic.mediahub.data.NavidromeSong
import com.nordic.mediahub.ui.theme.NordicAlpha
import com.nordic.mediahub.ui.theme.NordicShapes
import com.nordic.mediahub.ui.theme.NordicSpacing

@Composable
fun MusicHeroBanner(
    album: NavidromeAlbum,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interactionSource, enabled = onClick != null)
    Surface(
        color = colorScheme.surfaceVariant.copy(alpha = 0.42f),
        shape = NordicShapes.xl,
        modifier = modifier.fillMaxWidth().scale(scale)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = "查看专辑",
                interactionSource = interactionSource, indication = null, onClick = onClick) else Modifier)
    ) {
        MusicCollectionHeader(
            itemId = album.id, title = album.name, subtitle = musicArtistLabel(album.artist),
            metadata = listOfNotNull(musicSongCountLabel(album.songCount), album.year?.takeIf { it > 0 }?.toString()),
            artworkUrl = album.coverArt, fallbackIcon = Icons.Filled.Album,
            colorScheme = colorScheme, onPlayAll = null,
            modifier = Modifier.padding(NordicSpacing.lg)
        )
    }
}

@Composable
fun MusicSectionHeader(
    title: String,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(NordicSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(NordicSpacing.xs)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                color = colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Normal,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (actionLabel != null && onAction != null) {
            Surface(
                color = colorScheme.surfaceVariant.copy(alpha = 0.5f),
                contentColor = colorScheme.onSurface,
                shape = NordicShapes.full,
                border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.06f)),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClick = onAction)
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = NordicSpacing.md, vertical = NordicSpacing.sm),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        actionLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
fun CompactAlbumShelfCard(
    album: NavidromeAlbum,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    CompactMusicShelfItem(
        title = album.name,
        subtitle = musicArtistLabel(album.artist),
        meta = buildString {
            append(musicSongCountLabel(album.songCount))
            album.year?.takeIf { it > 0 }?.let {
                append(" / ")
                append(it)
            }
        },
        artworkUrl = album.coverArt,
        contentDescription = album.name,
        colorScheme = colorScheme,
        modifier = modifier,
        onClick = onClick
    )
}

@Composable
fun SongShelfCard(
    song: NavidromeSong,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    CompactMusicShelfItem(
        title = song.title,
        subtitle = musicArtistLabel(song.artist),
        meta = musicTrackDurationLabel(song.duration),
        artworkUrl = song.coverArt,
        contentDescription = song.title,
        colorScheme = colorScheme,
        modifier = modifier,
        onClick = onClick
    )
}

@Composable
fun ArtistShelfCard(
    artist: NavidromeArtist,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    CompactMusicShelfItem(
        title = artist.name,
        subtitle = musicAlbumCountLabel(artist.albumCount),
        meta = "Artist",
        artworkUrl = null,
        contentDescription = artist.name,
        colorScheme = colorScheme,
        modifier = modifier,
        artworkShape = NordicShapes.full,
        initials = artist.initials,
        onClick = onClick
    )
}

@Composable
fun SongListRow(
    song: NavidromeSong,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    MusicLibraryRow(
        title = song.title, subtitle = musicArtistLabel(song.artist), metadata = song.album,
        trailingText = musicTrackDurationLabel(song.duration),
        colorScheme = colorScheme, modifier = modifier, clickLabel = "播放歌曲", onClick = onClick,
        artwork = { CoverArt(song.coverArt, song.title, colorScheme, fallbackIcon = Icons.Filled.MusicNote) }
    )
}

@Composable
fun ArtistListRow(
    artist: NavidromeArtist,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    MusicLibraryRow(
        title = artist.name, subtitle = musicAlbumCountLabel(artist.albumCount),
        colorScheme = colorScheme, modifier = modifier, clickLabel = "查看歌手", onClick = onClick,
        artwork = { CoverArt(null, artist.name, colorScheme, shape = NordicShapes.full,
            initials = artist.initials, fallbackIcon = Icons.Filled.Person) }
    )
}

@Composable
private fun CompactMusicShelfItem(
    title: String,
    subtitle: String,
    meta: String,
    artworkUrl: String?,
    contentDescription: String,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier,
    artworkShape: Shape = NordicShapes.lg,
    initials: String? = null,
    onClick: () -> Unit = {}
) {
    val artworkSize = musicShelfArtworkSize(LocalDensity.current.fontScale)
    Column(
        modifier = modifier
            .width(artworkSize)
            .clickable(role = Role.Button, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)
    ) {
        CoverArt(
            imageUrl = artworkUrl,
            contentDescription = contentDescription,
            colorScheme = colorScheme,
            size = artworkSize,
            shape = artworkShape,
            initials = initials
        )

        Column(verticalArrangement = Arrangement.spacedBy(NordicSpacing.xs)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = colorScheme.onSurface,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Normal,
                color = colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                meta,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Normal,
                color = colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

