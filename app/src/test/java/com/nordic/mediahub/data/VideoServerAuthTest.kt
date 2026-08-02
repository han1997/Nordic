package com.nordic.mediahub.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoServerAuthTest {
    @Test
    fun normalizeVideoServerBaseUrl_defaultsBareHostnameToHttpsAndTrimsTrailingSlash() {
        assertEquals(
            "https://video.example.test",
            normalizeVideoServerBaseUrl("  video.example.test/  ")
        )
    }

    @Test
    fun normalizeVideoServerBaseUrl_preservesExplicitHttpScheme() {
        assertEquals(
            "http://video.example.test/base",
            normalizeVideoServerBaseUrl("http://video.example.test/base/")
        )
    }

    @Test
    fun normalizeVideoServerBaseUrl_preservesExplicitHttpsScheme() {
        assertEquals(
            "https://video.example.test/base",
            normalizeVideoServerBaseUrl("https://video.example.test/base/")
        )
    }

    @Test
    fun normalizeVideoServerBaseUrl_returnsEmptyForBlankInput() {
        assertEquals("", normalizeVideoServerBaseUrl(""))
        assertEquals("", normalizeVideoServerBaseUrl("   "))
    }

    @Test
    fun isReadyForVideoSync_requiresEmbyTypeAndUrlAndEitherApiKeyOrPassword() {
        assertTrue(
            VideoServerConfig(
                serverUrl = "https://video.example.test",
                apiKey = "api-key"
            ).isReadyForVideoSync()
        )
        assertTrue(
            VideoServerConfig(
                serverUrl = "https://video.example.test",
                username = "demo",
                password = "secret"
            ).isReadyForVideoSync()
        )
        assertFalse(VideoServerConfig(serverUrl = "https://video.example.test").isReadyForVideoSync())
        assertFalse(
            VideoServerConfig(
                serverUrl = "https://video.example.test",
                username = "demo"
            ).isReadyForVideoSync()
        )
        assertFalse(
            VideoServerConfig(
                type = VideoServerType.PLEX,
                serverUrl = "https://video.example.test",
                apiKey = "api-key"
            ).isReadyForVideoSync()
        )
        assertFalse(
            VideoServerConfig(
                type = VideoServerType.WEBDAV,
                serverUrl = "https://video.example.test",
                apiKey = "api-key"
            ).isReadyForVideoSync()
        )
        assertFalse(
            VideoServerConfig(
                serverUrl = "",
                apiKey = "api-key"
            ).isReadyForVideoSync()
        )
    }
}
