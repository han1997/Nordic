package com.nordic.mediahub.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nordic.mediahub.ui.theme.NordicAlpha
import com.nordic.mediahub.ui.theme.NordicControlSizes
import com.nordic.mediahub.ui.theme.NordicShapes
import com.nordic.mediahub.ui.theme.NordicSpacing

internal data class MediaPlayerAction(
    val icon: ImageVector,
    val description: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val active: Boolean? = null
)

@Composable
internal fun MediaPlayerIconAction(
    action: MediaPlayerAction,
    colors: ColorScheme,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    destructive: Boolean = false
) {
    val source = remember { MutableInteractionSource() }
    val foreground = when {
        !action.enabled -> colors.onSurface.copy(alpha = NordicAlpha.faint)
        primary -> colors.onPrimary
        destructive -> colors.onErrorContainer
        action.active == true -> colors.onPrimaryContainer
        else -> colors.onSurfaceVariant
    }
    Surface(
        color = when {
            primary -> colors.primary.copy(alpha = if (action.enabled) 1f else 0.32f)
            destructive -> colors.errorContainer
            action.active == true -> colors.primaryContainer
            else -> colors.surfaceVariant.copy(alpha = 0.5f)
        },
        contentColor = foreground, shape = NordicShapes.full,
        modifier = modifier.size(if (primary) 72.dp else NordicControlSizes.touchTarget)
            .pressScale(source, pressedScale = 0.94f, enabled = action.enabled)
            .semantics { if (action.active != null) selected = action.active }
            .clickable(enabled = action.enabled, role = Role.Button, interactionSource = source,
                indication = null, onClick = action.onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(action.icon, action.description, tint = foreground,
                modifier = Modifier.size(if (primary) 36.dp else NordicControlSizes.icon))
        }
    }
}

@Composable
internal fun MediaPlayerTool(
    text: String,
    description: String,
    colors: ColorScheme,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    active: Boolean? = null,
    destructive: Boolean = false
) {
    val source = remember { MutableInteractionSource() }
    Surface(
        color = if (destructive) colors.errorContainer else if (active == true) colors.primaryContainer else colors.surfaceVariant.copy(alpha = 0.5f),
        contentColor = when {
            !enabled -> colors.onSurface.copy(alpha = NordicAlpha.faint)
            destructive -> colors.onErrorContainer
            active == true -> colors.onPrimaryContainer
            else -> colors.onSurfaceVariant
        },
        shape = NordicShapes.md,
        modifier = modifier.heightIn(min = NordicControlSizes.touchTarget).pressScale(source, enabled = enabled)
            .semantics { contentDescription = description; if (active != null) selected = active }
            .clickable(enabled = enabled, role = Role.Button, interactionSource = source, indication = null, onClick = onClick)
    ) {
        Row(Modifier.padding(horizontal = NordicSpacing.sm, vertical = NordicSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(NordicSpacing.sm),
            verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) Icon(icon, null, modifier = Modifier.size(NordicControlSizes.compactIcon))
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun MediaPlayerTopBar(
    title: String,
    colors: ColorScheme,
    onClose: () -> Unit,
    speedLabel: String,
    onSpeed: () -> Unit,
    speedEnabled: Boolean,
    extraAction: MediaPlayerAction? = null
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NordicSpacing.sm)) {
        MediaPlayerIconAction(MediaPlayerAction(Icons.Filled.KeyboardArrowDown, "收起播放器", onClose), colors)
        Text(title, style = MaterialTheme.typography.titleSmall, color = colors.onSurfaceVariant,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        extraAction?.let { MediaPlayerIconAction(it, colors) }
        MediaPlayerTool(speedLabel, "播放速度 $speedLabel", colors, onSpeed, enabled = speedEnabled)
    }
}

@Composable
internal fun MediaTransportRow(
    leading: MediaPlayerAction,
    previous: MediaPlayerAction,
    play: MediaPlayerAction,
    next: MediaPlayerAction,
    trailing: MediaPlayerAction,
    colors: ColorScheme
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val mode = resolveMediaTransportMode(maxWidth)
        Column(verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)) {
            if (mode == MediaTransportMode.Stacked) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { MediaPlayerIconAction(play, colors, primary = true) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    MediaPlayerIconAction(previous, colors)
                    MediaPlayerIconAction(next, colors)
                }
            } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                if (mode == MediaTransportMode.Full) MediaPlayerIconAction(leading, colors)
                MediaPlayerIconAction(previous, colors)
                MediaPlayerIconAction(play, colors, primary = true)
                MediaPlayerIconAction(next, colors)
                if (mode == MediaTransportMode.Full) MediaPlayerIconAction(trailing, colors)
            }
            if (mode != MediaTransportMode.Full) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MediaPlayerIconAction(leading, colors)
                MediaPlayerIconAction(trailing, colors)
            }
        }
    }
}

@Composable
internal fun MediaPlayerTimeline(
    position: Float,
    durationSeconds: Int,
    colors: ColorScheme,
    enabled: Boolean,
    bufferedPosition: Float? = null,
    onScrub: (Float) -> Unit,
    onScrubFinished: () -> Unit,
    onScrubCancelled: () -> Unit
) {
    val timeline = resolvePlayerTimeline(if (position.isFinite()) position.toInt() else 0, durationSeconds)
    Column {
        PlayerThinSlider(
            position = (position.takeIf { it.isFinite() } ?: 0f).coerceIn(0f, timeline.sliderMaxSeconds.toFloat()), duration = timeline.sliderMaxSeconds,
            colorScheme = colors, enabled = enabled, modifier = Modifier.heightIn(min = NordicControlSizes.touchTarget),
            bufferedPosition = bufferedPosition,
            onPositionChange = onScrub, onPositionChangeFinished = onScrubFinished, onPositionChangeCanceled = onScrubCancelled
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NordicSpacing.md)) {
            val style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum")
            Text(formatDuration(timeline.positionSeconds), style = style, color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f), maxLines = 1)
            Text(formatKnownDuration(durationSeconds), style = style, color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f), textAlign = TextAlign.End, maxLines = 1)
        }
    }
}

@Composable
internal fun MediaAudioPlayerBody(
    artwork: @Composable (Modifier) -> Unit,
    controls: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier) {
        val mode = resolveMediaPlayerBodyMode(maxWidth, maxHeight, LocalDensity.current.fontScale)
        val scroll = rememberScrollState()
        val scrollingArtworkHeight = minOf(maxWidth, 240.dp).coerceAtLeast(0.dp)
        when (mode) {
            MediaPlayerBodyMode.SideBySide -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(NordicSpacing.xxl)) {
                artwork(Modifier.weight(1f).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(scroll, enabled = scroll.maxValue > 0),
                    verticalArrangement = Arrangement.spacedBy(NordicSpacing.md), content = controls)
            }
            MediaPlayerBodyMode.ScrollablePortrait -> Column(
                Modifier.fillMaxSize().verticalScroll(scroll, enabled = scroll.maxValue > 0),
                verticalArrangement = Arrangement.spacedBy(NordicSpacing.md)
            ) {
                artwork(Modifier.fillMaxWidth().height(scrollingArtworkHeight))
                controls()
            }
            MediaPlayerBodyMode.Portrait -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(NordicSpacing.md)) {
                artwork(Modifier.fillMaxWidth().weight(1f))
                controls()
            }
        }
    }
}

@Composable
internal fun MediaPlayerArtwork(title: String, imageUrl: String?, fallbackIcon: ImageVector, colors: ColorScheme, modifier: Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight, 420.dp).coerceAtLeast(0.dp)
        Surface(shape = NordicShapes.xl, shadowElevation = 10.dp,
            border = BorderStroke(1.dp, colors.onSurface.copy(alpha = 0.06f)), modifier = Modifier.size(side)) {
            CoverArt(imageUrl, title, colors, size = side, modifier = Modifier.fillMaxSize(), shape = NordicShapes.xl, fallbackIcon = fallbackIcon)
        }
    }
}
