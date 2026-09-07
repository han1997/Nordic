package com.nordic.mediahub.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nordic.mediahub.ui.theme.NordicAlpha
import com.nordic.mediahub.ui.theme.NordicControlSizes
import com.nordic.mediahub.ui.theme.NordicMotion
import com.nordic.mediahub.ui.theme.NordicShapes
import com.nordic.mediahub.ui.theme.NordicSpacing

internal data class HeaderAction(
    val icon: ImageVector,
    val contentDescription: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

@Composable
internal fun rememberPressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.985f,
    defaultScale: Float = 1f,
    enabled: Boolean = true,
    durationMillis: Int = NordicMotion.durationMicro
): Float {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && isPressed) pressedScale else defaultScale,
        animationSpec = tween(durationMillis = durationMillis, easing = NordicMotion.easingStandard)
    )
    return scale
}

@Composable
internal fun AnimatedIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colorScheme: ColorScheme = MaterialTheme.colorScheme,
    containerColor: Color = colorScheme.surfaceVariant.copy(alpha = 0.56f),
    iconSize: Dp = NordicControlSizes.icon
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interactionSource, pressedScale = 0.94f, enabled = enabled)
    IconButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier.size(NordicControlSizes.touchTarget).scale(scale)
            .clip(NordicShapes.md).background(containerColor)
            .then(if (containerColor.alpha > 0f) {
                Modifier.border(1.dp, colorScheme.onSurface.copy(alpha = 0.06f), NordicShapes.md)
            } else Modifier)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) colorScheme.onSurfaceVariant else colorScheme.onSurface.copy(alpha = NordicAlpha.faint),
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
internal fun HeaderActionGroup(
    actions: List<HeaderAction>,
    modifier: Modifier = Modifier,
    maxInlineActions: Int = actions.size
) {
    if (actions.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    val inlineCount = maxInlineActions.coerceIn(0, actions.size)
    val inlineActions = actions.take(inlineCount)
    val overflowActions = actions.drop(inlineCount)
    var menuExpanded by remember(actions.map { it.contentDescription }) { mutableStateOf(false) }
    Surface(
        color = colors.surfaceVariant.copy(alpha = 0.56f),
        contentColor = colors.onSurface,
        shape = NordicShapes.md,
        border = BorderStroke(1.dp, colors.onSurface.copy(alpha = 0.06f)),
        modifier = modifier
    ) {
        Row(
            Modifier.padding(NordicSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(NordicSpacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            inlineActions.forEach { action ->
                HeaderActionButton(action)
            }
            if (overflowActions.isNotEmpty()) {
                Box {
                    AnimatedIconButton(
                        icon = Icons.Filled.MoreHoriz,
                        contentDescription = "更多页面操作",
                        onClick = { menuExpanded = true },
                        containerColor = Color.Transparent,
                        iconSize = NordicControlSizes.compactIcon
                    )
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        overflowActions.forEach { action ->
                            DropdownMenuItem(
                                text = { Text(action.contentDescription, style = MaterialTheme.typography.bodyMedium) },
                                leadingIcon = { Icon(action.icon, contentDescription = null) },
                                enabled = action.enabled,
                                onClick = {
                                    menuExpanded = false
                                    action.onClick()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderActionButton(action: HeaderAction) {
    AnimatedIconButton(
        icon = action.icon,
        contentDescription = action.contentDescription,
        onClick = action.onClick,
        enabled = action.enabled,
        containerColor = Color.Transparent,
        iconSize = NordicControlSizes.compactIcon
    )
}
