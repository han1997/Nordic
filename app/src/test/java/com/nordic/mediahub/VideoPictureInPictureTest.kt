package com.nordic.mediahub

import com.nordic.mediahub.data.VideoItem
import com.nordic.mediahub.playback.VideoPlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoPictureInPictureTest {
    private val playing = VideoPlaybackState(
        video = VideoItem(
            id = "video-1",
            libraryId = "library-1",
            title = "Video One",
            type = "Movie",
            streamUrl = "https://example.test/video.mp4"
        ),
        isPlaying = true,
        playWhenReady = true
    )

    @Test
    fun bridge_allowsVisiblePlayingVideoWithPreferenceEnabled() {
        assertTrue(bridge().shouldEnterPip)
        assertFalse(bridge(showVideoPlayer = false).shouldEnterPip)
        assertFalse(bridge(pipEnabled = false).shouldEnterPip)
        assertFalse(bridge(state = playing.copy(isPlaying = false, playWhenReady = false)).shouldEnterPip)
    }

    @Test
    fun bridge_allowsBufferingOnlyWhilePlaybackIsRequested() {
        val buffering = playing.copy(isPlaying = false, isBuffering = true)
        assertTrue(bridge(state = buffering).shouldEnterPip)
        assertFalse(bridge(state = buffering.copy(playWhenReady = false)).shouldEnterPip)
        assertFalse(bridge(state = buffering, pipEnabled = false).shouldEnterPip)
    }

    @Test
    fun bridge_rejectsBothPlayerAndExternalErrorsIncludingWhileBuffering() {
        assertFalse(bridge(state = playing.copy(errorMessage = "播放失败")).shouldEnterPip)
        assertFalse(bridge(externalError = "无法播放").shouldEnterPip)
        assertFalse(bridge(state = playing.copy(isPlaying = false, isBuffering = true, errorMessage = "播放失败")).shouldEnterPip)
        assertTrue(bridge(state = playing.copy(errorMessage = " "), externalError = "").shouldEnterPip)
    }

    @Test
    fun bridge_rejectsMissingOrUnplayableVideo() {
        assertFalse(bridge(state = VideoPlaybackState()).shouldEnterPip)
        assertFalse(bridge(state = playing.copy(video = null)).shouldEnterPip)
        assertFalse(bridge(state = playing.copy(video = playing.video?.copy(streamUrl = null))).shouldEnterPip)
        assertFalse(bridge(state = playing.copy(video = playing.video?.copy(streamUrl = " "))).shouldEnterPip)
    }

    @Test
    fun bridge_usesEndedStateRatherThanDurationOrRoundedPosition() {
        assertTrue(bridge(state = playing.copy(durationSeconds = 0)).shouldEnterPip)
        assertTrue(bridge(state = playing.copy(durationSeconds = 10, positionSeconds = 10)).shouldEnterPip)
        assertFalse(bridge(state = playing.copy(hasEnded = true, durationSeconds = 0)).shouldEnterPip)
        assertFalse(bridge(state = playing.copy(hasEnded = true, durationSeconds = 120, positionSeconds = 119)).shouldEnterPip)
    }

    @Test
    fun bridge_doesNotChangeParamsOnProgressTicks() {
        assertEquals(bridge(), bridge(state = playing.copy(positionSeconds = 50, bufferedPositionSeconds = 90)))
        val portrait = bridge(state = playing.copy(videoAspectRatio = 9f / 16f))
        assertEquals(9, portrait.aspectRatioNumerator)
        assertEquals(16, portrait.aspectRatioDenominator)
    }

    @Test
    fun aspectRatio_preservesCommonLandscapeAndPortraitRatios() {
        assertEquals(16 to 9, resolveVideoPipAspectRatio(16f / 9f))
        assertEquals(9 to 16, resolveVideoPipAspectRatio(9f / 16f))
        assertEquals(4 to 3, resolveVideoPipAspectRatio(4f / 3f))
        assertEquals(1 to 1, resolveVideoPipAspectRatio(1f))
        assertEquals(47 to 20, resolveVideoPipAspectRatio(2.35f))
    }

    @Test
    fun aspectRatio_fallsBackForInvalidMetadata() {
        for (ratio in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertEquals(16 to 9, resolveVideoPipAspectRatio(ratio))
        }
    }

    @Test
    fun aspectRatio_clampsExtremesToAndroidLimits() {
        assertEquals(239 to 100, resolveVideoPipAspectRatio(239f / 100f))
        assertEquals(239 to 100, resolveVideoPipAspectRatio(32f / 9f))
        assertEquals(239 to 100, resolveVideoPipAspectRatio(Float.MAX_VALUE))
        assertEquals(100 to 239, resolveVideoPipAspectRatio(100f / 239f))
        assertEquals(100 to 239, resolveVideoPipAspectRatio(9f / 32f))
        assertEquals(100 to 239, resolveVideoPipAspectRatio(Float.MIN_VALUE))
    }

    @Test
    fun aspectRatio_approximationStaysInPlatformRange() {
        for (step in 1..10_000) {
            val (numerator, denominator) = resolveVideoPipAspectRatio(step / 1_000f)
            assertTrue(numerator > 0 && denominator > 0)
            assertTrue(numerator.toFloat() / denominator in (100f / 239f)..(239f / 100f))
        }
    }

    private fun bridge(
        state: VideoPlaybackState = playing,
        showVideoPlayer: Boolean = true,
        pipEnabled: Boolean = true,
        externalError: String? = null
    ) = resolveVideoPipBridgeState(showVideoPlayer, pipEnabled, state, externalError)
}
