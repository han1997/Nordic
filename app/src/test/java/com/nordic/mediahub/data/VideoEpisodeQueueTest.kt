package com.nordic.mediahub.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoEpisodeQueueTest {
    @Test
    fun resolveNextVideoEpisodeIndex_returnsNextIndexOnlyWhenAvailable() {
        assertEquals(1, resolveNextVideoEpisodeIndex(currentIndex = 0, itemCount = 3))
        assertEquals(2, resolveNextVideoEpisodeIndex(currentIndex = 1, itemCount = 3))

        assertNull(resolveNextVideoEpisodeIndex(currentIndex = 2, itemCount = 3))
        assertNull(resolveNextVideoEpisodeIndex(currentIndex = -1, itemCount = 3))
        assertNull(resolveNextVideoEpisodeIndex(currentIndex = 0, itemCount = 1))
        assertNull(resolveNextVideoEpisodeIndex(currentIndex = 0, itemCount = 0))
    }

    @Test
    fun resolvePreviousVideoEpisodeIndex_returnsPreviousIndexOnlyWhenAvailable() {
        assertEquals(0, resolvePreviousVideoEpisodeIndex(currentIndex = 1, itemCount = 3))
        assertEquals(1, resolvePreviousVideoEpisodeIndex(currentIndex = 2, itemCount = 3))

        assertNull(resolvePreviousVideoEpisodeIndex(currentIndex = 0, itemCount = 3))
        assertNull(resolvePreviousVideoEpisodeIndex(currentIndex = -1, itemCount = 3))
        assertNull(resolvePreviousVideoEpisodeIndex(currentIndex = 0, itemCount = 1))
        assertNull(resolvePreviousVideoEpisodeIndex(currentIndex = 0, itemCount = 0))
    }

    @Test
    fun videoEpisodeQueue_advancesThroughEpisodes() {
        val queue = VideoEpisodeQueue(
            libraryId = "series-1",
            episodes = listOf(
                episode("ep-1", 1),
                episode("ep-2", 2),
                episode("ep-3", 3)
            ),
            currentIndex = 0
        )

        assertTrue(queue.hasNext)
        assertEquals("ep-2", queue.nextEpisode()?.id)

        val nextQueue = queue.advanceToNext()
        assertEquals(1, nextQueue?.currentIndex)
        assertEquals("ep-3", nextQueue?.nextEpisode()?.id)

        val lastQueue = nextQueue?.advanceToNext()
        assertEquals(2, lastQueue?.currentIndex)
        assertFalse(lastQueue?.hasNext ?: true)
        assertNull(lastQueue?.advanceToNext())
    }

    @Test
    fun videoEpisodeQueue_navigatesBackward() {
        val queue = VideoEpisodeQueue(
            libraryId = "series-1",
            episodes = listOf(
                episode("ep-1", 1),
                episode("ep-2", 2),
                episode("ep-3", 3)
            ),
            currentIndex = 2
        )

        assertTrue(queue.hasPrevious)
        assertEquals("ep-2", queue.previousEpisode()?.id)

        val prevQueue = queue.goToPrevious()
        assertEquals(1, prevQueue?.currentIndex)
        assertEquals("ep-1", prevQueue?.previousEpisode()?.id)

        val firstQueue = prevQueue?.goToPrevious()
        assertEquals(0, firstQueue?.currentIndex)
        assertFalse(firstQueue?.hasPrevious ?: true)
        assertNull(firstQueue?.goToPrevious())
    }

    @Test
    fun toPlaybackVideoItem_preservesEpisodeProgress() {
        val progress = VideoProgress(currentTimeSeconds = 42, playedPercentage = 10f)
        val item = episode("ep-1", 1, progress).toPlaybackVideoItem("series-1")

        assertEquals("ep-1", item.id)
        assertEquals("series-1", item.libraryId)
        assertEquals("Episode", item.type)
        assertEquals(progress, item.progress)
        assertEquals("S1E1 Pilot", item.title)
    }

    @Test
    fun videoEpisodeQueue_withEmptyEpisodes_hasNoNextOrPrevious() {
        val queue = VideoEpisodeQueue(
            libraryId = "series-1",
            episodes = emptyList(),
            currentIndex = 0
        )
        assertFalse(queue.hasNext)
        assertNull(queue.nextEpisode())
        assertNull(queue.advanceToNext())
        assertFalse(queue.hasPrevious)
        assertNull(queue.previousEpisode())
        assertNull(queue.goToPrevious())
    }

    @Test
    fun videoEpisodeQueue_withOutOfBoundsIndex_hasNoNextOrPrevious() {
        val queue = VideoEpisodeQueue(
            libraryId = "series-1",
            episodes = listOf(episode("ep-1", 1)),
            currentIndex = 5
        )
        assertFalse(queue.hasNext)
        assertNull(queue.nextEpisode())
        assertNull(queue.advanceToNext())
        assertFalse(queue.hasPrevious)
        assertNull(queue.previousEpisode())
        assertNull(queue.goToPrevious())
    }

    @Test
    fun videoEpisodeQueue_firstEpisode_hasNoPrevious() {
        val queue = VideoEpisodeQueue(
            libraryId = "series-1",
            episodes = listOf(episode("ep-1", 1), episode("ep-2", 2)),
            currentIndex = 0
        )
        assertFalse(queue.hasPrevious)
        assertNull(queue.previousEpisode())
        assertNull(queue.goToPrevious())
    }

    @Test
    fun videoEpisodeQueue_bidirectionalNavigation() {
        val queue = VideoEpisodeQueue(
            libraryId = "series-1",
            episodes = listOf(
                episode("ep-1", 1),
                episode("ep-2", 2),
                episode("ep-3", 3)
            ),
            currentIndex = 1
        )

        assertTrue(queue.hasPrevious)
        assertTrue(queue.hasNext)
        assertEquals("ep-1", queue.previousEpisode()?.id)
        assertEquals("ep-3", queue.nextEpisode()?.id)

        val forward = queue.advanceToNext()
        assertEquals(2, forward?.currentIndex)

        val backward = queue.goToPrevious()
        assertEquals(0, backward?.currentIndex)
    }

    private fun episode(
        id: String,
        episodeNumber: Int,
        progress: VideoProgress? = null
    ): VideoEpisode {
        return VideoEpisode(
            id = id,
            name = "Pilot",
            seasonNumber = 1,
            episodeNumber = episodeNumber,
            overview = "",
            durationSeconds = 1200,
            progress = progress
        )
    }
}
