package com.nordic.mediahub.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AudiobookShelfAuthTest {
    @Test
    fun normalizeAudiobookShelfBaseUrl_addsHttpAndTrimsTrailingSlash() {
        assertEquals(
            "http://audio.example.test",
            normalizeAudiobookShelfBaseUrl("  audio.example.test/  ")
        )
    }

    @Test
    fun normalizeAudiobookShelfBaseUrl_preservesExplicitHttpsScheme() {
        assertEquals(
            "https://audio.example.test/base",
            normalizeAudiobookShelfBaseUrl("https://audio.example.test/base/")
        )
    }
}
