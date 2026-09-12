package com.nordic.mediahub.data

import org.junit.Assert.*
import org.junit.Test

class VideoEpisodeSequenceTest {
    @Test fun sequenceIsolatesSourceBeforeDeduplicatingRemoteIds() {
        val current = episode("one", 1)
        val next = episode("two", 2)
        val otherSource = next.copy(sourceId = "other", title = "Wrong account")
        val otherType = next.copy(sourceType = VideoServerType.WEBDAV)
        assertEquals(listOf(current, next), resolveVideoPlayerEpisodes(current, listOf(otherSource, otherType, next)))
        assertEquals(next, resolveNextVideoEpisode(current, listOf(otherSource, next)))
        assertNull(resolveNextVideoEpisode(current, listOf(otherSource, otherType)))
    }

    @Test fun webDavMissingCurrentFindsImmediateNaturalSuccessorRatherThanLastItem() {
        val current = file("episode1.mp4")
        val next = file("episode2.mp4")
        val last = file("episode10.mp4")
        assertEquals(next, resolveNextWebDavVideo(current, listOf(last, next)))
        assertNull(resolveNextWebDavVideo(last, listOf(current, next)))
        assertEquals(listOf(current, next, last), resolveVideoPlayerEpisodes(current, listOf(last, next)))
    }

    @Test fun webDavIsolatesDirectoryAndSourceAndUsesLiveCurrentItem() {
        val current = file("part1.mp4")
        val next = file("part2.mp4")
        val wrongSource = next.copy(sourceId = "other")
        val wrongDirectory = next.copy(libraryId = "/other/")
        val wrongType = next.copy(sourceType = VideoServerType.EMBY)
        val duplicate = current.copy(title = "stale", streamUrl = null)
        assertEquals(listOf(current, next), resolveVideoPlayerEpisodes(current,
            listOf(wrongSource, wrongDirectory, wrongType, duplicate, next, next)))
    }

    @Test fun nextCrossesLoadedSeasonsWithoutWrappingOrSkippingUnplayableEmbyEpisode() {
        val first = episode("first", 1)
        val unavailable = episode("unavailable", 2).copy(streamUrl = null)
        val nextSeason = episode("next-season", 1).copy(seasonNumber = 2)
        assertEquals(unavailable, resolveNextVideoEpisode(first, listOf(first, nextSeason, unavailable)))
        assertEquals(nextSeason, resolveNextVideoEpisode(unavailable, listOf(first, unavailable, nextSeason)))
        assertNull(resolveNextVideoEpisode(nextSeason, listOf(first, unavailable)))
        assertNull(resolveNextVideoEpisode(first.copy(type = "Movie"), listOf(unavailable)))
    }

    @Test fun equalEpisodeMetadataHasDeterministicIdTieBreak() {
        val first = episode("a", 1).copy(title = "Same")
        val second = first.copy(id = "b")
        assertEquals(second, resolveNextVideoEpisode(first, listOf(second)))
        assertNull(resolveNextVideoEpisode(second, listOf(first)))
    }

    private fun episode(id: String, number: Int) = VideoItem(id, "library", id, "Episode",
        seriesId = "series", seasonNumber = 1, episodeNumber = number,
        streamUrl = "https://example.test/$id", sourceId = "source")

    private fun file(name: String) = VideoItem(name, "/folder/", name, "Video",
        streamUrl = "https://example.test/$name", sourceType = VideoServerType.WEBDAV, sourceId = "dav")
}
