package com.nordic.mediahub.data

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
    fun testConnection_usesSubsonicPingOnly() = runTest {
        server.enqueueJson(subsonicOkResponse())

        repository().testConnection()

        val request = server.takeRequest().path.orEmpty()
        assertTrue(request.startsWith("/rest/ping.view?"))
        assertTrue(request.contains("u=demo"))
        assertEquals(1, server.requestCount)
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

        // Album-detail fetches run concurrently, so MockWebServer may serve the two
        // album-1/album-2 fixtures in either arrival order. Assert by song id rather
        // than by list position so the test is robust to nondeterministic completion.
        val songs = repository().getAllSongs()

        assertEquals(setOf("Song One", "Song Two"), songs.map { it.title }.toSet())
        assertEquals(2, songs.size)

        val songOne = songs.first { it.id == "song-1" }
        assertEquals("2026-06-18T12:00:00Z", songOne.created)
        assertTrue(songOne.streamUrl.orEmpty().contains("/rest/stream.view?id=song-1"))
        assertTrue(songOne.coverArt.orEmpty().contains("/rest/getCoverArt.view?id=cover-1"))

        val songTwo = songs.first { it.id == "song-2" }
        assertEquals(null, songTwo.created)
        assertTrue(songTwo.streamUrl.orEmpty().contains("/rest/stream.view?id=song-2"))
        assertTrue(songTwo.coverArt.orEmpty().contains("/rest/getCoverArt.view?id=cover-2"))

        val albumListRequest = server.takeRequest()
        assertTrue(albumListRequest.path.orEmpty().startsWith("/rest/getAlbumList2.view?"))
        assertTrue(albumListRequest.path.orEmpty().contains("type=alphabeticalByName"))
        assertTrue(albumListRequest.path.orEmpty().contains("size=100"))
        assertTrue(albumListRequest.path.orEmpty().contains("offset=0"))

        // The two getAlbum.view requests arrive in nondeterministic order; assert
        // each path is a getAlbum.view call without depending on which album first.
        assertTrue(server.takeRequest().path.orEmpty().contains("/rest/getAlbum.view"))
        assertTrue(server.takeRequest().path.orEmpty().contains("/rest/getAlbum.view"))
    }

    @Test
    fun getRecentlyAddedSongs_fetchesAlbumDetailsConcurrentlyAndFlattensResults() = runTest {
        val albums = listOf(
            NavidromeAlbum(id = "album-1", name = "Album One", coverArt = "cover-1"),
            NavidromeAlbum(id = "album-2", name = "Album Two", coverArt = "cover-2"),
            NavidromeAlbum(id = "album-3", name = "Album Three", coverArt = "cover-3")
        )
        val songsPerAlbum = 2
        repeat(albums.size) {
            server.enqueueJson(
                subsonicResponse(
                    """
                    "album": {
                      "id": "any-album",
                      "name": "Any Album",
                      "coverArt": "any-cover",
                      "song": [
                        {"id": "song-a-${it}", "title": "Song A ${it}", "artist": "Artist One", "album": "Any Album"},
                        {"id": "song-b-${it}", "title": "Song B ${it}", "artist": "Artist One", "album": "Any Album"}
                      ]
                    }
                    """.trimIndent()
                )
            )
        }

        val songs = repository().getRecentlyAddedSongs(albums, limit = albums.size * songsPerAlbum)

        assertEquals(albums.size * songsPerAlbum, songs.size)
        assertTrue(songs.all { it.streamUrl.orEmpty().contains("/rest/stream.view?id=song-") })
        assertTrue(songs.all { it.coverArt.orEmpty().contains("/rest/getCoverArt.view?id=any-cover") })

        assertEquals(albums.size, server.requestCount)
        repeat(albums.size) {
            val path = server.takeRequest().path.orEmpty()
            assertTrue(path.startsWith("/rest/getAlbum.view?"))
        }
    }

    @Test
    fun getRecentlyAddedSongs_returnsEmptyListForEmptyAlbums() = runTest {
        val songs = repository().getRecentlyAddedSongs(emptyList(), limit = 10)

        assertEquals(emptyList<NavidromeSong>(), songs)
        assertEquals(0, server.requestCount)
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
    fun getRecentSongs_mapsRandomSongsToPlayableSongs() = runTest {
        server.enqueueJson(
            subsonicResponse(
                """
                "randomSongs": {
                  "song": [
                    {
                      "id": "random-song-1",
                      "title": "Random Song",
                      "artist": "Artist One",
                      "album": "Album One",
                      "coverArt": "random-cover"
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        val songs = repository().getRecentSongs()

        assertEquals(listOf("Random Song"), songs.map { it.title })
        assertTrue(songs.single().streamUrl.orEmpty().contains("/rest/stream.view?id=random-song-1"))
        assertTrue(songs.single().coverArt.orEmpty().contains("/rest/getCoverArt.view?id=random-cover"))

        val request = server.takeRequest().path.orEmpty()
        assertTrue(request.startsWith("/rest/getRandomSongs.view?"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun getRecentSongs_fallsBackToAlbumSongsWhenRandomSongArrayIsMissingOrNull() = runTest {
        listOf(
            """"randomSongs": {}""",
            """"randomSongs": {"song": null}"""
        ).forEach { randomSongsField ->
            server.enqueueJson(subsonicResponse(randomSongsField))
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
            server.enqueueJson(
                albumDetailResponse(
                    """
                    ,"song": [
                      {"id": "fallback-song-1", "title": "Fallback Song", "artist": "Artist One", "album": "Album One"}
                    ]
                    """.trimIndent()
                )
            )

            val songs = repository().getRecentSongs()

            assertEquals(listOf("Fallback Song"), songs.map { it.title })
            assertTrue(songs.single().streamUrl.orEmpty().contains("/rest/stream.view?id=fallback-song-1"))
            assertTrue(songs.single().coverArt.orEmpty().contains("/rest/getCoverArt.view?id=cover-1"))

            val randomRequest = server.takeRequest().path.orEmpty()
            assertTrue(randomRequest.startsWith("/rest/getRandomSongs.view?"))

            val albumListRequest = server.takeRequest().path.orEmpty()
            assertTrue(albumListRequest.startsWith("/rest/getAlbumList2.view?"))
            assertTrue(albumListRequest.contains("type=newest"))
            assertTrue(albumListRequest.contains("size=20"))

            val albumRequest = server.takeRequest().path.orEmpty()
            assertTrue(albumRequest.startsWith("/rest/getAlbum.view?"))
            assertTrue(albumRequest.contains("id=album-1"))
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
    fun getAlbums_mapsMissingAndNullAlbumArraysToEmptyList() = runTest {
        listOf(
            """
            "albumList2": {}
            """.trimIndent(),
            """
            "albumList2": {"album": null}
            """.trimIndent()
        ).forEach { albumListField ->
            server.enqueueJson(subsonicResponse(albumListField))

            val albums = repository().getAlbums(NavidromeAlbumSort.Name)

            assertEquals(emptyList<NavidromeAlbum>(), albums)
        }
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
    fun getArtists_flattensIndexesAndComputesInitials() = runTest {
        server.enqueueJson(
            subsonicResponse(
                """
                "artists": {
                  "index": [
                    {
                      "name": "A",
                      "artist": [
                        {"id": "artist-1", "name": "Artist One", "albumCount": 2}
                      ]
                    },
                    {
                      "name": "B",
                      "artist": [
                        {"id": "artist-2", "name": "Beta", "albumCount": 3}
                      ]
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        val artists = repository().getArtists()

        assertEquals(listOf("Artist One", "Beta"), artists.map { it.name })
        assertEquals(listOf("AO", "B"), artists.map { it.initials })
        assertEquals(listOf(2, 3), artists.map { it.albumCount })

        val request = server.takeRequest().path.orEmpty()
        assertTrue(request.startsWith("/rest/getArtists.view?"))
    }

    @Test
    fun getArtists_mapsMissingAndNullIndexArraysToEmptyList() = runTest {
        listOf(
            """
            "artists": {}
            """.trimIndent(),
            """
            "artists": {"index": null}
            """.trimIndent()
        ).forEach { artistsField ->
            server.enqueueJson(subsonicResponse(artistsField))

            val artists = repository().getArtists()

            assertTrue(artists.isEmpty())
        }
    }

    @Test
    fun getArtists_skipsMissingAndNullNestedArtistArrays() = runTest {
        server.enqueueJson(
            subsonicResponse(
                """
                "artists": {
                  "index": [
                    {"name": "A"},
                    {"name": "B", "artist": null},
                    {
                      "name": "C",
                      "artist": [
                        {"id": "artist-3", "name": "Charlie Delta", "albumCount": 4}
                      ]
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        val artists = repository().getArtists()

        assertEquals(listOf("Charlie Delta"), artists.map { it.name })
        assertEquals(listOf("CD"), artists.map { it.initials })
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
    fun search_mapsArtistsAlbumsAndSongs() = runTest {
        server.enqueueJson(
            subsonicResponse(
                """
                "searchResult3": {
                  "artist": [
                    {"id": "artist-1", "name": "Artist One", "albumCount": 3}
                  ],
                  "album": [
                    {"id": "album-1", "name": "Album One", "artist": "Artist One", "coverArt": "album-cover", "songCount": 2}
                  ],
                  "song": [
                    {"id": "song-1", "title": "Song One", "artist": "Artist One", "album": "Album One", "coverArt": "song-cover"}
                  ]
                }
                """.trimIndent()
            )
        )

        val result = repository().search(" road ")

        assertEquals(listOf("Artist One"), result.artists.map { it.name })
        assertEquals("AO", result.artists[0].initials)
        assertEquals(3, result.artists[0].albumCount)
        assertEquals(listOf("Album One"), result.albums.map { it.name })
        assertTrue(result.albums[0].coverArt.orEmpty().contains("/rest/getCoverArt.view?id=album-cover"))
        assertEquals(listOf("Song One"), result.songs.map { it.title })
        assertTrue(result.songs[0].coverArt.orEmpty().contains("/rest/getCoverArt.view?id=song-cover"))
        assertTrue(result.songs[0].streamUrl.orEmpty().contains("/rest/stream.view?id=song-1"))

        val request = server.takeRequest().path.orEmpty()
        assertTrue(request.startsWith("/rest/search3.view?"))
        assertTrue(request.contains("query=road"))
    }

    @Test
    fun search_mapsMissingAndNullResultArraysToEmptyLists() = runTest {
        listOf(
            """
            "searchResult3": {}
            """.trimIndent(),
            """
            "searchResult3": {
              "artist": null,
              "album": null,
              "song": null
            }
            """.trimIndent()
        ).forEach { searchResultField ->
            server.enqueueJson(subsonicResponse(searchResultField))

            val result = repository().search("road")

            assertEquals(SearchMusicResult(), result)
        }
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
    fun getLyrics_fallsBackToPlainLyricsWhenStructuredLyricsArrayIsMissingOrNull() = runTest {
        listOf(
            """"lyricsList": {}""",
            """"lyricsList": {"structuredLyrics": null}"""
        ).forEach { lyricsListField ->
            server.enqueueJson(
                subsonicResponse(
                    """
                    $lyricsListField,
                    "lyrics": {
                      "value": "Plain fallback lyric"
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
            assertEquals(listOf("Plain fallback lyric"), lyrics.lines.map { it.text })
        }
    }

    @Test
    fun getLyrics_fallsBackToPlainLyricsWhenStructuredLineArraysAreMissingOrNull() = runTest {
        server.enqueueJson(
            subsonicResponse(
                """
                "lyricsList": {
                  "structuredLyrics": [
                    {"synced": true},
                    {"synced": true, "line": null}
                  ]
                },
                "lyrics": {
                  "value": "Plain fallback lyric"
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
        assertEquals(listOf("Plain fallback lyric"), lyrics.lines.map { it.text })
    }

    @Test
    fun getLyrics_skipsStructuredLinesWithMissingNullOrBlankValues() = runTest {
        server.enqueueJson(
            structuredLyricsResponse(
                """
                {
                  "synced": true,
                  "line": [
                    {"start": 1000},
                    {"start": 2000, "value": null},
                    {"start": 3000, "value": ""},
                    {"start": 4000, "value": "   "},
                    {"start": 5000, "value": " Kept structured line "}
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
        assertEquals(listOf("Kept structured line"), lyrics.lines.map { it.text })
        assertEquals(listOf(5000), lyrics.lines.map { it.startMillis })
    }

    @Test
    fun getLyrics_fallsBackToPlainLyricsWhenStructuredLineValuesAreMissingNullOrBlank() = runTest {
        server.enqueueJson(
            subsonicResponse(
                """
                "lyricsList": {
                  "structuredLyrics": [
                    {
                      "synced": true,
                      "line": [
                        {"start": 1000},
                        {"start": 2000, "value": null},
                        {"start": 3000, "value": ""},
                        {"start": 4000, "value": "   "}
                      ]
                    }
                  ]
                },
                "lyrics": {
                  "value": "Plain fallback lyric"
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
        assertEquals(listOf("Plain fallback lyric"), lyrics.lines.map { it.text })
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

    @Test
    fun star_callsCorrectEndpointWithAlbumId() = runTest {
        server.enqueueJson(subsonicOkResponse())

        repository().star(albumId = "al-1")

        val request = server.takeRequest().path.orEmpty()
        assertTrue(request.startsWith("/rest/star2.view?"))
        assertTrue(request.contains("albumId=al-1"))
        assertTrue(request.contains("u=demo"))
        assertTrue(request.contains("c=Nordic"))
        assertFalse(request.contains("id="))
        assertFalse(request.contains("artistId="))
    }

    @Test
    fun unstar_callsCorrectEndpointWithArtistId() = runTest {
        server.enqueueJson(subsonicOkResponse())

        repository().unstar(artistId = "ar-1")

        val request = server.takeRequest().path.orEmpty()
        assertTrue(request.startsWith("/rest/unstar.view?"))
        assertTrue(request.contains("artistId=ar-1"))
        assertFalse(request.contains("id="))
        assertFalse(request.contains("albumId="))
    }

    @Test
    fun getStarred2_mapsAlbumsSongsArtists() = runTest {
        server.enqueueJson(
            subsonicResponse(
                """
                "starred2": {
                  "album": [
                    {"id": "album-1", "name": "Starred Album", "coverArt": "album-cover"}
                  ],
                  "song": [
                    {"id": "song-1", "title": "Starred Song", "artist": "Artist One", "coverArt": "song-cover"}
                  ],
                  "artist": [
                    {"id": "artist-1", "name": "Starred Artist", "albumCount": 5}
                  ]
                }
                """.trimIndent()
            )
        )

        val starred = repository().getStarred()

        assertEquals(listOf("Starred Album"), starred.albums.map { it.name })
        assertTrue(starred.albums[0].coverArt.orEmpty().contains("/rest/getCoverArt.view?id=album-cover"))
        assertEquals(listOf("Starred Song"), starred.songs.map { it.title })
        assertTrue(starred.songs[0].coverArt.orEmpty().contains("/rest/getCoverArt.view?id=song-cover"))
        assertTrue(starred.songs[0].streamUrl.orEmpty().contains("/rest/stream.view?id=song-1"))
        assertEquals(listOf("Starred Artist"), starred.artists.map { it.name })
        assertEquals(listOf("SA"), starred.artists.map { it.initials })

        val request = server.takeRequest().path.orEmpty()
        assertTrue(request.startsWith("/rest/getStarred2.view?"))
    }

    @Test
    fun getStarred2_mapsMissingAndNullStarredArraysToEmptyLists() = runTest {
        listOf(
            """"starred2": {}""",
            """"starred2": {"album": null, "song": null, "artist": null}"""
        ).forEach { starredField ->
            server.enqueueJson(subsonicResponse(starredField))

            val starred = repository().getStarred()

            assertEquals(StarredContent(), starred)
        }
    }

    @Test
    fun createPlaylist_callsEndpointAndMapsResponse() = runTest {
        server.enqueueJson(
            subsonicResponse(
                """
                "playlist": {
                  "id": "playlist-1",
                  "name": "My Road Mix",
                  "owner": "demo",
                  "songCount": 2,
                  "duration": 390,
                  "coverArt": "playlist-cover"
                }
                """.trimIndent()
            )
        )

        val playlist = repository().createPlaylist(name = "My Road Mix", songIds = listOf("song-1", "song-2"))

        assertEquals("playlist-1", playlist.id)
        assertEquals("My Road Mix", playlist.name)
        assertEquals(2, playlist.songCount)
        assertEquals(390, playlist.duration)
        assertTrue(playlist.coverArt.orEmpty().contains("/rest/getCoverArt.view?id=playlist-cover"))

        val request = server.takeRequest().path.orEmpty()
        assertTrue(request.startsWith("/rest/createPlaylist.view?"))
        assertTrue(request.contains("name=My%20Road%20Mix"))
        assertTrue(request.contains("songId=song-1"))
        assertTrue(request.contains("songId=song-2"))
    }

    @Test
    fun createPlaylist_throwsTypedApiExceptionWhenPlaylistIsNull() = runTest {
        server.enqueueJson(subsonicOkResponse())

        val error = assertNavidromeApiError(NavidromeApiException.Kind.SUBSONIC) {
            repository().createPlaylist(name = "Empty")
        }

        assertTrue(error.message.orEmpty().contains("创建歌单返回为空"))
    }

    @Test
    fun updatePlaylist_addSongAndRemoveByIndex() = runTest {
        server.enqueueJson(subsonicOkResponse())
        server.enqueueJson(subsonicOkResponse())

        repository().addToPlaylist(playlistId = "playlist-1", songId = "song-9")
        repository().removeFromPlaylist(playlistId = "playlist-1", songIndex = 3)

        val addRequest = server.takeRequest().path.orEmpty()
        assertTrue(addRequest.startsWith("/rest/updatePlaylist.view?"))
        assertTrue(addRequest.contains("playlistId=playlist-1"))
        assertTrue(addRequest.contains("songIdToAdd=song-9"))

        val removeRequest = server.takeRequest().path.orEmpty()
        assertTrue(removeRequest.startsWith("/rest/updatePlaylist.view?"))
        assertTrue(removeRequest.contains("playlistId=playlist-1"))
        assertTrue(removeRequest.contains("songIndexToRemove=3"))
    }

    @Test
    fun renamePlaylist_callsUpdatePlaylistWithName() = runTest {
        server.enqueueJson(subsonicOkResponse())

        repository().renamePlaylist(playlistId = "playlist-1", newName = "Renamed")

        val request = server.takeRequest().path.orEmpty()
        assertTrue(request.startsWith("/rest/updatePlaylist.view?"))
        assertTrue(request.contains("playlistId=playlist-1"))
        assertTrue(request.contains("name=Renamed"))
    }

    @Test
    fun deletePlaylist_callsCorrectEndpoint() = runTest {
        server.enqueueJson(subsonicOkResponse())

        repository().deletePlaylist(playlistId = "playlist-1")

        val request = server.takeRequest().path.orEmpty()
        assertTrue(request.startsWith("/rest/deletePlaylist.view?"))
        assertTrue(request.contains("id=playlist-1"))
    }

    @Test
    fun getSimilarSongs_callsEndpointAndMapsSongs() = runTest {
        server.enqueueJson(
            subsonicResponse(
                """
                "similarSongs": {
                  "song": [
                    {
                      "id": "similar-1",
                      "title": "Similar Song",
                      "artist": "Artist One",
                      "album": "Album One",
                      "coverArt": "similar-cover"
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        val songs = repository().getSimilarSongs("song-1")

        assertEquals(listOf("Similar Song"), songs.map { it.title })
        assertTrue(songs[0].streamUrl.orEmpty().contains("/rest/stream.view?id=similar-1"))
        assertTrue(songs[0].coverArt.orEmpty().contains("/rest/getCoverArt.view?id=similar-cover"))

        val request = server.takeRequest().path.orEmpty()
        assertTrue(request.startsWith("/rest/getSimilarSongs.view?"))
        assertTrue(request.contains("id=song-1"))
        assertTrue(request.contains("count=50"))
    }

    @Test
    fun getSimilarSongs_mapsMissingAndNullSongArraysToEmptyList() = runTest {
        listOf(
            """"similarSongs": {}""",
            """"similarSongs": {"song": null}"""
        ).forEach { similarField ->
            server.enqueueJson(subsonicResponse(similarField))

            val songs = repository().getSimilarSongs("song-1")

            assertEquals(emptyList<NavidromeSong>(), songs)
        }
    }

    @Test
    fun getRandomSongs_callsEndpointAndMapsSongs() = runTest {
        server.enqueueJson(
            subsonicResponse(
                """
                "randomSongs": {
                  "song": [
                    {
                      "id": "random-1",
                      "title": "Random Song",
                      "artist": "Artist One",
                      "album": "Album One",
                      "coverArt": "random-cover"
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        val songs = repository().getRandomSongs(count = 30)

        assertEquals(listOf("Random Song"), songs.map { it.title })
        assertTrue(songs[0].streamUrl.orEmpty().contains("/rest/stream.view?id=random-1"))
        assertTrue(songs[0].coverArt.orEmpty().contains("/rest/getCoverArt.view?id=random-cover"))

        val request = server.takeRequest().path.orEmpty()
        assertTrue(request.startsWith("/rest/getRandomSongs.view?"))
        assertTrue(request.contains("size=30"))
    }

    @Test
    fun scrobble_callsEndpointWithSubmissionTrue() = runTest {
        server.enqueueJson(subsonicOkResponse())

        repository().scrobble(songId = "song-1", submission = true)

        val request = server.takeRequest().path.orEmpty()
        assertTrue(request.startsWith("/rest/scrobble.view?"))
        assertTrue(request.contains("id=song-1"))
        assertTrue(request.contains("submission=true"))
    }

    @Test
    fun scrobble_callsEndpointWithSubmissionFalse() = runTest {
        server.enqueueJson(subsonicOkResponse())

        repository().scrobble(songId = "song-1", submission = false)

        val request = server.takeRequest().path.orEmpty()
        assertTrue(request.startsWith("/rest/scrobble.view?"))
        assertTrue(request.contains("id=song-1"))
        assertTrue(request.contains("submission=false"))
    }

    @Test
    fun subsonicError_formatsNullSafeWhenCodeOrMessageIsMissing() = runTest {
        listOf(
            """
            {
              "subsonic-response": {
                "status": "failed",
                "version": "1.16.1",
                "error": {}
              }
            }
            """.trimIndent(),
            """
            {
              "subsonic-response": {
                "status": "failed",
                "version": "1.16.1",
                "error": {"code": 70}
              }
            }
            """.trimIndent(),
            """
            {
              "subsonic-response": {
                "status": "failed",
                "version": "1.16.1",
                "error": {"message": "User not authorized"}
              }
            }
            """.trimIndent()
        ).forEach { errorBody ->
            server.enqueueJson(errorBody)

            val error = assertNavidromeApiError(NavidromeApiException.Kind.SUBSONIC) {
                repository().getPlaylists()
            }

            assertTrue(error.message.orEmpty().contains("Subsonic错误"))
        }
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

    private fun subsonicOkResponse(): String {
        return """
            {
              "subsonic-response": {
                "status": "ok",
                "version": "1.16.1"
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
