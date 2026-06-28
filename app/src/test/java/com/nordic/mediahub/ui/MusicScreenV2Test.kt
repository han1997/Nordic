package com.nordic.mediahub.ui

import com.nordic.mediahub.api.NavidromeSong
import org.junit.Assert.assertEquals
import org.junit.Test

class MusicScreenV2Test {
    @Test
    fun musicHomePlaybackQueue_keepsFullRecentlyAddedQueueBehindPreview() {
        val songs = (1..15).map { index ->
            NavidromeSong(id = "song-$index", title = "Song $index")
        }

        val preview = musicHomePreviewSongs(songs)
        val queue = musicHomePlaybackQueue(songs)

        assertEquals(12, preview.size)
        assertEquals(15, queue.size)
        assertEquals(preview[4], queue[4])
    }
}
