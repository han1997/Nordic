package com.nordic.mediahub.ui

import com.nordic.mediahub.data.AudiobookChapter
import com.nordic.mediahub.data.AudiobookLibrarySummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudiobookScreenTest {
    @Test
    fun resolveAudiobookSelectedLibraryId_keepsExistingSelection() {
        val libraries = listOf(
            library("library-1"),
            library("library-2")
        )

        val selectedLibraryId = resolveAudiobookSelectedLibraryId(
            currentLibraryId = "library-2",
            libraries = libraries
        )

        assertEquals("library-2", selectedLibraryId)
    }

    @Test
    fun resolveAudiobookSelectedLibraryId_fallsBackToFirstLibraryWhenSelectionIsStale() {
        val libraries = listOf(
            library("library-1"),
            library("library-2")
        )

        val selectedLibraryId = resolveAudiobookSelectedLibraryId(
            currentLibraryId = "old-library",
            libraries = libraries
        )

        assertEquals("library-1", selectedLibraryId)
    }

    @Test
    fun resolveAudiobookSelectedLibraryId_clearsSelectionWhenLibrariesAreEmpty() {
        val selectedLibraryId = resolveAudiobookSelectedLibraryId(
            currentLibraryId = "old-library",
            libraries = emptyList()
        )

        assertNull(selectedLibraryId)
    }

    @Test
    fun resolveCurrentAudiobookChapter_usesTimestampOrderForUnsortedChapters() {
        val chapters = listOf(
            chapter(id = 2, startSeconds = 100),
            chapter(id = 3, startSeconds = 200),
            chapter(id = 1, startSeconds = 0)
        )

        val currentChapter = resolveCurrentAudiobookChapter(
            chapters = chapters,
            positionSeconds = 150
        )

        assertEquals(2, currentChapter?.id)
    }

    @Test
    fun resolveCurrentAudiobookChapter_returnsNullBeforeFirstChapter() {
        val currentChapter = resolveCurrentAudiobookChapter(
            chapters = listOf(chapter(id = 1, startSeconds = 30)),
            positionSeconds = 10
        )

        assertNull(currentChapter)
    }

    @Test
    fun resolveCurrentAudiobookChapter_clampsNegativePositionToStart() {
        val currentChapter = resolveCurrentAudiobookChapter(
            chapters = listOf(chapter(id = 1, startSeconds = 30)),
            positionSeconds = -5
        )

        assertNull(currentChapter)
    }

    private fun library(id: String): AudiobookLibrarySummary {
        return AudiobookLibrarySummary(
            id = id,
            name = id,
            mediaType = "book"
        )
    }

    private fun chapter(id: Int, startSeconds: Int): AudiobookChapter {
        return AudiobookChapter(
            id = id,
            title = "Chapter $id",
            startSeconds = startSeconds,
            endSeconds = startSeconds + 99
        )
    }
}
