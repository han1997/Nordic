package com.nordic.mediahub.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoPlayerScreenTest {
    @Test
    fun resolveVideoPlayerTimeline_keepsPositionWhenDurationUnknown() {
        val timeline = resolveVideoPlayerTimeline(
            positionSeconds = 40,
            durationSeconds = 0
        )

        assertEquals(VideoPlayerTimeline(positionSeconds = 40, sliderMaxSeconds = 40), timeline)
    }

    @Test
    fun resolveVideoPlayerTimeline_keepsNonEmptyRangeWhenDurationUnknownAtStart() {
        val timeline = resolveVideoPlayerTimeline(
            positionSeconds = 0,
            durationSeconds = 0
        )

        assertEquals(VideoPlayerTimeline(positionSeconds = 0, sliderMaxSeconds = 1), timeline)
    }

    @Test
    fun resolveVideoPlayerTimeline_usesKnownDurationWhenAheadOfPosition() {
        val timeline = resolveVideoPlayerTimeline(
            positionSeconds = 40,
            durationSeconds = 120
        )

        assertEquals(VideoPlayerTimeline(positionSeconds = 40, sliderMaxSeconds = 120), timeline)
    }

    @Test
    fun resolveVideoPlayerTimeline_expandsKnownDurationRangeToCurrentPosition() {
        val timeline = resolveVideoPlayerTimeline(
            positionSeconds = 130,
            durationSeconds = 120
        )

        assertEquals(VideoPlayerTimeline(positionSeconds = 130, sliderMaxSeconds = 130), timeline)
    }

    @Test
    fun formatVideoPlayerDurationLabel_usesUnknownLabelWhenDurationIsUnknown() {
        assertEquals("--:--", formatVideoPlayerDurationLabel(0))
        assertEquals("--:--", formatVideoPlayerDurationLabel(-1))
    }

    @Test
    fun formatVideoPlayerDurationLabel_formatsKnownDuration() {
        assertEquals("2:00", formatVideoPlayerDurationLabel(120))
    }
}
