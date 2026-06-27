package com.nordic.mediahub.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first
import java.util.UUID

internal const val AUDIOBOOK_BOOKMARK_MAX_PER_ITEM = 50

data class AudiobookBookmark(
    val id: String,
    val libraryItemId: String,
    val positionSeconds: Int,
    val label: String = "",
    val createdAtMillis: Long = 0L
)

class AudiobookBookmarkRepository(private val context: Context) {
    private val gson = Gson()
    private val bookmarkKey = stringPreferencesKey("audiobook_bookmarks")

    suspend fun load(): List<AudiobookBookmark> {
        val json = context.dataStore.data.first()[bookmarkKey]
        return parseAudiobookBookmarksJson(json, gson)
    }

    suspend fun loadForItem(libraryItemId: String): List<AudiobookBookmark> {
        return bookmarksForItem(load(), libraryItemId)
    }

    suspend fun addBookmark(
        libraryItemId: String,
        positionSeconds: Int,
        label: String = ""
    ): List<AudiobookBookmark> {
        val trimmedItemId = libraryItemId.trim()
        if (trimmedItemId.isBlank()) return load()

        val bookmark = AudiobookBookmark(
            id = UUID.randomUUID().toString(),
            libraryItemId = trimmedItemId,
            positionSeconds = positionSeconds.coerceAtLeast(0),
            label = label.trim(),
            createdAtMillis = System.currentTimeMillis()
        )
        val updated = addAudiobookBookmark(load(), bookmark)
        save(updated)
        return bookmarksForItem(updated, trimmedItemId)
    }

    suspend fun deleteBookmark(bookmarkId: String): List<AudiobookBookmark> {
        val updated = deleteAudiobookBookmark(load(), bookmarkId)
        save(updated)
        return updated
    }

    private suspend fun save(bookmarks: List<AudiobookBookmark>) {
        context.dataStore.edit {
            it[bookmarkKey] = gson.toJson(bookmarks)
        }
    }
}

internal fun addAudiobookBookmark(
    current: List<AudiobookBookmark>,
    bookmark: AudiobookBookmark,
    maxPerItem: Int = AUDIOBOOK_BOOKMARK_MAX_PER_ITEM
): List<AudiobookBookmark> {
    val normalized = normalizeAudiobookBookmark(bookmark) ?: return current.sortedNewestFirst()
    val withoutSameId = current.filterNot { it.id == normalized.id }
    val itemBookmarks = (listOf(normalized) + withoutSameId.filter { it.libraryItemId == normalized.libraryItemId })
        .sortedNewestFirst()
        .take(maxPerItem.coerceAtLeast(1))
    val otherBookmarks = withoutSameId.filterNot { it.libraryItemId == normalized.libraryItemId }
    return (itemBookmarks + otherBookmarks).sortedNewestFirst()
}

internal fun deleteAudiobookBookmark(
    current: List<AudiobookBookmark>,
    bookmarkId: String
): List<AudiobookBookmark> {
    val trimmedId = bookmarkId.trim()
    if (trimmedId.isBlank()) return current.sortedNewestFirst()
    return current.filterNot { it.id == trimmedId }.sortedNewestFirst()
}

internal fun bookmarksForItem(
    current: List<AudiobookBookmark>,
    libraryItemId: String
): List<AudiobookBookmark> {
    val trimmedItemId = libraryItemId.trim()
    if (trimmedItemId.isBlank()) return emptyList()
    return current
        .mapNotNull(::normalizeAudiobookBookmark)
        .filter { it.libraryItemId == trimmedItemId }
        .sortedNewestFirst()
}

internal fun parseAudiobookBookmarksJson(
    json: String?,
    gson: Gson = Gson()
): List<AudiobookBookmark> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching {
        val type = object : TypeToken<List<AudiobookBookmark>>() {}.type
        gson.fromJson<List<AudiobookBookmark>>(json, type).orEmpty()
            .mapNotNull(::normalizeAudiobookBookmark)
            .sortedNewestFirst()
    }.getOrNull().orEmpty()
}

private fun normalizeAudiobookBookmark(bookmark: AudiobookBookmark): AudiobookBookmark? {
    val id = bookmark.id.orEmpty().trim()
    val libraryItemId = bookmark.libraryItemId.orEmpty().trim()
    if (id.isBlank() || libraryItemId.isBlank()) return null
    return bookmark.copy(
        id = id,
        libraryItemId = libraryItemId,
        positionSeconds = bookmark.positionSeconds.coerceAtLeast(0),
        label = bookmark.label.orEmpty().trim(),
        createdAtMillis = bookmark.createdAtMillis.coerceAtLeast(0L)
    )
}

private fun List<AudiobookBookmark>.sortedNewestFirst(): List<AudiobookBookmark> {
    return sortedWith(
        compareByDescending<AudiobookBookmark> { it.createdAtMillis }
            .thenBy { it.positionSeconds }
            .thenBy { it.id }
    )
}
