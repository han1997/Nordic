package com.nordic.mediahub.ui

import com.nordic.mediahub.data.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoScreenTest {
    @Test
    fun continueWatchingShelf_ordersByLastPlayedDateBeforeResumePositionFallback() {
        val oldNearFinished = video(
            id = "old-near-finished",
            title = "Old Near Finished",
            playbackPositionSeconds = 5_000,
            lastPlayedDate = "2026-06-20T10:00:00.0000000Z"
        )
        val recentStarted = video(
            id = "recent-started",
            title = "Recent Started",
            playbackPositionSeconds = 60,
            lastPlayedDate = "2026-06-28T10:00:00.0000000Z"
        )
        val undatedFarther = video(
            id = "undated-farther",
            title = "Undated Farther",
            playbackPositionSeconds = 4_000
        )
        val undatedEarlier = video(
            id = "undated-earlier",
            title = "Undated Earlier",
            playbackPositionSeconds = 600
        )
        val alreadyPlayed = video(
            id = "already-played",
            title = "Already Played",
            playbackPositionSeconds = 90,
            lastPlayedDate = "2026-06-29T10:00:00.0000000Z",
            isPlayed = true
        )
        val notStarted = video(
            id = "not-started",
            title = "Not Started",
            playbackPositionSeconds = 0,
            lastPlayedDate = "2026-06-29T10:00:00.0000000Z"
        )

        val shelf = continueWatchingShelf(
            videos = listOf(
                oldNearFinished,
                undatedEarlier,
                alreadyPlayed,
                recentStarted,
                undatedFarther,
                notStarted
            ),
            limit = 3
        )

        assertEquals(
            listOf("recent-started", "old-near-finished", "undated-farther"),
            shelf.map { it.id }
        )
    }

    @Test
    fun continueWatchingShelf_excludesResumePositionsAtOrBeyondKnownDuration() {
        val resumable = video(
            id = "resumable",
            title = "Resumable",
            playbackPositionSeconds = 60,
            durationSeconds = 120
        )
        val atDuration = video(
            id = "at-duration",
            title = "At Duration",
            playbackPositionSeconds = 120,
            durationSeconds = 120
        )
        val beyondDuration = video(
            id = "beyond-duration",
            title = "Beyond Duration",
            playbackPositionSeconds = 150,
            durationSeconds = 120
        )

        val shelf = continueWatchingShelf(
            videos = listOf(atDuration, beyondDuration, resumable)
        )

        assertEquals(listOf("resumable"), shelf.map { it.id })
    }

    @Test
    fun continueWatchingShelf_keepsResumePositionsWhenDurationUnknown() {
        val unknownDuration = video(
            id = "unknown-duration",
            title = "Unknown Duration",
            playbackPositionSeconds = 150,
            durationSeconds = 0
        )

        val shelf = continueWatchingShelf(listOf(unknownDuration))

        assertEquals(listOf("unknown-duration"), shelf.map { it.id })
    }

    @Test
    fun resolveVideoSelectionAfterCatalogRefresh_keepsRefreshedItemWhenStillPresent() {
        val selected = video(
            id = "movie-1",
            title = "Old Title",
            playbackPositionSeconds = 30
        )
        val refreshed = video(
            id = "movie-1",
            title = "Updated Title",
            playbackPositionSeconds = 90
        )

        val resolved = resolveVideoSelectionAfterCatalogRefresh(
            selectedVideo = selected,
            selectedLibraryId = "library-1",
            videos = listOf(refreshed)
        )

        assertEquals(refreshed, resolved)
    }

    @Test
    fun resolveVideoSelectionAfterCatalogRefresh_clearsSelectionWhenLibraryChanges() {
        val selected = video(
            id = "movie-1",
            title = "Movie One",
            playbackPositionSeconds = 30,
            libraryId = "library-1"
        )
        val refreshed = video(
            id = "movie-1",
            title = "Movie One",
            playbackPositionSeconds = 30,
            libraryId = "library-2"
        )

        val resolved = resolveVideoSelectionAfterCatalogRefresh(
            selectedVideo = selected,
            selectedLibraryId = "library-2",
            videos = listOf(refreshed)
        )

        assertNull(resolved)
    }

    @Test
    fun resolveVideoSelectionAfterCatalogRefresh_clearsSelectionWhenItemDisappears() {
        val selected = video(
            id = "movie-1",
            title = "Movie One",
            playbackPositionSeconds = 30
        )
        val refreshed = video(
            id = "movie-2",
            title = "Movie Two",
            playbackPositionSeconds = 0
        )

        val resolved = resolveVideoSelectionAfterCatalogRefresh(
            selectedVideo = selected,
            selectedLibraryId = "library-1",
            videos = listOf(refreshed)
        )

        assertNull(resolved)
    }

    @Test
    fun relatedEpisodesFor_usesSeriesNameFallbackOnlyWhenSeriesIdIsMissing() {
        val series = video(
            id = "series-1",
            title = "Nordic Show",
            type = "Series"
        )
        val matchingId = video(
            id = "matching-id",
            title = "Matching Id",
            type = "Episode",
            seriesId = "series-1",
            seriesName = "Different Name"
        )
        val missingIdFallback = video(
            id = "missing-id-fallback",
            title = "Missing Id Fallback",
            type = "Episode",
            seriesName = "Nordic Show"
        )
        val blankIdFallback = video(
            id = "blank-id-fallback",
            title = "Blank Id Fallback",
            type = "Episode",
            seriesId = "",
            seriesName = "Nordic Show"
        )
        val differentIdSameName = video(
            id = "different-id-same-name",
            title = "Different Id Same Name",
            type = "Episode",
            seriesId = "series-2",
            seriesName = "Nordic Show"
        )
        val nonEpisode = video(
            id = "movie-same-name",
            title = "Nordic Show",
            type = "Movie",
            seriesName = "Nordic Show"
        )

        val related = listOf(
            differentIdSameName,
            nonEpisode,
            missingIdFallback,
            matchingId,
            blankIdFallback
        ).relatedEpisodesFor(series)

        assertEquals(
            listOf("blank-id-fallback", "matching-id", "missing-id-fallback"),
            related.map { it.id }
        )
    }

    @Test
    fun relatedEpisodesFor_sortsBySeasonEpisodeAndTitle() {
        val series = video(
            id = "series-1",
            title = "Nordic Show",
            type = "Series"
        )
        val episodes = listOf(
            episode(id = "s2e1", title = "D", seasonNumber = 2, episodeNumber = 1),
            episode(id = "s1e2", title = "B", seasonNumber = 1, episodeNumber = 2),
            episode(id = "unknown", title = "Z"),
            episode(id = "s1e1-c", title = "C", seasonNumber = 1, episodeNumber = 1),
            episode(id = "s1e1-a", title = "A", seasonNumber = 1, episodeNumber = 1)
        )

        val related = episodes.relatedEpisodesFor(series)

        assertEquals(
            listOf("s1e1-a", "s1e1-c", "s1e2", "s2e1", "unknown"),
            related.map { it.id }
        )
    }

    private fun episode(
        id: String,
        title: String,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null
    ): VideoItem {
        return video(
            id = id,
            title = title,
            type = "Episode",
            seriesId = "series-1",
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber
        )
    }

    private fun video(
        id: String,
        title: String,
        playbackPositionSeconds: Int = 0,
        lastPlayedDate: String? = null,
        isPlayed: Boolean = false,
        durationSeconds: Int = 0,
        libraryId: String = "library-1",
        type: String = "Movie",
        seriesId: String? = null,
        seriesName: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null
    ): VideoItem {
        return VideoItem(
            id = id,
            libraryId = libraryId,
            title = title,
            type = type,
            durationSeconds = durationSeconds,
            playbackPositionSeconds = playbackPositionSeconds,
            lastPlayedDate = lastPlayedDate,
            isPlayed = isPlayed,
            seriesId = seriesId,
            seriesName = seriesName,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber
        )
    }
}
