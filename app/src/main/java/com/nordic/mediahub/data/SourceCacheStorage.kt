package com.nordic.mediahub.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.flow.first

internal fun sourcePreferenceKey(prefix: String, sourceId: String) =
    stringPreferencesKey(if (sourceId.isBlank()) prefix else "${prefix}_$sourceId")

/** Move only a cache whose recorded old account identity matches this migrated source. */
internal suspend fun readSourceCacheJson(
    store: DataStore<Preferences>, prefix: String, sourceId: String, legacyIdentity: String, identity: String
): String? {
    val key = sourcePreferenceKey(prefix, sourceId)
    store.data.first()[key]?.let { return it }
    if (sourceId.isBlank()) return null
    var result: String? = null
    store.edit { prefs ->
        prefs[key]?.let { result = it; return@edit }
        val oldKey = stringPreferencesKey(prefix)
        val old = prefs[oldKey] ?: return@edit
        val root = runCatching { JsonParser.parseString(old).asJsonObject }.getOrNull() ?: return@edit
        if (root.get("configKey")?.asString != legacyIdentity) return@edit
        root.addProperty("configKey", identity)
        bindCachedMediaToSource(root, sourceId)
        result = root.toString()
        prefs[key] = result!!
        prefs.remove(oldKey)
    }
    return result
}

/** Cache migrations strip credentials and rebuild source-bound media identifiers, not CDN signatures. */
internal fun bindCachedMediaToSource(value: JsonElement, sourceId: String) {
    when {
        value.isJsonArray -> value.asJsonArray.forEach { bindCachedMediaToSource(it, sourceId) }
        value.isJsonObject -> {
            val obj: JsonObject = value.asJsonObject
            obj.entrySet().toList().forEach { (key, child) ->
                if (child.isJsonPrimitive && child.asJsonPrimitive.isString &&
                    (key.endsWith("url", true) || key == "coverArt")) {
                    val url = child.asString
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        obj.add(key, JsonPrimitive(stripAuthQuery(url).forMediaSource(sourceId)))
                    }
                } else bindCachedMediaToSource(child, sourceId)
            }
            if (obj.has("id") && (obj.has("streamUrl") || obj.has("title"))) obj.addProperty("sourceId", sourceId)
        }
    }
}
/** Eager upgrade hygiene: old account-bearing cache keys must not survive merely because a tab was never opened. */
internal suspend fun migrateLegacySourceCaches(store: DataStore<Preferences>, state: MediaSourceState) {
    val marker = booleanPreferencesKey("source_cache_migration_v1")
    if (store.data.first()[marker] == true) return
    store.edit { prefs ->
        if (prefs[marker] == true) return@edit
        val prefixes = listOf("navidrome_music_cache", "audiobook_library_cache", "emby_video_cache")
        prefixes.forEach { prefix ->
            val oldKey = stringPreferencesKey(prefix)
            val raw = prefs[oldKey] ?: return@forEach
            val json = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull()
            val oldIdentity = json?.get("configKey")?.asString
            val source = state.sources.firstOrNull { source -> when {
                prefix == "navidrome_music_cache" && source.kind == MediaSourceKind.NAVIDROME -> source.navidromeConfig().copy(sourceId = "").cacheKey() == oldIdentity
                prefix == "audiobook_library_cache" && source.kind == MediaSourceKind.AUDIOBOOKSHELF -> source.audiobookConfig().copy(sourceId = "").cacheKey() == oldIdentity
                prefix == "emby_video_cache" && source.kind == MediaSourceKind.EMBY -> source.videoConfig().copy(sourceId = "").cacheKey() == oldIdentity
                else -> false
            } }
            if (source != null && json != null) {
                val target = sourcePreferenceKey(prefix, source.id)
                if (prefs[target] == null) {
                    val identity = when (source.kind) {
                        MediaSourceKind.NAVIDROME -> source.navidromeConfig().cacheKey()
                        MediaSourceKind.AUDIOBOOKSHELF -> source.audiobookConfig().cacheKey()
                        else -> source.videoConfig().cacheKey()
                    }
                    json.addProperty("configKey", identity)
                    bindCachedMediaToSource(json, source.id)
                    prefs[target] = json.toString()
                }
            }
            // Unattributable catalog caches are disposable; history, bookmarks and downloads are untouched.
            prefs.remove(oldKey)
        }
        prefs[marker] = true
    }
}