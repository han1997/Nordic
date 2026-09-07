package com.nordic.mediahub.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicLibraryLayoutTest {
    @Test
    fun collectionHeaderReservesTextSpaceOrStacksWithoutShrinkingArtwork() {
        for (screen in listOf(320, 360, 392, 720)) {
            for (fontScale in listOf(1f, 1.5f, 2f)) {
                val width = (screen - 32).dp
                val layout = resolveMusicCollectionLayout(width, fontScale)
                assertTrue(layout.artworkSize in 128.dp..160.dp)
                if (!layout.stacked) assertTrue(width - layout.artworkSize - 24.dp >= 200.dp * fontScale)
            }
        }
        assertTrue(resolveMusicCollectionLayout(288.dp, 1f).stacked)
        assertFalse(resolveMusicCollectionLayout(360.dp, 1f).stacked)
        assertTrue(resolveMusicCollectionLayout(360.dp, 2f).stacked)
        assertFalse(resolveMusicCollectionLayout(688.dp, 2f).stacked)
    }

    @Test
    fun unavailableWidthDoesNotProduceNegativeArtwork() {
        assertEquals(0.dp, resolveMusicCollectionLayout((-1).dp, 1f).artworkSize)
        assertTrue(resolveMusicCollectionLayout(0.dp, Float.NaN).stacked)
    }

    @Test
    fun measuredTrailingTimeMovesBelowTitleForLargeTextInsteadOfTakingItsSpace() {
        assertTrue(shouldInlineMusicRowTrailing(264.dp, 36.dp, 1f))
        assertFalse(shouldInlineMusicRowTrailing(264.dp, 72.dp, 2f))
        assertTrue(shouldInlineMusicRowTrailing(640.dp, 72.dp, 2f))
    }

    @Test
    fun collectionCountsDistinguishLoadingFailedAndKnownEmptyResults() {
        assertEquals(12, resolveMusicCollectionCount(12, 0, true, false))
        assertEquals(12, resolveMusicCollectionCount(12, 0, false, true))
        assertEquals(0, resolveMusicCollectionCount(12, 0, false, false))
        assertEquals(4, resolveMusicCollectionCount(12, 4, false, true))
        assertEquals(0, resolveMusicCollectionCount(-1, -1, false, false))
    }

    @Test
    fun emptyContentIsNotShownOverAnExistingErrorOrLoadingIndicator() {
        assertTrue(shouldShowMusicCollectionEmpty(false, 0, false))
        assertFalse(shouldShowMusicCollectionEmpty(true, 0, false))
        assertFalse(shouldShowMusicCollectionEmpty(false, 0, true))
        assertFalse(shouldShowMusicCollectionEmpty(false, 2, false))
    }

    @Test
    fun musicMetadataUsesConsistentChineseCopyAndHonestUnknownDuration() {
        assertEquals("未知歌手", musicArtistLabel(null))
        assertEquals("未知歌手", musicArtistLabel("  "))
        assertEquals("歌手甲", musicArtistLabel(" 歌手甲 "))
        assertEquals("0 首歌曲", musicSongCountLabel(-1))
        assertEquals("3 张专辑", musicAlbumCountLabel(3))
        assertEquals("--:--", musicTrackDurationLabel(0))
        assertEquals("--:--", musicTrackDurationLabel(-5))
        assertEquals("1:05", musicTrackDurationLabel(65))
    }

    @Test
    fun shelfArtGrowsWithTextButRemainsBounded() {
        assertEquals(124.dp, musicShelfArtworkSize(1f))
        assertEquals(160.dp, musicShelfArtworkSize(2f))
        assertEquals(124.dp, musicShelfArtworkSize(Float.NaN))
        assertTrue(musicShelfArtworkSize(1.2f) > 124.dp)
    }
}
