package com.nordic.mediahub.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import kotlin.math.roundToInt

internal const val VIDEO_GESTURE_SKIP_BACK_SECONDS = 10
internal const val VIDEO_GESTURE_SKIP_FORWARD_SECONDS = 30
private const val VIDEO_PINCH_CYCLE_THRESHOLD = 1.2f

@Composable
internal fun Modifier.videoPlayerGestures(
    enabled: Boolean,
    isFullscreen: Boolean,
    durationSeconds: Int,
    currentPositionSeconds: Int,
    onToggleControls: () -> Unit,
    onSeekRelative: (Int) -> Unit,
    onScrubChange: (Float?) -> Unit,
    onSeek: (Int) -> Unit,
    onCycleAspectRatio: () -> Unit
): Modifier = composed {
    if (!enabled) return@composed this

    var widthPx by remember { mutableStateOf(0f) }
    val currentPosition by rememberUpdatedState(currentPositionSeconds)
    val scrubStartPos = remember { mutableStateOf(0f) }
    val scrubAccumulatedPx = remember { mutableStateOf(0f) }
    val scrubCurrentValue = remember { mutableStateOf(0f) }
    val pinchBaselineDistance = remember { mutableStateOf(0f) }

    this
        .onSizeChanged { widthPx = it.width.toFloat() }
        .pointerInput(enabled) {
            detectTapGestures(
                onTap = { onToggleControls() },
                onDoubleTap = { offset ->
                    if (widthPx > 0f) {
                        val delta = if (offset.x < widthPx / 2f) {
                            -VIDEO_GESTURE_SKIP_BACK_SECONDS
                        } else {
                            VIDEO_GESTURE_SKIP_FORWARD_SECONDS
                        }
                        onSeekRelative(delta)
                    }
                }
            )
        }
        .pointerInput(enabled, durationSeconds) {
            detectHorizontalDragGestures(
                onDragStart = {
                    if (durationSeconds > 0) {
                        scrubStartPos.value = currentPosition.toFloat()
                        scrubAccumulatedPx.value = 0f
                    }
                },
                onHorizontalDrag = { change, dragAmount ->
                    if (durationSeconds > 0 && widthPx > 0f) {
                        scrubAccumulatedPx.value += dragAmount
                        val newScrub = scrubStartPos.value +
                            (scrubAccumulatedPx.value / widthPx) * durationSeconds
                        val clamped = newScrub.coerceIn(0f, durationSeconds.toFloat())
                        scrubCurrentValue.value = clamped
                        onScrubChange(clamped)
                        change.consume()
                    }
                },
                onDragEnd = {
                    if (durationSeconds > 0) {
                        onSeek(scrubCurrentValue.value.roundToInt())
                        onScrubChange(null)
                    }
                },
                onDragCancel = {
                    if (durationSeconds > 0) {
                        onScrubChange(null)
                    }
                }
            )
        }
        .pointerInput(enabled, isFullscreen) {
            if (!isFullscreen) return@pointerInput
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                do {
                    val event = awaitPointerEvent()
                    val activePointers = event.changes.filter { it.pressed }
                    if (activePointers.size >= 2) {
                        val distance = pointerDistance(
                            activePointers[0].position,
                            activePointers[1].position
                        )
                        if (pinchBaselineDistance.value <= 0f) {
                            pinchBaselineDistance.value = distance
                        } else if (distance > 0f) {
                            val scale = distance / pinchBaselineDistance.value
                            if (scale >= VIDEO_PINCH_CYCLE_THRESHOLD) {
                                onCycleAspectRatio()
                                pinchBaselineDistance.value = distance
                            }
                        }
                    } else {
                        pinchBaselineDistance.value = 0f
                    }
                } while (event.changes.any { it.pressed })
            }
        }
}

private fun pointerDistance(a: Offset, b: Offset): Float {
    return (a - b).getDistance()
}
