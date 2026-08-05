package com.nordic.mediahub.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import kotlinx.coroutines.flow.first

private const val MUSIC_CACHE_SCHEMA_VERSION = 4

data class NavidromeMusicCache(
    val configKey: String = "",
    val updatedAtMillis: Long = 0L,
    val albums: List<NavidromeAlbum> = emptyList(),
    val songs: List<NavidromeSong> = emptyList(),
    val recentlyAddedSongs: List<NavidromeSong> = emptyList(),
    val artists: List<NavidromeArtist> = emptyList(),
    val albumDetailSongsById: Map<String, List<NavidromeSong>> = emptyMap(),
    val artistAlbumsById: Map<String, List<NavidromeAlbum>> = emptyMap(),
    val playlistSongsById: Map<String, List<NavidromeSong>> = emptyMap()
)

class NavidromeMusicCacheRepository(
    context: Context? = null,
    private val dataStoreProvider: () -> DataStore<Preferences> = {
        requireNotNull(context) { "context is required when no dataStoreProvider is given" }.dataStore
    }
) {
    private val gson = Gson()
    private val musicCacheKey = stringPreferencesKey("navidrome_music_cache")
    private val dataStore: DataStore<Preferences> by lazy { dataStoreProvider() }

    suspend fun load(config: NavidromeConfig): NavidromeMusicCache? {
        val cached = loadRaw(config) ?: return null
        return cached.takeIf {
            it.albums.isNotEmpty() || it.songs.isNotEmpty() || it.artists.isNotEmpty()
        }
    }

    suspend fun save(config: NavidromeConfig, cache: NavidromeMusicCache) {
        editCache(config) { existing ->
            // Browse refresh rebuilds the browse fields but must not wipe the
            // config-scoped detail caches written by detail open flows. When the
            // incoming browse cache carries no detail maps (the normal case from
            // buildCache), keep the existing detail maps untouched.
            cache.copy(
                configKey = config.cacheKey(),
                albumDetailSongsById = if (cache.albumDetailSongsById.isEmpty()) existing.albumDetailSongsById else cache.albumDetailSongsById,
                artistAlbumsById = if (cache.artistAlbumsById.isEmpty()) existing.artistAlbumsById else cache.artistAlbumsById,
                playlistSongsById = if (cache.playlistSongsById.isEmpty()) existing.playlistSongsById else cache.playlistSongsById
            )
        }
    }

    fun buildCache(
        config: NavidromeConfig,
        albums: List<NavidromeAlbum>,
        songs: List<NavidromeSong>,
        recentlyAddedSongs: List<NavidromeSong>,
        artists: List<NavidromeArtist>
    ): NavidromeMusicCache {
        return NavidromeMusicCache(
            configKey = config.cacheKey(),
            updatedAtMillis = System.currentTimeMillis(),
            albums = albums,
            songs = songs,
            recentlyAddedSongs = recentlyAddedSongs,
            artists = artists
        )
    }

    suspend fun loadAlbumDetailSongs(config: NavidromeConfig, albumId: String): List<NavidromeSong>? {
        return loadRaw(config)?.albumDetailSongsById?.get(albumId)
    }

    suspend fun saveAlbumDetailSongs(config: NavidromeConfig, albumId: String, songs: List<NavidromeSong>) {
        editCache(config) { existing ->
            existing.copy(albumDetailSongsById = existing.albumDetailSongsById + (albumId to songs))
        }
    }

    suspend fun loadArtistAlbums(config: NavidromeConfig, artistId: String): List<NavidromeAlbum>? {
        return loadRaw(config)?.artistAlbumsById?.get(artistId)
    }

    suspend fun saveArtistAlbums(config: NavidromeConfig, artistId: String, albums: List<NavidromeAlbum>) {
        editCache(config) { existing ->
            existing.copy(artistAlbumsById = existing.artistAlbumsById + (artistId to albums))
        }
    }

    suspend fun loadPlaylistSongs(config: NavidromeConfig, playlistId: String): List<NavidromeSong>? {
        return loadRaw(config)?.playlistSongsById?.get(playlistId)
    }

    suspend fun savePlaylistSongs(config: NavidromeConfig, playlistId: String, songs: List<NavidromeSong>) {
        editCache(config) { existing ->
            existing.copy(playlistSongsById = existing.playlistSongsById + (playlistId to songs))
        }
    }

    /**
     * Removes the persisted cache JSON when it belongs to [config]. Used on
     * saved-config switches so caches from a previous server/account do not
     * accumulate in DataStore. Never clears a cache that belongs to a different
     * config (e.g. the freshly-saved new config).
     */
    suspend fun clear(config: NavidromeConfig) {
        dataStore.edit { prefs ->
            val current = prefs[musicCacheKey]?.let { parseOrNull(it) }
            if (current?.configKey == config.cacheKey()) {
                prefs.remove(musicCacheKey)
            }
        }
    }

    private suspend fun loadRaw(config: NavidromeConfig): NavidromeMusicCache? {
        val json = dataStore.data.first()[musicCacheKey] ?: return null
        return parseOrNull(json)?.takeIf { it.configKey == config.cacheKey() }
    }

    private suspend fun editCache(
        config: NavidromeConfig,
        transform: (NavidromeMusicCache) -> NavidromeMusicCache
    ) {
        dataStore.edit { prefs ->
            val current = prefs[musicCacheKey]
                ?.let { parseOrNull(it) }
                ?.takeIf { it.configKey == config.cacheKey() }
                ?: NavidromeMusicCache(configKey = config.cacheKey())
            prefs[musicCacheKey] = gson.toJson(transform(current))
        }
    }

    private fun parseOrNull(json: String): NavidromeMusicCache? {
        return runCatching { gson.fromJson(json, NavidromeMusicCache::class.java) }.getOrNull()
    }
}

fun NavidromeConfig.cacheKey(): String {
    val normalizedUrl = normalizedBaseUrl().lowercase()
    val normalizedUser = username.trim().lowercase()
    return "$normalizedUrl|$normalizedUser|v$MUSIC_CACHE_SCHEMA_VERSION"
}
