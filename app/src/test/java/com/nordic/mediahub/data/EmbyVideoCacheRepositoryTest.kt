package com.nordic.mediahub.data

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbyVideoCacheRepositoryTest {

    @Test
    fun load_returnsNullWhenNoCacheStored() = runCacheTest {
        val repo = newRepo()
        assertNull(repo.load(config(apiKey = "key-1")))
    }

    @Test
    fun save_thenLoad_returnsCachedBrowseData() = runCacheTest {
        val repo = newRepo()
        val cfg = config(apiKey = "key-1")
        val cache = repo.buildCache(
            config = cfg,
            libraries = listOf(library("lib-1")),
            videos = listOf(video("v1")),
            selectedLibraryId = "lib-1"
        )
        repo.save(cfg, cache)

        val loaded = repo.load(cfg)
        assertNotNull(loaded)
        assertEquals(listOf("lib-1"), loaded!!.libraries.map { it.id })
        assertEquals(listOf("v1"), loaded.videos.map { it.id })
        assertEquals("lib-1", loaded.selectedLibraryId)
        assertTrue(loaded.updatedAtMillis > 0L)
        assertEquals(cfg.cacheKey(), loaded.configKey)
    }

    @Test
    fun load_returnsNullForMismatchedApiKey_configScopedIsolation() = runCacheTest {
        val repo = newRepo()
        val cfgA = config(apiKey = "key-a")
        repo.save(cfgA, repo.buildCache(cfgA, libraries = listOf(library("lib-1")), videos = listOf(video("v1")), selectedLibraryId = "lib-1"))

        // Same URL, different API key must not share the cache (different users).
        val cfgB = config(apiKey = "key-b")
        assertNull(repo.load(cfgB))
    }

    @Test
    fun load_returnsNullForMismatchedUser_configScopedIsolation() = runCacheTest {
        val repo = newRepo()
        val cfgA = config(user = "user-a", apiKey = "")
        repo.save(cfgA, repo.buildCache(cfgA, libraries = listOf(library("lib-1")), videos = listOf(video("v1")), selectedLibraryId = "lib-1"))

        val cfgB = config(user = "user-b", apiKey = "")
        assertNull(repo.load(cfgB))
    }

    @Test
    fun clear_removesCacheForMatchingConfig() = runCacheTest {
        val repo = newRepo()
        val cfg = config(apiKey = "key-1")
        repo.save(cfg, repo.buildCache(cfg, libraries = listOf(library("lib-1")), videos = listOf(video("v1")), selectedLibraryId = "lib-1"))
        assertNotNull(repo.load(cfg))

        repo.clear(cfg)

        assertNull(repo.load(cfg))
    }

    @Test
    fun clear_doesNotRemoveCacheForDifferentConfig() = runCacheTest {
        val repo = newRepo()
        val cfgA = config(apiKey = "key-a")
        val cfgB = config(apiKey = "key-b")
        repo.save(cfgA, repo.buildCache(cfgA, libraries = listOf(library("a")), videos = listOf(video("v1")), selectedLibraryId = "a"))
        repo.save(cfgB, repo.buildCache(cfgB, libraries = listOf(library("b")), videos = listOf(video("v2")), selectedLibraryId = "b"))

        repo.clear(cfgA)

        assertNull(repo.load(cfgA))
        val loadedB = repo.load(cfgB)
        assertNotNull(loadedB)
        assertEquals(listOf("v2"), loadedB!!.videos.map { it.id })
    }

    @Test
    fun saveResumeItems_thenLoad_returnsPersistedServerResumeRows() = runCacheTest {
        val repo = newRepo()
        val cfg = config(apiKey = "key-1")
        repo.save(
            cfg,
            repo.buildCache(
                config = cfg,
                libraries = listOf(library("lib-1")),
                videos = listOf(video("v1")),
                selectedLibraryId = "lib-1"
            )
        )

        val resumeRow = video("ep-1").copy(playbackPositionSeconds = 300)
        repo.saveResumeItems(cfg, listOf(resumeRow))

        val loaded = repo.load(cfg)
        assertNotNull(loaded)
        assertEquals(listOf("ep-1"), loaded!!.resumeVideos.map { it.id })
        assertEquals(300, loaded.resumeVideos.first().playbackPositionSeconds)
        // Resume persistence must not disturb the browse fields.
        assertEquals(listOf("lib-1"), loaded.libraries.map { it.id })
        assertEquals(listOf("v1"), loaded.videos.map { it.id })
    }

    @Test
    fun saveResumeItems_withoutExistingCache_isNoOp() = runCacheTest {
        val repo = newRepo()
        val cfg = config(apiKey = "key-1")

        repo.saveResumeItems(cfg, listOf(video("ep-1")))

        assertNull(repo.load(cfg))
    }

    @Test
    fun load_returnsCacheWithOnlyResumeRows() = runCacheTest {
        val repo = newRepo()
        val cfg = config(apiKey = "key-1")
        repo.save(
            cfg,
            repo.buildCache(
                config = cfg,
                libraries = emptyList(),
                videos = emptyList(),
                selectedLibraryId = null
            ).copy(resumeVideos = listOf(video("ep-1")))
        )

        val loaded = repo.load(cfg)
        assertNotNull(loaded)
        assertEquals(listOf("ep-1"), loaded!!.resumeVideos.map { it.id })
    }

    @Test
    fun load_returnsNullForMalformedStoredJson() = runCacheTest {
        val dataStore = fakeDataStore()
        val repo = EmbyVideoCacheRepository(context = null, dataStoreProvider = { dataStore })
        runBlocking {
            dataStore.edit { it[stringPreferencesKey("emby_video_cache")] = "{not-json" }
        }
        assertNull(repo.load(config(apiKey = "key-1")))
    }

    private fun config(user: String = "", apiKey: String): VideoServerConfig {
        return VideoServerConfig(
            type = VideoServerType.EMBY,
            serverUrl = "http://example.test",
            username = user,
            password = if (user.isNotBlank()) "secret" else "",
            apiKey = apiKey
        )
    }

    private fun library(id: String) = VideoLibrary(id = id, name = id, collectionType = "movies")

    private fun video(id: String) = VideoItem(
        id = id,
        libraryId = "lib-1",
        title = id,
        type = "Movie"
    )

    private fun newRepo(): EmbyVideoCacheRepository {
        return EmbyVideoCacheRepository(context = null, dataStoreProvider = { fakeDataStore() })
    }
}
