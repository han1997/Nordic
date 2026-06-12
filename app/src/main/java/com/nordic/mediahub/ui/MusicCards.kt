package com.nordic.mediahub.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
fun AlbumCard(album: com.nordic.mediahub.api.NavidromeAlbum, colorScheme: ColorScheme) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)
    )

    Surface(
        color = colorScheme.surfaceVariant.copy(alpha = 0.72f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().scale(scale).clickable(interactionSource, null) {}
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(54.dp).clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(colorScheme.primary.copy(0.2f), colorScheme.secondary.copy(0.14f))))
            ) {
                if (album.coverArt != null) {
                    AsyncImage(
                        model = album.coverArt,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(album.name, fontSize = 15.sp, color = colorScheme.onSurface, fontWeight = FontWeight.Medium)
                Text(album.artist ?: "Unknown", fontSize = 13.sp, color = colorScheme.onSurface.copy(0.6f))
            }
            Text("${album.songCount}首", fontSize = 13.sp, color = colorScheme.onSurface.copy(0.6f))
        }
    }
}

@Composable
fun SongCard(song: com.nordic.mediahub.api.NavidromeSong, colorScheme: ColorScheme) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)
    )

    Surface(
        color = colorScheme.surfaceVariant.copy(alpha = 0.72f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().scale(scale).clickable(interactionSource, null) {}
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(54.dp).clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(colorScheme.primary.copy(0.2f), colorScheme.secondary.copy(0.14f))))
            ) {
                if (song.coverArt != null) {
                    AsyncImage(
                        model = song.coverArt,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, fontSize = 15.sp, color = colorScheme.onSurface, fontWeight = FontWeight.Medium)
                Text(song.artist ?: "Unknown", fontSize = 13.sp, color = colorScheme.onSurface.copy(0.6f))
            }
            Text("${song.duration / 60}:${(song.duration % 60).toString().padStart(2, '0')}", fontSize = 13.sp, color = colorScheme.onSurface.copy(0.6f))
        }
    }
}

@Composable
fun ArtistCard(artist: com.nordic.mediahub.api.NavidromeArtist, colorScheme: ColorScheme) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)
    )

    Surface(
        color = colorScheme.surfaceVariant.copy(alpha = 0.72f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().scale(scale).clickable(interactionSource, null) {}
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(54.dp).clip(RoundedCornerShape(27.dp))
                    .background(Brush.linearGradient(listOf(colorScheme.primary.copy(0.2f), colorScheme.secondary.copy(0.14f))))
            ) {
                if (artist.coverArt != null) {
                    AsyncImage(
                        model = artist.coverArt,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(artist.name, fontSize = 15.sp, color = colorScheme.onSurface, fontWeight = FontWeight.Medium)
                Text("${artist.albumCount}张专辑", fontSize = 13.sp, color = colorScheme.onSurface.copy(0.6f))
            }
        }
    }
}
