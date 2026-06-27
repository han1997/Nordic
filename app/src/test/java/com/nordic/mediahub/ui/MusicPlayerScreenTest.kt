package com.nordic.mediahub.ui

import com.nordic.mediahub.data.MusicLyrics
import com.nordic.mediahub.data.MusicLyricsLine
import org.junit.Assert.assertEquals
import org.junit.Test

class MusicPlayerScreenTest {
    @Test
    fun selectVisibleLyricLines_marksActiveLineAndCarriesStartMillis() {
        val lyrics = MusicLyrics(
            synced = true,
            lines = listOf(
                MusicLyricsLine(startMillis = 0, text = "first"),
                MusicLyricsLine(startMillis = 10_000, text = "second"),
                MusicLyricsLine(startMillis = 20_000, text = "third"),
                MusicLyricsLine(startMillis = 30_000, text = "fourth")
            )
        )

        val visible = selectVisibleLyricLines(
            lyrics = lyrics,
            positionSeconds = 22,
            maxLineCount = 3
        )

        assertEquals(
            listOf(
                VisibleLyricLine(text = "second", active = false, startMillis = 10_000),
                VisibleLyricLine(text = "third", active = true, startMillis = 20_000),
                VisibleLyricLine(text = "fourth", active = false, startMillis = 30_000)
            ),
            visible
        )
    }

    @Test
    fun selectVisibleLyricLines_plainLyricsDoNotExposeSeekTimes() {
        val lyrics = MusicLyrics(
            synced = false,
            lines = listOf(
                MusicLyricsLine(startMillis = 0, text = "plain first"),
                MusicLyricsLine(startMillis = 10_000, text = "plain second")
            )
        )

        val visible = selectVisibleLyricLines(
            lyrics = lyrics,
            positionSeconds = 12,
            maxLineCount = 2
        )

        assertEquals(
            listOf(
                VisibleLyricLine(text = "plain first", active = false, startMillis = null),
                VisibleLyricLine(text = "plain second", active = false, startMillis = null)
            ),
            visible
        )
    }
}
