package com.nordic.mediahub.ui

import org.junit.Assert.*
import org.junit.Test

class MusicLibraryFeedbackTest {
    @Test fun initialLoadingHasOneFeedbackSurfaceInsteadOfAnEmptyResult() {
        val state = MusicLibraryFeedbackState(isInitialLoading = true)
        assertTrue(state.isVisible)
        assertTrue(state.suppressesEmptyState)
        assertNull(state.standaloneError)
        assertFalse(shouldShowMusicCollectionEmpty(false, 0, state.suppressesEmptyState))
    }

    @Test fun failedEmptyCollectionRetainsItsErrorAndDoesNotPretendToBeEmpty() {
        val state = MusicLibraryFeedbackState(error = "连接失败")
        assertEquals("连接失败", state.standaloneError)
        assertTrue(state.isVisible)
        assertFalse(shouldShowMusicCollectionEmpty(false, 0, state.suppressesEmptyState))
    }

    @Test fun cachedFailureUsesTheHeaderInsteadOfASecondFullPageError() {
        val state = MusicLibraryFeedbackState(error = "刷新失败", hasContent = true)
        assertNull(state.standaloneError)
        assertFalse(state.isVisible)
        assertTrue(state.suppressesEmptyState)
    }

    @Test fun successfulConfiguredEmptyLibraryCanShowItsOwnEmptyState() {
        val state = MusicLibraryFeedbackState()
        assertFalse(state.isVisible)
        assertFalse(state.suppressesEmptyState)
        assertTrue(shouldShowMusicCollectionEmpty(false, 0, state.suppressesEmptyState))
    }

    @Test fun unconfiguredLibraryDoesNotAlsoReportAnEmptyCollection() {
        val state = MusicLibraryFeedbackState(showSetup = true)
        assertTrue(state.isVisible)
        assertTrue(state.suppressesEmptyState)
    }

    @Test fun configurationNoticesDoNotTurnASuccessfulEmptyResultIntoAnError() {
        for (state in listOf(MusicLibraryFeedbackState(resetNotice = "配置已更新"),
            MusicLibraryFeedbackState(detailNotice = "详情已更新"))) {
            assertTrue(state.isVisible)
            assertFalse(state.suppressesEmptyState)
        }
    }
}
