package com.nordic.mediahub.playback

import com.nordic.mediahub.data.AudiobookAudioTrack
import com.nordic.mediahub.data.AudiobookPlaybackSession
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

    @Test
    fun resolveInitialAudiobookSyncPositionSeconds_usesFurthestKnownAbsolutePosition() {
        val session = session(currentTimeSeconds = 90, startTimeSeconds = 120)

        assertEquals(
            150,
            resolveInitialAudiobookSyncPositionSeconds(
                session = session,
                statePositionSeconds = 150
            )
        )
    }

    @Test
    fun resolveInitialAudiobookSyncPositionSeconds_clampsNegativePositions() {
        val session = session(currentTimeSeconds = -20, startTimeSeconds = -10)

        assertEquals(
            0,
            resolveInitialAudiobookSyncPositionSeconds(
                session = session,
                statePositionSeconds = -30
            )
        )
    }

    @Test
    fun resolveAudiobookSyncDeltaSeconds_clampsBackwardsMovement() {
        assertEquals(0, resolveAudiobookSyncDeltaSeconds(lastSyncedPositionSeconds = 120, currentPositionSeconds = 90))
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

    private fun session(currentTimeSeconds: Int, startTimeSeconds: Int): AudiobookPlaybackSession {
        return AudiobookPlaybackSession(
            sessionId = "session-1",
            libraryItemId = "item-1",
            displayTitle = "Sample Book",
            displayAuthor = "Sample Author",
            coverUrl = null,
            durationSeconds = 300,
            currentTimeSeconds = currentTimeSeconds,
            startTimeSeconds = startTimeSeconds,
            chapters = emptyList(),
            audioTracks = listOf(track(index = 0, startOffsetSeconds = 0))
        )
    }
}
