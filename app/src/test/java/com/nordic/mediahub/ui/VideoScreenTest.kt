package com.nordic.mediahub.ui

import com.nordic.mediahub.data.VideoItem
import org.junit.Assert.assertEquals
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

    private fun video(
        id: String,
        title: String,
        playbackPositionSeconds: Int,
        lastPlayedDate: String? = null,
        isPlayed: Boolean = false,
        durationSeconds: Int = 0
    ): VideoItem {
        return VideoItem(
            id = id,
            libraryId = "library-1",
            title = title,
            type = "Movie",
            durationSeconds = durationSeconds,
            playbackPositionSeconds = playbackPositionSeconds,
            lastPlayedDate = lastPlayedDate,
            isPlayed = isPlayed
        )
    }
}
