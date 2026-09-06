package com.nordic.mediahub

import android.content.pm.ActivityInfo
import com.nordic.mediahub.data.AudiobookPlaybackSession
import com.nordic.mediahub.data.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityTest {
    @Test
    fun resolveBottomDockPresentation_hidesEverythingForPlayerLayers() {
        assertEquals(
            BottomDockPresentation.Hidden,
            resolveBottomDockPresentation(
                hasPlayerLayer = true,
                fullDockVisible = true
            )
        )

        assertEquals(
            BottomDockPresentation.Hidden,
            resolveBottomDockPresentation(
                hasPlayerLayer = true,
                fullDockVisible = false
            )
        )
    }

    @Test
    fun resolveBottomDockPresentation_showsDockWhenVisibleAndNoPlayerLayer() {
        assertEquals(
            BottomDockPresentation.Dock,
            resolveBottomDockPresentation(
                hasPlayerLayer = false,
                fullDockVisible = true
            )
        )
    }

    @Test
    fun resolveBottomDockPresentation_showsHandleWhenDockHiddenAndNoPlayerLayer() {
        assertEquals(
            BottomDockPresentation.Handle,
            resolveBottomDockPresentation(
                hasPlayerLayer = false,
                fullDockVisible = false
            )
        )
    }

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
    fun resolveAudiobookProgressSyncPositionSeconds_usesLastSyncedWhenStateIsBehind() {
        assertEquals(
            120,
            resolveAudiobookProgressSyncPositionSeconds(
                statePositionSeconds = 0,
                lastSyncedPositionSeconds = 120
            )
        )
    }

    @Test
    fun resolveAudiobookProgressSyncPositionSeconds_usesStateWhenAheadOfLastSynced() {
        assertEquals(
            135,
            resolveAudiobookProgressSyncPositionSeconds(
                statePositionSeconds = 135,
                lastSyncedPositionSeconds = 120
            )
        )
    }

    @Test
    fun resolveAudiobookProgressSyncPositionSeconds_clampsNegativeValuesToZero() {
        assertEquals(
            0,
            resolveAudiobookProgressSyncPositionSeconds(
                statePositionSeconds = -10,
                lastSyncedPositionSeconds = -20
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
    fun resolveVideoProgressSyncBaselineSeconds_prefersLocalPositionOverServerRecord() {
        // The local player position is authoritative: a server record that is
        // ahead (watched further on another device) must not over-report.
        assertEquals(
            135,
            resolveVideoProgressSyncBaselineSeconds(
                statePositionSeconds = 135,
                video = video(playbackPositionSeconds = 90)
            )
        )
        assertEquals(
            40,
            resolveVideoProgressSyncBaselineSeconds(
                statePositionSeconds = 40,
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
    fun resolveMediaHandoffCloseSteps_closesOtherActiveMediaBeforeMusicStarts() {
        assertEquals(
            listOf(MediaPlaybackKind.Audiobook, MediaPlaybackKind.Video),
            resolveMediaHandoffCloseSteps(
                target = MediaPlaybackKind.Music,
                hasMusic = true,
                hasAudiobook = true,
                hasVideo = true
            )
        )
    }

    @Test
    fun resolveMediaHandoffCloseSteps_keepsReusedTargetAndStopsMusicLast() {
        assertEquals(
            listOf(MediaPlaybackKind.Video, MediaPlaybackKind.Music),
            resolveMediaHandoffCloseSteps(
                target = MediaPlaybackKind.Audiobook,
                hasMusic = true,
                hasAudiobook = true,
                hasVideo = true,
                replaceTargetPlayback = false
            )
        )
    }

    @Test
    fun resolveMediaHandoffCloseSteps_closesExistingTargetWhenReplacingIt() {
        assertEquals(
            listOf(MediaPlaybackKind.Audiobook, MediaPlaybackKind.Music),
            resolveMediaHandoffCloseSteps(
                target = MediaPlaybackKind.Audiobook,
                hasMusic = true,
                hasAudiobook = true,
                hasVideo = false,
                replaceTargetPlayback = true
            )
        )
    }

    @Test
    fun resolveMediaHandoffCloseSteps_closesCurrentVideoBeforeRestartingFromBeginning() {
        assertEquals(
            listOf(MediaPlaybackKind.Video),
            resolveMediaHandoffCloseSteps(
                target = MediaPlaybackKind.Video,
                hasMusic = false,
                hasAudiobook = false,
                hasVideo = true,
                replaceTargetPlayback = true
            )
        )
    }

    @Test
    fun runMediaHandoffCloseSteps_runsSequentiallyThenStartsTarget() {
        val events = mutableListOf<String>()

        runMediaHandoffCloseSteps(
            steps = listOf(MediaPlaybackKind.Audiobook, MediaPlaybackKind.Video, MediaPlaybackKind.Music),
            closeStep = { kind, onClosed, _ ->
                events += "close-$kind"
                onClosed()
            },
            onReady = { events += "start-target" },
            onFailed = { events += "failed" }
        )

        assertEquals(
            listOf("close-Audiobook", "close-Video", "close-Music", "start-target"),
            events
        )
    }

    @Test
    fun runMediaHandoffCloseSteps_stopsAtFailureWithoutStartingTarget() {
        val events = mutableListOf<String>()

        runMediaHandoffCloseSteps(
            steps = listOf(MediaPlaybackKind.Audiobook, MediaPlaybackKind.Video, MediaPlaybackKind.Music),
            closeStep = { kind, onClosed, onFailed ->
                events += "close-$kind"
                if (kind == MediaPlaybackKind.Video) onFailed() else onClosed()
            },
            onReady = { events += "start-target" },
            onFailed = { events += "failed" }
        )

        assertEquals(listOf("close-Audiobook", "close-Video", "failed"), events)
    }

    @Test
    fun resolveVideoOrientationRequest_locksPortraitByDefaultOutsideFullscreen() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            resolveVideoOrientationRequest(
                showVideoPlayer = true,
                lockedLandscape = false
            )
        )
    }

    @Test
    fun resolveVideoOrientationRequest_locksLandscapeWhileFullscreen() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            resolveVideoOrientationRequest(
                showVideoPlayer = true,
                lockedLandscape = true
            )
        )
    }

    @Test
    fun resolveVideoOrientationRequest_restoresSystemControlWhenPlayerClosed() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            resolveVideoOrientationRequest(
                showVideoPlayer = false,
                lockedLandscape = true
            )
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            resolveVideoOrientationRequest(
                showVideoPlayer = false,
                lockedLandscape = false
            )
        )
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
