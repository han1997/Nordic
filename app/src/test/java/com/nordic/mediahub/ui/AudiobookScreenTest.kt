package com.nordic.mediahub.ui

import com.nordic.mediahub.data.AudiobookChapter
import com.nordic.mediahub.data.AudiobookItemDetail
import com.nordic.mediahub.data.AudiobookItemSummary
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
    fun resolveAudiobookSelectedItemAfterLibraryRefresh_keepsItemStillPresent() {
        val selectedItem = detail("book-2")

        val resolved = resolveAudiobookSelectedItemAfterLibraryRefresh(
            selectedItem = selectedItem,
            items = listOf(summary("book-1"), summary("book-2"))
        )

        assertEquals(selectedItem, resolved)
    }

    @Test
    fun resolveAudiobookSelectedItemAfterLibraryRefresh_clearsItemWhenAbsent() {
        val selectedItem = detail("book-2")

        val resolved = resolveAudiobookSelectedItemAfterLibraryRefresh(
            selectedItem = selectedItem,
            items = listOf(summary("book-1"), summary("book-3"))
        )

        assertNull(resolved)
    }

    @Test
    fun resolveAudiobookLibraryPageAfterRefresh_returnsHomeWhenOpenDetailIsCleared() {
        val page = resolveAudiobookLibraryPageAfterRefresh(
            currentPage = AudiobookLibraryPage.Detail,
            previousSelectedItem = detail("book-2"),
            refreshedSelectedItem = null
        )

        assertEquals(AudiobookLibraryPage.Home, page)
    }

    @Test
    fun resolveAudiobookLibraryPageAfterRefresh_keepsDetailWhenSelectionRemains() {
        val selectedItem = detail("book-2")

        val page = resolveAudiobookLibraryPageAfterRefresh(
            currentPage = AudiobookLibraryPage.Detail,
            previousSelectedItem = selectedItem,
            refreshedSelectedItem = selectedItem
        )

        assertEquals(AudiobookLibraryPage.Detail, page)
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

    @Test
    fun sortAudiobookDetailChapters_ordersByStartTime() {
        val chapters = listOf(
            chapter(id = 3, startSeconds = 240),
            chapter(id = 1, startSeconds = 0),
            chapter(id = 2, startSeconds = 120)
        )

        val sorted = sortAudiobookDetailChapters(chapters)

        assertEquals(listOf(1, 2, 3), sorted.map { it.id })
    }

    @Test
    fun sortAudiobookDetailChapters_preservesOriginalOrderForEqualStarts() {
        val chapters = listOf(
            chapter(id = 2, startSeconds = 120),
            chapter(id = 1, startSeconds = 120),
            chapter(id = 3, startSeconds = 240)
        )

        val sorted = sortAudiobookDetailChapters(chapters)

        assertEquals(listOf(2, 1, 3), sorted.map { it.id })
    }

    @Test
    fun sortAudiobookDetailChapters_keepsEmptyListEmpty() {
        assertEquals(emptyList<AudiobookChapter>(), sortAudiobookDetailChapters(emptyList()))
    }

    private fun library(id: String): AudiobookLibrarySummary {
        return AudiobookLibrarySummary(
            id = id,
            name = id,
            mediaType = "book"
        )
    }

    private fun summary(id: String): AudiobookItemSummary {
        return AudiobookItemSummary(
            id = id,
            libraryId = "library-1",
            title = id,
            author = "Author",
            narrator = "Narrator",
            series = "",
            coverUrl = null,
            durationSeconds = 120,
            chapterCount = 1,
            updatedAtMillis = 0
        )
    }

    private fun detail(id: String): AudiobookItemDetail {
        return AudiobookItemDetail(
            id = id,
            libraryId = "library-1",
            title = id,
            subtitle = "",
            description = "",
            authors = listOf("Author"),
            narrators = listOf("Narrator"),
            series = emptyList(),
            coverUrl = null,
            durationSeconds = 120,
            chapters = emptyList(),
            progress = null
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
