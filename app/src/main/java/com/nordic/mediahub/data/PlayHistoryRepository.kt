package com.nordic.mediahub.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first

private const val PLAY_HISTORY_MAX_ENTRIES = 50

data class PlayHistoryEntry(
    val songId: String,
    val timestamp: Long,
    val playCount: Int = 1
)

class PlayHistoryRepository(private val context: Context) {
    private val gson = Gson()
    private val historyKey = stringPreferencesKey("navidrome_play_history")

    suspend fun load(): List<PlayHistoryEntry> {
        val json = context.dataStore.data.first()[historyKey] ?: return emptyList()
        return runCatching {
            val type = object : TypeToken<List<PlayHistoryEntry>>() {}.type
            val entries = gson.fromJson<List<PlayHistoryEntry>>(json, type) ?: emptyList()
            entries.map { entry ->
                entry.copy(playCount = entry.playCount.coerceAtLeast(1))
            }
        }.getOrNull() ?: emptyList()
    }

    suspend fun recordPlay(songId: String) {
        val current = load()
        val existing = current.firstOrNull { it.songId == songId }
        val updated = PlayHistoryEntry(
            songId = songId,
            timestamp = System.currentTimeMillis(),
            playCount = (existing?.playCount ?: 0) + 1
        )
        val trimmed = (listOf(updated) + current.filterNot { it.songId == songId })
            .take(PLAY_HISTORY_MAX_ENTRIES)
        save(trimmed)
    }

    private suspend fun save(entries: List<PlayHistoryEntry>) {
        context.dataStore.edit {
            it[historyKey] = gson.toJson(entries)
        }
    }
}
