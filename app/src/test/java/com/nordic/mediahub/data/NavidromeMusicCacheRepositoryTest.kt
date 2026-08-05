package com.nordic.mediahub.data

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavidromeMusicCacheRepositoryTest {

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
            albums = listOf(album("a1")),
            songs = listOf(song("s1")),
            recentlyAddedSongs = listOf(song("r1")),
            artists = listOf(artist("ar1"))
        )
        repo.save(cfg, cache)

        val loaded = repo.load(cfg)
        assertNotNull(loaded)
        assertEquals(listOf("a1"), loaded!!.albums.map { it.id })
        assertEquals(listOf("s1"), loaded.songs.map { it.id })
        assertEquals(listOf("r1"), loaded.recentlyAddedSongs.map { it.id })
        assertEquals(listOf("ar1"), loaded.artists.map { it.id })
        assertTrue(loaded.updatedAtMillis > 0L)
        assertEquals(cfg.cacheKey(), loaded.configKey)
    }

    @Test
    fun load_returnsNullForMismatchedConfig_configScopedIsolation() = runCacheTest {
        val repo = newRepo()
        val cfgA = config(user = "user-a")
        repo.save(cfgA, repo.buildCache(cfgA, albums = listOf(album("a1")), songs = listOf(song("s1")), recentlyAddedSongs = emptyList(), artists = emptyList()))

        // A different user at the same URL must not see user-a's cache.
        val cfgB = config(user = "user-b")
        assertNull(repo.load(cfgB))
    }

    @Test
    fun clear_removesCacheForMatchingConfig() = runCacheTest {
        val repo = newRepo()
        val cfg = config(user = "user-1")
        repo.save(cfg, repo.buildCache(cfg, albums = listOf(album("a1")), songs = listOf(song("s1")), recentlyAddedSongs = emptyList(), artists = emptyList()))
        assertNotNull(repo.load(cfg))

        repo.clear(cfg)

        assertNull(repo.load(cfg))
    }

    @Test
    fun clear_doesNotRemoveCacheForDifferentConfig() = runCacheTest {
        val repo = newRepo()
        val cfgA = config(user = "user-a")
        val cfgB = config(user = "user-b")
        repo.save(cfgA, repo.buildCache(cfgA, albums = listOf(album("a1")), songs = listOf(song("s1")), recentlyAddedSongs = emptyList(), artists = emptyList()))
        repo.save(cfgB, repo.buildCache(cfgB, albums = listOf(album("a2")), songs = listOf(song("s2")), recentlyAddedSongs = emptyList(), artists = emptyList()))

        // Clearing cfgA must leave cfgB intact (single shared DataStore key).
        repo.clear(cfgA)

        assertNull(repo.load(cfgA))
        val loadedB = repo.load(cfgB)
        assertNotNull(loadedB)
        assertEquals(listOf("a2"), loadedB!!.albums.map { it.id })
    }

    @Test
    fun detailCache_saveAndLoadAlbumDetailSongsByKey() = runCacheTest {
        val repo = newRepo()
        val cfg = config(user = "user-1")
        repo.save(cfg, repo.buildCache(cfg, albums = listOf(album("a1")), songs = listOf(song("s1")), recentlyAddedSongs = emptyList(), artists = emptyList()))

        assertNull(repo.loadAlbumDetailSongs(cfg, "album-1"))
        repo.saveAlbumDetailSongs(cfg, "album-1", listOf(song("track-1"), song("track-2")))

        val loaded = repo.loadAlbumDetailSongs(cfg, "album-1")
        assertNotNull(loaded)
        assertEquals(listOf("track-1", "track-2"), loaded!!.map { it.id })
    }

    @Test
    fun detailCache_saveAndLoadArtistAlbumsByKey() = runCacheTest {
        val repo = newRepo()
        val cfg = config(user = "user-1")
        repo.save(cfg, repo.buildCache(cfg, albums = listOf(album("a1")), songs = listOf(song("s1")), recentlyAddedSongs = emptyList(), artists = emptyList()))

        repo.saveArtistAlbums(cfg, "artist-1", listOf(album("aa1"), album("aa2")))

        val loaded = repo.loadArtistAlbums(cfg, "artist-1")
        assertEquals(listOf("aa1", "aa2"), loaded!!.map { it.id })
    }

    @Test
    fun detailCache_saveAndLoadPlaylistSongsByKey() = runCacheTest {
        val repo = newRepo()
        val cfg = config(user = "user-1")
        repo.save(cfg, repo.buildCache(cfg, albums = listOf(album("a1")), songs = listOf(song("s1")), recentlyAddedSongs = emptyList(), artists = emptyList()))

        repo.savePlaylistSongs(cfg, "playlist-1", listOf(song("ps1")))

        val loaded = repo.loadPlaylistSongs(cfg, "playlist-1")
        assertEquals(listOf("ps1"), loaded!!.map { it.id })
    }

    @Test
    fun detailCache_browseRefreshPreservesExistingDetailMaps() = runCacheTest {
        val repo = newRepo()
        val cfg = config(user = "user-1")
        repo.save(cfg, repo.buildCache(cfg, albums = listOf(album("a1")), songs = listOf(song("s1")), recentlyAddedSongs = emptyList(), artists = emptyList()))
        repo.saveAlbumDetailSongs(cfg, "album-1", listOf(song("track-1")))

        // A browse refresh rebuilds the browse fields but must not wipe detail caches.
        repo.save(cfg, repo.buildCache(cfg, albums = listOf(album("a2")), songs = listOf(song("s2")), recentlyAddedSongs = emptyList(), artists = emptyList()))

        val loaded = repo.load(cfg)
        assertEquals(listOf("a2"), loaded!!.albums.map { it.id })
        val detail = repo.loadAlbumDetailSongs(cfg, "album-1")
        assertEquals(listOf("track-1"), detail!!.map { it.id })
    }

    @Test
    fun detailCache_isConfigScoped() = runCacheTest {
        val repo = newRepo()
        val cfgA = config(user = "user-a")
        repo.save(cfgA, repo.buildCache(cfgA, albums = listOf(album("a1")), songs = listOf(song("s1")), recentlyAddedSongs = emptyList(), artists = emptyList()))
        repo.saveAlbumDetailSongs(cfgA, "album-1", listOf(song("track-1")))

        val cfgB = config(user = "user-b")
        // user-b must not read user-a's detail cache.
        assertNull(repo.loadAlbumDetailSongs(cfgB, "album-1"))
    }

    @Test
    fun load_returnsNullForMalformedStoredJson() = runCacheTest {
        val dataStore = fakeDataStore()
        val repo = NavidromeMusicCacheRepository(context = null, dataStoreProvider = { dataStore })
        // Write malformed JSON directly into the preference key the repo reads.
        runBlocking {
            dataStore.edit { it[stringPreferencesKey("navidrome_music_cache")] = "{not-json" }
        }
        assertNull(repo.load(config(user = "user-1")))
    }

    private fun config(user: String): NavidromeConfig {
        return NavidromeConfig(
            serverUrl = "http://example.test",
            username = user,
            password = "secret"
        )
    }

    private fun album(id: String) = NavidromeAlbum(id = id, name = id)
    private fun song(id: String) = NavidromeSong(id = id, title = id)
    private fun artist(id: String) = NavidromeArtist(id = id, name = id)

    private fun newRepo(): NavidromeMusicCacheRepository {
        return NavidromeMusicCacheRepository(context = null, dataStoreProvider = { fakeDataStore() })
    }
}
