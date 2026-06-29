package com.nordic.mediahub.ui

import com.nordic.mediahub.data.MusicLyrics
import com.nordic.mediahub.data.MusicLyricsLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicPlayerScreenTest {
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
            positionSeconds = 5,
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
            positionSeconds = 10,
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
            positionSeconds = 5,
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
            positionSeconds = 30,
            maxLineCount = 2
        )

        assertEquals(listOf("Plain first line", "Plain second line"), result.map { it.text })
        assertEquals(listOf(false, false), result.map { it.active })
    }
}
