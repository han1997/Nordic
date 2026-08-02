package com.nordic.mediahub.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun MetaChip(
    text: String,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val chipModifier = if (onClick != null) {
        modifier.clickable(enabled = enabled, onClick = onClick)
    } else {
        modifier
    }
    Surface(
        color = if (enabled) colorScheme.surfaceVariant.copy(alpha = 0.62f) else colorScheme.surface.copy(alpha = 0.30f),
        contentColor = colorScheme.onSurface,
        shape = RoundedCornerShape(999.dp),
        modifier = chipModifier
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            fontSize = 11.sp,
            color = colorScheme.onSurface.copy(alpha = 0.72f),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun ToneMetaChip(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = color.copy(alpha = 0.13f),
        contentColor = color,
        shape = RoundedCornerShape(999.dp),
        modifier = modifier
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            fontSize = 11.sp,
            color = color,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun ScreenBackButton(
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        color = colorScheme.surfaceVariant.copy(alpha = 0.56f),
        contentColor = colorScheme.onSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.06f)),
        modifier = modifier
            .height(42.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 13.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "‹",
                fontSize = 26.sp,
                color = colorScheme.onSurface.copy(alpha = 0.74f),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
internal fun CoverArt(
    imageUrl: String?,
    contentDescription: String,
    colorScheme: ColorScheme,
    size: Dp = 52.dp,
    modifier: Modifier = Modifier.size(size),
    shape: Shape = RoundedCornerShape(12.dp),
    fallbackText: String? = null,
    initials: String? = null,
    fallbackGlyph: String? = null
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
    val showGlyph = !showImage && !showInitials && fallbackGlyph != null
    val showText = !showImage && !showInitials && !showGlyph && !fallbackText.isNullOrBlank()

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        colorScheme.primary.copy(alpha = 0.20f),
                        colorScheme.surfaceVariant.copy(alpha = 0.82f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        when {
            showImage -> {
                AuthedAsyncImage(
                    url = imageUrl,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                    onError = { imageFailed = true }
                )
            }
            showInitials -> {
                Text(
                    initials!!,
                    fontSize = (size.value * 0.38f).sp,
                    color = accentColor.copy(alpha = 0.68f),
                    fontWeight = FontWeight.Bold
                )
            }
            showGlyph -> {
                Text(
                    fallbackGlyph!!,
                    fontSize = 24.sp,
                    color = colorScheme.primary.copy(alpha = 0.72f)
                )
            }
            showText -> {
                Text(
                    fallbackText!!,
                    fontSize = 12.sp,
                    color = colorScheme.onSurface.copy(alpha = 0.48f),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
internal fun PrimaryActionButton(
    text: String,
    colorScheme: ColorScheme,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = rememberPressScale(
        interactionSource = interactionSource,
        pressedScale = 0.985f,
        enabled = enabled
    )

    Surface(
        color = if (enabled) colorScheme.primary else colorScheme.primary.copy(alpha = 0.32f),
        contentColor = colorScheme.onPrimary,
        shape = RoundedCornerShape(999.dp),
        shadowElevation = if (enabled) 4.dp else 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .scale(scale)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text,
                fontSize = 16.sp,
                color = colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
