package com.nordic.mediahub.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import kotlin.math.abs
import kotlin.math.roundToInt

internal const val VIDEO_GESTURE_SKIP_BACK_SECONDS = 10
internal const val VIDEO_GESTURE_SKIP_FORWARD_SECONDS = 30
private const val VIDEO_PINCH_CYCLE_THRESHOLD = 1.2f

/**
 * Vertical distance (px) a drag must travel before it is classified as a
 * brightness/volume gesture instead of remaining "undecided" for the
 * horizontal-scrub recognizer (Hills/Yamby-style axis lock).
 */
internal const val VIDEO_GESTURE_AXIS_DECISION_PX = 48f

/** Video player gesture side: left half adjusts brightness, right half volume. */
internal enum class VideoGestureSide { Left, Right }

/**
 * Which axis a drag gesture has resolved to, decided once per gesture by the
 * first significant movement.
 */
internal enum class VideoGestureAxis { Horizontal, Vertical, Undecided }

/**
 * Resolves the gesture side for a tap/drag origin x coordinate.
 */
internal fun resolveVideoGestureSide(x: Float, widthPx: Float): VideoGestureSide {
    if (widthPx <= 0f) return VideoGestureSide.Left
    return if (x < widthPx / 2f) VideoGestureSide.Left else VideoGestureSide.Right
}

/**
 * Decides the gesture axis from accumulated drag deltas. The first axis to
 * exceed the decision threshold wins (axis lock); returns
 * [VideoGestureAxis.Undecided] while below threshold.
 */
internal fun resolveVideoGestureAxis(
    accumulatedX: Float,
    accumulatedY: Float,
    decisionPx: Float = VIDEO_GESTURE_AXIS_DECISION_PX
): VideoGestureAxis {
    val dx = abs(accumulatedX)
    val dy = abs(accumulatedY)
    if (dx < decisionPx && dy < decisionPx) return VideoGestureAxis.Undecided
    return if (dx >= dy) VideoGestureAxis.Horizontal else VideoGestureAxis.Vertical
}

/**
 * Converts a vertical drag step into a normalized brightness/volume delta.
 * Upward drag (negative dy) increases the value, so the result is negated;
 * the delta is expressed as a fraction of the reference height so the
 * gesture scales consistently across screens.
 */
internal fun resolveVideoVerticalGestureStep(dragAmountY: Float, referenceHeightPx: Float): Float {
    if (referenceHeightPx <= 0f) return 0f
    return -dragAmountY / referenceHeightPx
}

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
    onCycleAspectRatio: () -> Unit,
    onBrightnessDrag: ((Float) -> Unit)? = null,
    onVolumeDrag: ((Float) -> Unit)? = null,
    onGestureEnd: (() -> Unit)? = null
): Modifier = composed {
    if (!enabled) return@composed this

    var widthPx by remember { mutableStateOf(0f) }
    var heightPx by remember { mutableStateOf(0f) }
    val currentPosition by rememberUpdatedState(currentPositionSeconds)
    val scrubStartPos = remember { mutableStateOf(0f) }
    val scrubAccumulatedPx = remember { mutableStateOf(0f) }
    val scrubCurrentValue = remember { mutableStateOf(0f) }
    val pinchBaselineDistance = remember { mutableStateOf(0f) }
    val verticalAccumulatedPx = remember { mutableStateOf(0f) }
    val horizontalAccumulatedPx = remember { mutableStateOf(0f) }
    val gestureAxis = remember { mutableStateOf(VideoGestureAxis.Undecided) }
    val gestureSide = remember { mutableStateOf(VideoGestureSide.Left) }

    fun resetGestureState() {
        verticalAccumulatedPx.value = 0f
        horizontalAccumulatedPx.value = 0f
        gestureAxis.value = VideoGestureAxis.Undecided
    }

    fun dispatchVerticalStep(dragAmountY: Float, originX: Float) {
        val heightReference = if (heightPx > 0f) heightPx else 0f
        val step = resolveVideoVerticalGestureStep(dragAmountY, heightReference)
        val dragHandler = if (resolveVideoGestureSide(originX, widthPx) == VideoGestureSide.Left) {
            onBrightnessDrag
        } else {
            onVolumeDrag
        }
        dragHandler?.invoke(step)
    }

    this
        .onSizeChanged {
            widthPx = it.width.toFloat()
            heightPx = it.height.toFloat()
        }
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
            if (durationSeconds > 0) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        resetGestureState()
                        gestureSide.value = resolveVideoGestureSide(offset.x, widthPx)
                        scrubStartPos.value = currentPosition.toFloat()
                        scrubAccumulatedPx.value = 0f
                    },
                    onHorizontalDrag = { change, dragAmountX ->
                        horizontalAccumulatedPx.value += dragAmountX
                        val dragDeltaY = change.positionChange().y
                        verticalAccumulatedPx.value += dragDeltaY
                        gestureAxis.value = resolveVideoGestureAxis(
                            horizontalAccumulatedPx.value,
                            verticalAccumulatedPx.value
                        )
                        when (gestureAxis.value) {
                            VideoGestureAxis.Horizontal -> {
                                if (widthPx > 0f) {
                                    scrubAccumulatedPx.value += dragAmountX
                                    val newScrub = scrubStartPos.value +
                                        (scrubAccumulatedPx.value / widthPx) * durationSeconds
                                    val clamped = newScrub.coerceIn(0f, durationSeconds.toFloat())
                                    scrubCurrentValue.value = clamped
                                    onScrubChange(clamped)
                                }
                            }
                            VideoGestureAxis.Vertical -> {
                                dispatchVerticalStep(dragDeltaY, change.position.x)
                            }
                            VideoGestureAxis.Undecided -> Unit
                        }
                        change.consume()
                    },
                    onDragEnd = {
                        if (gestureAxis.value == VideoGestureAxis.Horizontal) {
                            onSeek(scrubCurrentValue.value.roundToInt())
                            onScrubChange(null)
                        }
                        onGestureEnd?.invoke()
                        resetGestureState()
                    },
                    onDragCancel = {
                        if (gestureAxis.value == VideoGestureAxis.Horizontal) {
                            onScrubChange(null)
                        }
                        onGestureEnd?.invoke()
                        resetGestureState()
                    }
                )
            } else {
                // No known duration: horizontal scrub is disabled, but vertical
                // drags still route to brightness/volume.
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        dispatchVerticalStep(dragAmount, change.position.x)
                        change.consume()
                    },
                    onDragEnd = { onGestureEnd?.invoke() },
                    onDragCancel = { onGestureEnd?.invoke() }
                )
            }
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
