package com.nordic.mediahub.playback

import com.nordic.mediahub.data.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoPlaybackEngineTest {
    @Test
    fun shouldReplaceCurrentVideoItem_returnsFalseForSameIdAndSameStreamUrl() {
        val currentVideo = video(
            id = "video-1",
            streamUrl = "https://emby.example/Videos/video-1/stream?api_key=old"
        )
        val requestedVideo = video(
            id = "video-1",
            streamUrl = "https://emby.example/Videos/video-1/stream?api_key=old"
        )

        assertFalse(shouldReplaceCurrentVideoItem(currentVideo, requestedVideo))
    }

    @Test
    fun shouldReplaceCurrentVideoItem_returnsTrueForSameIdAndDifferentStreamUrl() {
        val currentVideo = video(
            id = "video-1",
            streamUrl = "https://emby.example/Videos/video-1/stream?api_key=old"
        )
        val requestedVideo = video(
            id = "video-1",
            streamUrl = "https://emby.example/Videos/video-1/stream?api_key=new"
        )

        assertTrue(shouldReplaceCurrentVideoItem(currentVideo, requestedVideo))
    }

    @Test
    fun shouldReplaceCurrentVideoItem_returnsTrueForDifferentId() {
        val currentVideo = video(
            id = "video-1",
            streamUrl = "https://emby.example/Videos/video-1/stream?api_key=old"
        )
        val requestedVideo = video(
            id = "video-2",
            streamUrl = "https://emby.example/Videos/video-2/stream?api_key=old"
        )

        assertTrue(shouldReplaceCurrentVideoItem(currentVideo, requestedVideo))
    }

    @Test
    fun shouldReplaceCurrentVideoItem_returnsTrueWhenNoCurrentVideoExists() {
        val requestedVideo = video(
            id = "video-1",
            streamUrl = "https://emby.example/Videos/video-1/stream?api_key=old"
        )

        assertTrue(shouldReplaceCurrentVideoItem(null, requestedVideo))
    }

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
    fun resolveVideoInitialStartPositionMs_startsFromBeginningWhenResumeIsComplete() {
        assertEquals(
            0L,
            resolveVideoInitialStartPositionMs(
                video(playbackPositionSeconds = 120, durationSeconds = 120)
            )
        )
        assertEquals(
            0L,
            resolveVideoInitialStartPositionMs(
                video(playbackPositionSeconds = 300, durationSeconds = 120)
            )
        )
    }

    @Test
    fun resolveVideoInitialStartPositionMs_keepsResumePositionWhenDurationUnknown() {
        assertEquals(
            300_000L,
            resolveVideoInitialStartPositionMs(
                video(playbackPositionSeconds = 300, durationSeconds = 0)
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

    @Test
    fun resolveVideoRelativeSeekPositionSeconds_movesForwardWhenDurationUnknown() {
        assertEquals(
            70,
            resolveVideoRelativeSeekPositionSeconds(
                positionSeconds = 40,
                durationSeconds = 0,
                deltaSeconds = 30
            )
        )
    }

    @Test
    fun resolveVideoRelativeSeekPositionSeconds_clampsAtStartWhenDurationUnknown() {
        assertEquals(
            0,
            resolveVideoRelativeSeekPositionSeconds(
                positionSeconds = 5,
                durationSeconds = 0,
                deltaSeconds = -10
            )
        )
    }

    @Test
    fun resolveNextAspectRatioMode_cyclesThroughDisplayModes() {
        assertEquals(AspectRatioMode.CROP, resolveNextAspectRatioMode(AspectRatioMode.FIT))
        assertEquals(AspectRatioMode.FILL, resolveNextAspectRatioMode(AspectRatioMode.CROP))
        assertEquals(AspectRatioMode.FIT, resolveNextAspectRatioMode(AspectRatioMode.FILL))
    }

    @Test
    fun resolveVideoAspectRatio_appliesPixelRatio() {
        assertEquals(
            2f,
            resolveVideoAspectRatio(width = 720, height = 540, pixelWidthHeightRatio = 1.5f),
            0.001f
        )
    }

    @Test
    fun resolveVideoAspectRatio_fallsBackWhenSizeIsInvalid() {
        assertEquals(
            16f / 9f,
            resolveVideoAspectRatio(width = 0, height = 540, pixelWidthHeightRatio = 1f),
            0.001f
        )
        assertEquals(
            16f / 9f,
            resolveVideoAspectRatio(width = 720, height = 0, pixelWidthHeightRatio = 1f),
            0.001f
        )
    }

    @Test
    fun resolveVideoAspectRatio_ignoresInvalidPixelRatio() {
        assertEquals(
            16f / 9f,
            resolveVideoAspectRatio(width = 1920, height = 1080, pixelWidthHeightRatio = 0f),
            0.001f
        )
    }

    private fun video(
        id: String = "video-1",
        streamUrl: String? = "https://example.test/video.mp4",
        playbackPositionSeconds: Int = 0,
        durationSeconds: Int = 0,
        isPlayed: Boolean = false
    ): VideoItem {
        return VideoItem(
            id = id,
            libraryId = "library-1",
            title = "Video",
            type = "Movie",
            durationSeconds = durationSeconds,
            playbackPositionSeconds = playbackPositionSeconds,
            isPlayed = isPlayed,
            streamUrl = streamUrl
        )
    }
}
