package com.nordic.mediahub.ui

import com.nordic.mediahub.api.NavidromeSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun firstPlayableSongIndex_returnsFirstSongWithStreamUrl() {
        val songs = listOf(
            song(id = "missing"),
            song(id = "playable", streamUrl = "https://music.example/song.mp3"),
            song(id = "later", streamUrl = "https://music.example/later.mp3")
        )

        assertEquals(1, firstPlayableSongIndex(songs))
    }

    @Test
    fun firstPlayableSongIndex_ignoresBlankStreamUrls() {
        val songs = listOf(
            song(id = "blank", streamUrl = "   "),
            song(id = "playable", streamUrl = "https://music.example/song.mp3")
        )

        assertEquals(1, firstPlayableSongIndex(songs))
    }

    @Test
    fun firstPlayableSongIndex_returnsNullWhenNoSongsArePlayable() {
        val songs = listOf(
            song(id = "missing"),
            song(id = "blank", streamUrl = "")
        )

        assertNull(firstPlayableSongIndex(songs))
    }

    @Test
    fun musicAlbumDetailLoadErrorMessage_includesContextAndCause() {
        assertEquals(
            "获取专辑曲目失败: offline",
            musicAlbumDetailLoadErrorMessage(Exception("offline"))
        )
    }

    @Test
    fun musicArtistDetailLoadErrorMessage_includesContextAndCause() {
        assertEquals(
            "获取歌手专辑失败: timeout",
            musicArtistDetailLoadErrorMessage(Exception("timeout"))
        )
    }

    @Test
    fun musicDetailLoadErrorMessages_useFallbackWhenCauseIsMissing() {
        assertEquals("获取专辑曲目失败: 未知错误", musicAlbumDetailLoadErrorMessage(Exception()))
        assertEquals("获取歌手专辑失败: 未知错误", musicArtistDetailLoadErrorMessage(Exception()))
    }

    private fun song(id: String, streamUrl: String? = null): NavidromeSong {
        return NavidromeSong(
            id = id,
            title = id,
            streamUrl = streamUrl
        )
    }
}
