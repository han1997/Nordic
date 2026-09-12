package com.nordic.mediahub.ui

import com.nordic.mediahub.data.VideoItem
import com.nordic.mediahub.data.resolveVideoPlayerEpisodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoPlayerEpisodesTest {
    @Test
    fun episodes_isolateSeriesAndLibraryWhileAllowingMissingIdFallback() {
        val current = episode("current", 1, 2)
        val matching = episode("next", 1, 3)
        val fallback = episode("fallback", 1, 4).copy(seriesId = null)
        val wrongSeries = episode("wrong-series", 1, 5).copy(seriesId = "another-series")
        val wrongLibrary = episode("wrong-library", 1, 6).copy(libraryId = "another-library")
        val movie = episode("movie", 1, 7).copy(type = "Movie")
        val result = resolveVideoPlayerEpisodes(current, listOf(movie, wrongSeries, fallback, wrongLibrary, matching))
        assertEquals(listOf("current", "next", "fallback"), result.map { it.id })
    }

    @Test
    fun episodes_useCurrentItemInsteadOfStaleDuplicatesAndSortSpecialsBeforeUnknownSeasons() {
        val current = episode("current", 1, 2).copy(title = "当前数据")
        val result = resolveVideoPlayerEpisodes(current, listOf(
            current.copy(title = "旧数据", streamUrl = null), episode("later", 2, 1),
            episode("special", 0, 1), episode("first", 1, 1), episode("first", 1, 1),
            episode("unknown", null, null)
        ))
        assertEquals(listOf("special", "first", "current", "later", "unknown"), result.map { it.id })
        assertEquals(current, result.single { it.id == current.id })
        assertEquals(listOf(0, 1, 2, null), videoPlayerSeasons(result))
    }

    @Test
    fun episodes_missingIdentityDoesNotMergeUnrelatedUnknownSeries() {
        val current = episode("current", null, null).copy(seriesId = null, seriesName = null)
        val unknown = episode("unknown", 1, 2).copy(seriesId = null, seriesName = null)
        assertEquals(listOf(current), resolveVideoPlayerEpisodes(current, listOf(unknown)))
        assertTrue(resolveVideoPlayerEpisodes(null, listOf(unknown)).isEmpty())
        assertTrue(resolveVideoPlayerEpisodes(current.copy(type = "Movie"), listOf(unknown)).isEmpty())
    }

    @Test
    fun episodes_matchSeriesNamesCaseInsensitivelyWhenCurrentIdIsMissing() {
        val current = episode("current", 1, 1).copy(seriesId = null, seriesName = "THE SHOW")
        val next = episode("next", 1, 2).copy(seriesName = "the show")
        assertEquals(listOf("current", "next"), resolveVideoPlayerEpisodes(current, listOf(next)).map { it.id })
    }

    @Test
    fun selectingCurrentOrUnplayableEpisodeNeverRestartsPlayback() {
        val current = episode("current", 1, 1)
        assertFalse(shouldPlaySelectedVideoEpisode(current, current))
        assertFalse(shouldPlaySelectedVideoEpisode(current, episode("next", 1, 2).copy(streamUrl = " ")))
        assertFalse(shouldPlaySelectedVideoEpisode(current, episode("movie", 1, 2).copy(type = "Movie")))
        assertTrue(shouldPlaySelectedVideoEpisode(current, episode("next", 1, 2)))
    }

    @Test
    fun pickerLocatesCurrentEpisodeAndUsesFirstRowWhenSwitchingSeasons() {
        val episodes = listOf(episode("first", 1, 1), episode("current", 1, 2))
        assertEquals(1, videoPlayerEpisodeStartIndex(episodes, "current"))
        assertEquals(0, videoPlayerEpisodeStartIndex(episodes, "other-season"))
        assertEquals(0, videoPlayerEpisodeStartIndex(emptyList(), "current"))
        assertEquals("特别篇", videoPlayerSeasonLabel(0))
        assertEquals("未分季", videoPlayerSeasonLabel(null))
        assertEquals("第 2 季", videoPlayerSeasonLabel(2))
    }

    private fun episode(id: String, season: Int?, number: Int?) = VideoItem(
        id = id, libraryId = "library", title = id, type = "Episode",
        seriesId = "series", seriesName = "The show", seasonNumber = season,
        episodeNumber = number, streamUrl = "https://example.test/$id"
    )
}
