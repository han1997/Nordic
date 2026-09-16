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
    fun resolveAudiobookLibraryPageAfterConfigChange_returnsHomeForEveryPage() {
        AudiobookLibraryPage.values().forEach { page ->
            assertEquals(AudiobookLibraryPage.Home, resolveAudiobookLibraryPageAfterConfigChange(page))
        }
    }

    @Test
    fun shouldShowAudiobookDetailInvalidationNotice_onlyWhenOpenSelectionDisappears() {
        val selected = detail("book-2")

        assertEquals(
            true,
            shouldShowAudiobookDetailInvalidationNotice(
                currentPage = AudiobookLibraryPage.Detail,
                previousSelectedItem = selected,
                refreshedSelectedItem = null
            )
        )
        assertEquals(
            false,
            shouldShowAudiobookDetailInvalidationNotice(
                currentPage = AudiobookLibraryPage.Home,
                previousSelectedItem = selected,
                refreshedSelectedItem = null
            )
        )
    }

    @Test
    fun shouldShowAudiobookConfigResetNotice_whenDetailWouldCollapse() {
        assertEquals(
            true,
            shouldShowAudiobookConfigResetNotice(
                previousConfigChanged = true,
                libraryPage = AudiobookLibraryPage.Detail,
                selectedItem = null
            )
        )
    }

    @Test
    fun shouldShowAudiobookConfigResetNotice_whenSelectionWouldClear() {
        assertEquals(
            true,
            shouldShowAudiobookConfigResetNotice(
                previousConfigChanged = true,
                libraryPage = AudiobookLibraryPage.Home,
                selectedItem = detail("book-1")
            )
        )
    }

    @Test
    fun shouldShowAudiobookConfigResetNotice_ignoresFirstLaunchAndHomeState() {
        assertEquals(
            false,
            shouldShowAudiobookConfigResetNotice(
                previousConfigChanged = false,
                libraryPage = AudiobookLibraryPage.Detail,
                selectedItem = detail("book-1")
            )
        )
        assertEquals(
            false,
            shouldShowAudiobookConfigResetNotice(
                previousConfigChanged = true,
                libraryPage = AudiobookLibraryPage.Home,
                selectedItem = null
            )
        )
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
    fun resolveCurrentAudiobookChapterFromSorted_returnsLastChapterAtOrBeforePosition() {
        val sortedChapters = listOf(
            chapter(id = 1, startSeconds = 0),
            chapter(id = 2, startSeconds = 100),
            chapter(id = 3, startSeconds = 200)
        )

        val currentChapter = resolveCurrentAudiobookChapterFromSorted(
            sortedChapters = sortedChapters,
            positionSeconds = 150
        )

        assertEquals(2, currentChapter?.id)
    }

    @Test
    fun resolveCurrentAudiobookChapterFromSorted_returnsNullBeforeFirstChapter() {
        val currentChapter = resolveCurrentAudiobookChapterFromSorted(
            sortedChapters = listOf(chapter(id = 1, startSeconds = 30)),
            positionSeconds = 10
        )

        assertNull(currentChapter)
    }

    @Test
    fun resolveCurrentAudiobookChapterFromSorted_clampsNegativePositionToStart() {
        val currentChapter = resolveCurrentAudiobookChapterFromSorted(
            sortedChapters = listOf(chapter(id = 1, startSeconds = 30)),
            positionSeconds = -5
        )

        assertNull(currentChapter)
    }

    @Test
    fun resolveCurrentAudiobookChapterFromSorted_doesNotReSortUnsortedInput() {
        val unsortedChapters = listOf(
            chapter(id = 2, startSeconds = 100),
            chapter(id = 1, startSeconds = 0)
        )

        val currentChapter = resolveCurrentAudiobookChapterFromSorted(
            sortedChapters = unsortedChapters,
            positionSeconds = 150
        )

        assertEquals(1, currentChapter?.id)
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

    @Test
    fun sleepTimerRemainingLabel_reportsInactiveWhenNoTimer() {
        assertEquals("未开启", sleepTimerRemainingLabel(sleepTimerRemainingSeconds = null, atChapterEnd = false))
    }

    @Test
    fun sleepTimerRemainingLabel_reportsChapterEndMode() {
        assertEquals(
            "将在当前章节结束时停止",
            sleepTimerRemainingLabel(sleepTimerRemainingSeconds = null, atChapterEnd = true)
        )
    }

    @Test
    fun sleepTimerRemainingLabel_reportsStoppedWhenExpired() {
        assertEquals("已停止", sleepTimerRemainingLabel(sleepTimerRemainingSeconds = 0, atChapterEnd = false))
    }

    @Test
    fun sleepTimerRemainingLabel_formatsCountdown() {
        assertEquals("45秒后停止", sleepTimerRemainingLabel(sleepTimerRemainingSeconds = 45, atChapterEnd = false))
        assertEquals("1分30秒后停止", sleepTimerRemainingLabel(sleepTimerRemainingSeconds = 90, atChapterEnd = false))
        assertEquals("10分0秒后停止", sleepTimerRemainingLabel(sleepTimerRemainingSeconds = 600, atChapterEnd = false))
    }

    @Test
    fun resolveCurrentAudiobookChapter_matchesDetailProgressForHighlight() {
        // The detail chapter list highlights the same chapter the player resolves,
        // so both call sites must agree on the position → chapter mapping.
        val chapters = listOf(chapter(id = 1, startSeconds = 0), chapter(id = 2, startSeconds = 1800), chapter(id = 3, startSeconds = 3600))

        val current = resolveCurrentAudiobookChapter(sortAudiobookDetailChapters(chapters), 4211)

        // Chapter 3 starts at 3600, at or before 4211; chapter 2 would require a
        // position within [1800, 3600).
        assertEquals(3, current?.id)
        // Position is clamped to >= 0, so the first chapter (start == 0) still resolves.
        assertEquals(1, resolveCurrentAudiobookChapter(sortAudiobookDetailChapters(chapters), -1)?.id)
        assertEquals(2, resolveCurrentAudiobookChapter(sortAudiobookDetailChapters(chapters), 1800 + 99)?.id)
    }

    @Test
    fun resolveCurrentAudiobookChapter_returnsLastChapterAtOrBeforePosition() {
        val chapters = listOf(chapter(id = 1, startSeconds = 0), chapter(id = 2, startSeconds = 1800))

        val current = resolveCurrentAudiobookChapter(sortAudiobookDetailChapters(chapters), 1800)

        assertEquals(2, current?.id)
    }

    @Test
    fun audiobookAuthorLabel_normalizesBlankAuthorsToUnknown() {
        assertEquals("未知作者", audiobookAuthorLabel(""))
        assertEquals("未知作者", audiobookAuthorLabel("   "))
        assertEquals("未知作者", audiobookAuthorLabel(null))
        assertEquals("刘慈欣", audiobookAuthorLabel(" 刘慈欣 "))
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
