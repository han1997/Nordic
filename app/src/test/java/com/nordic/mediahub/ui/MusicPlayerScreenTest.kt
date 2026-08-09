package com.nordic.mediahub.ui

import com.nordic.mediahub.data.MusicLyrics
import com.nordic.mediahub.data.MusicLyricsLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicPlayerScreenTest {
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
    fun syncedLyricsBeforeFirstTimestamp_haveNoActiveLine() {
        val result = selectVisibleLyricLines(
            lyrics = MusicLyrics(
                synced = true,
                lines = listOf(
                    MusicLyricsLine(startMillis = 10_000, text = "First timed line"),
                    MusicLyricsLine(startMillis = 20_000, text = "Second timed line")
                )
            ),
            positionMillis = 5_000L,
            maxLineCount = 3
        )

        assertEquals(listOf("First timed line", "Second timed line"), result.map { it.text })
        assertEquals(listOf(false, false), result.map { it.active })
    }

    @Test
    fun syncedLyricsActivateFirstTimedLineAtStartTime() {
        val result = selectVisibleLyricLines(
            lyrics = MusicLyrics(
                synced = true,
                lines = listOf(
                    MusicLyricsLine(startMillis = 10_000, text = "First timed line"),
                    MusicLyricsLine(startMillis = 20_000, text = "Second timed line")
                )
            ),
            positionMillis = 10_000L,
            maxLineCount = 3
        )

        assertEquals("First timed line", result.single { it.active }.text)
    }

    @Test
    fun leadingUntimedSyncedLinesStayVisibleButInactive() {
        val result = selectVisibleLyricLines(
            lyrics = MusicLyrics(
                synced = true,
                lines = listOf(
                    MusicLyricsLine(text = "Untimed intro"),
                    MusicLyricsLine(startMillis = 12_000, text = "First timed line"),
                    MusicLyricsLine(startMillis = 24_000, text = "Second timed line")
                )
            ),
            positionMillis = 5_000L,
            maxLineCount = 3
        )

        assertEquals("Untimed intro", result.first().text)
        assertFalse(result.first().active)
        assertTrue(result.none { it.active })
    }

    @Test
    fun unsyncedLyricsHaveNoActiveLine() {
        val result = selectVisibleLyricLines(
            lyrics = MusicLyrics(
                synced = false,
                lines = listOf(
                    MusicLyricsLine(text = "Plain first line"),
                    MusicLyricsLine(text = "Plain second line")
                )
            ),
            positionMillis = 30_000L,
            maxLineCount = 2
        )

        assertEquals(listOf("Plain first line", "Plain second line"), result.map { it.text })
        assertEquals(listOf(false, false), result.map { it.active })
    }

    @Test
    fun selectVisibleLyricLines_subSecondStartMillis_activatesExactlyAtTimestamp() {
        val lyrics = MusicLyrics(
            synced = true,
            lines = listOf(
                MusicLyricsLine(startMillis = 0, text = "Intro"),
                MusicLyricsLine(startMillis = 10_500, text = "Line A"),
                MusicLyricsLine(startMillis = 11_500, text = "Line B")
            )
        )

        // 10499ms — still before Line A timestamp → Intro (first line) active.
        val justBefore = selectVisibleLyricLines(
            lyrics = lyrics,
            positionMillis = 10_499L,
            maxLineCount = 3
        )
        assertEquals("Intro", justBefore.single { it.active }.text)

        // 10500ms — exactly at Line A → Line A active, not the prior tick-1s of 11s.
        val atTimestamp = selectVisibleLyricLines(
            lyrics = lyrics,
            positionMillis = 10_500L,
            maxLineCount = 3
        )
        assertEquals("Line A", atTimestamp.single { it.active }.text)

        // 11500ms — at Line B → Line B active.
        val atLineB = selectVisibleLyricLines(
            lyrics = lyrics,
            positionMillis = 11_500L,
            maxLineCount = 3
        )
        assertEquals("Line B", atLineB.single { it.active }.text)
    }

    @Test
    fun selectVisibleLyricLines_zeroPosition_activatesFirstTimestamp() {
        val lyrics = MusicLyrics(
            synced = true,
            lines = listOf(
                MusicLyricsLine(startMillis = 0, text = "First line"),
                MusicLyricsLine(startMillis = 5_000, text = "Second line")
            )
        )

        val result = selectVisibleLyricLines(
            lyrics = lyrics,
            positionMillis = 0L,
            maxLineCount = 2
        )

        assertEquals("First line", result.single { it.active }.text)
    }

    @Test
    fun selectVisibleLyricLines_negativePosition_clampsToZero() {
        val lyrics = MusicLyrics(
            synced = true,
            lines = listOf(
                MusicLyricsLine(startMillis = 0, text = "First line"),
                MusicLyricsLine(startMillis = 5_000, text = "Second line")
            )
        )

        // Defensive: a negative position should never select a line behind 0.
        val result = selectVisibleLyricLines(
            lyrics = lyrics,
            positionMillis = -300L,
            maxLineCount = 2
        )

        assertEquals("First line", result.single { it.active }.text)
    }
}
