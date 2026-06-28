package com.nordic.mediahub

import com.nordic.mediahub.data.AudiobookPlaybackSession
import com.nordic.mediahub.data.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun resolveVideoProgressSyncBaselineSeconds_usesVideoResumeWhenStateIsZero() {
        assertEquals(
            90,
            resolveVideoProgressSyncBaselineSeconds(
                statePositionSeconds = 0,
                video = video(playbackPositionSeconds = 90)
            )
        )
    }

    @Test
    fun resolveVideoProgressSyncBaselineSeconds_usesStateWhenAlreadyAheadOfResume() {
        assertEquals(
            135,
            resolveVideoProgressSyncBaselineSeconds(
                statePositionSeconds = 135,
                video = video(playbackPositionSeconds = 90)
            )
        )
    }

    @Test
    fun resolveVideoProgressSyncBaselineSeconds_clampsNegativeValuesToZero() {
        assertEquals(
            0,
            resolveVideoProgressSyncBaselineSeconds(
                statePositionSeconds = -10,
                video = video(playbackPositionSeconds = -30)
            )
        )
    }

    @Test
    fun resolveAudiobookPlayRequestAction_startsNewSessionWhenNothingIsActive() {
        assertEquals(
            AudiobookPlayRequestAction.StartNewSession,
            resolveAudiobookPlayRequestAction(
                currentSession = null,
                requestedLibraryItemId = "book-1"
            )
        )
    }

    @Test
    fun resolveAudiobookPlayRequestAction_reusesCurrentSessionForSameBook() {
        assertEquals(
            AudiobookPlayRequestAction.ReuseCurrentSession,
            resolveAudiobookPlayRequestAction(
                currentSession = session(
                    libraryItemId = "book-1",
                    startTimeSeconds = 120,
                    currentTimeSeconds = 118
                ),
                requestedLibraryItemId = "book-1"
            )
        )
    }

    @Test
    fun resolveAudiobookPlayRequestAction_closesCurrentSessionBeforeDifferentBook() {
        assertEquals(
            AudiobookPlayRequestAction.CloseCurrentSessionBeforeStart,
            resolveAudiobookPlayRequestAction(
                currentSession = session(
                    libraryItemId = "book-1",
                    startTimeSeconds = 120,
                    currentTimeSeconds = 118
                ),
                requestedLibraryItemId = "book-2"
            )
        )
    }

    @Test
    fun resolveAudiobookCloseFailurePresentation_ignoresBackgroundHandoffFailure() {
        val presentation = resolveAudiobookCloseFailurePresentation(
            closeFailureMessage = "close failed",
            reopenPlayerOnFailure = false
        )

        assertFalse(presentation.showPlayer)
        assertNull(presentation.errorMessage)
    }

    @Test
    fun resolveAudiobookCloseFailurePresentation_showsManualCloseFailure() {
        val presentation = resolveAudiobookCloseFailurePresentation(
            closeFailureMessage = "close failed",
            reopenPlayerOnFailure = true
        )

        assertTrue(presentation.showPlayer)
        assertEquals("close failed", presentation.errorMessage)
    }

    private fun session(
        startTimeSeconds: Int,
        currentTimeSeconds: Int,
        libraryItemId: String = "book-1"
    ): AudiobookPlaybackSession {
        return AudiobookPlaybackSession(
            sessionId = "session-1",
            libraryItemId = libraryItemId,
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

    private fun video(playbackPositionSeconds: Int): VideoItem {
        return VideoItem(
            id = "video-1",
            libraryId = "library-1",
            title = "Video One",
            type = "Movie",
            playbackPositionSeconds = playbackPositionSeconds,
            streamUrl = "https://example.test/video.mp4"
        )
    }
}
