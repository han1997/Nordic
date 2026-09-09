package com.nordic.mediahub.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheKeyTest {
    @Test
    fun navidromeCacheKey_isConfigScopedByUrlAndUser() {
        val a = NavidromeConfig(serverUrl = "http://example.test", username = "user-1", password = "p")
        val b = NavidromeConfig(serverUrl = "http://example.test", username = "user-2", password = "p")
        val c = NavidromeConfig(serverUrl = "http://other.test", username = "user-1", password = "p")
        assertEquals(a.cacheKey(), a.copy().cacheKey())
        assertFalse(a.cacheKey() == b.cacheKey())
        assertFalse(a.cacheKey() == c.cacheKey())
    }

    @Test
    fun navidromeCacheKey_normalizesUrlCaseAndUserWhitespace() {
        val raw = NavidromeConfig(serverUrl = "HTTP://Example.TEST/", username = "  User-1  ", password = "p")
        val normalized = NavidromeConfig(serverUrl = "http://example.test", username = "user-1", password = "p")
        assertEquals(normalized.cacheKey(), raw.cacheKey())
    }

    @Test
    fun navidromeCacheKey_embedsSchemaVersion() {
        val cfg = NavidromeConfig(serverUrl = "http://example.test", username = "u", password = "p")
        assertTrue(cfg.cacheKey().endsWith("|v5"))
    }

    @Test
    fun audiobookCacheKey_isConfigScopedByUrlAndUser() {
        val a = AudiobookShelfConfig(serverUrl = "http://example.test", username = "user-1", password = "p")
        val b = AudiobookShelfConfig(serverUrl = "http://example.test", username = "user-2", password = "p")
        assertEquals(a.cacheKey(), a.copy().cacheKey())
        assertFalse(a.cacheKey() == b.cacheKey())
    }

    @Test
    fun audiobookCacheKey_normalizesUrlAndUser() {
        val raw = AudiobookShelfConfig(serverUrl = "  http://Example.TEST/  ", username = "  User-1  ", password = "p")
        val normalized = AudiobookShelfConfig(serverUrl = "http://example.test", username = "user-1", password = "p")
        assertEquals(normalized.cacheKey(), raw.cacheKey())
    }

    @Test
    fun audiobookCacheKey_embedsSchemaVersion() {
        val cfg = AudiobookShelfConfig(serverUrl = "http://example.test", username = "u", password = "p")
        assertTrue(cfg.cacheKey().endsWith("|v1"))
    }

    @Test
    fun videoCacheKey_distinguishesApiKeysAtSameUrl() {
        val a = VideoServerConfig(type = VideoServerType.EMBY, serverUrl = "http://example.test", apiKey = "key-a")
        val b = VideoServerConfig(type = VideoServerType.EMBY, serverUrl = "http://example.test", apiKey = "key-b")
        assertEquals(a.cacheKey(), a.copy().cacheKey())
        assertFalse(a.cacheKey() == b.cacheKey())
    }

    @Test
    fun videoCacheKey_distinguishesUsersAtSameUrlForPasswordLogin() {
        val a = VideoServerConfig(type = VideoServerType.EMBY, serverUrl = "http://example.test", username = "user-a", password = "p")
        val b = VideoServerConfig(type = VideoServerType.EMBY, serverUrl = "http://example.test", username = "user-b", password = "p")
        assertEquals(a.cacheKey(), a.copy().cacheKey())
        assertFalse(a.cacheKey() == b.cacheKey())
    }

    @Test
    fun videoCacheKey_distinguishesApiKeyFromPasswordLogin() {
        val apiKeyCfg = VideoServerConfig(type = VideoServerType.EMBY, serverUrl = "http://example.test", username = "user-a", apiKey = "key-a")
        val passwordCfg = VideoServerConfig(type = VideoServerType.EMBY, serverUrl = "http://example.test", username = "user-a", password = "p", apiKey = "")
        // Same URL + username but different auth identity (apiKey vs password) must not share cache.
        assertFalse(apiKeyCfg.cacheKey() == passwordCfg.cacheKey())
    }

    @Test
    fun videoCacheKey_normalizesUrlCase() {
        val raw = VideoServerConfig(type = VideoServerType.EMBY, serverUrl = "HTTP://Example.TEST/", apiKey = "key-a")
        val normalized = VideoServerConfig(type = VideoServerType.EMBY, serverUrl = "http://example.test", apiKey = "key-a")
        assertEquals(normalized.cacheKey(), raw.cacheKey())
    }

    @Test
    fun videoCacheKey_embedsSchemaVersion() {
        val cfg = VideoServerConfig(type = VideoServerType.EMBY, serverUrl = "http://example.test", apiKey = "key-a")
        assertTrue(cfg.cacheKey().endsWith("|v2"))
    }
}
