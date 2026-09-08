package com.nordic.mediahub.playback

import com.nordic.mediahub.data.VideoItem
import com.nordic.mediahub.data.VideoStreamInfo
import com.nordic.mediahub.data.VideoStreamKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoTracksTest {
    @Test
    fun resolveExternalSubtitleUrl_buildsVttDeliveryUrl() {
        val url = resolveExternalSubtitleUrl(
            baseUrl = "https://emby.example.com",
            itemId = "item-1",
            streamIndex = 3
        )
        assertEquals("https://emby.example.com/Videos/item-1/3/Subtitles?format=vtt", url)
    }

    @Test
    fun resolveExternalSubtitleUrl_rejectsInvalidInputs() {
        assertNull(resolveExternalSubtitleUrl("not a url", "item-1", 0))
        assertNull(resolveExternalSubtitleUrl("https://emby.example.com", "  ", 0))
        assertNull(resolveExternalSubtitleUrl("https://emby.example.com", "item-1", -1))
    }

    @Test
    fun externalSubtitleDescriptors_onlyIncludesExternalSubtitleStreams() {
        val video = VideoItem(
            id = "item-1",
            libraryId = "lib",
            title = "Movie",
            type = "Movie",
            streamUrl = "https://emby.example.com/Videos/item-1/stream?Static=true",
            mediaStreams = listOf(
                VideoStreamInfo(2, VideoStreamKind.Subtitle, "srt", "chi", "简体中文", isExternal = true),
                VideoStreamInfo(3, VideoStreamKind.Subtitle, "subrip", "eng", "English", isExternal = true),
                VideoStreamInfo(4, VideoStreamKind.Subtitle, "pgs", "jpn", "日本語", isExternal = false),
                VideoStreamInfo(5, VideoStreamKind.Audio, "aac", "chi", "国语", isExternal = false)
            )
        )

        val descriptors = externalSubtitleDescriptors(video)

        assertEquals(2, descriptors.size)
        assertEquals("emby-subtitle-2", descriptors[0].id)
        assertEquals("简体中文", descriptors[0].label)
        assertEquals("chi", descriptors[0].language)
        assertTrue(descriptors[0].url.endsWith("/Videos/item-1/2/Subtitles?format=vtt"))
        assertTrue(descriptors[1].url.endsWith("/Videos/item-1/3/Subtitles?format=vtt"))
    }

    @Test
    fun externalSubtitleDescriptors_emptyWithoutStreamUrlOrExternalSubs() {
        val noUrl = VideoItem(id = "i", libraryId = "l", title = "t", type = "Movie", streamUrl = null)
        assertTrue(externalSubtitleDescriptors(noUrl).isEmpty())

        val noExternal = VideoItem(
            id = "i", libraryId = "l", title = "t", type = "Movie",
            streamUrl = "https://emby.example.com/v",
            mediaStreams = listOf(
                VideoStreamInfo(2, VideoStreamKind.Subtitle, "pgs", "jpn", "日本語", isExternal = false)
            )
        )
        assertTrue(externalSubtitleDescriptors(noExternal).isEmpty())
    }
}
