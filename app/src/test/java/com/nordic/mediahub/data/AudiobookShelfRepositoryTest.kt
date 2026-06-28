package com.nordic.mediahub.data

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AudiobookShelfRepositoryTest {
    private lateinit var server: MockWebServer

    @Test
    fun resolveAudiobookSyncCurrentTimeSeconds_clampsZeroDurationToZero() {
        assertEquals(
            0,
            resolveAudiobookSyncCurrentTimeSeconds(currentTimeSeconds = 10, durationSeconds = 0)
        )
    }

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
    fun startPlayback_usesLoginTokenAndBuildsAbsoluteAudioUrl() = runTest {
        server.enqueueJson("""{"user":{"id":"u1","username":"demo","token":"token-123"}}""")
        server.enqueueJson(playbackSessionJson(contentUrl = "/audio/book-1.mp3?download=0"))

        val session = repository().startPlayback("book-1")

        assertEquals("session-1", session.sessionId)
        assertEquals("Book One", session.displayTitle)
        assertEquals("${server.url("/")}api/items/book-1/cover", session.coverUrl)
        assertEquals(1, session.audioTracks.size)
        assertEquals(
            "${server.url("/")}audio/book-1.mp3?download=0&token=token-123",
            session.audioTracks.single().contentUrl
        )

        val loginRequest = server.takeRequest()
        assertEquals("/login", loginRequest.path)
        assertEquals("true", loginRequest.getHeader("x-return-tokens"))

        val playRequest = server.takeRequest()
        assertEquals("/api/items/book-1/play", playRequest.path)
        assertEquals("Bearer token-123", playRequest.getHeader("Authorization"))
    }

    @Test
    fun startPlayback_appendsTokenWhenTokenTextIsOnlyInAudioPath() = runTest {
        server.enqueueJson("""{"user":{"id":"u1","username":"demo","token":"token-123"}}""")
        server.enqueueJson(playbackSessionJson(contentUrl = "/audio/token=placeholder/book-1.mp3?download=0"))

        val session = repository().startPlayback("book-1")

        assertEquals(
            "${server.url("/")}audio/token=placeholder/book-1.mp3?download=0&token=token-123",
            session.audioTracks.single().contentUrl
        )
    }

    @Test
    fun startPlayback_doesNotDuplicateExistingAudioTokenQueryParameter() = runTest {
        server.enqueueJson("""{"user":{"id":"u1","username":"demo","token":"token-123"}}""")
        server.enqueueJson(playbackSessionJson(contentUrl = "/audio/book-1.mp3?token=upstream-token"))

        val session = repository().startPlayback("book-1")

        assertEquals(
            "${server.url("/")}audio/book-1.mp3?token=upstream-token",
            session.audioTracks.single().contentUrl
        )
    }

    @Test
    fun getLibraries_usesAccessTokenWhenLoginTokenIsBlank() = runTest {
        server.enqueueJson("""{"user":{"id":"u1","username":"demo","token":"   ","accessToken":"access-123"}}""")
        server.enqueueJson(
            """
                {
                  "libraries": [
                    {"id":"lib-1","name":"Books","mediaType":"book"}
                  ]
                }
            """.trimIndent()
        )

        val libraries = repository().getLibraries()

        assertEquals(listOf("lib-1"), libraries.map { it.id })
        server.takeRequest()
        val librariesRequest = server.takeRequest()
        assertEquals("Bearer access-123", librariesRequest.getHeader("Authorization"))
    }

    @Test
    fun getLibraries_throwsTypedAuthExceptionForMissingLoginUsers() = runTest {
        listOf(
            """{}""",
            """{"user":null}"""
        ).forEach { response ->
            server.enqueueJson(response)

            assertAudiobookShelfApiError(AudiobookShelfApiException.Kind.AUTH) {
                repository().getLibraries()
            }
        }

        repeat(2) {
            assertEquals("/login", server.takeRequest().path)
        }
    }

    @Test
    fun getLibraries_throwsTypedAuthExceptionWhenLoginTokensAreMissingOrBlank() = runTest {
        listOf(
            """{"user":{"id":"u1","username":"demo"}}""",
            """{"user":{"id":"u1","username":"demo","token":null,"accessToken":null}}""",
            """{"user":{"id":"u1","username":"demo","token":"","accessToken":"   "}}"""
        ).forEach { response ->
            server.enqueueJson(response)

            assertAudiobookShelfApiError(AudiobookShelfApiException.Kind.AUTH) {
                repository().getLibraries()
            }
        }

        repeat(3) {
            assertEquals("/login", server.takeRequest().path)
        }
    }

    @Test
    fun getLibraries_filtersBookMediaTypeCaseInsensitively() = runTest {
        server.enqueueJson("""{"user":{"id":"u1","username":"demo","token":"token-123"}}""")
        server.enqueueJson(
            """
                {
                  "libraries": [
                    {"id":"lib-lower","name":"Lower","mediaType":"book"},
                    {"id":"lib-title","name":"Title","mediaType":"Book"},
                    {"id":"lib-upper","name":"Upper","mediaType":"BOOK"},
                    {"id":"lib-podcast","name":"Podcasts","mediaType":"podcast"}
                  ]
                }
            """.trimIndent()
        )

        val libraries = repository().getLibraries()

        assertEquals(listOf("lib-lower", "lib-title", "lib-upper"), libraries.map { it.id })

        server.takeRequest()
        val librariesRequest = server.takeRequest()
        assertEquals("/api/libraries", librariesRequest.path)
        assertEquals("Bearer token-123", librariesRequest.getHeader("Authorization"))
    }

    @Test
    fun getLibraryItems_pagesUntilServerTotalIsLoaded() = runTest {
        server.enqueueJson("""{"user":{"id":"u1","username":"demo","token":"token-123"}}""")
        server.enqueueJson(libraryItemsJson(itemRange = 1..50, total = 51))
        server.enqueueJson(libraryItemsJson(itemRange = 51..51, total = 51))

        val items = repository().getLibraryItems("lib-1")

        assertEquals(51, items.size)
        assertEquals("Book 1", items.first().title)
        assertEquals("Book 51", items.last().title)

        server.takeRequest()
        val firstPageRequest = server.takeRequest()
        assertEquals("Bearer token-123", firstPageRequest.getHeader("Authorization"))
        assertTrue(firstPageRequest.path.orEmpty().startsWith("/api/libraries/lib-1/items?"))
        assertTrue(firstPageRequest.path.orEmpty().contains("limit=50"))
        assertTrue(firstPageRequest.path.orEmpty().contains("page=0"))

        val secondPageRequest = server.takeRequest()
        assertEquals("Bearer token-123", secondPageRequest.getHeader("Authorization"))
        assertTrue(secondPageRequest.path.orEmpty().contains("limit=50"))
        assertTrue(secondPageRequest.path.orEmpty().contains("page=1"))
    }

    @Test
    fun getLibraryItems_mapsBlankAndNonBlankCoverPaths() = runTest {
        server.enqueueJson("""{"user":{"id":"u1","username":"demo","token":"token-123"}}""")
        server.enqueueJson(
            """
                {
                  "results": [
                    {
                      "id": "book-blank",
                      "libraryId": "lib-1",
                      "mediaType": "book",
                      "media": {
                        "id": "media-blank",
                        "metadata": {"title": "Blank Cover"},
                        "coverPath": "   ",
                        "duration": 60.0,
                        "numChapters": 1
                      }
                    },
                    {
                      "id": "book-relative",
                      "libraryId": "lib-1",
                      "mediaType": "book",
                      "media": {
                        "id": "media-relative",
                        "metadata": {"title": "Relative Cover"},
                        "coverPath": "/api/items/book-relative/cover",
                        "duration": 60.0,
                        "numChapters": 1
                      }
                    },
                    {
                      "id": "book-absolute",
                      "libraryId": "lib-1",
                      "mediaType": "book",
                      "media": {
                        "id": "media-absolute",
                        "metadata": {"title": "Absolute Cover"},
                        "coverPath": "https://cdn.example.test/cover.jpg",
                        "duration": 60.0,
                        "numChapters": 1
                      }
                    }
                  ],
                  "total": 3,
                  "limit": 50,
                  "page": 0,
                  "mediaType": "book",
                  "minified": true
                }
            """.trimIndent()
        )

        val items = repository().getLibraryItems("lib-1")

        assertNull(items[0].coverUrl)
        assertEquals("${server.url("/")}api/items/book-relative/cover", items[1].coverUrl)
        assertEquals("https://cdn.example.test/cover.jpg", items[2].coverUrl)
    }

    @Test
    fun getLibraryItem_ignoresBlankCoverPath() = runTest {
        server.enqueueJson("""{"user":{"id":"u1","username":"demo","token":"token-123"}}""")
        server.enqueueJson(libraryItemDetailJson(coverPath = "   "))

        val detail = repository().getLibraryItem("book-1")

        assertNull(detail.coverUrl)
    }

    @Test
    fun startPlayback_ignoresBlankSessionCoverPath() = runTest {
        server.enqueueJson("""{"user":{"id":"u1","username":"demo","token":"token-123"}}""")
        server.enqueueJson(
            playbackSessionJson(
                contentUrl = "/audio/book-1.mp3?download=0",
                coverPath = "   "
            )
        )

        val session = repository().startPlayback("book-1")

        assertNull(session.coverUrl)
    }

    @Test
    fun getLibraries_throwsTypedExceptionForEmptyResponseBody() = runTest {
        server.enqueueJson("""{"user":{"id":"u1","username":"demo","token":"token-123"}}""")
        server.enqueue(MockResponse().setResponseCode(200))

        val error = try {
            repository().getLibraries()
            null
        } catch (error: AudiobookShelfApiException) {
            error
        }

        requireNotNull(error)
        assertEquals(AudiobookShelfApiException.Kind.API, error.kind)
        assertTrue(error.message.orEmpty().contains("响应为空"))
    }

    @Test
    fun getLibraryItems_throwsTypedExceptionForEmptyResponseBody() = runTest {
        server.enqueueJson("""{"user":{"id":"u1","username":"demo","token":"token-123"}}""")
        server.enqueue(MockResponse().setResponseCode(200))

        val error = try {
            repository().getLibraryItems("lib-1")
            null
        } catch (error: AudiobookShelfApiException) {
            error
        }

        requireNotNull(error)
        assertEquals(AudiobookShelfApiException.Kind.API, error.kind)
    }

    @Test
    fun getLibraryItem_throwsTypedExceptionForEmptyResponseBody() = runTest {
        server.enqueueJson("""{"user":{"id":"u1","username":"demo","token":"token-123"}}""")
        server.enqueue(MockResponse().setResponseCode(200))

        val error = try {
            repository().getLibraryItem("book-1")
            null
        } catch (error: AudiobookShelfApiException) {
            error
        }

        requireNotNull(error)
        assertEquals(AudiobookShelfApiException.Kind.API, error.kind)
    }

    @Test
    fun startPlayback_throwsTypedExceptionForEmptyResponseBody() = runTest {
        server.enqueueJson("""{"user":{"id":"u1","username":"demo","token":"token-123"}}""")
        server.enqueue(MockResponse().setResponseCode(200))

        val error = try {
            repository().startPlayback("book-1")
            null
        } catch (error: AudiobookShelfApiException) {
            error
        }

        requireNotNull(error)
        assertEquals(AudiobookShelfApiException.Kind.API, error.kind)
    }

    @Test
    fun syncAndCloseSession_sendsProgressSyncAndCloseRequests() = runTest {
        server.enqueueJson("""{"user":{"id":"u1","username":"demo","accessToken":"access-123"}}""")
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(204))

        repository().syncAndCloseSession(sampleSession(durationSeconds = 100), currentTimeSeconds = 150, deltaSeconds = 12)

        server.takeRequest()
        val progressRequest = server.takeRequest()
        val progressBody = progressRequest.body.readUtf8()
        assertEquals("/api/me/progress/book-1", progressRequest.path)
        assertTrue(progressBody.contains(""""currentTime":100.0"""))
        assertTrue(progressBody.contains(""""progress":1.0"""))

        val syncRequest = server.takeRequest()
        val syncBody = syncRequest.body.readUtf8()
        assertEquals("/api/session/session-1/sync", syncRequest.path)
        assertTrue(syncBody.contains(""""currentTime":100.0"""))
        assertTrue(syncBody.contains(""""timeListened":12.0"""))

        val closeRequest = server.takeRequest()
        val closeBody = closeRequest.body.readUtf8()
        assertEquals("/api/session/session-1/close", closeRequest.path)
        assertTrue(closeBody.contains(""""currentTime":100.0"""))
    }

    @Test
    fun syncAndCloseSession_clampsNegativeCurrentTimeToZero() = runTest {
        server.enqueueJson("""{"user":{"id":"u1","username":"demo","accessToken":"access-123"}}""")
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(204))

        repository().syncAndCloseSession(sampleSession(durationSeconds = 100), currentTimeSeconds = -20, deltaSeconds = -5)

        server.takeRequest()
        val progressRequest = server.takeRequest()
        val progressBody = progressRequest.body.readUtf8()
        assertEquals("/api/me/progress/book-1", progressRequest.path)
        assertTrue(progressBody.contains(""""currentTime":0.0"""))
        assertTrue(progressBody.contains(""""progress":0.0"""))

        val syncRequest = server.takeRequest()
        val syncBody = syncRequest.body.readUtf8()
        assertEquals("/api/session/session-1/sync", syncRequest.path)
        assertTrue(syncBody.contains(""""currentTime":0.0"""))
        assertTrue(syncBody.contains(""""timeListened":0.0"""))

        val closeRequest = server.takeRequest()
        val closeBody = closeRequest.body.readUtf8()
        assertEquals("/api/session/session-1/close", closeRequest.path)
        assertTrue(closeBody.contains(""""currentTime":0.0"""))
    }

    @Test
    fun syncAndCloseSession_clampsZeroDurationCurrentTimeToZero() = runTest {
        server.enqueueJson("""{"user":{"id":"u1","username":"demo","accessToken":"access-123"}}""")
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(204))

        repository().syncAndCloseSession(sampleSession(durationSeconds = 0), currentTimeSeconds = 10, deltaSeconds = 8)

        server.takeRequest()
        val progressRequest = server.takeRequest()
        val progressBody = progressRequest.body.readUtf8()
        assertEquals("/api/me/progress/book-1", progressRequest.path)
        assertTrue(progressBody.contains(""""duration":1.0"""))
        assertTrue(progressBody.contains(""""currentTime":0.0"""))
        assertTrue(progressBody.contains(""""progress":0.0"""))

        val syncRequest = server.takeRequest()
        val syncBody = syncRequest.body.readUtf8()
        assertEquals("/api/session/session-1/sync", syncRequest.path)
        assertTrue(syncBody.contains(""""duration":1.0"""))
        assertTrue(syncBody.contains(""""currentTime":0.0"""))
        assertTrue(syncBody.contains(""""timeListened":8.0"""))

        val closeRequest = server.takeRequest()
        val closeBody = closeRequest.body.readUtf8()
        assertEquals("/api/session/session-1/close", closeRequest.path)
        assertTrue(closeBody.contains(""""duration":1.0"""))
        assertTrue(closeBody.contains(""""currentTime":0.0"""))
    }

    @Test
    fun syncProgress_throwsTypedExceptionWhenProgressUpdateFails() = runTest {
        server.enqueueJson("""{"user":{"id":"u1","username":"demo","token":"token-123"}}""")
        server.enqueue(MockResponse().setResponseCode(500))

        val error = try {
            repository().syncProgress(sampleSession(), currentTimeSeconds = 10, deltaSeconds = 5)
            null
        } catch (error: AudiobookShelfApiException) {
            error
        }

        requireNotNull(error)
        assertEquals(AudiobookShelfApiException.Kind.HTTP, error.kind)
        assertTrue(error.message.orEmpty().contains("HTTP 500"))
    }

    private fun repository(): AudiobookShelfRepository {
        return AudiobookShelfRepository(
            AudiobookShelfConfig(
                serverUrl = server.url("/").toString(),
                username = "demo",
                password = "secret"
            )
        )
    }

    private suspend fun assertAudiobookShelfApiError(
        kind: AudiobookShelfApiException.Kind,
        block: suspend () -> Unit
    ): AudiobookShelfApiException {
        val error = try {
            block()
            null
        } catch (error: AudiobookShelfApiException) {
            error
        }

        requireNotNull(error)
        assertEquals(kind, error.kind)
        return error
    }

    private fun sampleSession(durationSeconds: Int = 300): AudiobookPlaybackSession {
        return AudiobookPlaybackSession(
            sessionId = "session-1",
            libraryItemId = "book-1",
            displayTitle = "Book One",
            displayAuthor = "Author",
            coverUrl = null,
            durationSeconds = durationSeconds,
            currentTimeSeconds = 0,
            startTimeSeconds = 0,
            chapters = emptyList(),
            audioTracks = emptyList()
        )
    }

    private fun libraryItemsJson(itemRange: IntRange, total: Int): String {
        val results = itemRange.joinToString(",") { index ->
            """
                {
                  "id": "book-$index",
                  "libraryId": "lib-1",
                  "mediaType": "book",
                  "updatedAt": $index,
                  "media": {
                    "id": "media-$index",
                    "metadata": {"title": "Book $index"},
                    "duration": 60.0,
                    "numChapters": 1
                  }
                }
            """.trimIndent()
        }
        return """
            {
              "results": [$results],
              "total": $total,
              "limit": 50,
              "page": 0,
              "mediaType": "book",
              "minified": true
            }
        """.trimIndent()
    }

    private fun libraryItemDetailJson(coverPath: String): String {
        return """
            {
              "id": "book-1",
              "libraryId": "lib-1",
              "mediaType": "book",
              "media": {
                "id": "media-1",
                "metadata": {
                  "title": "Book One",
                  "authors": [],
                  "narrators": [],
                  "series": []
                },
                "coverPath": "$coverPath",
                "duration": 300.0,
                "chapters": []
              }
            }
        """.trimIndent()
    }

    private fun playbackSessionJson(
        contentUrl: String,
        coverPath: String = "/api/items/book-1/cover"
    ): String {
        return """
            {
              "id": "session-1",
              "libraryId": "lib-1",
              "libraryItemId": "book-1",
              "mediaType": "book",
              "displayTitle": "Book One",
              "displayAuthor": "Author",
              "coverPath": "$coverPath",
              "duration": 300.0,
              "startTime": 12.0,
              "currentTime": 12.0,
              "chapters": [
                {"id": 1, "start": 0.0, "end": 120.0, "title": "Chapter One"}
              ],
              "audioTracks": [
                {
                  "index": 0,
                  "duration": 300.0,
                  "startOffset": 0.0,
                  "title": "Track One",
                  "contentUrl": "$contentUrl"
                }
              ]
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
