package com.nordic.mediahub.playback

import com.nordic.mediahub.data.AudiobookAudioTrack
import org.junit.Assert.assertEquals
import org.junit.Test

class AudiobookPlaybackEngineTest {
    @Test
    fun resolveAudiobookAbsolutePositionSeconds_addsTrackOffset() {
        val tracks = listOf(
            track(index = 0, startOffsetSeconds = 0),
            track(index = 1, startOffsetSeconds = 120)
        )

        assertEquals(
            165,
            resolveAudiobookAbsolutePositionSeconds(
                tracks = tracks,
                currentIndex = 1,
                currentPositionMs = 45_000L
            )
        )
    }

    @Test
    fun resolveAudiobookAbsolutePositionSeconds_fallsBackToPlayerPositionForUnknownTrack() {
        assertEquals(
            42,
            resolveAudiobookAbsolutePositionSeconds(
                tracks = emptyList(),
                currentIndex = 4,
                currentPositionMs = 42_500L
            )
        )
    }

    private fun track(index: Int, startOffsetSeconds: Int): AudiobookAudioTrack {
        return AudiobookAudioTrack(
            index = index,
            title = "Track $index",
            contentUrl = "https://example.test/$index.mp3",
            startOffsetSeconds = startOffsetSeconds,
            durationSeconds = 120
        )
    }
}
