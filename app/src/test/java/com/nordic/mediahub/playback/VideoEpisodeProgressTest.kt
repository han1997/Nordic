package com.nordic.mediahub.playback

import com.nordic.mediahub.data.VideoItem
import com.nordic.mediahub.data.VideoServerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class VideoEpisodeProgressTest {
    @Test
    fun switchingEpisodesRetainsLatestLocalProgressAndOtherEpisodes() {
        val current = episode("current").copy(streamUrl = "https://example.test/current", durationSeconds = 900)
        val next = episode("next")
        val result = updateVideoEpisodeProgress(listOf(current.copy(streamUrl = "old"), next), current, 420)
        assertEquals(2, result.size)
        assertEquals(next, result.single { it.id == "next" })
        assertEquals(current.copy(playbackPositionSeconds = 420), result.single { it.id == "current" })
        assertEquals(420_000L, resolveVideoInitialStartPositionMs(result.single { it.id == "current" }))
    }

    @Test
    fun initialZeroPositionNeverRegressesResumeAndMoviesDoNotChangeEpisodeContext() {
        val current = episode("current").copy(playbackPositionSeconds = 120)
        val result = updateVideoEpisodeProgress(emptyList(), current, 0)
        assertEquals(120, result.single().playbackPositionSeconds)
        assertSame(result, updateVideoEpisodeProgress(result, current.copy(type = "Movie"), 300))
    }

    @Test
    fun progressSnapshotNeverReplacesAnEqualRemoteIdFromAnotherSource() {
        val current = episode("same").copy(sourceId = "first", streamUrl = "https://example.test/first")
        val other = current.copy(sourceId = "second", playbackPositionSeconds = 77)
        val result = updateVideoEpisodeProgress(listOf(current, other), current, 120)
        assertEquals(2, result.size)
        assertEquals(other, result.single { it.sourceId == "second" })
        assertEquals(120, result.single { it.sourceId == "first" }.playbackPositionSeconds)
    }

    @Test
    fun webDavPickerUsesLatestPositionAndRestartsCompletedFilesInsteadOfResumingAtEnd() {
        val video = episode("file").copy(type = "Video", sourceType = VideoServerType.WEBDAV,
            sourceId = "dav", streamUrl = "https://example.test/file", playbackPositionSeconds = 10)
        val partial = updateVideoEpisodeProgress(listOf(video), video, 120, 600).single()
        assertEquals(120, partial.playbackPositionSeconds)
        assertEquals(600, partial.durationSeconds)
        assertEquals(false, partial.isPlayed)
        val complete = updateVideoEpisodeProgress(listOf(partial), partial, 600, 600, true).single()
        assertEquals(0, complete.playbackPositionSeconds)
        assertEquals(true, complete.isPlayed)
        assertEquals(0L, resolveVideoInitialStartPositionMs(complete))
    }

    private fun episode(id: String) = VideoItem(id, "library", id, "Episode")
}
