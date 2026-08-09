package com.nordic.mediahub.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nordic.mediahub.ui.theme.NordicAlpha
import com.nordic.mediahub.ui.theme.NordicShapes
import com.nordic.mediahub.ui.theme.NordicSpacing

// State-semantic container alpha — design-system values, intentionally NOT part of NordicAlpha
// (which is only for generic secondary-text tiers). See quality-guidelines.md.
private const val EMPTY_STATE_CONTAINER_ALPHA = 0.72f
private const val LOADING_STATE_CONTAINER_ALPHA = 0.76f
private const val ERROR_STATE_SUBTITLE_ALPHA = 0.82f
private const val LOADING_PROGRESS_ALPHA = 0.72f
private const val LOADING_PROGRESS_TRACK_ALPHA = 0.08f

internal enum class MediaStateTone {
    Neutral,
    Error
}

internal enum class MediaStateDensity {
    Prominent,
    Compact
}

@Composable
internal fun MediaStateCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    hint: String = "",
    tone: MediaStateTone = MediaStateTone.Neutral,
    density: MediaStateDensity = MediaStateDensity.Prominent
) {
    val colorScheme = MaterialTheme.colorScheme
    val isError = tone == MediaStateTone.Error
    val containerColor = when {
        isError -> colorScheme.errorContainer
        density == MediaStateDensity.Compact -> colorScheme.surfaceVariant.copy(alpha = 0.5f)
        else -> colorScheme.surfaceVariant.copy(alpha = EMPTY_STATE_CONTAINER_ALPHA)
    }
    val contentColor = if (isError) colorScheme.onErrorContainer else colorScheme.onSurface
    val shape = if (density == MediaStateDensity.Compact) NordicShapes.lg else NordicShapes.xl
    val padding = if (density == MediaStateDensity.Compact) NordicSpacing.lg else NordicSpacing.xl

    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = shape,
        border = if (density == MediaStateDensity.Compact && !isError) {
            BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.05f))
        } else {
            null
        },
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(padding),
            verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor
            )
            Text(
                subtitle,
                style = if (density == MediaStateDensity.Compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Normal,
                lineHeight = if (density == MediaStateDensity.Compact) 19.sp else 20.sp,
                color = contentColor.copy(alpha = if (isError) ERROR_STATE_SUBTITLE_ALPHA else NordicAlpha.medium)
            )
            if (hint.isNotBlank()) {
                Text(
                    hint,
                    style = MaterialTheme.typography.labelLarge,
                    color = colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
internal fun MediaLoadingCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        color = colorScheme.surfaceVariant.copy(alpha = LOADING_STATE_CONTAINER_ALPHA),
        contentColor = colorScheme.onSurface,
        shape = NordicShapes.xl,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(NordicSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.onSurface
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Normal,
                color = colorScheme.onSurface.copy(alpha = NordicAlpha.medium)
            )
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = colorScheme.primary.copy(alpha = LOADING_PROGRESS_ALPHA),
                trackColor = colorScheme.onSurface.copy(alpha = LOADING_PROGRESS_TRACK_ALPHA)
            )
        }
    }
}
