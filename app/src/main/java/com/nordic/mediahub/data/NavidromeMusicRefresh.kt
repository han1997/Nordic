package com.nordic.mediahub.data

import androidx.compose.runtime.Stable

import com.nordic.mediahub.api.NavidromeAlbum
import com.nordic.mediahub.api.NavidromeArtist
import com.nordic.mediahub.api.NavidromeSong

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
    val freshAlbums = repository.getRecentAlbums()

    return NavidromeMusicRefreshData(
        albums = freshAlbums,
        recentlyAddedSongs = repository.getRecentlyAddedSongs(freshAlbums),
        songs = repository.getAllSongs(),
        artists = repository.getArtists()
    )
}
