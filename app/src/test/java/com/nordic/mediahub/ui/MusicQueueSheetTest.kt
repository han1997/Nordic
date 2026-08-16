package com.nordic.mediahub.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MusicQueueSheetTest {

    private val rowHeightPx = 64f

    @Test
    fun resolveQueueRowDisplacement_idleState_returnsZeroForAllRows() {
        val dragState = QueueDragState()
        assertEquals(0f, resolveQueueRowDisplacement(0, dragState, rowHeightPx), 0.001f)
        assertEquals(0f, resolveQueueRowDisplacement(5, dragState, rowHeightPx), 0.001f)
    }

    @Test
    fun resolveQueueRowDisplacement_draggedRowFollowsFinger() {
        val dragState = QueueDragState(draggedIndex = 2, accumulatedPx = 30f)
        assertEquals(30f, resolveQueueRowDisplacement(2, dragState, rowHeightPx), 0.001f)
    }

    @Test
    fun resolveQueueRowDisplacement_draggedRowNegativeDeltaFollowsFinger() {
        val dragState = QueueDragState(draggedIndex = 3, accumulatedPx = -45f)
        assertEquals(-45f, resolveQueueRowDisplacement(3, dragState, rowHeightPx), 0.001f)
    }

    @Test
    fun resolveQueueRowDisplacement_belowDraggedRowShiftsUpWhenDraggedDown() {
        val dragState = QueueDragState(draggedIndex = 1, accumulatedPx = rowHeightPx)
        assertEquals(-rowHeightPx, resolveQueueRowDisplacement(2, dragState, rowHeightPx), 0.001f)
    }

    @Test
    fun resolveQueueRowDisplacement_aboveDraggedRowShiftsDownWhenDraggedUp() {
        val dragState = QueueDragState(draggedIndex = 3, accumulatedPx = -rowHeightPx)
        assertEquals(rowHeightPx, resolveQueueRowDisplacement(2, dragState, rowHeightPx), 0.001f)
    }

    @Test
    fun resolveQueueRowDisplacement_multipleRowsShiftWhenCrossingMultipleRows() {
        val dragState = QueueDragState(draggedIndex = 1, accumulatedPx = rowHeightPx * 2)
        assertEquals(-rowHeightPx, resolveQueueRowDisplacement(2, dragState, rowHeightPx), 0.001f)
        assertEquals(-rowHeightPx, resolveQueueRowDisplacement(3, dragState, rowHeightPx), 0.001f)
    }

    @Test
    fun resolveQueueRowDisplacement_unaffectedRowsReturnZero() {
        val dragState = QueueDragState(draggedIndex = 2, accumulatedPx = rowHeightPx)
        assertEquals(0f, resolveQueueRowDisplacement(0, dragState, rowHeightPx), 0.001f)
        assertEquals(0f, resolveQueueRowDisplacement(1, dragState, rowHeightPx), 0.001f)
        assertEquals(0f, resolveQueueRowDisplacement(4, dragState, rowHeightPx), 0.001f)
    }

    @Test
    fun resolveQueueRowDisplacement_subThresholdDeltaReturnsZeroForOtherRows() {
        val dragState = QueueDragState(draggedIndex = 1, accumulatedPx = rowHeightPx * 0.4f)
        assertEquals(0f, resolveQueueRowDisplacement(2, dragState, rowHeightPx), 0.001f)
    }

    @Test
    fun resolveQueueRowDisplacement_zeroRowHeightReturnsZero() {
        val dragState = QueueDragState(draggedIndex = 1, accumulatedPx = 100f)
        assertEquals(0f, resolveQueueRowDisplacement(2, dragState, 0f), 0.001f)
    }

    @Test
    fun resolveQueueRowDisplacement_draggedUpMultipleRows() {
        val dragState = QueueDragState(draggedIndex = 4, accumulatedPx = -rowHeightPx * 2)
        assertEquals(rowHeightPx, resolveQueueRowDisplacement(2, dragState, rowHeightPx), 0.001f)
        assertEquals(rowHeightPx, resolveQueueRowDisplacement(3, dragState, rowHeightPx), 0.001f)
        assertEquals(0f, resolveQueueRowDisplacement(1, dragState, rowHeightPx), 0.001f)
    }
}
