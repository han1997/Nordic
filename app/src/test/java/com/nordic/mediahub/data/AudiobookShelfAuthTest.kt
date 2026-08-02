package com.nordic.mediahub.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AudiobookShelfAuthTest {
    @Test
    fun normalizeAudiobookShelfBaseUrl_defaultsBareHostnameToHttpsAndTrimsTrailingSlash() {
        assertEquals(
            "https://audio.example.test",
            normalizeAudiobookShelfBaseUrl("  audio.example.test/  ")
        )
    }

    @Test
    fun normalizeAudiobookShelfBaseUrl_preservesExplicitHttpScheme() {
        assertEquals(
            "http://audio.example.test/base",
            normalizeAudiobookShelfBaseUrl("http://audio.example.test/base/")
        )
    }

    @Test
    fun normalizeAudiobookShelfBaseUrl_preservesExplicitHttpsScheme() {
        assertEquals(
            "https://audio.example.test/base",
            normalizeAudiobookShelfBaseUrl("https://audio.example.test/base/")
        )
    }

    @Test
    fun normalizeAudiobookShelfBaseUrl_returnsEmptyForBlankInput() {
        assertEquals("", normalizeAudiobookShelfBaseUrl(""))
        assertEquals("", normalizeAudiobookShelfBaseUrl("   "))
    }

    @Test
    fun isReadyForAudiobookSync_requiresUrlUsernameAndPassword() {
        assertAudiobookReady(AudiobookShelfConfig(serverUrl = "", username = "demo", password = "secret"), false)
        assertAudiobookReady(AudiobookShelfConfig(serverUrl = "https://audio.example.test", username = "", password = "secret"), false)
        assertAudiobookReady(AudiobookShelfConfig(serverUrl = "https://audio.example.test", username = "demo", password = ""), false)
        assertAudiobookReady(
            AudiobookShelfConfig(serverUrl = "https://audio.example.test", username = "demo", password = "secret"),
            true
        )
    }

    private fun assertAudiobookReady(config: AudiobookShelfConfig, expected: Boolean) {
        assertEquals(expected, config.isReadyForAudiobookSync())
    }
}
