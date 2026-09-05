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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
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
    Surface(
        color = colorScheme.surfaceVariant.copy(alpha = 0.62f),
        shape = NordicShapes.xl,
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(
                            colorScheme.primary.copy(alpha = 0.18f),
                            colorScheme.secondary.copy(alpha = 0.1f),
                            colorScheme.surface.copy(alpha = 0.92f)
                        )
                    )
                )
                .padding(NordicSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(NordicSpacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(NordicSpacing.md)
            ) {
                Text(
                    "刚刚同步到你的曲库",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    album.name,
                    style = MaterialTheme.typography.headlineMedium,
                    lineHeight = 28.sp,
                    color = colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    album.artist ?: "Unknown artist",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurface.copy(alpha = NordicAlpha.medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(NordicSpacing.sm)) {
                    MetaChip("${album.songCount} tracks", colorScheme)
                    album.year?.let { MetaChip(it.toString(), colorScheme) }
                }
            }

            Box(
                modifier = Modifier
                    .size(124.dp)
                    .clip(NordicShapes.lg)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                colorScheme.primary.copy(alpha = 0.22f),
                                colorScheme.secondary.copy(alpha = 0.16f)
                            )
                        )
                    )
            ) {
                if (album.coverArt != null) {
                    AuthedAsyncImage(
                        url = album.coverArt,
                        contentDescription = album.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                }
            }
        }
    }
}

@Composable
fun MusicSectionHeader(
    title: String,
    subtitle: String,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier,
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
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Normal,
                color = colorScheme.onSurface.copy(alpha = NordicAlpha.subtle),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (actionLabel != null && onAction != null) {
            Surface(
                color = colorScheme.surfaceVariant.copy(alpha = 0.5f),
                contentColor = colorScheme.onSurface,
                shape = NordicShapes.full,
                border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.06f)),
                modifier = Modifier
                    .height(34.dp)
                    .clickable(onClick = onAction)
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = NordicSpacing.md),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        actionLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = colorScheme.primary
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
        subtitle = album.artist ?: "Unknown artist",
        meta = buildString {
            append("${album.songCount} tracks")
            album.year?.let {
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
        subtitle = song.artist ?: "Unknown artist",
        meta = formatDuration(song.duration),
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
        subtitle = "${artist.albumCount} albums",
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
            CoverArt(
                imageUrl = song.coverArt,
                contentDescription = song.title,
                colorScheme = colorScheme
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(NordicSpacing.xs)
            ) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    song.artist ?: "Unknown artist",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Normal,
                    color = colorScheme.onSurface.copy(alpha = NordicAlpha.medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                song.album?.takeIf { it.isNotBlank() }?.let { album ->
                    Text(
                        album,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Normal,
                        color = colorScheme.onSurface.copy(alpha = NordicAlpha.subtle),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Text(
                formatDuration(song.duration),
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurface.copy(alpha = NordicAlpha.subtle),
                maxLines = 1
            )
        }
    }
}

@Composable
fun ArtistListRow(
    artist: NavidromeArtist,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Surface(
        color = colorScheme.surfaceVariant.copy(alpha = 0.42f),
        contentColor = colorScheme.onSurface,
        shape = NordicShapes.md,
        border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.045f)),
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = NordicSpacing.md, vertical = NordicSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(NordicSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CoverArt(
                imageUrl = null,
                contentDescription = artist.name,
                colorScheme = colorScheme,
                shape = NordicShapes.full,
                initials = artist.initials
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(NordicSpacing.xs)
            ) {
                Text(
                    artist.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${artist.albumCount} albums",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Normal,
                    color = colorScheme.onSurface.copy(alpha = NordicAlpha.medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                "歌手",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurface.copy(alpha = NordicAlpha.subtle)
            )
        }
    }
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
    val interactionSource = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interactionSource)

    Column(
        modifier = modifier
            .width(124.dp)
            .scale(scale)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)
    ) {
        CoverArt(
            imageUrl = artworkUrl,
            contentDescription = contentDescription,
            colorScheme = colorScheme,
            size = 124.dp,
            shape = artworkShape,
            initials = initials
        )

        Column(verticalArrangement = Arrangement.spacedBy(NordicSpacing.xs)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Normal,
                color = colorScheme.onSurface.copy(alpha = NordicAlpha.medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                meta,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Normal,
                color = colorScheme.onSurface.copy(alpha = NordicAlpha.subtle),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

