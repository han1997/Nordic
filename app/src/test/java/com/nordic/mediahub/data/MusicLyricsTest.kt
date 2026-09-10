package com.nordic.mediahub.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class MusicLyricsTest {
    @Test
    fun plainLyrics_preserveAllLinesAndRepeatedVerses() {
        val lines = (1..30).map { MusicLyricsLine(text = "Line $it") } + MusicLyricsLine(text = "Line 1")
        val lyrics = requireNotNull(normalizeMusicLyrics(lines, synced = false))
        assertEquals(lines, lyrics.displayCues())
        assertEquals(31, lyrics.lines.size)
    }

    @Test
    fun normalization_sortsTimedLinesStablyAndPreservesUntimedText() {
        val lyrics = requireNotNull(normalizeMusicLyrics(listOf(
            MusicLyricsLine(text = "Intro"),
            MusicLyricsLine(2_000, "Second"),
            MusicLyricsLine(1_000, " First "),
            MusicLyricsLine(1_000, "First"),
            MusicLyricsLine(1_000, "Translation"),
            MusicLyricsLine(text = "Notes")
        ), synced = true))
        assertEquals(listOf("First", "Translation", "Second", "Intro", "Notes"), lyrics.lines.map { it.text })
        assertEquals(listOf(1_000, 1_000, 2_000, null, null), lyrics.lines.map { it.startMillis })
        assertEquals(listOf("First\nTranslation", "Second", "Intro", "Notes"), lyrics.displayCues().map { it.text })
    }

    @Test
    fun missingTimestamps_fallBackToPlainAndDoNotInventAnActiveLine() {
        val lyrics = requireNotNull(normalizeMusicLyrics(listOf(MusicLyricsLine(text = "Lyrics")), synced = true))
        assertFalse(lyrics.synced)
        assertNull(resolveActiveMusicLyricIndex(lyrics.displayCues(), 100_000L))
    }

    @Test
    fun emptyText_hasNoUsableLyrics() {
        assertNull(normalizeMusicLyrics(listOf(MusicLyricsLine(0, "  ")), synced = true))
        assertNull(normalizeMusicLyrics(emptyList(), synced = false))
    }

    @Test
    fun plainMode_doesNotTreatIncidentalTimestampsAsSynchronized() {
        val lyrics = requireNotNull(normalizeMusicLyrics(listOf(MusicLyricsLine(100, "Body")), synced = false))
        assertEquals(listOf(MusicLyricsLine(text = "Body")), lyrics.displayCues())
    }

    @Test
    fun activeCue_usesMillisecondsAndSupportsSeekingBothDirections() {
        val cues = listOf(MusicLyricsLine(10_500, "A"), MusicLyricsLine(11_500, "B"), MusicLyricsLine(text = "Notes"))
        assertNull(resolveActiveMusicLyricIndex(cues, 10_499))
        assertEquals(0, resolveActiveMusicLyricIndex(cues, 10_500))
        assertEquals(1, resolveActiveMusicLyricIndex(cues, 11_500))
        assertEquals(0, resolveActiveMusicLyricIndex(cues, 10_700))
        assertEquals(1, resolveActiveMusicLyricIndex(cues, Long.MAX_VALUE))
        assertNull(resolveActiveMusicLyricIndex(cues, -100))
    }

    @Test
    fun zeroTimestamp_activatesAtZeroAndNegativePositionClamps() {
        val cues = listOf(MusicLyricsLine(0, "First"), MusicLyricsLine(100, "Next"))
        assertEquals(0, resolveActiveMusicLyricIndex(cues, -50))
        assertEquals(0, resolveActiveMusicLyricIndex(cues, 0))
        assertNull(resolveActiveMusicLyricIndex(emptyList(), 0))
    }

    @Test
    fun longTextAndSameTimestampLines_areNotTruncated() {
        val longText = (1..20).joinToString("\n") { "Long verse $it" }
        val cues = MusicLyrics(listOf(MusicLyricsLine(0, longText), MusicLyricsLine(0, "Another line")), synced = true).displayCues()
        assertEquals(1, cues.size)
        assertEquals("$longText\nAnother line", cues.single().text)
    }
}
