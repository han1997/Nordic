package com.nordic.mediahub.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavidromeAuthTest {
    @Test
    fun normalizeNavidromeBaseUrl_defaultsBareHostnameToHttpsAndTrimsTrailingSlash() {
        assertEquals(
            "https://nav.example.test",
            normalizeNavidromeBaseUrl("  nav.example.test/  ")
        )
    }

    @Test
    fun normalizeNavidromeBaseUrl_preservesExplicitHttpScheme() {
        assertEquals(
            "http://nav.example.test/base",
            normalizeNavidromeBaseUrl("http://nav.example.test/base/")
        )
    }

    @Test
    fun normalizeNavidromeBaseUrl_preservesExplicitHttpsScheme() {
        assertEquals(
            "https://nav.example.test/base",
            normalizeNavidromeBaseUrl("https://nav.example.test/base/")
        )
    }

    @Test
    fun normalizeNavidromeBaseUrl_returnsEmptyForBlankInput() {
        assertEquals("", normalizeNavidromeBaseUrl(""))
        assertEquals("", normalizeNavidromeBaseUrl("   "))
    }

    @Test
    fun isReadyForMusicSync_requiresUrlUsernameAndPassword() {
        assertFalse(NavidromeConfig().isReadyForMusicSync())
        assertFalse(
            NavidromeConfig(serverUrl = "https://nav.example.test", username = "", password = "secret").isReadyForMusicSync()
        )
        assertFalse(
            NavidromeConfig(serverUrl = "https://nav.example.test", username = "demo", password = "").isReadyForMusicSync()
        )
        assertTrue(
            NavidromeConfig(
                serverUrl = "https://nav.example.test",
                username = "demo",
                password = "secret"
            ).isReadyForMusicSync()
        )
    }

    @Test
    fun addNavidromeAuth_appendsSubsonicAuthQueryParameters() {
        val config = NavidromeConfig(
            serverUrl = "https://nav.example.test",
            username = "demo",
            password = "secret"
        )

        val url = "https://nav.example.test/rest/ping".toHttpUrl()
            .newBuilder()
            .addNavidromeAuth(config)
            .build()

        assertEquals("demo", url.queryParameter("u"))
        assertEquals(NAVIDROME_API_VERSION, url.queryParameter("v"))
        assertEquals(NAVIDROME_CLIENT_NAME, url.queryParameter("c"))
        assertNotNull(url.queryParameter("t"))
        assertNotNull(url.queryParameter("s"))
        assertTrue(url.queryParameter("t")!!.isNotBlank())
        assertTrue(url.queryParameter("s")!!.isNotBlank())
    }
}
