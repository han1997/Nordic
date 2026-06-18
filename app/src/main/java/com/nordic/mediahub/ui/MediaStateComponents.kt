package com.nordic.mediahub.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
        else -> colorScheme.surfaceVariant.copy(alpha = 0.72f)
    }
    val contentColor = if (isError) colorScheme.onErrorContainer else colorScheme.onSurface
    val shape = RoundedCornerShape(if (density == MediaStateDensity.Compact) 20.dp else 24.dp)
    val padding = if (density == MediaStateDensity.Compact) 18.dp else 20.dp

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
            verticalArrangement = Arrangement.spacedBy(if (density == MediaStateDensity.Compact) 6.dp else 8.dp)
        ) {
            Text(
                title,
                fontSize = if (density == MediaStateDensity.Compact) 17.sp else 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
            Text(
                subtitle,
                fontSize = if (density == MediaStateDensity.Compact) 13.sp else 14.sp,
                lineHeight = if (density == MediaStateDensity.Compact) 19.sp else 20.sp,
                color = contentColor.copy(alpha = if (isError) 0.82f else 0.64f)
            )
            if (hint.isNotBlank()) {
                Text(
                    hint,
                    fontSize = 13.sp,
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
        color = colorScheme.surfaceVariant.copy(alpha = 0.76f),
        contentColor = colorScheme.onSurface,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                title,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface
            )
            Text(
                subtitle,
                fontSize = 13.sp,
                color = colorScheme.onSurface.copy(alpha = 0.62f)
            )
        }
    }
}
