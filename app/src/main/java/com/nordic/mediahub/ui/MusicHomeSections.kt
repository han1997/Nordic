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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onToggleStar: (() -> Unit)? = null
) {
    Surface(
        color = colorScheme.surfaceVariant.copy(alpha = 0.62f),
        shape = RoundedCornerShape(24.dp),
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
                .padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "刚刚同步到你的曲库",
                        fontSize = 12.sp,
                        color = colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (onToggleStar != null) {
                        StarToggleButton(
                            isStarred = album.starred != null,
                            colorScheme = colorScheme,
                            onClick = onToggleStar
                        )
                    }
                }
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
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                title,
                fontSize = 20.sp,
                color = colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                fontSize = 13.sp,
                color = colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (actionLabel != null && onAction != null) {
            Surface(
                color = colorScheme.surfaceVariant.copy(alpha = 0.5f),
                contentColor = colorScheme.onSurface,
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.06f)),
                modifier = Modifier
                    .height(34.dp)
                    .clickable(onClick = onAction)
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        actionLabel,
                        fontSize = 13.sp,
                        color = colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun AlbumShelfCard(
    album: NavidromeAlbum,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = rememberPressScale(
        interactionSource = interactionSource,
        pressedScale = 0.98f,
        durationMillis = 180
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
    val scale = rememberPressScale(
        interactionSource = interactionSource,
        pressedScale = 0.98f,
        durationMillis = 180
    )

    Column(
        modifier = modifier
            .width(88.dp)
            .scale(scale)
            .clickable(interactionSource = interactionSource, indication = null) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MusicArtwork(
            imageUrl = null,
            contentDescription = artist.name,
            colorScheme = colorScheme,
            size = 78.dp,
            shape = CircleShape,
            initials = artist.initials
        )

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
    onClick: () -> Unit = {},
    onToggleStar: (() -> Unit)? = null
) {
    Box {
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
        if (onToggleStar != null) {
            StarToggleButton(
                isStarred = album.starred != null,
                colorScheme = colorScheme,
                onClick = onToggleStar,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
            )
        }
    }
}

@Composable
fun SongShelfCard(
    song: NavidromeSong,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onToggleStar: (() -> Unit)? = null
) {
    Box {
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
        if (onToggleStar != null) {
            StarToggleButton(
                isStarred = song.starred != null,
                colorScheme = colorScheme,
                onClick = onToggleStar,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
            )
        }
    }
}

@Composable
fun ArtistShelfCard(
    artist: NavidromeArtist,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onToggleStar: (() -> Unit)? = null
) {
    Box {
        CompactMusicShelfItem(
            title = artist.name,
            subtitle = "${artist.albumCount} albums",
            meta = "Artist",
            artworkUrl = null,
            contentDescription = artist.name,
            colorScheme = colorScheme,
            modifier = modifier,
            artworkShape = CircleShape,
            initials = artist.initials,
            onClick = onClick
        )
        if (onToggleStar != null) {
            StarToggleButton(
                isStarred = artist.starred != null,
                colorScheme = colorScheme,
                onClick = onToggleStar,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
            )
        }
    }
}

@Composable
fun SongListRow(
    song: NavidromeSong,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onToggleStar: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = rememberPressScale(
        interactionSource = interactionSource,
        pressedScale = 0.992f
    )

    Surface(
        color = colorScheme.surfaceVariant.copy(alpha = 0.42f),
        contentColor = colorScheme.onSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.045f)),
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MusicArtwork(
                imageUrl = song.coverArt,
                contentDescription = song.title,
                colorScheme = colorScheme
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    song.title,
                    fontSize = 15.sp,
                    color = colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    song.artist ?: "Unknown artist",
                    fontSize = 13.sp,
                    color = colorScheme.onSurface.copy(alpha = 0.64f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                song.album?.takeIf { it.isNotBlank() }?.let { album ->
                    Text(
                        album,
                        fontSize = 12.sp,
                        color = colorScheme.onSurface.copy(alpha = 0.46f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onToggleStar != null) {
                    StarToggleButton(
                        isStarred = song.starred != null,
                        colorScheme = colorScheme,
                        onClick = onToggleStar
                    )
                }
                if (onAddToPlaylist != null) {
                    Surface(
                        color = colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        contentColor = colorScheme.onSurface,
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier
                            .size(28.dp)
                            .clickable(onClick = onAddToPlaylist)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "+",
                                fontSize = 16.sp,
                                color = colorScheme.primary.copy(alpha = 0.72f),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Text(
                    formatDuration(song.duration),
                    fontSize = 12.sp,
                    color = colorScheme.onSurface.copy(alpha = 0.48f),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun ArtistListRow(
    artist: NavidromeArtist,
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
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MusicArtwork(
                imageUrl = null,
                contentDescription = artist.name,
                colorScheme = colorScheme,
                shape = CircleShape,
                initials = artist.initials
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    artist.name,
                    fontSize = 15.sp,
                    color = colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${artist.albumCount} albums",
                    fontSize = 13.sp,
                    color = colorScheme.onSurface.copy(alpha = 0.62f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (onToggleStar != null) {
                StarToggleButton(
                    isStarred = artist.starred != null,
                    colorScheme = colorScheme,
                    onClick = onToggleStar
                )
            }

            Text(
                "歌手",
                fontSize = 12.sp,
                color = colorScheme.onSurface.copy(alpha = 0.44f),
                fontWeight = FontWeight.Medium
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
    artworkShape: Shape = RoundedCornerShape(18.dp),
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
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MusicArtwork(
            imageUrl = artworkUrl,
            contentDescription = contentDescription,
            colorScheme = colorScheme,
            size = 124.dp,
            shape = artworkShape,
            initials = initials
        )

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

@Composable
private fun MusicArtwork(
    imageUrl: String?,
    contentDescription: String,
    colorScheme: ColorScheme,
    size: Dp = 52.dp,
    shape: Shape = RoundedCornerShape(12.dp),
    initials: String? = null
) {
    var imageFailed by remember(imageUrl) { mutableStateOf(false) }
    val fallbackAccent = remember(contentDescription) {
        Math.floorMod(contentDescription.hashCode(), 3)
    }
    val accentColor = when (fallbackAccent) {
        0 -> colorScheme.primary
        1 -> colorScheme.secondary
        else -> colorScheme.tertiary
    }

    val showImage = !imageUrl.isNullOrBlank() && !imageFailed
    val showInitials = !showImage && !initials.isNullOrBlank()

    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        accentColor.copy(alpha = 0.2f),
                        colorScheme.surfaceVariant.copy(alpha = 0.82f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (showImage) {
            AsyncImage(
                model = imageUrl!!,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
                onError = { imageFailed = true }
            )
        } else if (showInitials) {
            Text(
                initials!!,
                fontSize = (size.value * 0.38f).sp,
                color = accentColor.copy(alpha = 0.68f),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun StarToggleButton(
    isStarred: Boolean,
    colorScheme: ColorScheme,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = if (isStarred) "★" else "☆"
    val color = if (isStarred) colorScheme.primary else colorScheme.onSurface.copy(alpha = 0.44f)
    Surface(
        color = if (isStarred) colorScheme.primary.copy(alpha = 0.14f) else colorScheme.surface.copy(alpha = 0.5f),
        contentColor = color,
        shape = RoundedCornerShape(999.dp),
        modifier = modifier
            .size(28.dp)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                fontSize = 15.sp,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
internal fun MusicMetaChip(
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
