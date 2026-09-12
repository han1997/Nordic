package com.nordic.mediahub.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nordic.mediahub.ui.theme.NordicControlSizes
import com.nordic.mediahub.ui.theme.NordicAlpha
import com.nordic.mediahub.ui.theme.NordicMotion
import com.nordic.mediahub.ui.theme.NordicShapes
import com.nordic.mediahub.ui.theme.NordicSpacing

@Composable
internal fun MediaPageHeader(
    title: String,
    subtitle: String,
    actions: List<HeaderAction>,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier,
    showBack: Boolean = false,
    onBack: () -> Unit = {}
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val layout = resolveMediaHeaderActionLayout(
            maxWidth, showBack, actions.size, LocalDensity.current.fontScale,
            fixedActionCount = actions.count { it.fixed }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(NordicSpacing.md),
            verticalAlignment = if (showBack) Alignment.Top else Alignment.CenterVertically
        ) {
            if (showBack) {
                ScreenBackButton(colorScheme, Modifier.padding(top = NordicSpacing.xs), onBack)
            }
            Column(
                modifier = Modifier.weight(1f).padding(top = if (showBack) NordicSpacing.md else 0.dp),
                verticalArrangement = Arrangement.spacedBy(NordicSpacing.xs)
            ) {
                if (!showBack && LocalSourceDomain.current != null) {
                    MediaSourceTitle(title)
                } else {
                    Text(title, style = if (showBack) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                        color = colorScheme.onBackground, maxLines = if (showBack) 2 else 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
                }
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = if (showBack) 2 else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            HeaderActionGroup(actions, maxInlineActions = layout.inlineActionCount)
        }
    }
}

@Composable
internal fun MediaConfigPanel(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(NordicMotion.durationMedium, easing = NordicMotion.easingStandard)) + expandVertically(),
        exit = fadeOut(tween(NordicMotion.durationShort)) + shrinkVertically(),
        modifier = modifier
    ) {
        content()
    }
}

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
        shape = NordicShapes.full,
        modifier = chipModifier
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = NordicSpacing.md, vertical = NordicSpacing.xs),
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurface.copy(alpha = NordicAlpha.medium),
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
        shape = NordicShapes.full,
        modifier = modifier
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = NordicSpacing.sm, vertical = NordicSpacing.xs),
            style = MaterialTheme.typography.bodySmall,
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
    AnimatedIconButton(
        icon = Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = "返回",
        onClick = onClick,
        modifier = modifier,
        colorScheme = colorScheme
    )
}

@Composable
internal fun CoverArt(
    imageUrl: String?,
    contentDescription: String,
    colorScheme: ColorScheme,
    size: Dp = 52.dp,
    modifier: Modifier = Modifier.size(size),
    shape: Shape = NordicShapes.sm,
    fallbackText: String? = null,
    initials: String? = null,
    fallbackIcon: ImageVector? = null,
    crossfadeEnabled: Boolean = false
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
    val showIcon = !showImage && !showInitials && fallbackIcon != null
    val showText = !showImage && !showInitials && !showIcon && !fallbackText.isNullOrBlank()

    Box(
        modifier = modifier
            .clip(shape)
            .then(
                // Skip the gradient backdrop once a real image covers the box:
                // grid pages stack dozens of these cards and the hidden brush
                // would still cost a full-surface draw every frame.
                if (showImage) Modifier else Modifier.background(
                    Brush.linearGradient(
                        listOf(
                            colorScheme.primary.copy(alpha = 0.20f),
                            colorScheme.surfaceVariant.copy(alpha = 0.82f)
                        )
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
                    crossfadeEnabled = crossfadeEnabled,
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
            showIcon -> {
                Icon(
                    imageVector = fallbackIcon!!,
                    contentDescription = null,
                    tint = colorScheme.primary.copy(alpha = 0.72f),
                    modifier = Modifier.size((size.value * 0.42f).dp)
                )
            }
            showText -> {
                Text(
                    fallbackText!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurface.copy(alpha = NordicAlpha.subtle),
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
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        color = if (enabled) colorScheme.primary else colorScheme.primary.copy(alpha = 0.32f),
        contentColor = colorScheme.onPrimary,
        shape = NordicShapes.full,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .pressScale(
                interactionSource,
                pressedScale = 0.985f,
                enabled = enabled
            )
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = NordicSpacing.lg, vertical = NordicSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(NordicSpacing.sm, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text,
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f, fill = false),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Shared secondary (outlined, primary-tinted) action button. Use for
 * non-primary detail actions such as "从头播放" alongside [PrimaryActionButton].
 */
@Composable
internal fun SecondaryActionButton(
    text: String,
    colorScheme: ColorScheme,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        color = if (enabled) colorScheme.primaryContainer else colorScheme.surfaceVariant.copy(alpha = 0.4f),
        contentColor = colorScheme.onPrimaryContainer,
        shape = NordicShapes.full,
        border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.22f)),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = NordicControlSizes.touchTarget)
            .pressScale(
                interactionSource,
                pressedScale = 0.985f,
                enabled = enabled
            )
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = NordicSpacing.lg, vertical = NordicSpacing.sm),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) colorScheme.onPrimaryContainer else colorScheme.onSurface.copy(alpha = NordicAlpha.faint),
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
