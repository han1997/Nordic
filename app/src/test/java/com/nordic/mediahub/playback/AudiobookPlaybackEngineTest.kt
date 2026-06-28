package com.nordic.mediahub.playback

import com.nordic.mediahub.data.AudiobookAudioTrack
import com.nordic.mediahub.data.AudiobookChapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun resolveAudiobookAbsolutePositionSeconds_clampsKnownTrackPositionToTrackDuration() {
        val tracks = listOf(
            track(index = 0, startOffsetSeconds = 0),
            track(index = 1, startOffsetSeconds = 120)
        )

        assertEquals(
            240,
            resolveAudiobookAbsolutePositionSeconds(
                tracks = tracks,
                currentIndex = 1,
                currentPositionMs = 180_000L
            )
        )
    }

    @Test
    fun resolveAudiobookTrackSeekPosition_mapsAbsolutePositionToTrackOffset() {
        val tracks = listOf(
            track(index = 0, startOffsetSeconds = 0),
            track(index = 1, startOffsetSeconds = 120)
        )

        assertEquals(
            AudiobookTrackSeekPosition(mediaItemIndex = 1, localOffsetSeconds = 45),
            resolveAudiobookTrackSeekPosition(
                tracks = tracks,
                absolutePositionSeconds = 165
            )
        )
    }

    @Test
    fun resolveAudiobookTrackSeekPosition_clampsNegativePositionToFirstTrackStart() {
        val tracks = listOf(
            track(index = 0, startOffsetSeconds = 0),
            track(index = 1, startOffsetSeconds = 120)
        )

        assertEquals(
            AudiobookTrackSeekPosition(mediaItemIndex = 0, localOffsetSeconds = 0),
            resolveAudiobookTrackSeekPosition(
                tracks = tracks,
                absolutePositionSeconds = -30
            )
        )
    }

    @Test
    fun resolveAudiobookTrackSeekPosition_clampsBeyondFinalTrackDuration() {
        val tracks = listOf(
            track(index = 0, startOffsetSeconds = 0),
            track(index = 1, startOffsetSeconds = 120)
        )

        assertEquals(
            AudiobookTrackSeekPosition(mediaItemIndex = 1, localOffsetSeconds = 120),
            resolveAudiobookTrackSeekPosition(
                tracks = tracks,
                absolutePositionSeconds = 500
            )
        )
    }

    @Test
    fun resolveAudiobookTrackSeekPosition_returnsNullForEmptyTracks() {
        assertNull(
            resolveAudiobookTrackSeekPosition(
                tracks = emptyList(),
                absolutePositionSeconds = 42
            )
        )
    }

    @Test
    fun resolvePreviousAudiobookChapterStartSeconds_restartsCurrentChapterAfterThreshold() {
        assertEquals(
            120,
            resolvePreviousAudiobookChapterStartSeconds(
                chapters = chapters(),
                positionSeconds = 140,
                restartThresholdSeconds = 5
            )
        )
    }

    @Test
    fun resolvePreviousAudiobookChapterStartSeconds_movesToPreviousChapterNearStart() {
        assertEquals(
            0,
            resolvePreviousAudiobookChapterStartSeconds(
                chapters = chapters(),
                positionSeconds = 124,
                restartThresholdSeconds = 5
            )
        )
    }

    @Test
    fun resolvePreviousAudiobookChapterStartSeconds_returnsNullWithoutPreviousChapterNearStart() {
        assertNull(
            resolvePreviousAudiobookChapterStartSeconds(
                chapters = chapters(),
                positionSeconds = 3,
                restartThresholdSeconds = 5
            )
        )
    }

    @Test
    fun resolveNextAudiobookChapterStartSeconds_movesToNextChapter() {
        assertEquals(
            240,
            resolveNextAudiobookChapterStartSeconds(
                chapters = chapters(),
                positionSeconds = 130
            )
        )
    }

    @Test
    fun resolveNextAudiobookChapterStartSeconds_returnsNullAtLastChapter() {
        assertNull(
            resolveNextAudiobookChapterStartSeconds(
                chapters = chapters(),
                positionSeconds = 260
            )
        )
    }

    @Test
    fun resolveAudiobookRelativeSeekPositionSeconds_movesWithinBounds() {
        assertEquals(
            70,
            resolveAudiobookRelativeSeekPositionSeconds(
                positionSeconds = 40,
                durationSeconds = 120,
                deltaSeconds = 30
            )
        )
    }

    @Test
    fun resolveAudiobookRelativeSeekPositionSeconds_clampsAtStart() {
        assertEquals(
            0,
            resolveAudiobookRelativeSeekPositionSeconds(
                positionSeconds = 10,
                durationSeconds = 120,
                deltaSeconds = -30
            )
        )
    }

    @Test
    fun resolveAudiobookRelativeSeekPositionSeconds_clampsAtEnd() {
        assertEquals(
            120,
            resolveAudiobookRelativeSeekPositionSeconds(
                positionSeconds = 100,
                durationSeconds = 120,
                deltaSeconds = 30
            )
        )
    }

    @Test
    fun resolveNextAudiobookPlaybackSpeed_cyclesKnownSpeeds() {
        assertEquals(1.25f, resolveNextAudiobookPlaybackSpeed(1f), 0.001f)
        assertEquals(0.75f, resolveNextAudiobookPlaybackSpeed(2f), 0.001f)
    }

    @Test
    fun resolveNextAudiobookPlaybackSpeed_movesUnknownSpeedToNextHigherStep() {
        assertEquals(1.25f, resolveNextAudiobookPlaybackSpeed(1.1f), 0.001f)
        assertEquals(0.75f, resolveNextAudiobookPlaybackSpeed(2.2f), 0.001f)
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

    private fun chapters(): List<AudiobookChapter> {
        return listOf(
            chapter(1, startSeconds = 0, endSeconds = 119),
            chapter(2, startSeconds = 120, endSeconds = 239),
            chapter(3, startSeconds = 240, endSeconds = 360)
        )
    }

    private fun chapter(id: Int, startSeconds: Int, endSeconds: Int): AudiobookChapter {
        return AudiobookChapter(
            id = id,
            title = "Chapter $id",
            startSeconds = startSeconds,
            endSeconds = endSeconds
        )
    }
}
