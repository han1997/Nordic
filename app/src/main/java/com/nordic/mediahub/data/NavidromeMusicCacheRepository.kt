package com.nordic.mediahub.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.nordic.mediahub.api.NavidromeAlbum
import com.nordic.mediahub.api.NavidromeArtist
import com.nordic.mediahub.api.NavidromeSong
import kotlinx.coroutines.flow.first

private const val MUSIC_CACHE_SCHEMA_VERSION = 1

data class NavidromeMusicCache(
    val configKey: String = "",
    val updatedAtMillis: Long = 0L,
    val albums: List<NavidromeAlbum> = emptyList(),
    val songs: List<NavidromeSong> = emptyList(),
    val artists: List<NavidromeArtist> = emptyList()
)

class NavidromeMusicCacheRepository(private val context: Context) {
    private val gson = Gson()
    private val musicCacheKey = stringPreferencesKey("navidrome_music_cache")

    suspend fun load(config: NavidromeConfig): NavidromeMusicCache? {
        val json = context.dataStore.data.first()[musicCacheKey] ?: return null
        return runCatching {
            gson.fromJson(json, NavidromeMusicCache::class.java)
        }.getOrNull()
            ?.takeIf { it.configKey == config.cacheKey() }
            ?.takeIf { it.albums.isNotEmpty() || it.songs.isNotEmpty() || it.artists.isNotEmpty() }
    }

    suspend fun save(config: NavidromeConfig, cache: NavidromeMusicCache) {
        context.dataStore.edit {
            it[musicCacheKey] = gson.toJson(cache.copy(configKey = config.cacheKey()))
        }
    }

    fun buildCache(
        config: NavidromeConfig,
        albums: List<NavidromeAlbum>,
        songs: List<NavidromeSong>,
        artists: List<NavidromeArtist>
    ): NavidromeMusicCache {
        return NavidromeMusicCache(
            configKey = config.cacheKey(),
            updatedAtMillis = System.currentTimeMillis(),
            albums = albums,
            songs = songs,
            artists = artists
        )
    }
}

fun NavidromeConfig.cacheKey(): String {
    val normalizedUrl = normalizedBaseUrl().lowercase()
    val normalizedUser = username.trim().lowercase()
    return "$normalizedUrl|$normalizedUser|v$MUSIC_CACHE_SCHEMA_VERSION"
}
