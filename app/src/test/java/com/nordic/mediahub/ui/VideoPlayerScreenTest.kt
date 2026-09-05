package com.nordic.mediahub.ui

import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import com.nordic.mediahub.data.VideoItem
import com.nordic.mediahub.playback.AspectRatioMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@androidx.annotation.OptIn(UnstableApi::class)
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

    @Test
    fun formatVideoPlayerRemainingLabel_formatsRemainingTime() {
        assertEquals("-1:40", formatVideoPlayerRemainingLabel(durationSeconds = 120, positionSeconds = 20))
    }

    @Test
    fun formatVideoPlayerRemainingLabel_usesUnknownLabelWhenDurationUnknown() {
        assertEquals("--:--", formatVideoPlayerRemainingLabel(durationSeconds = 0, positionSeconds = 20))
        assertEquals("--:--", formatVideoPlayerRemainingLabel(durationSeconds = -1, positionSeconds = 20))
    }

    @Test
    fun formatVideoPlayerRemainingLabel_clampsPositionToDuration() {
        assertEquals("-0:00", formatVideoPlayerRemainingLabel(durationSeconds = 120, positionSeconds = 500))
        assertEquals("-2:00", formatVideoPlayerRemainingLabel(durationSeconds = 120, positionSeconds = 0))
    }

    @Test
    fun resolveSeekFeedbackLabel_formatsForwardAndBackwardDeltas() {
        assertEquals("+0:30", resolveSeekFeedbackLabel(30))
        assertEquals("-0:10", resolveSeekFeedbackLabel(-10))
        assertEquals("+0:00", resolveSeekFeedbackLabel(0))
    }

    @Test
    fun videoPlayerStatusText_prioritizesErrors() {
        assertEquals(
            "播放异常",
            videoPlayerStatusText(
                hasVideo = true,
                isBuffering = true,
                errorMessage = "Playback failed"
            )
        )
    }

    @Test
    fun videoPlayerStatusText_reportsBufferingWhenVideoIsLoading() {
        assertEquals(
            "缓冲中",
            videoPlayerStatusText(
                hasVideo = true,
                isBuffering = true,
                errorMessage = null
            )
        )
    }

    @Test
    fun videoPlayerStatusText_reportsIdleWhenNoVideoIsLoaded() {
        assertEquals(
            "暂无视频",
            videoPlayerStatusText(
                hasVideo = false,
                isBuffering = false,
                errorMessage = null
            )
        )
    }

    @Test
    fun videoPlayerStatusText_hidesStatusForReadyVideo() {
        assertNull(
            videoPlayerStatusText(
                hasVideo = true,
                isBuffering = false,
                errorMessage = null
            )
        )
    }

    @Test
    fun resolveVideoStatusTone_prioritizesErrors() {
        assertEquals(
            VideoStatusTone.Error,
            resolveVideoStatusTone(
                hasVideo = true,
                isBuffering = true,
                errorMessage = "Playback failed"
            )
        )
    }

    @Test
    fun resolveVideoStatusTone_reportsBufferingWhenVideoIsLoading() {
        assertEquals(
            VideoStatusTone.Buffering,
            resolveVideoStatusTone(
                hasVideo = true,
                isBuffering = true,
                errorMessage = null
            )
        )
    }

    @Test
    fun resolveVideoStatusTone_reportsIdleWhenNoVideoIsLoaded() {
        assertEquals(
            VideoStatusTone.Idle,
            resolveVideoStatusTone(
                hasVideo = false,
                isBuffering = false,
                errorMessage = null
            )
        )
    }

    @Test
    fun resolveVideoStatusTone_hidesToneForReadyVideo() {
        assertNull(
            resolveVideoStatusTone(
                hasVideo = true,
                isBuffering = false,
                errorMessage = null
            )
        )
    }

    @Test
    fun resolveVideoPlayerResizeMode_mapsAspectRatioModesToMedia3ResizeModes() {
        assertEquals(
            AspectRatioFrameLayout.RESIZE_MODE_FIT,
            resolveVideoPlayerResizeMode(AspectRatioMode.FIT)
        )
        assertEquals(
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
            resolveVideoPlayerResizeMode(AspectRatioMode.CROP)
        )
        assertEquals(
            AspectRatioFrameLayout.RESIZE_MODE_FILL,
            resolveVideoPlayerResizeMode(AspectRatioMode.FILL)
        )
    }

    @Test
    fun videoPlayerInfoChips_usesExistingVisibleMetadata() {
        val chips = videoPlayerInfoChips(
            video(
                type = "Episode",
                year = 2024,
                durationSeconds = 3661
            )
        )

        assertEquals(listOf("Episode", "2024", "61:01"), chips)
    }

    @Test
    fun videoPlayerInfoRows_omitsMissingMetadata() {
        val rows = videoPlayerInfoRows(
            video = video(
                type = "",
                durationSeconds = 0,
                communityRating = null,
                seriesName = " "
            ),
            positionSeconds = 0,
            durationSeconds = 0
        )

        assertTrue(rows.isEmpty())
    }

    @Test
    fun videoPlayerInfoRows_formatsEpisodeRatingDurationAndProgress() {
        val rows = videoPlayerInfoRows(
            video = video(
                seriesName = "Nordic Show",
                seasonNumber = 2,
                episodeNumber = 5,
                communityRating = 8.25f,
                durationSeconds = 120
            ),
            positionSeconds = 40,
            durationSeconds = 180
        )

        assertEquals(
            listOf(
                VideoPlayerInfoLine("剧集", "Nordic Show"),
                VideoPlayerInfoLine("分集", "S2E5"),
                VideoPlayerInfoLine("评分", "8.3"),
                VideoPlayerInfoLine("时长", "3:00"),
                VideoPlayerInfoLine("进度", "0:40 / 3:00")
            ),
            rows
        )
    }

    @Test
    fun videoPlayerProgressLabel_formatsUnknownDurationProgress() {
        assertEquals(
            "1:30",
            videoPlayerProgressLabel(positionSeconds = 90, durationSeconds = 0)
        )
        assertNull(videoPlayerProgressLabel(positionSeconds = 0, durationSeconds = 120))
    }

    private fun video(
        type: String = "Movie",
        overview: String = "",
        year: Int? = null,
        durationSeconds: Int = 0,
        playbackPositionSeconds: Int = 0,
        isPlayed: Boolean = false,
        communityRating: Float? = null,
        seriesName: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null
    ): VideoItem {
        return VideoItem(
            id = "video-1",
            libraryId = "library-1",
            title = "Video",
            type = type,
            overview = overview,
            year = year,
            durationSeconds = durationSeconds,
            playbackPositionSeconds = playbackPositionSeconds,
            isPlayed = isPlayed,
            communityRating = communityRating,
            seriesName = seriesName,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            streamUrl = "https://example.test/video.mp4"
        )
    }
}
