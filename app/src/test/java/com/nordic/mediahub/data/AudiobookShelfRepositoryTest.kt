package com.nordic.mediahub.data

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AudiobookShelfRepositoryTest {
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
    fun startPlayback_usesLoginTokenAndBuildsAbsoluteAudioUrl() = runTest {
        server.enqueueJson("""{"user":{"id":"u1","username":"demo","token":"token-123"}}""")
        server.enqueueJson(playbackSessionJson(contentUrl = "/audio/book-1.mp3?download=0"))

        val session = repository().startPlayback("book-1")

        assertEquals("session-1", session.sessionId)
        assertEquals("Book One", session.displayTitle)
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
        assertTrue(progressBody.contains(""""currentTime":150.0"""))
        assertTrue(progressBody.contains(""""progress":1.0"""))

        val syncRequest = server.takeRequest()
        val syncBody = syncRequest.body.readUtf8()
        assertEquals("/api/session/session-1/sync", syncRequest.path)
        assertTrue(syncBody.contains(""""timeListened":12.0"""))

        val closeRequest = server.takeRequest()
        val closeBody = closeRequest.body.readUtf8()
        assertEquals("/api/session/session-1/close", closeRequest.path)
        assertTrue(closeBody.contains(""""currentTime":150.0"""))
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

    private fun playbackSessionJson(contentUrl: String): String {
        return """
            {
              "id": "session-1",
              "libraryId": "lib-1",
              "libraryItemId": "book-1",
              "mediaType": "book",
              "displayTitle": "Book One",
              "displayAuthor": "Author",
              "coverPath": "/api/items/book-1/cover",
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
