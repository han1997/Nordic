package com.nordic.mediahub.playback

import com.nordic.mediahub.data.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoPlaybackEngineTest {
    @Test
    fun resolveVideoInitialStartPositionMs_usesResumePositionForUnplayedVideo() {
        assertEquals(
            42_000L,
            resolveVideoInitialStartPositionMs(
                video(playbackPositionSeconds = 42, durationSeconds = 120)
            )
        )
    }

    @Test
    fun resolveVideoInitialStartPositionMs_startsPlayedVideoFromBeginning() {
        assertEquals(
            0L,
            resolveVideoInitialStartPositionMs(
                video(playbackPositionSeconds = 42, durationSeconds = 120, isPlayed = true)
            )
        )
    }

    @Test
    fun resolveVideoInitialStartPositionMs_clampsResumePositionToDuration() {
        assertEquals(
            120_000L,
            resolveVideoInitialStartPositionMs(
                video(playbackPositionSeconds = 300, durationSeconds = 120)
            )
        )
    }

    @Test
    fun resolveVideoRelativeSeekPositionSeconds_movesWithinBounds() {
        assertEquals(
            70,
            resolveVideoRelativeSeekPositionSeconds(
                positionSeconds = 40,
                durationSeconds = 120,
                deltaSeconds = 30
            )
        )
    }

    @Test
    fun resolveVideoRelativeSeekPositionSeconds_clampsAtStart() {
        assertEquals(
            0,
            resolveVideoRelativeSeekPositionSeconds(
                positionSeconds = 5,
                durationSeconds = 120,
                deltaSeconds = -10
            )
        )
    }

    @Test
    fun resolveVideoRelativeSeekPositionSeconds_clampsAtEnd() {
        assertEquals(
            120,
            resolveVideoRelativeSeekPositionSeconds(
                positionSeconds = 100,
                durationSeconds = 120,
                deltaSeconds = 30
            )
        )
    }

    private fun video(
        playbackPositionSeconds: Int,
        durationSeconds: Int,
        isPlayed: Boolean = false
    ): VideoItem {
        return VideoItem(
            id = "video-1",
            libraryId = "library-1",
            title = "Video",
            type = "Movie",
            durationSeconds = durationSeconds,
            playbackPositionSeconds = playbackPositionSeconds,
            isPlayed = isPlayed,
            streamUrl = "https://example.test/video.mp4"
        )
    }
}
