package com.nordic.mediahub.playback

import com.nordic.mediahub.data.MusicLyrics
import com.nordic.mediahub.data.MusicLyricsLine
import com.nordic.mediahub.data.NavidromeSong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MusicLyricsControllerTest {
    private fun song(id: String = "song") = NavidromeSong(id = id, title = "Title", artist = "Artist")
    private fun lyrics(text: String) = MusicLyrics(listOf(MusicLyricsLine(text = text)))

    @Test
    fun loadingAndContent_publishOneStateForTheCurrentSong() = runTest {
        val response = CompletableDeferred<MusicLyrics?>()
        val controller = MusicLyricsController<String>(backgroundScope) { _, _ -> response.await() }
        controller.select(song(), "server")
        assertEquals(MusicLyricsUiState.Loading("song"), controller.state.value)
        runCurrent()
        response.complete(lyrics("Body"))
        runCurrent()
        assertEquals("Body", (controller.state.value as MusicLyricsUiState.Content).lyrics.lines.single().text)
    }

    @Test
    fun lateCancelledRequest_cannotOverwriteNewSongAndDoesNotBlockIt() = runTest {
        val oldResponse = CompletableDeferred<Unit>()
        val controller = MusicLyricsController<String>(backgroundScope) { _, track ->
            if (track.id == "old") withContext(NonCancellable) { oldResponse.await() }
            lyrics(track.id)
        }
        controller.select(song("old"), "server")
        runCurrent()
        try {
            controller.select(song("new"), "server")
            assertEquals(MusicLyricsUiState.Loading("new"), controller.state.value)
            runCurrent()
            val current = controller.state.value
            assertEquals("new", (current as MusicLyricsUiState.Content).lyrics.lines.single().text)
            oldResponse.complete(Unit)
            runCurrent()
            assertEquals(current, controller.state.value)
        } finally {
            oldResponse.complete(Unit)
        }
    }

    @Test
    fun sourceChange_invalidatesSameSongAndCancelsOldRequest() = runTest {
        var cancelled = false
        val controller = MusicLyricsController<String>(backgroundScope) { source, _ ->
            if (source == "old") try { awaitCancellation() } finally { cancelled = true }
            lyrics(source)
        }
        controller.select(song(), "old")
        runCurrent()
        controller.select(song(), "new")
        runCurrent()
        assertTrue(cancelled)
        assertEquals("new", (controller.state.value as MusicLyricsUiState.Content).lyrics.lines.single().text)
    }

    @Test
    fun metadataKey_ignoresFavoritesButReloadsFallbackQueryChanges() = runTest {
        var calls = 0
        val controller = MusicLyricsController<String>(backgroundScope) { _, _ -> calls++; lyrics("Body") }
        controller.select(song(), "server")
        runCurrent()
        controller.select(song().copy(starred = "2026-09-10"), "server")
        runCurrent()
        assertEquals(1, calls)
        controller.select(song().copy(artist = "Other"), "server")
        runCurrent()
        controller.select(song().copy(title = "Other"), "server")
        runCurrent()
        assertEquals(3, calls)
    }

    @Test
    fun retry_onlyRunsForRetryableErrorsAndDoesNotDuplicateLoading() = runTest {
        var calls = 0
        val response = CompletableDeferred<MusicLyrics?>()
        val controller = MusicLyricsController<String>(backgroundScope) { _, _ ->
            calls++
            if (calls == 1) error("network failure")
            response.await()
        }
        controller.select(song(), "server")
        runCurrent()
        assertTrue((controller.state.value as MusicLyricsUiState.Error).canRetry)
        controller.retry()
        controller.retry()
        runCurrent()
        assertEquals(2, calls)
        response.complete(null)
        runCurrent()
        assertEquals(MusicLyricsUiState.Empty("song"), controller.state.value)
        controller.retry()
        runCurrent()
        assertEquals(2, calls)
    }

    @Test
    fun missingConfiguration_isNotAnEmptyOrRetryableResult() = runTest {
        var calls = 0
        val controller = MusicLyricsController<String>(backgroundScope) { _, _ -> calls++; null }
        controller.select(song(), null)
        val state = controller.state.value as MusicLyricsUiState.Error
        assertEquals("未配置 Navidrome", state.message)
        assertFalse(state.canRetry)
        controller.retry()
        runCurrent()
        assertEquals(0, calls)
    }

    @Test
    fun clearingSong_cancelsLoadingWithoutPublishingAnError() = runTest {
        var cancelled = false
        val controller = MusicLyricsController<String>(backgroundScope) { _, _ ->
            try { awaitCancellation() } finally { cancelled = true }
        }
        controller.select(song(), "server")
        runCurrent()
        controller.select(null, "server")
        runCurrent()
        assertTrue(cancelled)
        assertEquals(MusicLyricsUiState.Idle, controller.state.value)
    }

    @Test
    fun displayChoice_survivesTrackAndSourceChangesButNotANewSession() = runTest {
        val controller = MusicLyricsController<String>(backgroundScope) { _, _ -> null }
        assertFalse(controller.showLyrics.value)
        controller.toggleDisplay()
        controller.select(song(), "first")
        controller.select(song("next"), "second")
        runCurrent()
        assertTrue(controller.showLyrics.value)
        val newSession = MusicLyricsController<String>(backgroundScope) { _, _ -> null }
        assertFalse(newSession.showLyrics.value)
    }

    @Test
    fun close_preventsLateNonCancellableResultFromPublishing() = runTest {
        val response = CompletableDeferred<Unit>()
        val controller = MusicLyricsController<String>(backgroundScope) { _, _ ->
            withContext(NonCancellable) { response.await() }
            lyrics("Late")
        }
        controller.select(song(), "server")
        runCurrent()
        val before = controller.state.value
        controller.close()
        response.complete(Unit)
        runCurrent()
        assertEquals(before, controller.state.value)
    }
}
