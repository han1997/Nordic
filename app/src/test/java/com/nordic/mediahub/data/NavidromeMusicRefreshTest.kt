package com.nordic.mediahub.data

import com.nordic.mediahub.api.NavidromeAlbum
import com.nordic.mediahub.api.NavidromeArtist
import com.nordic.mediahub.api.NavidromeSong
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
