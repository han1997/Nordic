package com.nordic.mediahub.data

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
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
                    {"id": "song-1", "title": "Song One", "artist": "Artist One", "album": "Album One"}
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
}

private fun MockWebServer.enqueueJson(body: String) {
    enqueue(
        MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(body)
    )
}
