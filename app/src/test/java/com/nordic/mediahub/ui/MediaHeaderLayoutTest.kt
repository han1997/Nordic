package com.nordic.mediahub.ui

import androidx.compose.ui.unit.dp
import com.nordic.mediahub.ui.theme.NordicControlSizes
import com.nordic.mediahub.ui.theme.NordicSpacing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaHeaderLayoutTest {
    @Test
    fun headerActionsFitRealTargetsAcrossViewportAndFontSizes() {
        for (width in listOf(320, 360, 392, 720)) {
            for (showBack in listOf(false, true)) {
                for (count in 0..5) {
                    for (scale in listOf(1f, 1.5f, 2f)) {
                        val available = (width - 32).dp
                        val layout = resolveMediaHeaderActionLayout(available, showBack, count, scale)
                        val slots = layout.inlineActionCount + if (layout.showsOverflow) 1 else 0
                        val expected = if (slots == 0) 0.dp else
                            NordicControlSizes.touchTarget * slots + NordicSpacing.xs * (slots - 1) + NordicSpacing.xs * 2
                        assertEquals(expected, layout.actionGroupWidth)
                        assertTrue(layout.inlineActionCount in 0..count)
                        assertEquals(count > layout.inlineActionCount, layout.showsOverflow)
                        val back = if (showBack) 48.dp + NordicSpacing.md else 0.dp
                        val actions = if (count > 0) expected + NordicSpacing.md else 0.dp
                        assertTrue("$width/$showBack/$count/$scale: $layout", available - back - actions >= 64.dp)
                    }
                }
            }
        }
    }

    @Test
    fun compactDetailHeaderMovesActionsToMenuInsteadOfShrinkingThem() {
        val compact = resolveMediaHeaderActionLayout(288.dp, true, 3, 2f)
        assertEquals(0, compact.inlineActionCount)
        assertTrue(compact.showsOverflow)
        assertEquals(56.dp, compact.actionGroupWidth)
        val wide = resolveMediaHeaderActionLayout(688.dp, true, 3, 2f)
        assertEquals(3, wide.inlineActionCount)
        assertFalse(wide.showsOverflow)
    }

    @Test
    fun absentActionsDoNotReserveAnEmptyActionContainer() {
        assertEquals(MediaHeaderActionLayout(0, false, 0.dp), resolveMediaHeaderActionLayout(288.dp, false, 0))
        assertEquals(MediaHeaderActionLayout(0, false, 0.dp), resolveMediaHeaderActionLayout(288.dp, false, -1))
    }
}
