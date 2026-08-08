package com.nordic.mediahub.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavidromeMusicRefreshTest {
    @Test
    fun loadNavidromeMusicRefresh_usesTargetConfigInsteadOfSavedRepositoryForNewConfig() = runTest {
        val savedConfig = readyConfig(username = "old-user")
        val targetConfig = readyConfig(username = "new-user")
        val savedRepository = FakeNavidromeMusicDataSource("old")
        val factoryConfigs = mutableListOf<NavidromeConfig>()

        val result = requireNotNull(
            loadNavidromeMusicRefresh(
                targetConfig = targetConfig,
                savedConfig = savedConfig,
                savedRepository = savedRepository,
                repositoryFactory = { config ->
                    factoryConfigs += config
                    FakeNavidromeMusicDataSource(config.username)
                }
            )
        )

        assertEquals(listOf(targetConfig), factoryConfigs)
        assertFalse(savedRepository.wasUsed)
        assertEquals(listOf("new-user-album"), result.albums.map { it.id })
        assertEquals(listOf("new-user-song"), result.songs.map { it.id })
        assertEquals(listOf("new-user-recent"), result.recentlyAddedSongs.map { it.id })
        assertEquals(listOf("new-user-artist"), result.artists.map { it.id })
    }

    @Test
    fun loadNavidromeMusicRefresh_reusesSavedRepositoryWhenTargetConfigMatches() = runTest {
        val savedConfig = readyConfig(username = "saved-user")
        val savedRepository = FakeNavidromeMusicDataSource("saved")
        var factoryWasUsed = false

        val result = requireNotNull(
            loadNavidromeMusicRefresh(
                targetConfig = savedConfig,
                savedConfig = savedConfig,
                savedRepository = savedRepository,
                repositoryFactory = {
                    factoryWasUsed = true
                    FakeNavidromeMusicDataSource("factory")
                }
            )
        )

        assertFalse(factoryWasUsed)
        assertTrue(savedRepository.wasUsed)
        assertEquals(listOf("saved-album"), result.albums.map { it.id })
    }

    @Test
    fun loadNavidromeMusicRefresh_assemblesAllFourFetchesIntoRefreshData() = runTest {
        val config = readyConfig(username = "demo")
        val repository = RecordingNavidromeMusicDataSource()

        val result = requireNotNull(
            loadNavidromeMusicRefresh(
                targetConfig = config,
                savedConfig = config,
                savedRepository = repository
            )
        )

        assertEquals(listOf("album-1", "album-2"), result.albums.map { it.id })
        assertEquals(listOf("recent-song-1", "recent-song-2"), result.recentlyAddedSongs.map { it.id })
        assertEquals(listOf("song-1", "song-2", "song-3"), result.songs.map { it.id })
        assertEquals(listOf("artist-1", "artist-2"), result.artists.map { it.id })

        assertEquals(1, repository.recentAlbumsCalls)
        assertEquals(1, repository.recentlyAddedSongsCalls)
        assertEquals(1, repository.allSongsCalls)
        assertEquals(1, repository.artistsCalls)
    }

    private fun readyConfig(username: String): NavidromeConfig {
        return NavidromeConfig(
            serverUrl = "http://example.test",
            username = username,
            password = "secret"
        )
    }
}

private class FakeNavidromeMusicDataSource(
    private val label: String
) : NavidromeMusicDataSource {
    var wasUsed: Boolean = false
        private set

    override suspend fun getRecentAlbums(): List<NavidromeAlbum> {
        wasUsed = true
        return listOf(NavidromeAlbum(id = "$label-album", name = "$label album"))
    }

    override suspend fun getRecentlyAddedSongs(albums: List<NavidromeAlbum>): List<NavidromeSong> {
        wasUsed = true
        return listOf(NavidromeSong(id = "$label-recent", title = "$label recent"))
    }

    override suspend fun getAllSongs(): List<NavidromeSong> {
        wasUsed = true
        return listOf(NavidromeSong(id = "$label-song", title = "$label song"))
    }

    override suspend fun getArtists(): List<NavidromeArtist> {
        wasUsed = true
        return listOf(NavidromeArtist(id = "$label-artist", name = "$label artist"))
    }
}

private class RecordingNavidromeMusicDataSource : NavidromeMusicDataSource {
    var recentAlbumsCalls = 0
        private set
    var recentlyAddedSongsCalls = 0
        private set
    var allSongsCalls = 0
        private set
    var artistsCalls = 0
        private set

    override suspend fun getRecentAlbums(): List<NavidromeAlbum> {
        recentAlbumsCalls++
        return listOf(
            NavidromeAlbum(id = "album-1", name = "Album One"),
            NavidromeAlbum(id = "album-2", name = "Album Two")
        )
    }

    override suspend fun getRecentlyAddedSongs(albums: List<NavidromeAlbum>): List<NavidromeSong> {
        recentlyAddedSongsCalls++
        return listOf(
            NavidromeSong(id = "recent-song-1", title = "Recent Song One"),
            NavidromeSong(id = "recent-song-2", title = "Recent Song Two")
        )
    }

    override suspend fun getAllSongs(): List<NavidromeSong> {
        allSongsCalls++
        return listOf(
            NavidromeSong(id = "song-1", title = "Song One"),
            NavidromeSong(id = "song-2", title = "Song Two"),
            NavidromeSong(id = "song-3", title = "Song Three")
        )
    }

    override suspend fun getArtists(): List<NavidromeArtist> {
        artistsCalls++
        return listOf(
            NavidromeArtist(id = "artist-1", name = "Artist One"),
            NavidromeArtist(id = "artist-2", name = "Artist Two")
        )
    }
}
