package com.nordic.mediahub.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class QueueItemGeometryTest {
    private val rows = listOf(QueueItemBounds(0, 0, 100), QueueItemBounds(1, 104, 160), QueueItemBounds(2, 268, 100))
    @Test fun dropUsesVariableRowCentersAndBothDirections() {
        assertEquals(1, resolveQueueDropTarget(0, 140f, rows, 3))
        assertEquals(2, resolveQueueDropTarget(0, 270f, rows, 3))
        assertEquals(0, resolveQueueDropTarget(2, -270f, rows, 3))
    }
    @Test fun outsideViewportIsBoundedAndInvalidDataIsSafe() {
        assertEquals(2, resolveQueueDropTarget(0, 10_000f, rows, 3))
        assertEquals(0, resolveQueueDropTarget(0, Float.NaN, rows, 3))
        assertEquals(1, resolveQueueDropTarget(1, 100f, emptyList(), 3))
        assertEquals(-1, resolveQueueDropTarget(0, 0f, emptyList(), 0))
    }
    @Test fun neighborsShiftByTheDraggedItemsActualExtent() {
        val drag = QueueDragState(0, 150f, targetIndex = 1, draggedExtentPx = 104f)
        assertEquals(-104f, resolveQueueRowDisplacement(1, drag, 64f), 0.01f)
        assertEquals(0f, resolveQueueRowDisplacement(2, drag, 64f), 0.01f)
        assertEquals(150f, resolveQueueRowDisplacement(0, drag, 64f), 0.01f)
    }
}
