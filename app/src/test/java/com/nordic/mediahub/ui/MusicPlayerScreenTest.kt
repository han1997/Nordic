package com.nordic.mediahub.ui

import com.nordic.mediahub.data.MusicLyrics
import com.nordic.mediahub.data.MusicLyricsLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.nordic.mediahub.playback.MusicLyricsUiState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

class MusicPlayerScreenTest {
    @Test
    fun lyricSurfaceDrag_doesNotDismissEvenWithShortOrEmptyContent() {
        val bounds = Rect(10f, 100f, 300f, 400f)
        assertFalse(shouldDismissMusicPlayerFromDrag(true, Offset(50f, 200f), bounds))
        assertFalse(shouldDismissMusicPlayerFromDrag(true, Offset.Zero, null))
        assertTrue(shouldDismissMusicPlayerFromDrag(true, Offset(50f, 50f), bounds))
        assertTrue(shouldDismissMusicPlayerFromDrag(false, Offset(50f, 200f), bounds))
    }

    @Test
    fun resolvePlayerThinSliderPosition_usesAbsolutePointerX() {
        assertEquals(75f, resolvePlayerThinSliderPosition(pointerX = 150f, trackWidth = 200, durationSeconds = 100), 0.001f)
    }

    @Test
    fun resolvePlayerThinSliderPosition_clampsOutsideTrack() {
        assertEquals(0f, resolvePlayerThinSliderPosition(pointerX = -20f, trackWidth = 200, durationSeconds = 100), 0.001f)
        assertEquals(100f, resolvePlayerThinSliderPosition(pointerX = 260f, trackWidth = 200, durationSeconds = 100), 0.001f)
    }

    @Test
    fun resolvePlayerThinSliderPosition_handlesInvalidWidthAndDuration() {
        assertEquals(0f, resolvePlayerThinSliderPosition(pointerX = 20f, trackWidth = 0, durationSeconds = 100), 0.001f)
        assertEquals(1f, resolvePlayerThinSliderPosition(pointerX = 200f, trackWidth = 200, durationSeconds = 0), 0.001f)
    }

    @Test
    fun resolvePlayerThinSliderThumbOffsetPx_centersThumbWithinTrackTravel() {
        assertEquals(0f, resolvePlayerThinSliderThumbOffsetPx(trackWidthPx = 200f, thumbSizePx = 12f, progress = 0f), 0.001f)
        assertEquals(94f, resolvePlayerThinSliderThumbOffsetPx(trackWidthPx = 200f, thumbSizePx = 12f, progress = 0.5f), 0.001f)
        assertEquals(188f, resolvePlayerThinSliderThumbOffsetPx(trackWidthPx = 200f, thumbSizePx = 12f, progress = 1f), 0.001f)
    }

    @Test
    fun resolvePlayerThinSliderThumbOffsetPx_clampsProgressAndInvalidTravel() {
        assertEquals(0f, resolvePlayerThinSliderThumbOffsetPx(trackWidthPx = 8f, thumbSizePx = 12f, progress = 1f), 0.001f)
        assertEquals(0f, resolvePlayerThinSliderThumbOffsetPx(trackWidthPx = 200f, thumbSizePx = 12f, progress = -1f), 0.001f)
        assertEquals(188f, resolvePlayerThinSliderThumbOffsetPx(trackWidthPx = 200f, thumbSizePx = 12f, progress = 2f), 0.001f)
    }

    @Test
    fun resolveLyricsModeLabel_distinguishesSyncedAndPlainLyrics() {
        assertEquals(
            "同步歌词",
            resolveLyricsModeLabel(
                MusicLyrics(
                    synced = true,
                    lines = listOf(MusicLyricsLine(startMillis = 1_000, text = "Timed"))
                )
            )
        )
        assertEquals(
            "普通歌词",
            resolveLyricsModeLabel(
                MusicLyrics(
                    synced = false,
                    lines = listOf(MusicLyricsLine(text = "Plain"))
                )
            )
        )
    }

    @Test
    fun resolveLyricsModeLabel_returnsNullWhenLyricsAreEmpty() {
        assertEquals(null, resolveLyricsModeLabel(null))
        assertEquals(
            null,
            resolveLyricsModeLabel(
                MusicLyrics(lines = listOf(MusicLyricsLine(text = "   ")))
            )
        )
    }

    @Test
    fun lyricsStateForSong_neverDisplaysThePreviousSong() {
        val previous = MusicLyricsUiState.Content("previous", 1L, MusicLyrics(listOf(MusicLyricsLine(text = "Old"))))
        assertEquals(MusicLyricsUiState.Loading("current"), lyricsStateForSong("current", previous))
        assertEquals(MusicLyricsUiState.Idle, lyricsStateForSong(null, previous))
    }

    @Test
    fun lyricsStateForSong_preservesCurrentLoadingAndErrorStates() {
        val error = MusicLyricsUiState.Error("current", "加载歌词失败", canRetry = true)
        assertEquals(error, lyricsStateForSong("current", error))
        assertEquals(MusicLyricsUiState.Loading("current"), lyricsStateForSong("current", MusicLyricsUiState.Idle))
    }
}
