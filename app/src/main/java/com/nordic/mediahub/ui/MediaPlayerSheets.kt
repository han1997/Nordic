package com.nordic.mediahub.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nordic.mediahub.playback.resolvePlaybackSpeedLabel
import com.nordic.mediahub.ui.theme.NordicAlpha
import com.nordic.mediahub.ui.theme.NordicControlSizes
import com.nordic.mediahub.ui.theme.NordicShapes
import com.nordic.mediahub.ui.theme.NordicSpacing
import kotlin.math.abs

@Composable
internal fun MediaPlayerSheetHeader(
    title: String,
    colors: ColorScheme,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailingAction: (@Composable () -> Unit)? = null
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val stackAction = trailingAction != null && (LocalDensity.current.fontScale > 1.3f || maxWidth < 320.dp)
        Column(verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NordicSpacing.md)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NordicSpacing.xs)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
                    if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (!stackAction) trailingAction?.invoke()
                AnimatedIconButton(Icons.Filled.Close, "关闭$title", onDismiss, colorScheme = colors, containerColor = Color.Transparent)
            }
            if (stackAction) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { trailingAction?.invoke() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaPlayerSheet(
    title: String,
    colors: ColorScheme,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    skipPartiallyExpanded: Boolean = true,
    trailingAction: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.86f
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surface.copy(alpha = 1f),
        shape = NordicShapes.xl,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = skipPartiallyExpanded)
    ) {
        // Material3 1.3 derives dialog system bars from system night mode, which can
        // differ from Nordic's explicit theme. Change only this dialog's window.
        val view = LocalView.current
        val window = (view.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.let {
                WindowCompat.getInsetsController(it, it.decorView).apply {
                    isAppearanceLightStatusBars = colors.surface.luminance() > 0.5f
                    isAppearanceLightNavigationBars = colors.surface.luminance() > 0.5f
                }
            }
        }
        Column(
            Modifier.fillMaxWidth().heightIn(max = maxHeight).navigationBarsPadding()
                .padding(horizontal = NordicSpacing.lg).padding(bottom = NordicSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)
        ) {
            MediaPlayerSheetHeader(title, colors, onDismiss, subtitle = subtitle, trailingAction = trailingAction)
            content()
        }
    }
}

@Composable
internal fun MediaPlaybackSpeedSheet(
    options: List<Float>,
    currentSpeed: Float,
    colors: ColorScheme,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    MediaPlayerSheet("播放速度", colors, onDismiss, subtitle = "当前 ${resolvePlaybackSpeedLabel(currentSpeed)}") {
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(NordicSpacing.xs)) {
            items(options, key = { it }) { speed ->
                MediaPlayerChoiceRow(resolvePlaybackSpeedLabel(speed), abs(speed - currentSpeed) < 0.001f, colors,
                    onClick = { onSelect(speed) })
            }
        }
    }
}

@Composable
internal fun MediaPlayerChoiceRow(
    title: String,
    selected: Boolean?,
    colors: ColorScheme,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    val source = remember { MutableInteractionSource() }
    val interaction = if (selected == null) Modifier.clickable(role = Role.Button, interactionSource = source,
        indication = null, onClick = onClick) else Modifier.selectable(selected = selected, role = Role.RadioButton,
        interactionSource = source, indication = null, onClick = onClick)
    Surface(
        color = if (selected == true) colors.primaryContainer else colors.surfaceVariant.copy(alpha = 0.42f),
        contentColor = colors.onSurface,
        shape = NordicShapes.md,
        modifier = modifier.fillMaxWidth()
    ) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).pressScale(source)
            .then(interaction)
            .padding(horizontal = NordicSpacing.md, vertical = NordicSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(NordicSpacing.md), verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NordicSpacing.xs)) {
            Text(title, style = MaterialTheme.typography.bodyMedium,
                color = if (selected == true) colors.onPrimaryContainer else colors.onSurface,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = if (selected == true) colors.onPrimaryContainer else colors.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (selected == true) Icon(Icons.Filled.Check, "当前选择", tint = colors.onPrimaryContainer)
    }
    }
}

/**
 * Shared transient notice pill for player surfaces (seek feedback, favorite
 * failure, etc.): full-round surface with a subtle primary-tinted border and
 * a primary leading message plus optional secondary detail. Single source of
 * truth so overlay notices keep one visual language across players.
 */
@Composable
internal fun MediaTransientPill(
    message: String,
    colors: ColorScheme,
    modifier: Modifier = Modifier,
    detail: String? = null
) {
    Surface(
        color = colors.surface.copy(alpha = 0.94f),
        contentColor = colors.onSurface,
        shape = NordicShapes.full,
        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.24f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = NordicSpacing.lg, vertical = NordicSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(NordicSpacing.sm, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                message,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.primary,
                maxLines = 1
            )
            if (!detail.isNullOrBlank()) Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurface.copy(alpha = NordicAlpha.subtle),
                maxLines = 1
            )
        }
    }
}
