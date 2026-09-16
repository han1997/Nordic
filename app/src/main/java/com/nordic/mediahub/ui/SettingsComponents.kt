package com.nordic.mediahub.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nordic.mediahub.ui.theme.NordicControlSizes
import com.nordic.mediahub.ui.theme.NordicShapes
import com.nordic.mediahub.ui.theme.NordicSpacing

@Composable
internal fun SettingsSectionTitle(title: String, topPadding: Dp = NordicSpacing.xl) {
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = topPadding, bottom = NordicSpacing.sm).semantics { heading() })
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SettingsRow(
    title: String,
    subtitle: String = "",
    value: String? = null,
    icon: ImageVector? = null,
    checked: Boolean? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
    highlighted: Boolean = false,
    id: String = "",
    onCheckedChange: ((Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(highlighted) { if (highlighted) { androidx.compose.runtime.withFrameNanos { }; bringIntoView.bringIntoView() } }
    val actionModifier = when {
        checked != null && onCheckedChange != null -> Modifier.toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
        onClick != null -> Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        else -> Modifier
    }
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val titleStyle = MaterialTheme.typography.titleMedium
    val valueStyle = MaterialTheme.typography.bodyMedium
    val titleWidth = remember(title, titleStyle, measurer, density) {
        with(density) { measurer.measure(AnnotatedString(title), titleStyle, maxLines = 1, softWrap = false).size.width.toDp() }
    }
    val valueWidth = remember(value, valueStyle, measurer, density) {
        with(density) { measurer.measure(AnnotatedString(value.orEmpty()), valueStyle, maxLines = 1, softWrap = false).size.width.toDp() }
    }
    val contentColor = when {
        !enabled -> colors.onSurface.copy(alpha = 0.38f)
        destructive -> colors.error
        highlighted -> colors.onPrimaryContainer
        else -> colors.onSurface
    }
    val secondaryColor = (if (highlighted) colors.onPrimaryContainer else colors.onSurfaceVariant)
        .copy(alpha = if (enabled) 1f else 0.38f)
    BoxWithConstraints(Modifier.fillMaxWidth().then(if (id.isBlank()) Modifier else Modifier.testTag(id))
        .bringIntoViewRequester(bringIntoView).clip(NordicShapes.sm)
        .background(if (highlighted) colors.primaryContainer else colors.surface.copy(alpha = 0f))
        .then(actionModifier)) {
        val inlineValue = !value.isNullOrBlank() && checked == null &&
            shouldInlineSettingsValue(maxWidth, titleWidth, valueWidth, density.fontScale, icon != null, onClick != null)
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = NordicSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(NordicSpacing.md), verticalAlignment = Alignment.CenterVertically) {
            icon?.let { Icon(it, null, Modifier.size(NordicControlSizes.icon), tint = if (!enabled || destructive) contentColor else colors.primary) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NordicSpacing.xs)) {
                Text(title, style = titleStyle, color = contentColor)
                if (!inlineValue && !value.isNullOrBlank()) Text(value, style = valueStyle, color = secondaryColor)
                if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = secondaryColor)
            }
            when {
                checked != null -> Switch(checked, onCheckedChange = null, enabled = enabled)
                inlineValue -> Text(value.orEmpty(), style = valueStyle, color = secondaryColor)
            }
            if (onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                Modifier.size(NordicControlSizes.compactIcon), tint = secondaryColor)
        }
    }
}

/** A quiet group boundary; individual rows retain their own role and click target. */
@Composable
internal fun SettingsGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier.fillMaxWidth(), shape = NordicShapes.md, color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.6f))) {
        Column(Modifier.padding(horizontal = NordicSpacing.lg, vertical = NordicSpacing.xs), content = content)
    }
}

internal data class SettingsChoice(val value: String, val label: String)
internal data class SettingsChoiceRequest(val title: String, val current: String, val choices: List<SettingsChoice>, val select: (String) -> Unit)

@Composable
internal fun SettingsChoiceDialog(request: SettingsChoiceRequest, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(onDismissRequest = onDismiss, title = { Text(request.title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 400.dp).selectableGroup()) {
                items(request.choices, key = { it.value }) { option ->
                    val selected = option.value == request.current
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(NordicShapes.md)
                        .background(if (selected) colors.primaryContainer else colors.surface.copy(alpha = 0f))
                        .selectable(selected, role = Role.RadioButton,
                            onClick = { request.select(option.value); onDismiss() }), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected, onClick = null)
                        Text(option.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
                            color = if (selected) colors.onPrimaryContainer else colors.onSurface)
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}