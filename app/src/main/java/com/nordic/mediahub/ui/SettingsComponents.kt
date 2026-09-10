package com.nordic.mediahub.ui

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
internal fun SettingsSectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = NordicSpacing.xl, bottom = NordicSpacing.sm).semantics { heading() })
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
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag(id).bringIntoViewRequester(bringIntoView)
        .background(if (highlighted) colors.primaryContainer else colors.surface.copy(alpha = 0f), NordicShapes.sm)
        .then(actionModifier).padding(vertical = NordicSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(NordicSpacing.md), verticalAlignment = Alignment.CenterVertically) {
        icon?.let { Icon(it, null, Modifier.size(NordicControlSizes.icon), tint = if (destructive) colors.error else colors.primary) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NordicSpacing.xs)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = if (destructive) colors.error else colors.onSurface)
            val detail = listOfNotNull(subtitle.takeIf { it.isNotBlank() }, value?.takeIf { it.length > 10 }).joinToString(" · ")
            if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        when {
            checked != null -> Switch(checked, onCheckedChange = null, enabled = enabled)
            value != null && value.length <= 10 -> Text(value, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        if (onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(NordicControlSizes.compactIcon), tint = colors.onSurfaceVariant)
    }
}

internal data class SettingsChoice(val value: String, val label: String)
internal data class SettingsChoiceRequest(val title: String, val current: String, val choices: List<SettingsChoice>, val select: (String) -> Unit)

@Composable
internal fun SettingsChoiceDialog(request: SettingsChoiceRequest, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(request.title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 400.dp).selectableGroup()) {
                items(request.choices, key = { it.value }) { option ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .selectable(option.value == request.current, role = Role.RadioButton,
                            onClick = { request.select(option.value); onDismiss() }), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(option.value == request.current, onClick = null)
                        Text(option.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}