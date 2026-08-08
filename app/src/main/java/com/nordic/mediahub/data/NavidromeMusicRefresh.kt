package com.nordic.mediahub.data

import androidx.compose.runtime.Stable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

@Stable
data class NavidromeMusicRefreshData(
    val albums: List<NavidromeAlbum>,
    val songs: List<NavidromeSong>,
    val recentlyAddedSongs: List<NavidromeSong>,
    val artists: List<NavidromeArtist>
)

interface NavidromeMusicDataSource {
    suspend fun getRecentAlbums(): List<NavidromeAlbum>
    suspend fun getRecentlyAddedSongs(albums: List<NavidromeAlbum>): List<NavidromeSong>
    suspend fun getAllSongs(): List<NavidromeSong>
    suspend fun getArtists(): List<NavidromeArtist>
}

suspend fun loadNavidromeMusicRefresh(
    targetConfig: NavidromeConfig,
    savedConfig: NavidromeConfig? = null,
    savedRepository: NavidromeMusicDataSource? = null,
    repositoryFactory: (NavidromeConfig) -> NavidromeMusicDataSource = { NavidromeRepository(it) }
): NavidromeMusicRefreshData? {
    if (!targetConfig.isReadyForMusicSync()) return null

    val repository = if (targetConfig == savedConfig && savedRepository != null) {
        savedRepository
    } else {
        repositoryFactory(targetConfig)
    }
    return coroutineScope {
        val freshAlbums = repository.getRecentAlbums()
        val recentlyAddedSongs = async { repository.getRecentlyAddedSongs(freshAlbums) }
        val songs = async { repository.getAllSongs() }
        val artists = async { repository.getArtists() }

        NavidromeMusicRefreshData(
            albums = freshAlbums,
            recentlyAddedSongs = recentlyAddedSongs.await(),
            songs = songs.await(),
            artists = artists.await()
        )
    }
}
