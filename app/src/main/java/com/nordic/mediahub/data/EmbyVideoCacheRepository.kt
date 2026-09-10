package com.nordic.mediahub.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import kotlinx.coroutines.flow.first

private const val VIDEO_CACHE_SCHEMA_VERSION = 3

/**
 * Upper bound on cached per-library item lists. Switching to a library not in
 * the cache falls back to the loading full fetch; keeping every visited
 * library forever would grow the DataStore JSON without limit.
 */
internal const val LIBRARY_CACHE_MAX_ENTRIES = 4

data class EmbyVideoCache(
    val configKey: String = "",
    val updatedAtMillis: Long = 0L,
    val libraries: List<VideoLibrary> = emptyList(),
    val videos: List<VideoItem> = emptyList(),
    val selectedLibraryId: String? = null,
    val resumeVideos: List<VideoItem> = emptyList(),
    val itemsByLibrary: Map<String, List<VideoItem>> = emptyMap(),
    val libraryFetchedAt: Map<String, Long> = emptyMap()
)

class EmbyVideoCacheRepository(
    context: Context? = null,
    private val dataStoreProvider: () -> DataStore<Preferences> = {
        requireNotNull(context) { "context is required when no dataStoreProvider is given" }.dataStore
    }
) {
    private val gson = Gson()
    private fun videoCacheKey(config: VideoServerConfig) = sourcePreferenceKey("emby_video_cache", config.sourceId)
    private val dataStore: DataStore<Preferences> by lazy { dataStoreProvider() }

    suspend fun load(config: VideoServerConfig): EmbyVideoCache? {
        val cached = loadRaw(config) ?: return null
        return cached.takeIf {
            it.libraries.isNotEmpty() || it.videos.isNotEmpty() ||
                it.resumeVideos.isNotEmpty() || it.itemsByLibrary.isNotEmpty()
        }
    }

    suspend fun save(config: VideoServerConfig, cache: EmbyVideoCache) {
        // Video detail derives from cached `videos`; there is no separate detail
        // cache to preserve. Browse fields are authoritative — write the incoming
        // cache directly so an empty refresh (e.g. the user deleted every video
        // library) is reflected in the cache instead of keeping stale content.
        // This matches the Music/Audiobook browse-cache save contract.
        dataStore.edit { prefs ->
            prefs[videoCacheKey(config)] = gson.toJson(cache.copy(configKey = config.cacheKey()))
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
            prefs[videoCacheKey(config)] = gson.toJson(
                current.copy(resumeVideos = items, configKey = config.cacheKey())
            )
        }
    }

    /**
     * Persists one library's item list and stamps its fetch time, then evicts
     * the least-recently-fetched libraries beyond [LIBRARY_CACHE_MAX_ENTRIES].
     * Library switches render from this map instantly (cache-then-network)
     * instead of re-paginating the whole library on every switch.
     */
    suspend fun saveLibraryItems(
        config: VideoServerConfig,
        libraryId: String,
        items: List<VideoItem>
    ) {
        val current = loadRaw(config) ?: return
        val fetchedAt = System.currentTimeMillis()
        val mergedItems = current.itemsByLibrary + (libraryId to items)
        val mergedStamps = current.libraryFetchedAt + (libraryId to fetchedAt)
        val evicted = evictBeyondLimit(mergedItems, mergedStamps)
        dataStore.edit { prefs ->
            prefs[videoCacheKey(config)] = gson.toJson(
                current.copy(
                    itemsByLibrary = evicted.first,
                    libraryFetchedAt = evicted.second,
                    configKey = config.cacheKey()
                )
            )
        }
    }

    fun buildCache(
        config: VideoServerConfig,
        libraries: List<VideoLibrary>,
        videos: List<VideoItem>,
        selectedLibraryId: String?,
        itemsByLibrary: Map<String, List<VideoItem>> = emptyMap(),
        libraryFetchedAt: Map<String, Long> = emptyMap()
    ): EmbyVideoCache {
        return EmbyVideoCache(
            configKey = config.cacheKey(),
            updatedAtMillis = System.currentTimeMillis(),
            libraries = libraries,
            videos = videos,
            selectedLibraryId = selectedLibraryId,
            itemsByLibrary = itemsByLibrary,
            libraryFetchedAt = libraryFetchedAt
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
            val current = prefs[videoCacheKey(config)]?.let { parseOrNull(it) }
            if (current?.configKey == config.cacheKey()) {
                prefs.remove(videoCacheKey(config))
            }
        }
    }

    private suspend fun loadRaw(config: VideoServerConfig): EmbyVideoCache? {
        val json = readSourceCacheJson(dataStore, "emby_video_cache", config.sourceId,
            config.copy(sourceId = "").cacheKey(), config.cacheKey()) ?: return null
        return parseOrNull(json)?.takeIf { it.configKey == config.cacheKey() }
    }

    private fun parseOrNull(json: String): EmbyVideoCache? {
        return runCatching { gson.fromJson(json, EmbyVideoCache::class.java) }.getOrNull()
    }
}

/**
 * Drops the stalest library entries when the merged map exceeds the cache
 * limit, ordered by fetch stamp (oldest evicted first). The freshly written
 * library always survives because its stamp is the newest.
 */
internal fun evictBeyondLimit(
    itemsByLibrary: Map<String, List<VideoItem>>,
    libraryFetchedAt: Map<String, Long>,
    limit: Int = LIBRARY_CACHE_MAX_ENTRIES
): Pair<Map<String, List<VideoItem>>, Map<String, Long>> {
    if (itemsByLibrary.size <= limit) return itemsByLibrary to libraryFetchedAt
    val kept = libraryFetchedAt.entries
        .sortedByDescending { it.value }
        .take(limit)
        .map { it.key }
        .toSet()
    return itemsByLibrary.filterKeys { it in kept } to
        libraryFetchedAt.filterKeys { it in kept }
}

fun VideoServerConfig.cacheKey(): String {
    if (sourceId.isNotBlank()) return "$sourceId|v$VIDEO_CACHE_SCHEMA_VERSION"
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
