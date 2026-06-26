package com.nordic.mediahub.playback

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
    fun resolveQueueIndexAfterMove_keepsCurrentIndexWhenFutureItemMovesAfterCurrent() {
        assertEquals(
            1,
            resolveQueueIndexAfterMove(fromIndex = 4, targetIndex = 2, currentIndex = 1, itemCount = 5)
        )
    }

    @Test
    fun resolveQueueIndexAfterMove_tracksCurrentItemWhenPreviousItemMovesAfterCurrent() {
        assertEquals(
            1,
            resolveQueueIndexAfterMove(fromIndex = 0, targetIndex = 2, currentIndex = 2, itemCount = 4)
        )
    }

    @Test
    fun resolveQueueIndexAfterMove_leavesInvalidCurrentIndexUnchanged() {
        assertEquals(
            8,
            resolveQueueIndexAfterMove(fromIndex = 0, targetIndex = 1, currentIndex = 8, itemCount = 3)
        )
    }
}
