package com.nordic.mediahub.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nordic.mediahub.api.NavidromeAlbum
import com.nordic.mediahub.api.NavidromeArtist
import com.nordic.mediahub.api.NavidromeSong

@Composable
fun MusicHeroBanner(
    album: NavidromeAlbum,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier
) {
    Surface(
        color = colorScheme.surfaceVariant.copy(alpha = 0.62f),
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.fillMaxWidth()
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
                .padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "刚刚同步到你的曲库",
                    fontSize = 12.sp,
                    color = colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    album.name,
                    fontSize = 24.sp,
                    lineHeight = 28.sp,
                    color = colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    album.artist ?: "Unknown artist",
                    fontSize = 14.sp,
                    color = colorScheme.onSurface.copy(alpha = 0.68f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MusicMetaChip("${album.songCount} tracks", colorScheme)
                    album.year?.let { MusicMetaChip(it.toString(), colorScheme) }
                }
            }

            Box(
                modifier = Modifier
                    .size(124.dp)
                    .clip(RoundedCornerShape(20.dp))
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
                    AsyncImage(
                        model = album.coverArt,
                        contentDescription = album.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(124.dp)
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
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            title,
            fontSize = 20.sp,
            color = colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            subtitle,
            fontSize = 13.sp,
            color = colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun AlbumShelfCard(
    album: NavidromeAlbum,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
    )

    Column(
        modifier = modifier
            .width(156.dp)
            .scale(scale)
            .clickable(interactionSource = interactionSource, indication = null) {},
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Box(
            modifier = Modifier
                .size(156.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            colorScheme.primary.copy(alpha = 0.18f),
                            colorScheme.secondary.copy(alpha = 0.12f)
                        )
                    )
                )
        ) {
            if (album.coverArt != null) {
                AsyncImage(
                    model = album.coverArt,
                    contentDescription = album.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                color = colorScheme.onSurface.copy(alpha = 0.64f),
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
    }
}

@Composable
fun ArtistRoundCard(
    artist: NavidromeArtist,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
    )

    Column(
        modifier = modifier
            .width(88.dp)
            .scale(scale)
            .clickable(interactionSource = interactionSource, indication = null) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(78.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            colorScheme.primary.copy(alpha = 0.18f),
                            colorScheme.secondary.copy(alpha = 0.12f)
                        )
                    )
                )
        ) {
            if (artist.coverArt != null) {
                AsyncImage(
                    model = artist.coverArt,
                    contentDescription = artist.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                artist.name,
                fontSize = 13.sp,
                color = colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${artist.albumCount} albums",
                fontSize = 11.sp,
                color = colorScheme.onSurface.copy(alpha = 0.5f)
            )
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
        artworkUrl = artist.coverArt,
        contentDescription = artist.name,
        colorScheme = colorScheme,
        modifier = modifier,
        onClick = onClick
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
    onClick: () -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)
    )

    Column(
        modifier = modifier
            .width(124.dp)
            .scale(scale)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(124.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            colorScheme.primary.copy(alpha = 0.16f),
                            colorScheme.secondary.copy(alpha = 0.1f)
                        )
                    )
                )
        ) {
            if (artworkUrl != null) {
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                title,
                fontSize = 13.sp,
                color = colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                fontSize = 12.sp,
                color = colorScheme.onSurface.copy(alpha = 0.64f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                meta,
                fontSize = 11.sp,
                color = colorScheme.onSurface.copy(alpha = 0.46f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatDuration(duration: Int): String {
    val minutes = duration / 60
    val seconds = (duration % 60).toString().padStart(2, '0')
    return "$minutes:$seconds"
}

@Composable
private fun MusicMetaChip(
    text: String,
    colorScheme: ColorScheme
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(colorScheme.surface.copy(alpha = 0.64f))
            .padding(horizontal = 9.dp, vertical = 5.dp)
    ) {
        Text(
            text,
            fontSize = 12.sp,
            color = colorScheme.onSurface.copy(alpha = 0.72f),
            fontWeight = FontWeight.Medium
        )
    }
}
