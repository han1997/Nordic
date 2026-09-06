package com.nordic.mediahub.playback

import com.nordic.mediahub.data.VideoItem
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

    private fun episode(id: String) = VideoItem(id, "library", id, "Episode")
}
