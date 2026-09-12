package com.nordic.mediahub.playback

import com.nordic.mediahub.data.VideoItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VideoAutoPlayNextControllerTest {
    private val first = episode("first")
    private val second = episode("second").copy(playbackPositionSeconds = 120)
    private val playing = VideoPlaybackState(video = first, isPlaying = true, playWhenReady = true)
    private val ended = playing.copy(isPlaying = false, hasEnded = true)

    @Test fun defaultsOffAndEnablingAfterEndDoesNotStartRetroactively() = runTest {
        val c = VideoAutoPlayNextController(this) { testScheduler.currentTime }
        c.setForeground(true)
        c.updatePlayback(playing, second)
        c.updatePlayback(ended, second)
        c.setEnabled(true)
        c.updatePlayback(ended, second)
        advanceUntilIdle()
        assertFalse(c.state.value is VideoAutoPlayNextState.Countdown)
        assertFalse(c.state.value is VideoAutoPlayNextState.Ready)
    }

    @Test fun fullFiveSecondsAndSingleClaimPreserveTheNextResumePosition() = runTest {
        val c = controller()
        c.updatePlayback(ended, second)
        assertEquals(5, countdown(c).secondsRemaining)
        advanceTimeBy(4_999)
        assertEquals(1, countdown(c).secondsRemaining)
        advanceTimeBy(1)
        runCurrent()
        val request = ready(c).request
        assertEquals(120, request.next.playbackPositionSeconds)
        assertTrue(c.claim(request))
        assertFalse(c.claim(request))
        assertTrue(c.canStart(request))
        c.complete(request)
        assertFalse(c.canStart(request))
    }

    @Test fun repeatedCompletionAndMetadataUpdatesDoNotResetTheDeadline() = runTest {
        val c = controller()
        c.updatePlayback(ended, second)
        advanceTimeBy(3_000)
        c.updatePlayback(ended.copy(durationSeconds = 999_999), second.copy(title = "Updated"))
        advanceTimeBy(2_000)
        runCurrent()
        assertTrue(c.state.value is VideoAutoPlayNextState.Ready)
        val request = ready(c).request
        c.updatePlayback(ended, second)
        assertEquals(request.token, ready(c).request.token)
        assertTrue(c.claim(request))
        c.updatePlayback(ended, second)
        assertFalse(c.claim(request))
    }

    @Test fun playNowCancelsTheTimerWithoutIssuingASecondRequest() = runTest {
        val c = controller()
        c.updatePlayback(ended, second)
        advanceTimeBy(1_000)
        c.playNow()
        val request = ready(c).request
        c.playNow()
        advanceUntilIdle()
        assertEquals(request, ready(c).request)
        assertTrue(c.claim(request))
        assertFalse(c.claim(request))
    }

    @Test fun cancellationAtDeadlineWinsBeforeQueuedTimerWorkRuns() = runTest {
        val c = controller()
        c.updatePlayback(ended, second)
        advanceTimeBy(5_000)
        c.dismissCurrentPlayback()
        runCurrent()
        assertEquals(VideoAutoPlayNextState.Dismissed, c.state.value)
        c.setEnabled(false)
        c.setEnabled(true)
        c.updatePlayback(ended, second)
        advanceUntilIdle()
        assertEquals(VideoAutoPlayNextState.Dismissed, c.state.value)
    }

    @Test fun leavingForegroundCancelsAndReturningDoesNotRearm() = runTest {
        val c = controller()
        c.updatePlayback(ended, second)
        advanceTimeBy(2_000)
        c.setForeground(false)
        c.setForeground(true)
        c.updatePlayback(ended, second)
        advanceUntilIdle()
        assertEquals(VideoAutoPlayNextState.Dismissed, c.state.value)
    }

    @Test fun openingPanelOrDisablingPreferenceCancelsCountdown() = runTest {
        for (disable in listOf(false, true)) {
            val c = controller()
            c.updatePlayback(ended, second)
            if (disable) { c.setEnabled(false); c.setEnabled(true) }
            else { c.setPanelOpen(true); c.setPanelOpen(false) }
            advanceUntilIdle()
            assertEquals(VideoAutoPlayNextState.Dismissed, c.state.value)
        }
    }

    @Test fun endWhilePanelOpenOrInPipNeverStartsOnReturning() = runTest {
        for (panel in listOf(false, true)) {
            val c = controller()
            if (panel) c.setPanelOpen(true) else c.setForeground(false)
            c.updatePlayback(ended, second)
            c.setPanelOpen(false)
            c.setForeground(true)
            c.updatePlayback(ended, second)
            advanceUntilIdle()
            assertFalse(c.state.value is VideoAutoPlayNextState.Ready)
            assertFalse(c.state.value is VideoAutoPlayNextState.Countdown)
        }
    }

    @Test fun dismissingPreEndPromptSuppressesThisCycleButReplayCanCountDownAgain() = runTest {
        val c = controller()
        c.dismissCurrentPlayback()
        c.updatePlayback(ended, second)
        advanceUntilIdle()
        assertEquals(VideoAutoPlayNextState.Dismissed, c.state.value)
        c.updatePlayback(playing, second)
        c.updatePlayback(ended, second)
        assertEquals(5, countdown(c).secondsRemaining)
        c.dismissCurrentPlayback()
    }

    @Test fun actualEndedEventWorksWithUnknownDurationButMetadataAloneCannotTrigger() = runTest {
        val c = controller()
        c.updatePlayback(playing.copy(positionSeconds = 1_000, durationSeconds = 1), second)
        assertEquals(VideoAutoPlayNextState.Idle, c.state.value)
        c.updatePlayback(ended.copy(positionSeconds = 0, durationSeconds = 0), second)
        assertEquals(5, countdown(c).secondsRemaining)
        c.dismissCurrentPlayback()
    }

    @Test fun restoredEndedSnapshotDoesNotStartCountdown() = runTest {
        val c = VideoAutoPlayNextController(this) { testScheduler.currentTime }
        c.setEnabled(true)
        c.setForeground(true)
        c.updatePlayback(ended, second)
        advanceUntilIdle()
        assertEquals(VideoAutoPlayNextState.Idle, c.state.value)
    }

    @Test fun missingNextAndUnplayableNextDoNotCountDown() = runTest {
        for (next in listOf(null, second.copy(streamUrl = " "))) {
            val c = controller()
            c.updatePlayback(ended, next)
            advanceUntilIdle()
            assertFalse(c.state.value is VideoAutoPlayNextState.Ready)
            assertFalse(c.state.value is VideoAutoPlayNextState.Countdown)
        }
    }

    @Test fun errorsBufferingAndChangedTargetCancelRatherThanSkipOrRetry() = runTest {
        for (change in 0..3) {
            val c = controller()
            c.updatePlayback(ended, second)
            when (change) {
                0 -> c.updatePlayback(ended.copy(errorMessage = "播放失败"), second)
                1 -> c.updatePlayback(ended.copy(isBuffering = true), second)
                2 -> c.updatePlayback(ended, second.copy(sourceId = "other"))
                3 -> c.updatePlayback(ended, null)
            }
            c.updatePlayback(ended, second)
            advanceUntilIdle()
            assertEquals(VideoAutoPlayNextState.Dismissed, c.state.value)
        }
    }

    @Test fun claimedRequestSurvivesOwnEmptyEngineStateDuringHandoff() = runTest {
        val c = controller()
        c.updatePlayback(ended, second)
        c.playNow()
        val request = ready(c).request
        assertTrue(c.claim(request))
        c.updatePlayback(VideoPlaybackState(), second)
        assertTrue(c.canStart(request))
        c.setForeground(false)
        assertFalse(c.canStart(request))
        c.setForeground(true)
        assertFalse(c.canStart(request))
    }

    @Test fun claimedRequestSurvivesOldItemsIdleCallbacksButNotPreparationErrors() = runTest {
        val c = controller()
        c.updatePlayback(ended, second)
        c.playNow()
        val request = ready(c).request
        assertTrue(c.claim(request))
        c.updatePlayback(playing.copy(isPlaying = false, hasEnded = false), second)
        assertTrue(c.canStart(request))
        c.updatePlayback(VideoPlaybackState(), second)
        assertTrue(c.canStart(request))
        c.updatePlayback(VideoPlaybackState(errorMessage = "准备失败"), second)
        assertFalse(c.canStart(request))
        c.updatePlayback(VideoPlaybackState(), second)
        advanceUntilIdle()
        assertFalse(c.state.value is VideoAutoPlayNextState.Ready)
    }

    @Test fun changedCatalogInvalidatesClaimedRequestDuringEmptyEnginePreparation() = runTest {
        val c = controller()
        c.updatePlayback(ended, second)
        c.playNow()
        val request = ready(c).request
        assertTrue(c.claim(request))
        c.updatePlayback(VideoPlaybackState(), second.copy(sourceId = "changed"))
        assertEquals(VideoAutoPlayNextState.Dismissed, c.state.value)
        assertFalse(c.canStart(request))
        c.updatePlayback(VideoPlaybackState(), second)
        advanceUntilIdle()
        assertFalse(c.canStart(request))
    }

    @Test fun manualIntentAndNewSourceInvalidateAlreadyClaimedRequests() = runTest {
        for (changeSource in listOf(false, true)) {
            val c = controller()
            c.updatePlayback(ended, second)
            c.playNow()
            val request = ready(c).request
            assertTrue(c.claim(request))
            if (changeSource) c.updatePlayback(playing.copy(video = first.copy(sourceId = "other")), second)
            else c.dismissCurrentPlayback()
            assertFalse(c.canStart(request))
        }
    }

    @Test fun staleRequestCannotStartAfterReplayOfTheSameRemoteId() = runTest {
        val c = controller()
        c.updatePlayback(ended, second)
        c.playNow()
        val old = ready(c).request
        assertTrue(c.claim(old))
        c.resetPlaybackCycle()
        c.updatePlayback(playing, second)
        c.updatePlayback(ended, second)
        c.playNow()
        val fresh = ready(c).request
        assertNotEquals(old.token, fresh.token)
        assertFalse(c.claim(old))
        assertTrue(c.claim(fresh))
        assertFalse(c.canStart(old))
        assertTrue(c.canStart(fresh))
        c.complete(fresh)
    }

    @Test fun startingNextItemArmsOnlyThatItemsNewCompletion() = runTest {
        val c = controller()
        c.updatePlayback(ended, second)
        c.playNow()
        val old = ready(c).request
        assertTrue(c.claim(old))
        val third = episode("third")
        c.updatePlayback(playing.copy(video = second), third)
        assertFalse(c.canStart(old))
        assertEquals(VideoAutoPlayNextState.Idle, c.state.value)
        c.updatePlayback(ended.copy(video = second), third)
        assertEquals(third, countdown(c).request.next)
        c.dismissCurrentPlayback()
    }

    private fun TestScope.controller() = VideoAutoPlayNextController(this) { testScheduler.currentTime }.also {
        it.setEnabled(true)
        it.setForeground(true)
        it.updatePlayback(playing, second)
    }
    private fun countdown(c: VideoAutoPlayNextController) = c.state.value as VideoAutoPlayNextState.Countdown
    private fun ready(c: VideoAutoPlayNextController) = c.state.value as VideoAutoPlayNextState.Ready
    private fun episode(id: String) = VideoItem(id, "library", id, "Episode",
        streamUrl = "https://example.test/$id", sourceId = "source")
}
