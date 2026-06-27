package com.nordic.mediahub.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudiobookBookmarkRepositoryTest {
    @Test
    fun addAudiobookBookmark_sortsNewestFirstAndScopesByLibraryItem() {
        val current = listOf(
            bookmark(id = "old", libraryItemId = "book-1", createdAtMillis = 100L),
            bookmark(id = "other", libraryItemId = "book-2", createdAtMillis = 300L)
        )

        val updated = addAudiobookBookmark(
            current = current,
            bookmark = bookmark(id = "new", libraryItemId = "book-1", positionSeconds = 120, createdAtMillis = 500L)
        )

        assertEquals(listOf("new", "old"), bookmarksForItem(updated, "book-1").map { it.id })
        assertEquals(listOf("other"), bookmarksForItem(updated, "book-2").map { it.id })
        assertEquals(120, bookmarksForItem(updated, "book-1").first().positionSeconds)
    }

    @Test
    fun addAudiobookBookmark_boundsBookmarksPerAudiobook() {
        val current = (1..3).map { index ->
            bookmark(
                id = "book-1-$index",
                libraryItemId = "book-1",
                createdAtMillis = index.toLong()
            )
        } + bookmark(id = "book-2-1", libraryItemId = "book-2", createdAtMillis = 20L)

        val updated = addAudiobookBookmark(
            current = current,
            bookmark = bookmark(id = "book-1-new", libraryItemId = "book-1", createdAtMillis = 10L),
            maxPerItem = 3
        )

        assertEquals(listOf("book-1-new", "book-1-3", "book-1-2"), bookmarksForItem(updated, "book-1").map { it.id })
        assertEquals(listOf("book-2-1"), bookmarksForItem(updated, "book-2").map { it.id })
    }

    @Test
    fun deleteAudiobookBookmark_removesMatchingBookmarkOnly() {
        val current = listOf(
            bookmark(id = "keep-1", libraryItemId = "book-1"),
            bookmark(id = "delete", libraryItemId = "book-1"),
            bookmark(id = "keep-2", libraryItemId = "book-2")
        )

        val updated = deleteAudiobookBookmark(current, "delete")

        assertEquals(listOf("keep-1"), bookmarksForItem(updated, "book-1").map { it.id })
        assertEquals(listOf("keep-2"), bookmarksForItem(updated, "book-2").map { it.id })
    }

    @Test
    fun parseAudiobookBookmarksJson_returnsEmptyListForMalformedJson() {
        assertTrue(parseAudiobookBookmarksJson("{not-json").isEmpty())
    }

    @Test
    fun parseAudiobookBookmarksJson_filtersInvalidRowsAndNormalizesValues() {
        val parsed = parseAudiobookBookmarksJson(
            """
                [
                  {"id":" valid ","libraryItemId":" book-1 ","positionSeconds":-12,"label":" note ","createdAtMillis":5},
                  {"id":"","libraryItemId":"book-1","positionSeconds":10,"createdAtMillis":6},
                  {"id":"missing-book","libraryItemId":"","positionSeconds":10,"createdAtMillis":7}
                ]
            """.trimIndent()
        )

        assertEquals(1, parsed.size)
        assertEquals("valid", parsed.single().id)
        assertEquals("book-1", parsed.single().libraryItemId)
        assertEquals(0, parsed.single().positionSeconds)
        assertEquals("note", parsed.single().label)
    }

    private fun bookmark(
        id: String,
        libraryItemId: String,
        positionSeconds: Int = 30,
        label: String = "",
        createdAtMillis: Long = 100L
    ): AudiobookBookmark {
        return AudiobookBookmark(
            id = id,
            libraryItemId = libraryItemId,
            positionSeconds = positionSeconds,
            label = label,
            createdAtMillis = createdAtMillis
        )
    }
}
