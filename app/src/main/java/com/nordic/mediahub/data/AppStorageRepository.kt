package com.nordic.mediahub.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import coil.imageLoader
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

internal data class DownloadSourceSummary(val sourceId: String, val name: String, val count: Int, val bytes: Long)
internal data class StorageSummary(val imageBytes: Long, val catalogBytes: Long, val downloads: List<DownloadSourceSummary>)
internal data class LegacyDataSummary(val musicHistory: Int, val bookmarks: Int, val downloads: Int, val hasRecords: Boolean)

@OptIn(coil.annotation.ExperimentalCoilApi::class)
internal class AppStorageRepository(private val context: Context) {
    private val store = context.dataStore
    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        withContext(Dispatchers.IO) { store.edit { block(it) } }
    }
    private val cachePrefixes = listOf("navidrome_music_cache", "audiobook_library_cache", "emby_video_cache", "webdav_browse")

    suspend fun inspect(sources: MediaSourceState): StorageSummary = withContext(Dispatchers.IO) {
        val prefs = store.data.first()
        val catalogBytes = prefs.asMap().entries.filter { entry -> cachePrefixes.any { entry.key.name == it || entry.key.name.startsWith("${it}_") } }
            .sumOf { entry ->
                val raw = entry.value.toString()
                val payload = if (entry.key.name.startsWith("webdav_browse")) {
                    runCatching { JsonParser.parseString(raw).asJsonObject.getAsJsonObject("directories") }
                        .getOrNull()?.takeIf { it.size() > 0 }?.toString().orEmpty()
                } else raw
                payload.toByteArray(Charsets.UTF_8).size.toLong()
            }
        val root = musicDownloadRoot(context)
        val known = sources.sources.filter { it.kind == MediaSourceKind.NAVIDROME }.associateBy { it.id }
        val ids = linkedSetOf<String>().apply {
            addAll(known.keys)
            root.listFiles()?.filter { it.isDirectory && it.name.matches(Regex("[A-Za-z0-9_-]{1,100}")) }?.forEach { add(it.name) }
            if (audioFiles(root).isNotEmpty()) add("")
        }
        val downloads = ids.map { id ->
            val files = audioFiles(musicDownloadDirectory(context, id))
            DownloadSourceSummary(id, known[id]?.name ?: if (id.isBlank()) "待归属旧下载" else "已移除来源 · ${id.take(8)}",
                files.size, files.sumOf { it.length() })
        }
        StorageSummary(context.imageLoader.diskCache?.size ?: 0, catalogBytes, downloads)
    }
    suspend fun clearImages() = withContext(Dispatchers.IO) {
        context.imageLoader.memoryCache?.clear()
        context.imageLoader.diskCache?.clear()
    }
    suspend fun clearCatalogs() {
        edit { prefs ->
            prefs.asMap().keys.toList().forEach { key ->
                val name = key.name
                if (name == "webdav_browse" || name.startsWith("webdav_browse_")) {
                    val typed = stringPreferencesKey(name)
                    val root = runCatching { JsonParser.parseString(prefs[typed]).asJsonObject }.getOrNull()
                    // Favorites are user data, not disposable directory cache.
                    root?.let { it.add("directories", JsonObject()); prefs[typed] = it.toString() }
                } else if (cachePrefixes.take(3).any { name == it || name.startsWith("${it}_") }) prefs.remove(stringPreferencesKey(name))
            }
        }
    }
    suspend fun clearLocalRecords(source: MediaSource) {
        edit { prefs ->
            when (source.domain) {
                MediaDomain.MUSIC -> prefs.remove(sourcePreferenceKey("navidrome_play_history", source.id))
                MediaDomain.AUDIOBOOK -> prefs.remove(sourcePreferenceKey("audiobook_bookmarks", source.id))
                MediaDomain.VIDEO -> prefs.remove(sourcePreferenceKey("webdav_progress", source.id))
            }
        }
    }
    suspend fun legacySummary(): LegacyDataSummary = withContext(Dispatchers.IO) {
        val prefs = store.data.first()
        fun count(key: String) = prefs[stringPreferencesKey(key)]?.let {
            runCatching { JsonParser.parseString(it).asJsonArray.size() }.getOrDefault(0)
        } ?: 0
        LegacyDataSummary(count("navidrome_play_history"), count("audiobook_bookmarks"), audioFiles(musicDownloadRoot(context)).size,
            prefs[stringPreferencesKey("navidrome_play_history")] != null || prefs[stringPreferencesKey("audiobook_bookmarks")] != null)
    }
    suspend fun assignLegacy(source: MediaSource): Int = withContext(Dispatchers.IO) {
        require(source.domain != MediaDomain.VIDEO)
        val prefix = if (source.domain == MediaDomain.MUSIC) "navidrome_play_history" else "audiobook_bookmarks"
        val identity = if (source.domain == MediaDomain.MUSIC) "songId" else "id"
        edit { prefs ->
            val oldKey = stringPreferencesKey(prefix)
            val old = prefs[oldKey] ?: return@edit
            val target = sourcePreferenceKey(prefix, source.id)
            val legacy = JsonParser.parseString(old).asJsonArray
            val current = prefs[target]?.let { JsonParser.parseString(it).asJsonArray } ?: JsonArray()
            val merged = JsonArray()
            val ids = mutableSetOf<String>()
            (current.toList() + legacy.toList()).forEach { row ->
                val id = row.asJsonObject.get(identity)?.asString?.takeIf { it.isNotBlank() }
                    ?: throw IOException("旧记录格式不完整，原数据已保留")
                if (ids.add(id)) merged.add(row)
            }
            prefs[target] = merged.toString()
            prefs.remove(oldKey)
        }
        if (source.domain == MediaDomain.MUSIC) {
            val count = assignLegacyMusicDownloads(musicDownloadRoot(context), source.id)
            MusicDownloadManagers.get(context, source.id).restoreDownloadState()
            count
        } else 0
    }
    suspend fun clearLegacyRecords() {
        edit { prefs ->
            prefs.remove(stringPreferencesKey("navidrome_play_history"))
            prefs.remove(stringPreferencesKey("audiobook_bookmarks"))
        }
    }
}

private fun audioFiles(directory: File): List<File> = directory.listFiles()?.filter { it.isFile && isDownloadedMusicFile(it.name) } ?: emptyList()

/** Explicit user assignment only. Never overwrite an existing source download or move a directory. */
internal fun assignLegacyMusicDownloads(root: File, sourceId: String): Int {
    require(sourceId.matches(Regex("[A-Za-z0-9_-]{1,100}")))
    val canonicalRoot = root.canonicalFile
    val targetDirectory = File(canonicalRoot, sourceId).canonicalFile
    require(targetDirectory.parentFile == canonicalRoot)
    if (!targetDirectory.exists() && !targetDirectory.mkdirs()) throw IOException("无法创建来源下载目录")
    var moved = 0
    audioFiles(canonicalRoot).forEach { file ->
        if (file.canonicalFile.parentFile != canonicalRoot) return@forEach
        val stem = file.name.substringBeforeLast('.')
        val oldMetadata = File(canonicalRoot, musicDownloadMetadataFileName(stem))
        val song = loadDownloadedSongMetadata(oldMetadata) ?: NavidromeSong(stem, file.name)
        val newStem = safeMusicFileId(song.id)
        val target = File(targetDirectory, "$newStem.${file.extension}").canonicalFile
        require(target.parentFile == targetDirectory)
        if (target.exists()) return@forEach
        if (!file.renameTo(target)) throw IOException("移动旧下载失败，未移动的文件仍保留")
        val assigned = song.copy(sourceId = sourceId,
            streamUrl = song.streamUrl?.let { stripAuthQuery(it).forMediaSource(sourceId) },
            coverArt = song.coverArt?.let { stripAuthQuery(it).forMediaSource(sourceId) })
        File(targetDirectory, musicDownloadMetadataFileName(newStem)).writeText(Gson().toJson(assigned), Charsets.UTF_8)
        oldMetadata.takeIf { it.exists() && it.canonicalFile.parentFile == canonicalRoot }?.delete()
        moved++
    }
    return moved
}