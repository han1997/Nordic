package com.nordic.mediahub.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import kotlinx.coroutines.flow.first

private const val VIDEO_CACHE_SCHEMA_VERSION = 2

data class EmbyVideoCache(
    val configKey: String = "",
    val updatedAtMillis: Long = 0L,
    val libraries: List<VideoLibrary> = emptyList(),
    val videos: List<VideoItem> = emptyList(),
    val selectedLibraryId: String? = null,
    val resumeVideos: List<VideoItem> = emptyList()
)

class EmbyVideoCacheRepository(
    context: Context? = null,
    private val dataStoreProvider: () -> DataStore<Preferences> = {
        requireNotNull(context) { "context is required when no dataStoreProvider is given" }.dataStore
    }
) {
    private val gson = Gson()
    private val videoCacheKey = stringPreferencesKey("emby_video_cache")
    private val dataStore: DataStore<Preferences> by lazy { dataStoreProvider() }

    suspend fun load(config: VideoServerConfig): EmbyVideoCache? {
        val cached = loadRaw(config) ?: return null
        return cached.takeIf {
            it.libraries.isNotEmpty() || it.videos.isNotEmpty() || it.resumeVideos.isNotEmpty()
        }
    }

    suspend fun save(config: VideoServerConfig, cache: EmbyVideoCache) {
        // Video detail derives from cached `videos`; there is no separate detail
        // cache to preserve. Browse fields are authoritative — write the incoming
        // cache directly so an empty refresh (e.g. the user deleted every video
        // library) is reflected in the cache instead of keeping stale content.
        // This matches the Music/Audiobook browse-cache save contract.
        dataStore.edit { prefs ->
            prefs[videoCacheKey] = gson.toJson(cache.copy(configKey = config.cacheKey()))
        }
    }

    /**
     * Persists the server's continue-watching response (`Items/Resume`). The
     * list is cached as-is so the next cold start can render the last known
     * server data (cache-then-network) instead of deriving a shelf from the
     * local catalog cache, which may carry stale progress.
     */
    suspend fun saveResumeItems(config: VideoServerConfig, items: List<VideoItem>) {
        val current = loadRaw(config) ?: return
        dataStore.edit { prefs ->
            prefs[videoCacheKey] = gson.toJson(
                current.copy(resumeVideos = items, configKey = config.cacheKey())
            )
        }
    }

    fun buildCache(
        config: VideoServerConfig,
        libraries: List<VideoLibrary>,
        videos: List<VideoItem>,
        selectedLibraryId: String?
    ): EmbyVideoCache {
        return EmbyVideoCache(
            configKey = config.cacheKey(),
            updatedAtMillis = System.currentTimeMillis(),
            libraries = libraries,
            videos = videos,
            selectedLibraryId = selectedLibraryId
        )
    }

    /**
     * Removes the persisted cache JSON when it belongs to [config]. Used on
     * saved-config switches so caches from a previous Emby server/account do not
     * accumulate in DataStore. Never clears a cache that belongs to a different
     * config (e.g. the freshly-saved new config).
     */
    suspend fun clear(config: VideoServerConfig) {
        dataStore.edit { prefs ->
            val current = prefs[videoCacheKey]?.let { parseOrNull(it) }
            if (current?.configKey == config.cacheKey()) {
                prefs.remove(videoCacheKey)
            }
        }
    }

    private suspend fun loadRaw(config: VideoServerConfig): EmbyVideoCache? {
        val json = dataStore.data.first()[videoCacheKey] ?: return null
        return parseOrNull(json)?.takeIf { it.configKey == config.cacheKey() }
    }

    private fun parseOrNull(json: String): EmbyVideoCache? {
        return runCatching { gson.fromJson(json, EmbyVideoCache::class.java) }.getOrNull()
    }
}

fun VideoServerConfig.cacheKey(): String {
    val normalizedUrl = normalizedBaseUrl().lowercase()
    // Emby auth is either an API key or a username/password pair. Two configs at
    // the same URL can map to different users (different api keys, or different
    // usernames), so the cache identity must distinguish them. Prefer the API key
    // when present (it is the primary auth when both are set) and fall back to the
    // username for password-login configs.
    val normalizedApiKey = apiKey.trim()
    val normalizedUser = username.trim().lowercase()
    val identity = if (normalizedApiKey.isNotBlank()) "key:$normalizedApiKey" else "user:$normalizedUser"
    return "$normalizedUrl|$identity|v$VIDEO_CACHE_SCHEMA_VERSION"
}
