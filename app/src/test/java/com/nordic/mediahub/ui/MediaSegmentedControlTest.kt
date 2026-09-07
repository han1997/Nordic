package com.nordic.mediahub.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSegmentedControlTest {
    @Test
    fun shortLabelsShareAvailableWidthInsteadOfBecomingUnrelatedChips() {
        assertFalse(shouldScrollMediaSegments(288.dp, listOf(64.dp, 64.dp, 64.dp)))
    }

    @Test
    fun longLabelsAndLargeFontsScrollRatherThanShrinkingOrTruncating() {
        assertTrue(shouldScrollMediaSegments(232.dp, listOf(80.dp, 80.dp, 80.dp)))
        assertTrue(shouldScrollMediaSegments(288.dp, listOf(80.dp, 116.dp, 80.dp)))
        assertFalse(shouldScrollMediaSegments(688.dp, listOf(128.dp, 128.dp, 128.dp)))
    }

    @Test
    fun geometryIncludesPaddingGapsAndMinimumTouchWidth() {
        assertFalse(shouldScrollMediaSegments(208.dp, listOf(64.dp, 64.dp, 64.dp)))
        assertTrue(shouldScrollMediaSegments(207.dp, listOf(64.dp, 64.dp, 64.dp)))
        assertFalse(shouldScrollMediaSegments(56.dp, listOf(12.dp)))
        assertTrue(shouldScrollMediaSegments(55.dp, listOf(12.dp)))
    }

    @Test
    fun manySortOptionsUseOneScrollableStripAndEmptyOptionsHaveNoStrip() {
        assertTrue(shouldScrollMediaSegments(720.dp, List(6) { 64.dp }))
        assertFalse(shouldScrollMediaSegments(0.dp, emptyList()))
    }
}
