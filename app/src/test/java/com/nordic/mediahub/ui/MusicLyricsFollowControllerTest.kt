package com.nordic.mediahub.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MusicLyricsFollowControllerTest {
    @Test
    fun scrollClassification_ignoresAnimationsButUserInputAlwaysWins() {
        assertEquals(LyricScrollActivity.Automatic, lyricScrollActivity(false, true, true))
        assertEquals(LyricScrollActivity.Idle, lyricScrollActivity(false, false, true))
        assertEquals(LyricScrollActivity.User, lyricScrollActivity(false, true, false))
        assertEquals(LyricScrollActivity.User, lyricScrollActivity(true, true, true))
        assertEquals(LyricScrollActivity.User, lyricScrollActivity(true, false, false))
    }

    @Test
    fun resumesAt2000MillisNot1999() = runTest {
        val controller = MusicLyricsFollowController(backgroundScope, isPlaying = true)
        controller.onUserScrollStarted()
        controller.onUserScrollStopped()
        advanceTimeBy(1_999)
        runCurrent()
        assertFalse(controller.state.value.following)
        advanceTimeBy(1)
        runCurrent()
        assertTrue(controller.state.value.following)
    }

    @Test
    fun draggingAndFlinging_doNotStartTheCountdown() = runTest {
        val controller = MusicLyricsFollowController(backgroundScope, isPlaying = true)
        controller.onUserScrollStarted()
        advanceTimeBy(10_000)
        runCurrent()
        assertFalse(controller.state.value.following)
        controller.onUserScrollStopped()
        advanceTimeBy(2_000)
        runCurrent()
        assertTrue(controller.state.value.following)
    }

    @Test
    fun anotherGesture_restartsTheCountdownAndRepeatedStopDoesNotExtendIt() = runTest {
        val controller = MusicLyricsFollowController(backgroundScope, isPlaying = true)
        controller.onUserScrollStarted()
        controller.onUserScrollStopped()
        advanceTimeBy(1_500)
        controller.onUserScrollStarted()
        advanceTimeBy(1_000)
        assertFalse(controller.state.value.following)
        controller.onUserScrollStopped()
        advanceTimeBy(1_000)
        controller.onUserScrollStopped()
        advanceTimeBy(1_000)
        runCurrent()
        assertTrue(controller.state.value.following)
    }

    @Test
    fun pause_cancelsCountdownAndResumeRestoresFollowing() = runTest {
        val controller = MusicLyricsFollowController(backgroundScope, isPlaying = true)
        controller.onUserScrollStarted()
        controller.onUserScrollStopped()
        advanceTimeBy(1_000)
        controller.setPlaying(false)
        advanceTimeBy(10_000)
        runCurrent()
        assertFalse(controller.state.value.following)
        controller.setPlaying(true)
        assertTrue(controller.state.value.following)
    }

    @Test
    fun pausedBrowsing_waitsAndResumeDoesNotStealAnActiveDrag() = runTest {
        val controller = MusicLyricsFollowController(backgroundScope, isPlaying = false)
        controller.onUserScrollStarted()
        controller.onUserScrollStopped()
        advanceTimeBy(10_000)
        runCurrent()
        assertFalse(controller.state.value.following)
        controller.onUserScrollStarted()
        controller.setPlaying(true)
        assertFalse(controller.state.value.following)
        controller.onUserScrollStopped()
        advanceTimeBy(2_000)
        runCurrent()
        assertTrue(controller.state.value.following)
    }

    @Test
    fun returnToCurrent_restoresImmediatelyEvenWhilePaused() = runTest {
        val controller = MusicLyricsFollowController(backgroundScope, isPlaying = false)
        controller.onUserScrollStarted()
        controller.onUserScrollStopped()
        controller.returnToCurrent()
        assertTrue(controller.state.value.following)
        assertEquals(1L, controller.state.value.revision)
        controller.returnToCurrent()
        assertEquals(2L, controller.state.value.revision)
    }

    @Test
    fun leavingLyrics_cancelsPendingResume() = runTest {
        val controller = MusicLyricsFollowController(backgroundScope, isPlaying = true)
        controller.onUserScrollStarted()
        controller.onUserScrollStopped()
        controller.close()
        advanceTimeBy(10_000)
        runCurrent()
        assertFalse(controller.state.value.following)
    }

    @Test
    fun alignment_usesMeasuredHeightAndPaddingCoordinates() {
        assertEquals(50f, lyricAlignmentDelta(130, 40, 0, 200), 0f)
        assertEquals(10f, lyricAlignmentDelta(0, 20, -100, 100), 0f)
        assertEquals(100f, lyricAlignmentDelta(0, 250, -100, 100), 0f)
        assertEquals(0f, lyricAlignmentDelta(100, 20, 0, 0), 0f)
    }
}
