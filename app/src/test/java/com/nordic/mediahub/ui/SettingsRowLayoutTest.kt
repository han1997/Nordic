package com.nordic.mediahub.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRowLayoutTest {
    @Test fun shortValueFitsAlongsideItsActualTitle() {
        assertTrue(shouldInlineSettingsValue(328.dp, 64.dp, 56.dp, 1f, hasNavigation = true))
        assertTrue(shouldInlineSettingsValue(328.dp, 128.dp, 112.dp, 2f, hasNavigation = true))
    }
    @Test fun largeFontLongValueDoesNotSqueezeTitle() {
        assertFalse(shouldInlineSettingsValue(328.dp, 320.dp, 196.dp, 2f, hasNavigation = true))
        assertTrue(shouldInlineSettingsValue(688.dp, 320.dp, 196.dp, 2f, hasNavigation = true))
    }
    @Test fun iconAndNavigationMustFitInTheSameBudget() {
        assertTrue(shouldInlineSettingsValue(240.dp, 140.dp, 56.dp, 1f))
        assertFalse(shouldInlineSettingsValue(240.dp, 140.dp, 56.dp, 1f, hasIcon = true, hasNavigation = true))
    }
    @Test fun inlineTextFitsAcrossWidthAndFontMatrix() {
        for (width in listOf(320, 360, 392, 720)) for (font in listOf(1f, 1.5f, 2f)) {
            val content = (width - 32).dp
            val title = (150 * font).dp
            val value = (98 * font).dp
            if (shouldInlineSettingsValue(content, title, value, font, hasIcon = true, hasNavigation = true)) {
                assertTrue(content >= title + value + 80.dp)
            }
        }
    }
    @Test fun invalidConstraintsChooseStackedRatherThanCrash() {
        assertFalse(shouldInlineSettingsValue(0.dp, 140.dp, 56.dp, 1f))
        assertFalse(shouldInlineSettingsValue(Float.NaN.dp, 140.dp, 56.dp, 1f))
        assertFalse(shouldInlineSettingsValue(328.dp, 140.dp, 56.dp, Float.NaN))
    }
}
