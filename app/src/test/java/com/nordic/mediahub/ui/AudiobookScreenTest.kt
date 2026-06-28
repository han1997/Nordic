package com.nordic.mediahub.ui

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

    private fun library(id: String): AudiobookLibrarySummary {
        return AudiobookLibrarySummary(
            id = id,
            name = id,
            mediaType = "book"
        )
    }
}
