package com.nordic.mediahub.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class HeaderAction(
    val icon: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

@Composable
fun rememberPressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.985f,
    defaultScale: Float = 1f,
    enabled: Boolean = true,
    durationMillis: Int = 150
): Float {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && isPressed) pressedScale else defaultScale,
        animationSpec = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing)
    )
    return scale
}

@Composable
fun AnimatedIconButton(icon: String, onClick: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interactionSource, pressedScale = 0.94f)

    IconButton(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier
            .size(42.dp)
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(colorScheme.surfaceVariant.copy(alpha = 0.58f))
    ) {
        Text(icon, fontSize = 20.sp, color = colorScheme.onSurface.copy(alpha = 0.78f))
    }
}

@Composable
fun HeaderActionGroup(
    actions: List<HeaderAction>,
    modifier: Modifier = Modifier
) {
    if (actions.isEmpty()) return

    val colorScheme = MaterialTheme.colorScheme

    Surface(
        color = colorScheme.surfaceVariant.copy(alpha = 0.56f),
        contentColor = colorScheme.onSurface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.06f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            actions.forEach { action ->
                HeaderActionButton(action)
            }
        }
    }
}

@Composable
private fun HeaderActionButton(action: HeaderAction) {
    val colorScheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val scale = rememberPressScale(
        interactionSource = interactionSource,
        pressedScale = 0.94f,
        enabled = action.enabled
    )

    Box(
        modifier = Modifier
            .size(34.dp)
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                enabled = action.enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = action.onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            action.icon,
            fontSize = 17.sp,
            color = colorScheme.onSurface.copy(alpha = if (action.enabled) 0.78f else 0.36f)
        )
    }
}
