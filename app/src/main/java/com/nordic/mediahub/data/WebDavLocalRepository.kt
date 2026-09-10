package com.nordic.mediahub.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal data class WebDavFolder(val path: String, val name: String)
internal data class WebDavBrowseState(
    val directories: Map<String, WebDavDirectory> = emptyMap(),
    val favorites: List<WebDavFolder> = emptyList(),
    val lastDirectory: String? = null
)
internal data class WebDavProgress(
    val path: String, val title: String, val positionSeconds: Int, val durationSeconds: Int,
    val updatedAtMillis: Long, val completed: Boolean = false, val contentVersion: String? = null
)
private data class WebDavHistory(val entries: List<WebDavProgress> = emptyList())

internal class WebDavLocalRepository(
    context: Context? = null,
    private val sourceId: String,
    private val dataStoreProvider: () -> DataStore<Preferences> = { requireNotNull(context).dataStore }
) {
    private val store by lazy { dataStoreProvider() }
    private val browseKey = sourcePreferenceKey("webdav_browse", sourceId)
    private val progressKey = sourcePreferenceKey("webdav_progress", sourceId)
    private val gson = Gson()
    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        withContext(Dispatchers.IO) { store.edit { block(it) } }
    }
    val browse: Flow<WebDavBrowseState> = store.data.map { decodeBrowse(it[browseKey]) }.flowOn(Dispatchers.IO)
    val progress: Flow<List<WebDavProgress>> = store.data.map { decodeHistory(it[progressKey]).entries }.flowOn(Dispatchers.IO)

    suspend fun directory(path: String): WebDavDirectory? = browse.first().directories[path]
    suspend fun saveDirectory(directory: WebDavDirectory) {
        edit { prefs ->
            val old = decodeBrowse(prefs[browseKey])
            val merged = old.directories.toMutableMap().apply {
                // Large listings remain browseable but are not copied into an unbounded JSON cache.
                if (directory.entries.size <= 2000) put(directory.path, directory) else remove(directory.path)
            }
            val kept = linkedMapOf<String, WebDavDirectory>()
            var count = 0
            for (entry in merged.values.sortedByDescending { it.fetchedAtMillis }) {
                if (kept.size >= 20 || count + entry.entries.size > 5000) continue
                kept[entry.path] = entry
                count += entry.entries.size
            }
            prefs[browseKey] = gson.toJson(old.copy(directories = kept, lastDirectory = directory.path))
        }
    }
    suspend fun toggleFavorite(path: String, name: String) {
        edit { prefs ->
            val old = decodeBrowse(prefs[browseKey])
            val next = if (old.favorites.any { it.path == path }) old.favorites.filterNot { it.path == path }
                else (old.favorites + WebDavFolder(path, name)).takeLast(100)
            prefs[browseKey] = gson.toJson(old.copy(favorites = next))
        }
    }
    suspend fun record(video: VideoItem, position: Int, duration: Int, completed: Boolean) {
        if (video.sourceType != VideoServerType.WEBDAV) return
        require(video.sourceId == sourceId) { "观看进度来源不匹配" }
        edit { prefs ->
            val old = decodeHistory(prefs[progressKey]).entries
            val record = WebDavProgress(video.id, video.title, position.coerceAtLeast(0), duration.coerceAtLeast(0),
                System.currentTimeMillis(), completed, video.contentVersion)
            prefs[progressKey] = gson.toJson(WebDavHistory((listOf(record) + old.filterNot { it.path == video.id }).take(50)))
        }
    }
    suspend fun removeProgress(path: String) {
        edit { prefs -> prefs[progressKey] = gson.toJson(WebDavHistory(decodeHistory(prefs[progressKey]).entries.filterNot { it.path == path })) }
    }
    private fun decodeBrowse(raw: String?): WebDavBrowseState = raw?.let {
        runCatching { gson.fromJson(it, WebDavBrowseState::class.java) }.getOrNull()
    } ?: WebDavBrowseState()
    private fun decodeHistory(raw: String?): WebDavHistory = raw?.let {
        runCatching { gson.fromJson(it, WebDavHistory::class.java) }.getOrNull()
    } ?: WebDavHistory()
}

internal fun resumeWebDavVideo(video: VideoItem, history: List<WebDavProgress>): VideoItem {
    val record = history.firstOrNull { it.path == video.id && !it.completed } ?: return video
    if (record.contentVersion != null && video.contentVersion != null && record.contentVersion != video.contentVersion) return video
    return video.copy(playbackPositionSeconds = record.positionSeconds,
        durationSeconds = record.durationSeconds)
}