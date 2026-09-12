package com.nordic.mediahub.data

import com.nordic.mediahub.playback.VideoAutoPlayNextController
import com.nordic.mediahub.playback.VideoAutoPlayNextState
import com.nordic.mediahub.playback.VideoPlaybackState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class WebDavPlaybackContextTest {
    private val first = WebDavEntry("/series/1.mkv", "1.mkv", false, etag = "v1")
    private val second = WebDavEntry("/series/2.mkv", "2.mkv", false, etag = "v2")
    private val last = WebDavEntry("/series/10.mkv", "10.mkv", false)
    private val subtitle = WebDavEntry("/series/2.zh.srt", "2.zh.srt", false)
    private val entries = listOf(last, first, subtitle, second, WebDavEntry("/series/folder/", "folder", true))

    @Test fun preparedDirectoryHasRealStreamsSubtitlesAndSourceLocalResumeData() {
        withRepository { repo ->
            val context = repo.prepareEpisodeContext(entries, listOf(progress(second, 120)))
            assertEquals(3, context.size)
            assertTrue(context.all { !it.streamUrl.isNullOrBlank() && it.sourceId == repo.sourceId && it.sourceType == VideoServerType.WEBDAV })
            val current = context.single { it.id == first.path }
            val next = resolveNextVideoEpisode(current, context)!!
            assertEquals(second.path, next.id)
            assertEquals(120, next.playbackPositionSeconds)
            assertEquals(600, next.durationSeconds)
            assertEquals("v2", next.contentVersion)
            assertTrue(next.streamUrl!!.startsWith("https://example.test/dav/series/2.mkv"))
            assertFalse(next.streamUrl!!.contains("secret"))
            val text = next.externalSubtitles.single()
            assertEquals("zh", text.language)
            assertTrue(text.url.contains("/series/2.zh.srt"))
            assertEquals("application/x-subrip", text.mimeType)
        }
    }

    @Test fun changedFileVersionsAndCompletedHistoryDoNotReuseOldPositions() {
        withRepository { repo ->
            val context = repo.prepareEpisodeContext(entries, listOf(
                progress(first, 50).copy(contentVersion = "old-version"),
                progress(second, 600).copy(completed = true)
            ))
            assertEquals(0, context.single { it.id == first.path }.playbackPositionSeconds)
            assertEquals(0, context.single { it.id == second.path }.playbackPositionSeconds)
        }
    }

    @Test fun displaySearchAndVideoOnlyFilterDoNotDefineTheCapturedPlaybackQueue() {
        withRepository { repo ->
            val visible = visibleWebDavEntries(entries, "1.mkv", AppPreferences(webDavOnlyVideos = true))
            assertEquals(listOf(first), visible)
            val context = repo.prepareEpisodeContext(entries, emptyList())
            val next = resolveNextVideoEpisode(context.single { it.id == first.path }, context)!!
            assertEquals(second.path, next.id)
            assertEquals(1, next.externalSubtitles.size)
        }
    }

    @Test fun realPreparedDirectoryFeedsTheEndToCountdownToNextPlaybackChain() = runTest {
        val sourceId = "context-${UUID.randomUUID()}"
        val repo = repository(sourceId)
        val controller = VideoAutoPlayNextController(this) { testScheduler.currentTime }
        try {
            val context = repo.prepareEpisodeContext(entries, listOf(progress(second, 120)))
            val current = context.single { it.id == first.path }
            val next = resolveNextVideoEpisode(current, context)
            controller.setEnabled(true)
            controller.setForeground(true)
            val playback = VideoPlaybackState(video = current, isPlaying = true)
            controller.updatePlayback(playback, next)
            controller.updatePlayback(playback.copy(isPlaying = false, hasEnded = true), next)
            advanceTimeBy(4_999)
            assertTrue(controller.state.value is VideoAutoPlayNextState.Countdown)
            advanceTimeBy(1)
            runCurrent()
            val request = (controller.state.value as VideoAutoPlayNextState.Ready).request
            assertEquals(second.path, request.next.id)
            assertEquals(120, request.next.playbackPositionSeconds)
            assertEquals(1, request.next.externalSubtitles.size)
            assertTrue(controller.claim(request))
            assertTrue(controller.canStart(request))
            controller.complete(request)
        } finally {
            controller.resetPlaybackCycle()
            ScopedMediaRegistry.remove(sourceId)
        }
    }

    private fun progress(entry: WebDavEntry, position: Int) = WebDavProgress(
        entry.path, entry.name, position, 600, 1L, contentVersion = entry.etag
    )
    private fun repository(sourceId: String) = WebDavRepository(VideoServerConfig(
        serverUrl = "https://example.test/dav/", username = "user", password = "secret",
        type = VideoServerType.WEBDAV, sourceId = sourceId
    ))
    private fun withRepository(block: (WebDavRepository) -> Unit) {
        val sourceId = "context-${UUID.randomUUID()}"
        try { block(repository(sourceId)) } finally { ScopedMediaRegistry.remove(sourceId) }
    }
}
