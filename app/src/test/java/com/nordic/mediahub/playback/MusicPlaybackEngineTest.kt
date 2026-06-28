package com.nordic.mediahub.playback

import com.nordic.mediahub.api.NavidromeSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MusicPlaybackEngineTest {
    @Test
    fun resolvePlayNextTargetIndex_movesFutureItemAfterCurrent() {
        assertEquals(2, resolvePlayNextTargetIndex(index = 4, currentIndex = 1, itemCount = 5))
    }

    @Test
    fun resolvePlayNextTargetIndex_movesPreviousItemAfterCurrent() {
        assertEquals(2, resolvePlayNextTargetIndex(index = 0, currentIndex = 2, itemCount = 4))
    }

    @Test
    fun resolvePlayNextTargetIndex_ignoresCurrentItem() {
        assertNull(resolvePlayNextTargetIndex(index = 1, currentIndex = 1, itemCount = 3))
    }

    @Test
    fun resolvePlayNextTargetIndex_ignoresItemAlreadyNext() {
        assertNull(resolvePlayNextTargetIndex(index = 2, currentIndex = 1, itemCount = 4))
    }

    @Test
    fun moveItemToIndex_movesItemToResolvedPosition() {
        assertEquals(
            listOf("B", "C", "A", "D"),
            listOf("A", "B", "C", "D").moveItemToIndex(fromIndex = 0, targetIndex = 2)
        )
    }

    @Test
    fun resolveCurrentIndexAfterMove_tracksCurrentItemWhenPreviousItemMovesAfterIt() {
        assertEquals(
            1,
            resolveCurrentIndexAfterMove(fromIndex = 0, targetIndex = 2, currentIndex = 2, itemCount = 4)
        )
    }

    @Test
    fun resolveCurrentIndexAfterMove_tracksCurrentItemWhenFutureItemMovesBeforeIt() {
        assertEquals(
            3,
            resolveCurrentIndexAfterMove(fromIndex = 3, targetIndex = 0, currentIndex = 2, itemCount = 4)
        )
    }

    @Test
    fun resolveQueueStartIndex_clampsNegativeIndexToFirstItem() {
        assertEquals(0, resolveQueueStartIndex(itemCount = 3, startIndex = -2))
    }

    @Test
    fun resolveQueueStartIndex_clampsTooLargeIndexToLastItem() {
        assertEquals(2, resolveQueueStartIndex(itemCount = 3, startIndex = 8))
    }

    @Test
    fun resolveQueueStartIndex_returnsNullForEmptyQueue() {
        assertNull(resolveQueueStartIndex(itemCount = 0, startIndex = 1))
    }

    @Test
    fun resolvePlayableMusicQueue_filtersUnplayableSongs() {
        val queue = resolvePlayableMusicQueue(
            songs = listOf(
                song(id = "missing"),
                song(id = "playable-1", streamUrl = "https://music.example/1.mp3"),
                song(id = "blank", streamUrl = " "),
                song(id = "playable-2", streamUrl = "https://music.example/2.mp3")
            ),
            startIndex = 1
        )

        requireNotNull(queue)
        assertEquals(listOf("playable-1", "playable-2"), queue.songs.map { it.id })
        assertEquals(0, queue.startIndex)
    }

    @Test
    fun resolvePlayableMusicQueue_keepsPlayableRequestedStartSong() {
        val queue = resolvePlayableMusicQueue(
            songs = listOf(
                song(id = "playable-1", streamUrl = "https://music.example/1.mp3"),
                song(id = "missing"),
                song(id = "playable-2", streamUrl = "https://music.example/2.mp3")
            ),
            startIndex = 2
        )

        requireNotNull(queue)
        assertEquals(listOf("playable-1", "playable-2"), queue.songs.map { it.id })
        assertEquals(1, queue.startIndex)
    }

    @Test
    fun resolvePlayableMusicQueue_mapsUnplayableStartToNextPlayableSong() {
        val queue = resolvePlayableMusicQueue(
            songs = listOf(
                song(id = "playable-1", streamUrl = "https://music.example/1.mp3"),
                song(id = "missing"),
                song(id = "playable-2", streamUrl = "https://music.example/2.mp3")
            ),
            startIndex = 1
        )

        requireNotNull(queue)
        assertEquals(listOf("playable-1", "playable-2"), queue.songs.map { it.id })
        assertEquals(1, queue.startIndex)
    }

    @Test
    fun resolvePlayableMusicQueue_mapsUnplayableStartToPreviousPlayableSongWhenNoNextPlayableExists() {
        val queue = resolvePlayableMusicQueue(
            songs = listOf(
                song(id = "playable-1", streamUrl = "https://music.example/1.mp3"),
                song(id = "missing")
            ),
            startIndex = 1
        )

        requireNotNull(queue)
        assertEquals(listOf("playable-1"), queue.songs.map { it.id })
        assertEquals(0, queue.startIndex)
    }

    @Test
    fun resolvePlayableMusicQueue_returnsNullWhenNoSongsArePlayable() {
        assertNull(
            resolvePlayableMusicQueue(
                songs = listOf(
                    song(id = "missing"),
                    song(id = "blank", streamUrl = "")
                ),
                startIndex = 1
            )
        )
    }

    private fun song(id: String, streamUrl: String? = null): NavidromeSong {
        return NavidromeSong(
            id = id,
            title = id,
            streamUrl = streamUrl
        )
    }
}

