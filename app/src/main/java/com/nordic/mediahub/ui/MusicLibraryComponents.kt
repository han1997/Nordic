package com.nordic.mediahub.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nordic.mediahub.ui.theme.NordicControlSizes
import com.nordic.mediahub.ui.theme.NordicShapes
import com.nordic.mediahub.ui.theme.NordicSpacing

/** One list density and artwork/error behavior for songs, albums, artists and playlists. */
@Composable
internal fun MusicLibraryRow(
    title: String,
    subtitle: String,
    colorScheme: ColorScheme,
    artwork: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    metadata: String? = null,
    trailingText: String? = null,
    clickLabel: String? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val trailingStyle = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum")
    val trailingWidth = remember(trailingText, trailingStyle, textMeasurer, density) {
        if (trailingText == null) 0.dp else with(density) {
            textMeasurer.measure(AnnotatedString(trailingText), trailingStyle, maxLines = 1, softWrap = false).size.width.toDp()
        }
    }
    Surface(
        color = colorScheme.surfaceVariant.copy(alpha = 0.42f),
        contentColor = colorScheme.onSurface,
        shape = NordicShapes.md,
        border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.045f)),
        modifier = modifier.fillMaxWidth().pressScale(interactionSource)
            .clickable(role = Role.Button, onClickLabel = clickLabel,
                interactionSource = interactionSource, indication = null, onClick = onClick)
    ) {
        BoxWithConstraints(Modifier.padding(horizontal = NordicSpacing.md, vertical = NordicSpacing.sm)) {
            val inlineTrailing = trailingText != null && shouldInlineMusicRowTrailing(maxWidth, trailingWidth, density.fontScale)
            val supportingText = listOfNotNull(trailingText.takeUnless { inlineTrailing }, metadata?.takeIf { it.isNotBlank() })
                .joinToString(" · ")
            Row(horizontalArrangement = Arrangement.spacedBy(NordicSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                // The adjacent title already names this artwork; avoid duplicate TalkBack announcements.
                Box(Modifier.clearAndSetSemantics {}) { artwork() }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NordicSpacing.xs)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = colorScheme.onSurface,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (supportingText.isNotBlank()) Text(supportingText, style = trailingStyle,
                        color = colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (inlineTrailing) Text(trailingText.orEmpty(), style = trailingStyle,
                    color = colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

/** Keeps the same content hierarchy while allowing narrow/large-text headers to stack. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MusicCollectionHeader(
    itemId: String,
    title: String,
    subtitle: String,
    metadata: List<String>,
    artworkUrl: String?,
    fallbackIcon: ImageVector,
    colorScheme: ColorScheme,
    onPlayAll: (() -> Unit)?,
    modifier: Modifier = Modifier,
    playEnabled: Boolean = true,
    description: String? = null,
    initials: String? = null,
    artworkShape: Shape = NordicShapes.lg
) {
    BoxWithConstraints(modifier.fillMaxWidth().padding(vertical = NordicSpacing.sm)) {
        val layout = resolveMusicCollectionLayout(maxWidth, LocalDensity.current.fontScale)
        val artwork: @Composable () -> Unit = {
            CoverArt(artworkUrl, title, colorScheme, size = layout.artworkSize,
                shape = artworkShape, initials = initials, fallbackIcon = fallbackIcon)
        }
        val details: @Composable () -> Unit = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = if (layout.stacked) Alignment.CenterHorizontally else Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)
            ) {
                Text(title, style = MaterialTheme.typography.headlineMedium, color = colorScheme.onSurface,
                    textAlign = if (layout.stacked) TextAlign.Center else TextAlign.Start,
                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() })
                if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    textAlign = if (layout.stacked) TextAlign.Center else TextAlign.Start)
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(NordicSpacing.sm,
                        if (layout.stacked) Alignment.CenterHorizontally else Alignment.Start),
                    verticalArrangement = Arrangement.spacedBy(NordicSpacing.xs)
                ) {
                    metadata.filter { it.isNotBlank() }.distinct().forEach { MetaChip(it, colorScheme) }
                }
                if (onPlayAll != null) PrimaryActionButton("播放全部", colorScheme, enabled = playEnabled, onClick = onPlayAll,
                    modifier = Modifier.widthIn(max = 360.dp), icon = Icons.Filled.PlayArrow)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(NordicSpacing.lg)) {
            if (layout.stacked) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(NordicSpacing.lg)) {
                    artwork()
                    details()
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(NordicSpacing.xxl), verticalAlignment = Alignment.Top) {
                    artwork()
                    Box(Modifier.weight(1f)) { details() }
                }
            }
            description?.trim()?.takeIf { it.isNotEmpty() }?.let { text ->
                MusicCollectionDescription(itemId, text, colorScheme)
            }
        }
    }
}

@Composable
internal fun MusicCollectionDescription(itemId: String, text: String, colors: ColorScheme) {
    var expanded by rememberSaveable(itemId, text) { mutableStateOf(false) }
    var canExpand by remember(itemId, text) { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    Column(Modifier.fillMaxWidth()) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) canExpand = it.hasVisualOverflow })
        if (canExpand || expanded) TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.heightIn(min = NordicControlSizes.touchTarget)
                .pressScale(interactionSource),
            interactionSource = interactionSource,
            colors = ButtonDefaults.textButtonColors(contentColor = colors.onPrimaryContainer)
        ) {
            Text(if (expanded) "收起简介" else "展开简介", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
internal fun MusicCollectionAction(
    text: String,
    icon: ImageVector,
    colors: ColorScheme,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        color = if (destructive) colors.errorContainer else colors.surfaceVariant.copy(alpha = 0.56f),
        contentColor = if (destructive) colors.onErrorContainer else colors.onSurface,
        shape = NordicShapes.full,
        modifier = modifier.heightIn(min = NordicControlSizes.touchTarget).pressScale(interactionSource)
            .clickable(role = Role.Button, interactionSource = interactionSource, indication = null, onClick = onClick)
    ) {
        Row(Modifier.padding(horizontal = NordicSpacing.lg, vertical = NordicSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(NordicSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(NordicControlSizes.compactIcon))
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
