package com.nordic.mediahub

import com.nordic.mediahub.playback.VideoPlaybackState
import kotlin.math.abs
import kotlin.math.roundToInt

/** Only PiP-relevant values cross into Activity callbacks; progress ticks do not update params. */
internal data class VideoPipBridgeState(
    val shouldEnterPip: Boolean = false,
    val aspectRatioNumerator: Int = 16,
    val aspectRatioDenominator: Int = 9
)

internal fun resolveVideoPipBridgeState(
    showVideoPlayer: Boolean,
    pipEnabled: Boolean,
    playbackState: VideoPlaybackState,
    externalError: String?
): VideoPipBridgeState {
    val (numerator, denominator) = resolveVideoPipAspectRatio(playbackState.videoAspectRatio)
    return VideoPipBridgeState(
        shouldEnterPip = showVideoPlayer && pipEnabled &&
            !playbackState.video?.streamUrl.isNullOrBlank() &&
            externalError.isNullOrBlank() && playbackState.errorMessage.isNullOrBlank() &&
            !playbackState.hasEnded &&
            (playbackState.isPlaying || (playbackState.isBuffering && playbackState.playWhenReady)),
        aspectRatioNumerator = numerator,
        aspectRatioDenominator = denominator
    )
}

/** Android accepts only 1:2.39 through 2.39:1, including for portrait/ultrawide media. */
internal fun resolveVideoPipAspectRatio(ratio: Float): Pair<Int, Int> {
    if (!ratio.isFinite() || ratio <= 0f) return 16 to 9
    if (ratio <= 100f / 239f) return 100 to 239
    if (ratio >= 239f / 100f) return 239 to 100
    for (denominator in 1..100) {
        val numerator = ratio * denominator
        val rounded = numerator.roundToInt()
        if (abs(numerator - rounded) < 0.002f) {
            return rounded to denominator
        }
    }
    return (ratio * 100).roundToInt() to 100
}
