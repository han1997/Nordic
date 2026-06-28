package com.nordic.mediahub

import com.nordic.mediahub.data.AudiobookPlaybackSession
import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivityTest {
    @Test
    fun resolveAudiobookProgressSyncBaselineSeconds_usesSessionResumeWhenStateIsZero() {
        assertEquals(
            120,
            resolveAudiobookProgressSyncBaselineSeconds(
                statePositionSeconds = 0,
                session = session(startTimeSeconds = 120, currentTimeSeconds = 118)
            )
        )
    }

    @Test
    fun resolveAudiobookProgressSyncBaselineSeconds_usesStateWhenAlreadyAheadOfResume() {
        assertEquals(
            135,
            resolveAudiobookProgressSyncBaselineSeconds(
                statePositionSeconds = 135,
                session = session(startTimeSeconds = 120, currentTimeSeconds = 118)
            )
        )
    }

    @Test
    fun resolveAudiobookProgressSyncBaselineSeconds_clampsNegativeValuesToZero() {
        assertEquals(
            0,
            resolveAudiobookProgressSyncBaselineSeconds(
                statePositionSeconds = -10,
                session = session(startTimeSeconds = -20, currentTimeSeconds = -30)
            )
        )
    }

    private fun session(
        startTimeSeconds: Int,
        currentTimeSeconds: Int
    ): AudiobookPlaybackSession {
        return AudiobookPlaybackSession(
            sessionId = "session-1",
            libraryItemId = "book-1",
            displayTitle = "Book One",
            displayAuthor = "Author",
            coverUrl = null,
            durationSeconds = 300,
            currentTimeSeconds = currentTimeSeconds,
            startTimeSeconds = startTimeSeconds,
            chapters = emptyList(),
            audioTracks = emptyList()
        )
    }
}
