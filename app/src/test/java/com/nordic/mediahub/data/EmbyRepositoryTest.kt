package com.nordic.mediahub.data

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EmbyRepositoryTest {
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
    fun isReadyForVideoSync_requiresEmbyUrlAndAuth() {
        assertFalse(VideoServerConfig(serverUrl = "http://example.test").isReadyForVideoSync())
        assertTrue(
            VideoServerConfig(
                serverUrl = "http://example.test",
                apiKey = "api-key"
            ).isReadyForVideoSync()
        )
        assertTrue(
            VideoServerConfig(
                serverUrl = "http://example.test",
                username = "demo",
                password = "secret"
            ).isReadyForVideoSync()
        )
        assertFalse(
            VideoServerConfig(
                type = VideoServerType.PLEX,
                serverUrl = "http://example.test",
                apiKey = "api-key"
            ).isReadyForVideoSync()
        )
    }

    @Test
    fun getCatalog_usesApiKeyAndMapsLibrariesItemsAndImages() = runTest {
        server.enqueueJson("""[{"Id":"u1","Name":"demo"}]""")
        server.enqueueJson(
            """
                {
                  "Items": [
                    {"Id":"lib-movie","Name":"Movies","Type":"CollectionFolder","CollectionType":"movies","ChildCount":12},
                    {"Id":"lib-music","Name":"Music","Type":"CollectionFolder","CollectionType":"music","ChildCount":3}
                  ],
                  "TotalRecordCount": 2
                }
            """.trimIndent()
        )
        server.enqueueJson(
            """
                {
                  "Items": [
                    {
                      "Id":"movie-1",
                      "Name":"Arrival",
                      "Type":"Movie",
                      "Overview":"First contact story.",
                      "ProductionYear":2016,
                      "RunTimeTicks":69600000000,
                      "ImageTags":{"Primary":"tag-1"}
                    }
                  ],
                  "TotalRecordCount": 1
                }
            """.trimIndent()
        )

        val catalog = repository(apiKey = "api-key").getCatalog()

        assertEquals(1, catalog.libraries.size)
        assertEquals("lib-movie", catalog.selectedLibraryId)
        assertEquals("Movies", catalog.libraries.single().name)
        assertEquals(1, catalog.items.size)
        val item = catalog.items.single()
        assertEquals("Arrival", item.title)
        assertEquals(6960, item.durationSeconds)
        assertTrue(item.imageUrl.orEmpty().contains("/Items/movie-1/Images/Primary"))
        assertTrue(item.imageUrl.orEmpty().contains("api_key=api-key"))
        assertTrue(item.imageUrl.orEmpty().contains("tag=tag-1"))
        assertTrue(item.streamUrl.orEmpty().contains("/Videos/movie-1/stream"))
        assertTrue(item.streamUrl.orEmpty().contains("Static=true"))
        assertTrue(item.streamUrl.orEmpty().contains("api_key=api-key"))

        val usersRequest = server.takeRequest()
        assertEquals("/Users", usersRequest.path)
        assertEquals("api-key", usersRequest.getHeader("X-Emby-Token"))

        val viewsRequest = server.takeRequest()
        assertEquals("/Users/u1/Views", viewsRequest.path)
        assertEquals("api-key", viewsRequest.getHeader("X-Emby-Token"))

        val itemsRequest = server.takeRequest()
        assertTrue(itemsRequest.path.orEmpty().startsWith("/Users/u1/Items?"))
        assertTrue(itemsRequest.path.orEmpty().contains("ParentId=lib-movie"))
        assertEquals("api-key", itemsRequest.getHeader("X-Emby-Token"))
    }

    @Test
    fun getCatalog_authenticatesWithUsernameAndPasswordWhenApiKeyIsMissing() = runTest {
        server.enqueueJson(
            """
                {
                  "User": {"Id":"u-auth","Name":"demo"},
                  "AccessToken":"token-123"
                }
            """.trimIndent()
        )
        server.enqueueJson(
            """
                {
                  "Items": [
                    {"Id":"lib-tv","Name":"TV","Type":"CollectionFolder","CollectionType":"tvshows"}
                  ],
                  "TotalRecordCount": 1
                }
            """.trimIndent()
        )
        server.enqueueJson("""{"Items":[],"TotalRecordCount":0}""")

        val catalog = repository(apiKey = "").getCatalog()

        assertEquals("lib-tv", catalog.selectedLibraryId)
        val authRequest = server.takeRequest()
        assertEquals("/Users/AuthenticateByName", authRequest.path)
        val authBody = authRequest.body.readUtf8()
        assertTrue(authBody.contains(""""Username":"demo""""))
        assertTrue(authBody.contains(""""Pw":"secret""""))

        val viewsRequest = server.takeRequest()
        assertEquals("token-123", viewsRequest.getHeader("X-Emby-Token"))
    }

    @Test
    fun getCatalog_pagesThroughLibraryItemsUntilTotalCountIsLoaded() = runTest {
        server.enqueueJson("""[{"Id":"u1","Name":"demo"}]""")
        server.enqueueJson(
            """
                {
                  "Items": [
                    {"Id":"lib-movie","Name":"Movies","Type":"CollectionFolder","CollectionType":"movies"}
                  ],
                  "TotalRecordCount": 1
                }
            """.trimIndent()
        )
        server.enqueueJson(
            """
                {
                  "Items": [
                    {"Id":"movie-1","Name":"Arrival","Type":"Movie"}
                  ],
                  "TotalRecordCount": 2
                }
            """.trimIndent()
        )
        server.enqueueJson(
            """
                {
                  "Items": [
                    {"Id":"movie-2","Name":"Dune","Type":"Movie"}
                  ],
                  "TotalRecordCount": 2
                }
            """.trimIndent()
        )

        val catalog = repository(apiKey = "api-key").getCatalog()

        assertEquals(listOf("Arrival", "Dune"), catalog.items.map { it.title })
        assertEquals(listOf(null, null), catalog.items.map { it.imageUrl })

        server.takeRequest()
        server.takeRequest()
        val firstItemsRequest = server.takeRequest()
        val secondItemsRequest = server.takeRequest()
        assertTrue(firstItemsRequest.path.orEmpty().contains("StartIndex=0"))
        assertTrue(firstItemsRequest.path.orEmpty().contains("Limit=100"))
        assertTrue(secondItemsRequest.path.orEmpty().contains("StartIndex=1"))
        assertTrue(secondItemsRequest.path.orEmpty().contains("Limit=100"))
    }

    @Test
    fun getCatalog_throwsTypedExceptionForHttpErrors() = runTest {
        server.enqueueJson("""[{"Id":"u1","Name":"demo"}]""")
        server.enqueue(MockResponse().setResponseCode(500))

        val error = try {
            repository(apiKey = "api-key").getCatalog()
            null
        } catch (error: EmbyApiException) {
            error
        }

        requireNotNull(error)
        assertEquals(EmbyApiException.Kind.HTTP, error.kind)
        assertTrue(error.message.orEmpty().contains("HTTP 500"))
    }

    private fun repository(apiKey: String = "api-key"): EmbyRepository {
        return EmbyRepository(
            VideoServerConfig(
                serverUrl = server.url("/").toString(),
                username = "demo",
                password = "secret",
                apiKey = apiKey
            )
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
