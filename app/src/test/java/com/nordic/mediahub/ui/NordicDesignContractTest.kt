package com.nordic.mediahub.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.nordic.mediahub.ui.theme.NordicTypography
import com.nordic.mediahub.ui.theme.nordicColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NordicDesignContractTest {
    @Test
    fun everyMaterialTextSlotUsesTheNativeFamilyAndExplicitMetrics() {
        val t = NordicTypography
        val styles = listOf(t.displayLarge, t.displayMedium, t.displaySmall,
            t.headlineLarge, t.headlineMedium, t.headlineSmall, t.titleLarge,
            t.titleMedium, t.titleSmall, t.bodyLarge, t.bodyMedium, t.bodySmall,
            t.labelLarge, t.labelMedium, t.labelSmall)
        assertEquals(15, styles.size)
        styles.forEach { style ->
            assertEquals(FontFamily.Default, style.fontFamily)
            assertTrue(style.fontSize.value > 0)
            assertTrue(style.lineHeight.value >= style.fontSize.value)
            assertEquals(0.sp, style.letterSpacing)
        }
        assertEquals(32.sp, t.displaySmall.fontSize)
        assertEquals(22.sp, t.headlineMedium.fontSize)
        assertEquals(16.sp, t.titleMedium.fontSize)
        assertEquals(14.sp, t.bodyMedium.fontSize)
    }

    @Test
    fun bothThemesProvideReadableForegroundsForControlsAndContainers() {
        for (dark in listOf(false, true)) {
            val c = nordicColorScheme(dark)
            val neutral = c.surface.compositeOver(c.background)
            val roles = listOf(
                Triple("primary", c.onPrimary, c.primary),
                Triple("secondary", c.onSecondary, c.secondary),
                Triple("tertiary", c.onTertiary, c.tertiary),
                Triple("background", c.onBackground, c.background),
                Triple("surface", c.onSurface, neutral),
                Triple("surfaceVariant", c.onSurfaceVariant, c.surfaceVariant),
                Triple("queueEmpty", c.onSurfaceVariant, c.surfaceVariant.copy(alpha = 0.46f).compositeOver(c.surface.copy(alpha = 1f))),
                Triple("sheetChoiceNormal", c.onSurface, c.surfaceVariant.copy(alpha = 0.42f).compositeOver(c.surface.copy(alpha = 1f))),
                Triple("sheetChoiceSelectedSubtitle", c.onPrimaryContainer, c.primaryContainer.compositeOver(c.surface.copy(alpha = 1f))),
                Triple("segmentSelected", c.primary, c.surface.copy(alpha = 0.96f).compositeOver(c.surfaceVariant)),
                Triple("segmentNormal", c.onSurfaceVariant, c.surfaceVariant.copy(alpha = 0.56f).compositeOver(c.background)),
                Triple("primaryContainer", c.onPrimaryContainer, c.primaryContainer.compositeOver(c.surfaceVariant)),
                Triple("secondaryContainer", c.onSecondaryContainer, c.secondaryContainer.compositeOver(c.surfaceVariant)),
                Triple("tertiaryContainer", c.onTertiaryContainer, c.tertiaryContainer.compositeOver(c.surfaceVariant)),
                Triple("error", c.onError, c.error),
                Triple("errorContainer", c.onErrorContainer, c.errorContainer),
                Triple("dialogError", c.error, c.surfaceContainerHigh),
                Triple("dialogFocusedLabel", c.onPrimaryContainer, c.surfaceContainerHigh),
                Triple("dialogCancel", c.onSurfaceVariant, c.surfaceContainerHigh),
                Triple("eqBandLabel", c.onSurfaceVariant, c.surface),
                Triple("eqPreset", c.onSurfaceVariant, c.surfaceVariant.copy(alpha = 0.56f).compositeOver(c.surface)),
                Triple("musicEmpty", c.onSurface.copy(alpha = 0.68f), c.surfaceVariant.copy(alpha = 0.5f).compositeOver(c.background)),
                Triple("musicLoading", c.onSurface.copy(alpha = 0.68f), c.surfaceVariant.copy(alpha = 0.76f).compositeOver(c.background)),
                Triple("musicError", c.onErrorContainer.copy(alpha = 0.82f), c.errorContainer),
                Triple("inverse", c.inverseOnSurface, c.inverseSurface)
            )
            roles.forEach { (name, foreground, background) ->
                val ratio = contrast(foreground, background)
                assertTrue("dark=$dark role=$name contrast=$ratio", ratio >= 4.5f)
            }
        }
    }

    private fun contrast(foreground: Color, background: Color): Float {
        val first = foreground.compositeOver(background).luminance()
        val second = background.luminance()
        return (maxOf(first, second) + 0.05f) / (minOf(first, second) + 0.05f)
    }
}
