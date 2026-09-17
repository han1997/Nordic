package com.nordic.mediahub.ui

import androidx.compose.material3.lightColorScheme
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackDockStatusTest {
    @Test fun subtitleColorMapsEachStatusCategory() {
        val colors = androidx.compose.material3.lightColorScheme()
        assertEquals(colors.onSurfaceVariant, resolveDockStatusSubtitleColor(colors, hasStatus = false, statusIsError = false))
        assertEquals(colors.primary, resolveDockStatusSubtitleColor(colors, hasStatus = true, statusIsError = false))
        assertEquals(colors.error, resolveDockStatusSubtitleColor(colors, hasStatus = true, statusIsError = true))
    }
}