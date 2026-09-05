package com.nordic.mediahub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.nordic.mediahub.ui.theme.NordicAlpha
import com.nordic.mediahub.ui.theme.NordicShapes
import kotlin.math.roundToInt

/**
 * Thin-line progress bar with a small circular thumb — shared across Music,
 * Audiobook, and Video players. Replaces the stock Material3
 * [androidx.compose.material3.Slider] to match the mainstream media-app
 * aesthetic. Keeps the scrub-local-state contract: onPositionChange fires
 * during drag, onPositionChangeFinished fires on release and is the only
 * place a real seek should land, onPositionChangeCanceled clears the scrub.
 */
@Composable
internal fun PlayerThinSlider(
    position: Float,
    duration: Int,
    colorScheme: ColorScheme,
    enabled: Boolean,
    activeColor: Color? = null,
    inactiveColor: Color? = null,
    thumbColor: Color? = null,
    bufferedPosition: Float? = null,
    bufferColor: Color? = null,
    onPositionChange: (Float) -> Unit,
    onPositionChangeFinished: () -> Unit,
    onPositionChangeCanceled: () -> Unit
) {
    val safeDuration = maxOf(duration, 1)
    val progress = (position / safeDuration).coerceIn(0f, 1f)
    val trackHeight = 4.dp
    val thumbSize = 12.dp

    val resolvedActiveColor = activeColor ?: if (enabled) {
        colorScheme.primary
    } else {
        colorScheme.onSurface.copy(alpha = NordicAlpha.faint)
    }
    val resolvedInactiveColor = inactiveColor ?: colorScheme.onSurface.copy(
        alpha = if (enabled) 0.14f else 0.08f
    )
    val resolvedThumbColor = thumbColor ?: if (enabled) {
        colorScheme.primary
    } else {
        colorScheme.onSurface.copy(alpha = 0.2f)
    }
    val resolvedBufferColor = bufferColor ?: if (enabled) {
        colorScheme.primary.copy(alpha = 0.30f)
    } else {
        colorScheme.onSurface.copy(alpha = 0.10f)
    }
    val bufferProgress = bufferedPosition
        ?.let { (it / safeDuration).coerceIn(progress, 1f) }
        ?: progress

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(thumbSize + trackHeight) // touch target room
            .pointerInput(enabled, safeDuration) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset ->
                        onPositionChange(
                            resolvePlayerThinSliderPosition(offset.x, size.width, safeDuration)
                        )
                    },
                    onDragEnd = { onPositionChangeFinished() },
                    onDragCancel = { onPositionChangeCanceled() }
                ) { change, _ ->
                    change.consume()
                    onPositionChange(
                        resolvePlayerThinSliderPosition(change.position.x, size.width, safeDuration)
                    )
                }
            }
            .padding(vertical = (thumbSize - trackHeight) / 2),
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val thumbOffsetPx = resolvePlayerThinSliderThumbOffsetPx(
            trackWidthPx = constraints.maxWidth.toFloat(),
            thumbSizePx = with(density) { thumbSize.toPx() },
            progress = progress
        )
        val trackWidthPx = constraints.maxWidth.toFloat()
        // Inactive track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(NordicShapes.full)
                .background(resolvedInactiveColor)
        )
        // Active track
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .height(trackHeight)
                .clip(NordicShapes.full)
                .background(resolvedActiveColor)
        )
        // Buffer track (between progress and bufferedPosition)
        if (bufferProgress > progress) {
            Box(
                modifier = Modifier
                    .offset { IntOffset((progress * trackWidthPx).roundToInt(), 0) }
                    .fillMaxWidth(bufferProgress - progress)
                    .height(trackHeight)
                    .clip(NordicShapes.full)
                    .background(resolvedBufferColor)
            )
        }
        // Thumb
        Box(
            modifier = Modifier
                .offset { IntOffset(thumbOffsetPx.roundToInt(), 0) }
                .size(thumbSize)
                .clip(NordicShapes.full)
                .background(SolidColor(resolvedThumbColor))
        )
    }
}

internal fun resolvePlayerThinSliderPosition(pointerX: Float, trackWidth: Int, durationSeconds: Int): Float {
    val safeDuration = maxOf(durationSeconds, 1)
    if (trackWidth <= 0) return 0f
    val ratio = (pointerX / trackWidth.toFloat()).coerceIn(0f, 1f)
    return ratio * safeDuration
}

internal fun resolvePlayerThinSliderThumbOffsetPx(trackWidthPx: Float, thumbSizePx: Float, progress: Float): Float {
    val travelPx = (trackWidthPx - thumbSizePx).coerceAtLeast(0f)
    return travelPx * progress.coerceIn(0f, 1f)
}