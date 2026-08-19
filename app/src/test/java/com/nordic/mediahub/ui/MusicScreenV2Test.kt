package com.nordic.mediahub.ui

import com.nordic.mediahub.data.NavidromeAlbum
import com.nordic.mediahub.data.NavidromeArtist
import com.nordic.mediahub.data.NavidromePlaylist
import com.nordic.mediahub.data.NavidromeSong
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
    fun resolveMusicLibraryPageAfterConfigChange_returnsHomeForEveryPage() {
        MusicLibraryPage.values().forEach { page ->
            assertEquals(MusicLibraryPage.Home, resolveMusicLibraryPageAfterConfigChange(page))
        }
    }

    @Test
    fun resolveMusicLibraryPageForward_homeToAlbumDetail_isTrue() {
        assertEquals(
            true,
            resolveMusicLibraryPageForward(MusicLibraryPage.Home, MusicLibraryPage.AlbumDetail)
        )
    }

    @Test
    fun resolveMusicLibraryPageForward_albumDetailToHome_isFalse() {
        assertEquals(
            false,
            resolveMusicLibraryPageForward(MusicLibraryPage.AlbumDetail, MusicLibraryPage.Home)
        )
    }

    @Test
    fun resolveMusicLibraryPageForward_playlistsToPlaylistDetail_isTrue() {
        assertEquals(
            true,
            resolveMusicLibraryPageForward(MusicLibraryPage.Playlists, MusicLibraryPage.PlaylistDetail)
        )
    }

    @Test
    fun resolveMusicLibraryPageForward_playlistDetailToPlaylists_isFalse() {
        assertEquals(
            false,
            resolveMusicLibraryPageForward(MusicLibraryPage.PlaylistDetail, MusicLibraryPage.Playlists)
        )
    }

    @Test
    fun resolveMusicLibraryPageForward_homeToAlbums_isTrue() {
        assertEquals(
            true,
            resolveMusicLibraryPageForward(MusicLibraryPage.Home, MusicLibraryPage.Albums)
        )
    }

    @Test
    fun resolveMusicLibraryPageForward_albumsToAlbumDetail_isTrue() {
        assertEquals(
            true,
            resolveMusicLibraryPageForward(MusicLibraryPage.Albums, MusicLibraryPage.AlbumDetail)
        )
    }

    @Test
    fun resolveMusicLibraryPageForward_homeToHome_isFalse() {
        assertEquals(
            false,
            resolveMusicLibraryPageForward(MusicLibraryPage.Home, MusicLibraryPage.Home)
        )
    }

    @Test
    fun resolveMusicLibraryPageForward_sameDepthPeers_isFalse() {
        assertEquals(
            false,
            resolveMusicLibraryPageForward(MusicLibraryPage.Albums, MusicLibraryPage.Songs)
        )
        assertEquals(
            false,
            resolveMusicLibraryPageForward(MusicLibraryPage.AlbumDetail, MusicLibraryPage.ArtistDetail)
        )
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
    fun resolveMusicBackNavigation_returnsRecordedSourcePage() {
        val result = resolveMusicBackNavigation(
            currentPage = MusicLibraryPage.AlbumDetail,
            backStack = listOf(MusicLibraryPage.Home, MusicLibraryPage.Albums),
            hasSelectedAlbum = true,
            hasSelectedArtist = false,
            hasSelectedPlaylist = false
        )

        assertEquals(MusicLibraryPage.Albums, result.page)
        assertEquals(listOf(MusicLibraryPage.Home), result.backStack)
    }

    @Test
    fun resolveMusicBackNavigation_returnsSearchSourcePage() {
        val result = resolveMusicBackNavigation(
            currentPage = MusicLibraryPage.ArtistDetail,
            backStack = listOf(MusicLibraryPage.Home, MusicLibraryPage.Search),
            hasSelectedAlbum = false,
            hasSelectedArtist = true,
            hasSelectedPlaylist = false
        )

        assertEquals(MusicLibraryPage.Search, result.page)
        assertEquals(listOf(MusicLibraryPage.Home), result.backStack)
    }

    @Test
    fun resolveMusicBackNavigation_canReturnToPreviousDetailWhenStillValid() {
        val result = resolveMusicBackNavigation(
            currentPage = MusicLibraryPage.AlbumDetail,
            backStack = listOf(MusicLibraryPage.Home, MusicLibraryPage.ArtistDetail),
            hasSelectedAlbum = true,
            hasSelectedArtist = true,
            hasSelectedPlaylist = false
        )

        assertEquals(MusicLibraryPage.ArtistDetail, result.page)
        assertEquals(listOf(MusicLibraryPage.Home), result.backStack)
    }

    @Test
    fun resolveMusicBackNavigation_skipsInvalidDetailOrigin() {
        val result = resolveMusicBackNavigation(
            currentPage = MusicLibraryPage.AlbumDetail,
            backStack = listOf(MusicLibraryPage.Home, MusicLibraryPage.ArtistDetail),
            hasSelectedAlbum = true,
            hasSelectedArtist = false,
            hasSelectedPlaylist = false
        )

        assertEquals(MusicLibraryPage.Home, result.page)
        assertEquals(emptyList<MusicLibraryPage>(), result.backStack)
    }

    @Test
    fun resolveMusicBackNavigation_skipsInvalidDetailThenReturnsListOrigin() {
        val result = resolveMusicBackNavigation(
            currentPage = MusicLibraryPage.PlaylistDetail,
            backStack = listOf(
                MusicLibraryPage.Home,
                MusicLibraryPage.Playlists,
                MusicLibraryPage.AlbumDetail
            ),
            hasSelectedAlbum = false,
            hasSelectedArtist = false,
            hasSelectedPlaylist = true
        )

        assertEquals(MusicLibraryPage.Playlists, result.page)
        assertEquals(listOf(MusicLibraryPage.Home), result.backStack)
    }

    @Test
    fun resolveMusicBackNavigation_fallsBackPlaylistDetailToPlaylists() {
        val result = resolveMusicBackNavigation(
            currentPage = MusicLibraryPage.PlaylistDetail,
            backStack = emptyList(),
            hasSelectedAlbum = false,
            hasSelectedArtist = false,
            hasSelectedPlaylist = true
        )

        assertEquals(MusicLibraryPage.Playlists, result.page)
        assertEquals(emptyList<MusicLibraryPage>(), result.backStack)
    }

    @Test
    fun resolveSelectedMusicDetailAfterRefresh_updatesMatchingObjects() {
        val updatedAlbum = album("album-1", name = "Updated")
        val updatedArtist = artist("artist-1", name = "Updated")
        val updatedPlaylist = playlist("playlist-1", name = "Updated")

        assertEquals(
            updatedAlbum,
            resolveSelectedAlbumAfterMusicRefresh(album("album-1"), listOf(updatedAlbum))
        )
        assertEquals(
            updatedArtist,
            resolveSelectedArtistAfterMusicRefresh(artist("artist-1"), listOf(updatedArtist))
        )
        assertEquals(
            updatedPlaylist,
            resolveSelectedPlaylistAfterMusicRefresh(playlist("playlist-1"), listOf(updatedPlaylist))
        )
    }

    @Test
    fun resolveSelectedMusicDetailAfterRefresh_clearsMissingObjects() {
        assertNull(resolveSelectedAlbumAfterMusicRefresh(album("old"), listOf(album("new"))))
        assertNull(resolveSelectedArtistAfterMusicRefresh(artist("old"), listOf(artist("new"))))
        assertNull(resolveSelectedPlaylistAfterMusicRefresh(playlist("old"), listOf(playlist("new"))))
    }

    @Test
    fun shouldShowMusicDetailInvalidationNotice_onlyForVisibleMissingDetail() {
        assertEquals(
            true,
            shouldShowMusicDetailInvalidationNotice(
                detailPage = MusicLibraryPage.AlbumDetail,
                currentPage = MusicLibraryPage.AlbumDetail,
                hadSelection = true,
                hasRefreshedSelection = false
            )
        )
        assertEquals(
            false,
            shouldShowMusicDetailInvalidationNotice(
                detailPage = MusicLibraryPage.AlbumDetail,
                currentPage = MusicLibraryPage.Albums,
                hadSelection = true,
                hasRefreshedSelection = false
            )
        )
    }

    @Test
    fun mediaRefreshErrorPresentation_keepsCachedErrorInSubtitle() {
        assertEquals("正在显示上次缓存：offline", mediaRefreshErrorSubtitle("正在显示上次缓存：offline", true))
        assertNull(standaloneMediaError("正在显示上次缓存：offline", true))
        assertNull(mediaRefreshErrorSubtitle("连接失败: offline", false))
        assertEquals("连接失败: offline", standaloneMediaError("连接失败: offline", false))
    }

    @Test
    fun shouldShowMusicSearchClearAction_onlyShowsForNonBlankQuery() {
        assertEquals(false, shouldShowMusicSearchClearAction(""))
        assertEquals(false, shouldShowMusicSearchClearAction("   "))
        assertEquals(true, shouldShowMusicSearchClearAction("北欧民谣"))
    }

    @Test
    fun filterMusicSongs_matchesTitleArtistAndAlbumCaseInsensitively() {
        val songs = listOf(
            song(id = "one").copy(title = "Quiet Harbor", artist = "Nordic Echo", album = "Morning"),
            song(id = "two").copy(title = "Night Drive", artist = "Signal", album = "Harbor Lights"),
            song(id = "three").copy(title = "Forest Walk", artist = "Field", album = "Green")
        )

        assertEquals(listOf("one", "two"), filterMusicSongs(songs, "harbor").map { it.id })
        assertEquals(listOf("one"), filterMusicSongs(songs, "ECHO").map { it.id })
    }

    @Test
    fun filterMusicSongs_blankQueryKeepsOriginalList() {
        val songs = listOf(song(id = "one"), song(id = "two"))

        assertEquals(songs, filterMusicSongs(songs, "   "))
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

    private fun album(id: String, name: String = id): NavidromeAlbum {
        return NavidromeAlbum(id = id, name = name)
    }

    private fun artist(id: String, name: String = id): NavidromeArtist {
        return NavidromeArtist(id = id, name = name)
    }

    private fun playlist(id: String, name: String = id): NavidromePlaylist {
        return NavidromePlaylist(id = id, name = name)
    }
}
