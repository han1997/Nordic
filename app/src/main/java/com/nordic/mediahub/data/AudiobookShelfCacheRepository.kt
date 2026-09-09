package com.nordic.mediahub.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import kotlinx.coroutines.flow.first

private const val AUDIOBOOK_CACHE_SCHEMA_VERSION = 2

data class AudiobookShelfCache(
    val configKey: String = "",
    val updatedAtMillis: Long = 0L,
    val libraries: List<AudiobookLibrarySummary> = emptyList(),
    val items: List<AudiobookItemSummary> = emptyList(),
    val selectedLibraryId: String? = null,
    val itemDetails: Map<String, AudiobookItemDetail> = emptyMap(),
    val itemsByLibrary: Map<String, List<AudiobookItemSummary>> = emptyMap(),
    val libraryFetchedAt: Map<String, Long> = emptyMap()
)

class AudiobookCacheRepository(
    context: Context? = null,
    private val dataStoreProvider: () -> DataStore<Preferences> = {
        requireNotNull(context) { "context is required when no dataStoreProvider is given" }.dataStore
    }
) {
    private val gson = Gson()
    private val audiobookCacheKey = stringPreferencesKey("audiobook_library_cache")
    private val dataStore: DataStore<Preferences> by lazy { dataStoreProvider() }

    suspend fun load(config: AudiobookShelfConfig): AudiobookShelfCache? {
        val cached = loadRaw(config) ?: return null
        return cached.takeIf { it.libraries.isNotEmpty() || it.items.isNotEmpty() }
    }

    suspend fun save(config: AudiobookShelfConfig, cache: AudiobookShelfCache) {
        editCache(config) { existing ->
            // Browse refresh rebuilds libraries/items but must not wipe config-scoped
            // item-detail caches written by openItemDetail. When the incoming browse
            // cache carries no detail map (the normal case from buildCache), keep the
            // existing detail map untouched.
            cache.copy(
                configKey = config.cacheKey(),
                itemDetails = if (cache.itemDetails.isEmpty()) existing.itemDetails else cache.itemDetails
            )
        }
    }

    fun buildCache(
        config: AudiobookShelfConfig,
        libraries: List<AudiobookLibrarySummary>,
        items: List<AudiobookItemSummary>,
        selectedLibraryId: String?,
        itemsByLibrary: Map<String, List<AudiobookItemSummary>> = emptyMap(),
        libraryFetchedAt: Map<String, Long> = emptyMap()
    ): AudiobookShelfCache {
        return AudiobookShelfCache(
            configKey = config.cacheKey(),
            updatedAtMillis = System.currentTimeMillis(),
            libraries = libraries,
            items = items,
            selectedLibraryId = selectedLibraryId,
            itemsByLibrary = itemsByLibrary,
            libraryFetchedAt = libraryFetchedAt
        )
    }

    /**
     * Persists one library's item list and stamps its fetch time, then evicts
     * the least-recently-fetched libraries beyond [LIBRARY_CACHE_MAX_ENTRIES].
     * Library switches render from this map instantly (cache-then-network)
     * instead of re-fetching the whole library on every switch.
     */
    suspend fun saveLibraryItems(
        config: AudiobookShelfConfig,
        libraryId: String,
        items: List<AudiobookItemSummary>
    ) {
        editCache(config) { existing ->
            val fetchedAt = System.currentTimeMillis()
            val mergedItems = existing.itemsByLibrary + (libraryId to items)
            val mergedStamps = existing.libraryFetchedAt + (libraryId to fetchedAt)
            val evicted = evictAudiobookLibrariesBeyondLimit(mergedItems, mergedStamps)
            existing.copy(
                itemsByLibrary = evicted.first,
                libraryFetchedAt = evicted.second
            )
        }
    }

    suspend fun loadItemDetail(config: AudiobookShelfConfig, itemId: String): AudiobookItemDetail? {
        return loadRaw(config)?.itemDetails?.get(itemId)
    }

    suspend fun saveItemDetail(config: AudiobookShelfConfig, itemId: String, detail: AudiobookItemDetail) {
        editCache(config) { existing ->
            existing.copy(itemDetails = existing.itemDetails + (itemId to detail))
        }
    }

    /**
     * Removes the persisted cache JSON when it belongs to [config]. Used on
     * saved-config switches so caches from a previous AudiobookShelf server/account
     * do not accumulate in DataStore. Never clears a cache that belongs to a
     * different config (e.g. the freshly-saved new config).
     */
    suspend fun clear(config: AudiobookShelfConfig) {
        dataStore.edit { prefs ->
            val current = prefs[audiobookCacheKey]?.let { parseOrNull(it) }
            if (current?.configKey == config.cacheKey()) {
                prefs.remove(audiobookCacheKey)
            }
        }
    }

    private suspend fun loadRaw(config: AudiobookShelfConfig): AudiobookShelfCache? {
        val json = dataStore.data.first()[audiobookCacheKey] ?: return null
        return parseOrNull(json)?.takeIf { it.configKey == config.cacheKey() }
    }

    private suspend fun editCache(
        config: AudiobookShelfConfig,
        transform: (AudiobookShelfCache) -> AudiobookShelfCache
    ) {
        dataStore.edit { prefs ->
            val current = prefs[audiobookCacheKey]
                ?.let { parseOrNull(it) }
                ?.takeIf { it.configKey == config.cacheKey() }
                ?: AudiobookShelfCache(configKey = config.cacheKey())
            prefs[audiobookCacheKey] = gson.toJson(transform(current))
        }
    }

    private fun parseOrNull(json: String): AudiobookShelfCache? {
        return runCatching { gson.fromJson(json, AudiobookShelfCache::class.java) }.getOrNull()
    }
}

fun AudiobookShelfConfig.cacheKey(): String {
    val normalizedUrl = normalizedBaseUrl().lowercase()
    val normalizedUser = username.trim().lowercase()
    return "$normalizedUrl|$normalizedUser|v$AUDIOBOOK_CACHE_SCHEMA_VERSION"
}

/**
 * Drops the stalest library entries when the merged map exceeds the cache
 * limit, ordered by fetch stamp (oldest evicted first). Same policy as the
 * video domain's [evictBeyondLimit].
 */
internal fun evictAudiobookLibrariesBeyondLimit(
    itemsByLibrary: Map<String, List<AudiobookItemSummary>>,
    libraryFetchedAt: Map<String, Long>,
    limit: Int = LIBRARY_CACHE_MAX_ENTRIES
): Pair<Map<String, List<AudiobookItemSummary>>, Map<String, Long>> {
    if (itemsByLibrary.size <= limit) return itemsByLibrary to libraryFetchedAt
    val kept = libraryFetchedAt.entries
        .sortedByDescending { it.value }
        .take(limit)
        .map { it.key }
        .toSet()
    return itemsByLibrary.filterKeys { it in kept } to
        libraryFetchedAt.filterKeys { it in kept }
}
