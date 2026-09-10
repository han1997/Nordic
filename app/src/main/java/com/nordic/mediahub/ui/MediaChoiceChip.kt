package com.nordic.mediahub.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nordic.mediahub.ui.theme.NordicAlpha
import com.nordic.mediahub.ui.theme.NordicControlSizes
import com.nordic.mediahub.ui.theme.NordicMotion
import com.nordic.mediahub.ui.theme.NordicShapes
import com.nordic.mediahub.ui.theme.NordicSpacing

/** A mutually-exclusive choice, distinct from the smaller non-interactive metadata chips. */
@Composable
internal fun MediaChoiceChip(
    text: String,
    selected: Boolean,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val container by animateColorAsState(
        targetValue = if (selected) colorScheme.primaryContainer else colorScheme.surfaceVariant.copy(alpha = 0.56f),
        animationSpec = tween(NordicMotion.durationMicro, easing = NordicMotion.easingStandard),
        label = "media-choice-container"
    )
    val content by animateColorAsState(
        targetValue = when {
            !enabled -> colorScheme.onSurface.copy(alpha = NordicAlpha.faint)
            selected -> colorScheme.onPrimaryContainer
            else -> colorScheme.onSurfaceVariant
        },
        animationSpec = tween(NordicMotion.durationMicro, easing = NordicMotion.easingStandard),
        label = "media-choice-content"
    )
    Surface(
        color = container,
        contentColor = content,
        shape = NordicShapes.md,
        border = BorderStroke(1.dp, if (selected) colorScheme.primary.copy(alpha = 0.35f) else colorScheme.onSurface.copy(alpha = 0.06f)),
        modifier = modifier
            .heightIn(min = NordicControlSizes.touchTarget)
            .widthIn(max = NordicControlSizes.choiceMaxWidth)
            .pressScale(interactionSource, enabled = enabled)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier.padding(horizontal = NordicSpacing.md, vertical = NordicSpacing.sm),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
