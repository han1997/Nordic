package com.nordic.mediahub.data

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudiobookCacheRepositoryTest {

    @Test
    fun load_returnsNullWhenNoCacheStored() = runCacheTest {
        val repo = newRepo()
        assertNull(repo.load(config(user = "user-1")))
    }

    @Test
    fun save_thenLoad_returnsCachedBrowseData() = runCacheTest {
        val repo = newRepo()
        val cfg = config(user = "user-1")
        val cache = repo.buildCache(
            config = cfg,
            libraries = listOf(library("lib-1")),
            items = listOf(summary("book-1")),
            selectedLibraryId = "lib-1"
        )
        repo.save(cfg, cache)

        val loaded = repo.load(cfg)
        assertNotNull(loaded)
        assertEquals(listOf("lib-1"), loaded!!.libraries.map { it.id })
        assertEquals(listOf("book-1"), loaded.items.map { it.id })
        assertEquals("lib-1", loaded.selectedLibraryId)
        assertTrue(loaded.updatedAtMillis > 0L)
        assertEquals(cfg.cacheKey(), loaded.configKey)
    }

    @Test
    fun load_returnsNullForMismatchedConfig_configScopedIsolation() = runCacheTest {
        val repo = newRepo()
        val cfgA = config(user = "user-a")
        repo.save(cfgA, repo.buildCache(cfgA, libraries = listOf(library("lib-1")), items = listOf(summary("b1")), selectedLibraryId = "lib-1"))

        val cfgB = config(user = "user-b")
        assertNull(repo.load(cfgB))
    }

    @Test
    fun clear_removesCacheForMatchingConfig() = runCacheTest {
        val repo = newRepo()
        val cfg = config(user = "user-1")
        repo.save(cfg, repo.buildCache(cfg, libraries = listOf(library("lib-1")), items = listOf(summary("b1")), selectedLibraryId = "lib-1"))
        assertNotNull(repo.load(cfg))

        repo.clear(cfg)

        assertNull(repo.load(cfg))
    }

    @Test
    fun clear_doesNotRemoveCacheForDifferentConfig() = runCacheTest {
        val repo = newRepo()
        val cfgA = config(user = "user-a")
        val cfgB = config(user = "user-b")
        repo.save(cfgA, repo.buildCache(cfgA, libraries = listOf(library("a")), items = listOf(summary("b1")), selectedLibraryId = "a"))
        repo.save(cfgB, repo.buildCache(cfgB, libraries = listOf(library("b")), items = listOf(summary("b2")), selectedLibraryId = "b"))

        repo.clear(cfgA)

        assertNull(repo.load(cfgA))
        val loadedB = repo.load(cfgB)
        assertNotNull(loadedB)
        assertEquals(listOf("b2"), loadedB!!.items.map { it.id })
    }

    @Test
    fun detailCache_saveAndLoadItemDetailByKey() = runCacheTest {
        val repo = newRepo()
        val cfg = config(user = "user-1")
        repo.save(cfg, repo.buildCache(cfg, libraries = listOf(library("lib-1")), items = listOf(summary("b1")), selectedLibraryId = "lib-1"))

        assertNull(repo.loadItemDetail(cfg, "book-1"))
        repo.saveItemDetail(cfg, "book-1", detail("book-1"))

        val loaded = repo.loadItemDetail(cfg, "book-1")
        assertNotNull(loaded)
        assertEquals("book-1", loaded!!.id)
    }

    @Test
    fun detailCache_browseRefreshPreservesExistingItemDetails() = runCacheTest {
        val repo = newRepo()
        val cfg = config(user = "user-1")
        repo.save(cfg, repo.buildCache(cfg, libraries = listOf(library("lib-1")), items = listOf(summary("b1")), selectedLibraryId = "lib-1"))
        repo.saveItemDetail(cfg, "book-1", detail("book-1"))

        // A browse refresh rebuilds libraries/items but must not wipe detail caches.
        repo.save(cfg, repo.buildCache(cfg, libraries = listOf(library("lib-2")), items = listOf(summary("b2")), selectedLibraryId = "lib-2"))

        val loaded = repo.load(cfg)
        assertEquals(listOf("lib-2"), loaded!!.libraries.map { it.id })
        val detail = repo.loadItemDetail(cfg, "book-1")
        assertNotNull(detail)
        assertEquals("book-1", detail!!.id)
    }

    @Test
    fun detailCache_isConfigScoped() = runCacheTest {
        val repo = newRepo()
        val cfgA = config(user = "user-a")
        repo.save(cfgA, repo.buildCache(cfgA, libraries = listOf(library("lib-1")), items = listOf(summary("b1")), selectedLibraryId = "lib-1"))
        repo.saveItemDetail(cfgA, "book-1", detail("book-1"))

        val cfgB = config(user = "user-b")
        assertNull(repo.loadItemDetail(cfgB, "book-1"))
    }

    @Test
    fun load_returnsNullForMalformedStoredJson() = runCacheTest {
        val dataStore = fakeDataStore()
        val repo = AudiobookCacheRepository(context = null, dataStoreProvider = { dataStore })
        runBlocking {
            dataStore.edit { it[stringPreferencesKey("audiobook_library_cache")] = "{not-json" }
        }
        assertNull(repo.load(config(user = "user-1")))
    }

    private fun config(user: String): AudiobookShelfConfig {
        return AudiobookShelfConfig(
            serverUrl = "http://example.test",
            username = user,
            password = "secret"
        )
    }

    private fun library(id: String) = AudiobookLibrarySummary(id = id, name = id, mediaType = "book")

    private fun summary(id: String) = AudiobookItemSummary(
        id = id,
        libraryId = "lib-1",
        title = id,
        author = "Author",
        narrator = "Narrator",
        series = "",
        coverUrl = null,
        durationSeconds = 120,
        chapterCount = 1,
        updatedAtMillis = 0
    )

    private fun detail(id: String) = AudiobookItemDetail(
        id = id,
        libraryId = "lib-1",
        title = id,
        subtitle = "",
        description = "",
        authors = listOf("Author"),
        narrators = listOf("Narrator"),
        series = emptyList(),
        coverUrl = null,
        durationSeconds = 120,
        chapters = emptyList(),
        progress = null
    )

    private fun newRepo(): AudiobookCacheRepository {
        return AudiobookCacheRepository(context = null, dataStoreProvider = { fakeDataStore() })
    }
}
