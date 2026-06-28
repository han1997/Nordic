package com.nordic.mediahub.data

import com.nordic.mediahub.api.NavidromeAlbum
import com.nordic.mediahub.api.NavidromePlaylist
import com.nordic.mediahub.api.NavidromeSong
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NavidromeRepositoryTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun getAllSongs_expandsSongsFromAllPagedAlbums() = runTest {
        server.enqueueJson(
            subsonicResponse(
                """
                "albumList2": {
                  "album": [
                    {"id": "album-1", "name": "Album One", "coverArt": "cover-1", "songCount": 1},
                    {"id": "album-2", "name": "Album Two", "coverArt": "cover-2", "songCount": 1}
                  ]
                }
                """.trimIndent()
            )
        )
        server.enqueueJson(
            subsonicResponse(
                """
                "album": {
                  "id": "album-1",
                  "name": "Album One",
                  "coverArt": "cover-1",
                  "song": [
                    {"id": "song-1", "title": "Song One", "artist": "Artist One", "album": "Album One", "created": "2026-06-18T12:00:00Z"}
                  ]
                }
                """.trimIndent()
            )
        )
        server.enqueueJson(
            subsonicResponse(
                """
                "album": {
                  "id": "album-2",
                  "name": "Album Two",
                  "coverArt": "cover-2",
                  "song": [
                    {"id": "song-2", "title": "Song Two", "artist": "Artist Two", "album": "Album Two"}
                  ]
                }
                """.trimIndent()
            )
        )

        val songs = repository().getAllSongs()

        assertEquals(listOf("Song One", "Song Two"), songs.map { it.title })
        assertEquals("2026-06-18T12:00:00Z", songs[0].created)
        assertTrue(songs[0].streamUrl.orEmpty().contains("/rest/stream.view?id=song-1"))
        assertTrue(songs[1].coverArt.orEmpty().contains("/rest/getCoverArt.view?id=cover-2"))

        val albumListRequest = server.takeRequest()
        assertTrue(albumListRequest.path.orEmpty().startsWith("/rest/getAlbumList2.view?"))
        assertTrue(albumListRequest.path.orEmpty().contains("type=alphabeticalByName"))
        assertTrue(albumListRequest.path.orEmpty().contains("size=100"))
        assertTrue(albumListRequest.path.orEmpty().contains("offset=0"))

        assertTrue(server.takeRequest().path.orEmpty().contains("/rest/getAlbum.view"))
        assertTrue(server.takeRequest().path.orEmpty().contains("/rest/getAlbum.view"))
    }

    @Test
    fun getAllSongs_mapsMissingAndNullAlbumDetailSongsToEmptyList() = runTest {
        listOf(
            "",
            ""","song": null"""
        ).forEach { songField ->
            server.enqueueJson(
                subsonicResponse(
                    """
                    "albumList2": {
                      "album": [
                        {"id": "album-1", "name": "Album One", "coverArt": "cover-1", "songCount": 1}
                      ]
                    }
                    """.trimIndent()
                )
            )
            server.enqueueJson(albumDetailResponse(songField))

            val songs = repository().getAllSongs()

            assertEquals(emptyList<NavidromeSong>(), songs)
        }
    }

    @Test
    fun getAlbums_mapsSortModesToAlbumListRequests() = runTest {
        server.enqueueJson(emptyAlbumListResponse())
        server.enqueueJson(emptyAlbumListResponse())
        server.enqueueJson(emptyAlbumListResponse())

        repository().getAlbums(NavidromeAlbumSort.RecentlyAdded)
        repository().getAlbums(NavidromeAlbumSort.ReleaseYear)
        repository().getAlbums(NavidromeAlbumSort.Name)

        val recentlyAddedRequest = server.takeRequest().path.orEmpty()
        assertTrue(recentlyAddedRequest.startsWith("/rest/getAlbumList2.view?"))
        assertTrue(recentlyAddedRequest.contains("type=newest"))
        assertTrue(recentlyAddedRequest.contains("size=100"))
        assertTrue(recentlyAddedRequest.contains("offset=0"))

        val releaseYearRequest = server.takeRequest().path.orEmpty()
        assertTrue(releaseYearRequest.contains("type=byYear"))
        assertTrue(releaseYearRequest.contains("fromYear=2100"))
        assertTrue(releaseYearRequest.contains("toYear=1900"))

        val nameRequest = server.takeRequest().path.orEmpty()
        assertTrue(nameRequest.contains("type=alphabeticalByName"))
        assertFalse(nameRequest.contains("fromYear="))
        assertFalse(nameRequest.contains("toYear="))
    }

    @Test
    fun getAlbums_ignoresBlankCoverArtIds() = runTest {
        server.enqueueJson(
            subsonicResponse(
                """
                "albumList2": {
                  "album": [
                    {"id": "album-empty", "name": "Empty Cover", "coverArt": "", "songCount": 1},
                    {"id": "album-blank", "name": "Blank Cover", "coverArt": "   ", "songCount": 1},
                    {"id": "album-valid", "name": "Valid Cover", "coverArt": "valid-cover", "songCount": 1}
                  ]
                }
                """.trimIndent()
            )
        )

        val albums = repository().getAlbums(NavidromeAlbumSort.RecentlyAdded)

        assertNull(albums[0].coverArt)
        assertNull(albums[1].coverArt)
        assertTrue(albums[2].coverArt.orEmpty().contains("/rest/getCoverArt.view?id=valid-cover"))
    }

    @Test
    fun getAlbumSongs_mapsMissingAndNullSongListsToEmptyList() = runTest {
        listOf(
            "",
            ""","song": null"""
        ).forEach { songField ->
            server.enqueueJson(albumDetailResponse(songField))

            val songs = repository().getAlbumSongs("album-1")

            assertEquals(emptyList<NavidromeSong>(), songs)
        }
    }

    @Test
    fun getArtistAlbums_mapsPresentAlbumsAndCoverArtUrls() = runTest {
        server.enqueueJson(
            artistDetailResponse(
                """
                ,"album": [
                  {"id": "album-1", "name": "Album One", "artist": "Artist One", "coverArt": "cover-1"}
                ]
                """.trimIndent()
            )
        )

        val albums = repository().getArtistAlbums("artist-1")

        assertEquals(1, albums.size)
        assertEquals("Album One", albums.single().name)
        assertTrue(albums.single().coverArt.orEmpty().contains("/rest/getCoverArt.view?id=cover-1"))
        assertTrue(server.takeRequest().path.orEmpty().contains("id=artist-1"))
    }

    @Test
    fun getArtistAlbums_mapsMissingAndNullAlbumListsToEmptyList() = runTest {
        listOf(
            "",
            ""","album": null"""
        ).forEach { albumField ->
            server.enqueueJson(artistDetailResponse(albumField))

            val albums = repository().getArtistAlbums("artist-1")

            assertEquals(emptyList<NavidromeAlbum>(), albums)
        }
    }

    @Test
    fun getPlaylists_mapsPlaylistSummariesAndCoverArtUrls() = runTest {
        server.enqueueJson(
            subsonicResponse(
                """
                "playlists": {
                  "playlist": [
                    {
                      "id": "playlist-1",
                      "name": "Road Mix",
                      "owner": "demo",
                      "songCount": 2,
                      "duration": 390,
                      "coverArt": "playlist-cover"
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        val playlists = repository().getPlaylists()

        assertEquals(1, playlists.size)
        assertEquals("Road Mix", playlists[0].name)
        assertEquals(2, playlists[0].songCount)
        assertTrue(playlists[0].coverArt.orEmpty().contains("/rest/getCoverArt.view?id=playlist-cover"))

        val request = server.takeRequest().path.orEmpty()
        assertTrue(request.startsWith("/rest/getPlaylists.view?"))
        assertTrue(request.contains("u=demo"))
        assertTrue(request.contains("c=Nordic"))
        assertTrue(request.contains("f=json"))
    }

    @Test
    fun getPlaylists_mapsMissingAndNullPlaylistArraysToEmptyList() = runTest {
        listOf(
            """"playlists": {}""",
            """"playlists": {"playlist": null}"""
        ).forEach { playlistsField ->
            server.enqueueJson(subsonicResponse(playlistsField))

            val playlists = repository().getPlaylists()

            assertEquals(emptyList<NavidromePlaylist>(), playlists)
        }
    }

    @Test
    fun getPlaylists_throwsTypedApiExceptionForEmptyResponseBody() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))

        val error = assertNavidromeApiError(NavidromeApiException.Kind.API) {
            repository().getPlaylists()
        }

        assertTrue(error.message.orEmpty().contains("响应为空"))
    }

    @Test
    fun getPlaylists_throwsTypedExceptionForHttpErrors() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        val error = assertNavidromeApiError(NavidromeApiException.Kind.HTTP) {
            repository().getPlaylists()
        }

        assertTrue(error.message.orEmpty().contains("HTTP"))
    }

    @Test
    fun getPlaylists_throwsTypedExceptionForSubsonicErrors() = runTest {
        server.enqueueJson(
            subsonicFailedResponse(
                code = 40,
                message = "Wrong username or password"
            )
        )

        val error = assertNavidromeApiError(NavidromeApiException.Kind.SUBSONIC) {
            repository().getPlaylists()
        }

        assertTrue(error.message.orEmpty().contains("[40]"))
    }

    @Test
    fun getPlaylistSongs_mapsEntriesToPlayableSongs() = runTest {
        server.enqueueJson(
            subsonicResponse(
                """
                "playlist": {
                  "id": "playlist-1",
                  "name": "Road Mix",
                  "coverArt": "playlist-cover",
                  "entry": [
                    {"id": "song-1", "title": "Song One", "artist": "Artist One", "album": "Playlist Album"},
                    {"id": "song-2", "title": "Song Two", "artist": "Artist Two", "album": "Playlist Album", "coverArt": "song-cover"}
                  ]
                }
                """.trimIndent()
            )
        )

        val songs = repository().getPlaylistSongs("playlist-1")

        assertEquals(listOf("Song One", "Song Two"), songs.map { it.title })
        assertTrue(songs[0].streamUrl.orEmpty().contains("/rest/stream.view?id=song-1"))
        assertTrue(songs[0].coverArt.orEmpty().contains("/rest/getCoverArt.view?id=playlist-cover"))
        assertTrue(songs[1].coverArt.orEmpty().contains("/rest/getCoverArt.view?id=song-cover"))

        val request = server.takeRequest().path.orEmpty()
        assertTrue(request.startsWith("/rest/getPlaylist.view?"))
        assertTrue(request.contains("id=playlist-1"))
    }

    @Test
    fun getPlaylistSongs_mapsMissingAndNullEntriesToEmptyList() = runTest {
        listOf(
            """
            "playlist": {
              "id": "playlist-1",
              "name": "Road Mix",
              "coverArt": "playlist-cover"
            }
            """.trimIndent(),
            """
            "playlist": {
              "id": "playlist-1",
              "name": "Road Mix",
              "coverArt": "playlist-cover",
              "entry": null
            }
            """.trimIndent()
        ).forEach { playlistField ->
            server.enqueueJson(subsonicResponse(playlistField))

            val songs = repository().getPlaylistSongs("playlist-1")

            assertEquals(emptyList<NavidromeSong>(), songs)
        }
    }

    @Test
    fun getPlaylistSongs_ignoresBlankPlaylistFallbackCoverArt() = runTest {
        server.enqueueJson(
            subsonicResponse(
                """
                "playlist": {
                  "id": "playlist-1",
                  "name": "Road Mix",
                  "coverArt": "   ",
                  "entry": [
                    {"id": "song-1", "title": "Song One", "artist": "Artist One", "album": "Playlist Album"},
                    {"id": "song-2", "title": "Song Two", "artist": "Artist Two", "album": "Playlist Album", "coverArt": ""}
                  ]
                }
                """.trimIndent()
            )
        )

        val songs = repository().getPlaylistSongs("playlist-1")

        assertNull(songs[0].coverArt)
        assertNull(songs[1].coverArt)
        assertTrue(songs[0].streamUrl.orEmpty().contains("/rest/stream.view?id=song-1"))
    }

    @Test
    fun getPlaylistSongs_usesFallbackWhenSongCoverArtIsBlank() = runTest {
        server.enqueueJson(
            subsonicResponse(
                """
                "playlist": {
                  "id": "playlist-1",
                  "name": "Road Mix",
                  "coverArt": "playlist-cover",
                  "entry": [
                    {"id": "song-1", "title": "Song One", "artist": "Artist One", "album": "Playlist Album", "coverArt": "   "}
                  ]
                }
                """.trimIndent()
            )
        )

        val songs = repository().getPlaylistSongs("playlist-1")

        assertTrue(songs[0].coverArt.orEmpty().contains("/rest/getCoverArt.view?id=playlist-cover"))
    }

    @Test
    fun getLyrics_preservesStructuredLyricMillisecondStartsWithoutOffset() = runTest {
        server.enqueueJson(
            structuredLyricsResponse(
                """
                {
                  "synced": true,
                  "line": [
                    {"start": 2000, "value": "First structured line"},
                    {"start": 3001, "value": "Second structured line"}
                  ]
                }
                """.trimIndent()
            )
        )

        val lyrics = requireNotNull(
            repository().getLyrics(
                NavidromeSong(id = "song-1", title = "Song One", artist = "Artist One")
            )
        )

        assertTrue(lyrics.synced)
        assertEquals(listOf("First structured line", "Second structured line"), lyrics.lines.map { it.text })
        assertEquals(listOf(2000, 3001), lyrics.lines.map { it.startMillis })
    }

    @Test
    fun getLyrics_preservesSmallStructuredLyricMillisecondStarts() = runTest {
        server.enqueueJson(
            structuredLyricsResponse(
                """
                {
                  "synced": true,
                  "line": [
                    {"start": 120, "value": "Early structured line"}
                  ]
                }
                """.trimIndent()
            )
        )

        val lyrics = requireNotNull(
            repository().getLyrics(
                NavidromeSong(
                    id = "song-1",
                    title = "Song One",
                    artist = "Artist One",
                    duration = 300
                )
            )
        )

        assertTrue(lyrics.synced)
        assertEquals(listOf("Early structured line"), lyrics.lines.map { it.text })
        assertEquals(listOf(120), lyrics.lines.map { it.startMillis })
    }

    @Test
    fun getLyrics_appliesPositiveStructuredLyricOffsetToShowLyricsSooner() = runTest {
        server.enqueueJson(
            structuredLyricsResponse(
                """
                {
                  "synced": true,
                  "offset": 250,
                  "line": [
                    {"start": 2000, "value": "Shifted earlier"}
                  ]
                }
                """.trimIndent()
            )
        )

        val lyrics = requireNotNull(
            repository().getLyrics(
                NavidromeSong(id = "song-1", title = "Song One", artist = "Artist One")
            )
        )

        assertEquals(listOf("Shifted earlier"), lyrics.lines.map { it.text })
        assertEquals(listOf(1750), lyrics.lines.map { it.startMillis })
    }

    @Test
    fun getLyrics_appliesNegativeStructuredLyricOffsetToShowLyricsLater() = runTest {
        server.enqueueJson(
            structuredLyricsResponse(
                """
                {
                  "synced": true,
                  "offset": -250,
                  "line": [
                    {"start": 2000, "value": "Shifted later"}
                  ]
                }
                """.trimIndent()
            )
        )

        val lyrics = requireNotNull(
            repository().getLyrics(
                NavidromeSong(id = "song-1", title = "Song One", artist = "Artist One")
            )
        )

        assertEquals(listOf("Shifted later"), lyrics.lines.map { it.text })
        assertEquals(listOf(2250), lyrics.lines.map { it.startMillis })
    }

    @Test
    fun getLyrics_clampsPositiveStructuredLyricOffsetBeforeZero() = runTest {
        server.enqueueJson(
            structuredLyricsResponse(
                """
                {
                  "synced": true,
                  "offset": 250,
                  "line": [
                    {"start": 100, "value": "Clamped start"},
                    {"start": 500, "value": "Still shifted"}
                  ]
                }
                """.trimIndent()
            )
        )

        val lyrics = requireNotNull(
            repository().getLyrics(
                NavidromeSong(id = "song-1", title = "Song One", artist = "Artist One")
            )
        )

        assertEquals(listOf("Clamped start", "Still shifted"), lyrics.lines.map { it.text })
        assertEquals(listOf(0, 250), lyrics.lines.map { it.startMillis })
    }

    @Test
    fun getLyrics_skipsKnownLrcMetadataRows() = runTest {
        val value = listOf(
            "[ar:Artist One]",
            "[ti:Song One]",
            "[al:Album One]",
            "[length:03:30]",
            "[offset:+500]",
            "[00:10.00]First lyric",
            "[00:20.50]Second lyric"
        ).joinToString("\\n")
        server.enqueueJson(
            subsonicResponse(
                """
                "lyrics": {
                  "artist": "Artist One",
                  "title": "Song One",
                  "value": "$value"
                }
                """.trimIndent()
            )
        )

        val lyrics = requireNotNull(
            repository().getLyrics(
                NavidromeSong(id = "song-1", title = "Song One", artist = "Artist One", duration = 180)
            )
        )

        assertTrue(lyrics.synced)
        assertEquals(listOf("First lyric", "Second lyric"), lyrics.lines.map { it.text })
        assertEquals(listOf(9_500, 20_000), lyrics.lines.map { it.startMillis })

        val request = server.takeRequest().path.orEmpty()
        assertTrue(request.startsWith("/rest/getLyricsBySongId.view?"))
        assertTrue(request.contains("id=song-1"))
    }

    @Test
    fun getLyrics_appliesNegativeLrcOffsetToShowLyricsLater() = runTest {
        val value = listOf(
            "[offset:-750]",
            "[00:10.00]Delayed lyric"
        ).joinToString("\\n")
        server.enqueueJson(
            subsonicResponse(
                """
                "lyrics": {
                  "value": "$value"
                }
                """.trimIndent()
            )
        )

        val lyrics = requireNotNull(
            repository().getLyrics(
                NavidromeSong(id = "song-1", title = "Song One", artist = "Artist One")
            )
        )

        assertEquals(listOf("Delayed lyric"), lyrics.lines.map { it.text })
        assertEquals(listOf(10_750), lyrics.lines.map { it.startMillis })
    }

    @Test
    fun getLyrics_clampsPositiveLrcOffsetBeforeZero() = runTest {
        val value = listOf(
            "[offset:+1500]",
            "[00:01.00]Intro lyric",
            "[00:02.00]Second lyric"
        ).joinToString("\\n")
        server.enqueueJson(
            subsonicResponse(
                """
                "lyrics": {
                  "value": "$value"
                }
                """.trimIndent()
            )
        )

        val lyrics = requireNotNull(
            repository().getLyrics(
                NavidromeSong(id = "song-1", title = "Song One", artist = "Artist One")
            )
        )

        assertEquals(listOf("Intro lyric", "Second lyric"), lyrics.lines.map { it.text })
        assertEquals(listOf(0, 500), lyrics.lines.map { it.startMillis })
    }

    @Test
    fun getLyrics_preservesNonMetadataBracketedPlainRows() = runTest {
        val value = listOf(
            "[Chorus]",
            "[custom:Keep this line]",
            "Plain lyric"
        ).joinToString("\\n")
        server.enqueueJson(
            subsonicResponse(
                """
                "lyrics": {
                  "value": "$value"
                }
                """.trimIndent()
            )
        )

        val lyrics = requireNotNull(
            repository().getLyrics(
                NavidromeSong(id = "song-1", title = "Song One", artist = "Artist One")
            )
        )

        assertFalse(lyrics.synced)
        assertEquals(
            listOf("[Chorus]", "[custom:Keep this line]", "Plain lyric"),
            lyrics.lines.map { it.text }
        )
    }

    private suspend fun assertNavidromeApiError(
        kind: NavidromeApiException.Kind,
        block: suspend () -> Unit
    ): NavidromeApiException {
        val error = try {
            block()
            null
        } catch (error: NavidromeApiException) {
            error
        }

        requireNotNull(error)
        assertEquals(kind, error.kind)
        return error
    }

    private fun repository(): NavidromeRepository {
        return NavidromeRepository(
            NavidromeConfig(
                serverUrl = server.url("/").toString(),
                username = "demo",
                password = "secret"
            )
        )
    }

    private fun subsonicResponse(dataFields: String): String {
        return """
            {
              "subsonic-response": {
                "status": "ok",
                "version": "1.16.1",
                $dataFields
              }
            }
        """.trimIndent()
    }

    private fun subsonicFailedResponse(code: Int, message: String): String {
        return """
            {
              "subsonic-response": {
                "status": "failed",
                "version": "1.16.1",
                "error": {
                  "code": $code,
                  "message": "$message"
                }
              }
            }
        """.trimIndent()
    }

    private fun emptyAlbumListResponse(): String {
        return subsonicResponse(
            """
            "albumList2": {
              "album": []
            }
            """.trimIndent()
        )
    }

    private fun albumDetailResponse(songField: String): String {
        return subsonicResponse(
            """
            "album": {
              "id": "album-1",
              "name": "Album One",
              "coverArt": "cover-1"
              $songField
            }
            """.trimIndent()
        )
    }

    private fun artistDetailResponse(albumField: String): String {
        return subsonicResponse(
            """
            "artist": {
              "id": "artist-1",
              "name": "Artist One"
              $albumField
            }
            """.trimIndent()
        )
    }

    private fun structuredLyricsResponse(structuredLyrics: String): String {
        return subsonicResponse(
            """
            "lyricsList": {
              "structuredLyrics": [
                $structuredLyrics
              ]
            }
            """.trimIndent()
        )
    }
}

private fun MockWebServer.enqueueJson(body: String) {
    enqueue(
        MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(body)
    )
}
