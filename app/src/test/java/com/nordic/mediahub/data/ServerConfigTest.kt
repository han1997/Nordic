package com.nordic.mediahub.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerConfigTest {
    @Test
    fun isReadyForMusicSync_requiresUrlUsernameAndPassword() {
        assertFalse(NavidromeConfig(serverUrl = "http://example.test").isReadyForMusicSync())
        assertFalse(
            NavidromeConfig(
                serverUrl = "http://example.test",
                username = "demo"
            ).isReadyForMusicSync()
        )
        assertTrue(
            NavidromeConfig(
                serverUrl = "http://example.test",
                username = "demo",
                password = "secret"
            ).isReadyForMusicSync()
        )
    }

    @Test
    fun isReadyForAudiobookSync_requiresUrlUsernameAndPassword() {
        assertFalse(AudiobookShelfConfig(serverUrl = "http://example.test").isReadyForAudiobookSync())
        assertFalse(
            AudiobookShelfConfig(
                serverUrl = "http://example.test",
                username = "demo"
            ).isReadyForAudiobookSync()
        )
        assertTrue(
            AudiobookShelfConfig(
                serverUrl = "http://example.test",
                username = "demo",
                password = "secret"
            ).isReadyForAudiobookSync()
        )
    }
}
