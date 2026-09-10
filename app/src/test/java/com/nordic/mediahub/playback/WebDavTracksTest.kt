package com.nordic.mediahub.playback

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import com.nordic.mediahub.data.*
import org.junit.Assert.*
import org.junit.Test

@androidx.annotation.OptIn(UnstableApi::class)
class WebDavTracksTest {
    @Test fun tracksComeFromMedia3AndSelectionUsesGroupIdentity() {
        val tracks = listOf(
            VideoTrackCandidate("audio-runtime", 0, 0, VideoStreamKind.Audio, formatId = "audio-0", language = "en", selected = true),
            VideoTrackCandidate("subtitle-runtime", 1, 0, VideoStreamKind.Subtitle, formatId = "webdav-subtitle-0", language = "zh", label = "中文外挂", selected = true)
        )
        val video = VideoItem("/a.mkv", "/", "a", "Video", sourceType = VideoServerType.WEBDAV)
        val subtitles = availableVideoStreams(video, VideoStreamKind.Subtitle, tracks)
        assertEquals("subtitle-runtime", subtitles.single().trackGroupId)
        assertEquals("zh", subtitles.single().language)
        assertTrue(subtitles.single().isExternal)
        assertEquals(subtitles.single(), selectedVideoStream(subtitles, tracks))
        assertEquals("audio-runtime", availableVideoStreams(video, VideoStreamKind.Audio, tracks).single().trackGroupId)
    }
    @Test fun webDavSidecarsKeepTheirMimeTypesAndNeverUseEmbyRoutes() {
        val video = VideoItem("/a.mkv", "/", "a", "Video", sourceType = VideoServerType.WEBDAV,
            externalSubtitles = listOf(ExternalVideoSubtitle("https://example.com/dav/a.srt", "application/x-subrip", "a.srt", "zh"),
                ExternalVideoSubtitle("https://example.com/dav/a.ass", "text/x-ssa", "a.ass")))
        val descriptors = externalSubtitleDescriptors(video)
        assertEquals(listOf("application/x-subrip", "text/x-ssa"), descriptors.map { it.mimeType })
        assertTrue(descriptors.none { it.url.contains("/Videos/") })
    }
    @Test fun playbackStateDefaultsDoNotPretendSubtitlesAreEnabled() {
        assertFalse(VideoPlaybackState().subtitlesEnabled)
        assertFalse(VideoPlaybackState().canRestartFromBeginning)
    }
}