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
                      "CommunityRating":8.6,
                      "UserData":{
                        "Played":false,
                        "PlaybackPositionTicks":1200000000,
                        "LastPlayedDate":"2026-06-28T08:15:30.0000000Z"
                      },
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
        assertEquals(120, item.playbackPositionSeconds)
        assertEquals("2026-06-28T08:15:30.0000000Z", item.lastPlayedDate)
        assertFalse(item.isPlayed)
        assertEquals(8.6f, item.communityRating ?: 0f, 0.001f)
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
        assertTrue(itemsRequest.path.orEmpty().contains("UserData"))
        assertTrue(itemsRequest.path.orEmpty().contains("CommunityRating"))
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
    fun getCatalog_throwsTypedAuthExceptionForInvalidPasswordAccessTokens() = runTest {
        listOf(
            """
                {
                  "User": {"Id":"u-auth","Name":"demo"}
                }
            """.trimIndent(),
            """
                {
                  "User": {"Id":"u-auth","Name":"demo"},
                  "AccessToken": null
                }
            """.trimIndent(),
            """
                {
                  "User": {"Id":"u-auth","Name":"demo"},
                  "AccessToken": "   "
                }
            """.trimIndent()
        ).forEach { response ->
            server.enqueueJson(response)

            assertEmbyApiError(EmbyApiException.Kind.AUTH) {
                repository(apiKey = "").getCatalog()
            }
        }

        repeat(3) {
            assertEquals("/Users/AuthenticateByName", server.takeRequest().path)
        }
    }

    @Test
    fun getCatalog_filtersVideoLibrariesCaseInsensitively() = runTest {
        server.enqueueJson("""[{"Id":"u1","Name":"demo"}]""")
        server.enqueueJson(
            """
                {
                  "Items": [
                    {"Id":"lib-movie","Name":"Movies","Type":"CollectionFolder","CollectionType":"Movies"},
                    {"Id":"lib-fallback","Name":"Videos","Type":"collectionfolder","CollectionType":""},
                    {"Id":"lib-music","Name":"Music","Type":"CollectionFolder","CollectionType":"Music"}
                  ],
                  "TotalRecordCount": 3
                }
            """.trimIndent()
        )
        server.enqueueJson("""{"Items":[],"TotalRecordCount":0}""")

        val catalog = repository(apiKey = "api-key").getCatalog()

        assertEquals(listOf("lib-movie", "lib-fallback"), catalog.libraries.map { it.id })
        assertEquals("lib-movie", catalog.selectedLibraryId)
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
    fun getCatalog_mapsSeriesEpisodeMetadataAndOnlyEpisodesArePlayable() = runTest {
        server.enqueueJson("""[{"Id":"u1","Name":"demo"}]""")
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
        server.enqueueJson(
            """
                {
                  "Items": [
                    {
                      "Id":"series-1",
                      "Name":"Together",
                      "Type":"Series",
                      "Overview":"A campus story.",
                      "ImageTags":{"Primary":"series-tag"}
                    },
                    {
                      "Id":"episode-1",
                      "Name":"Episode 2",
                      "Type":"Episode",
                      "SeriesId":"series-1",
                      "SeriesName":"Together",
                      "ParentIndexNumber":1,
                      "IndexNumber":2,
                      "RunTimeTicks":18000000000,
                      "ImageTags":{"Primary":"episode-tag"}
                    }
                  ],
                  "TotalRecordCount": 2
                }
            """.trimIndent()
        )

        val catalog = repository(apiKey = "api-key").getCatalog()

        val series = catalog.items.first { it.id == "series-1" }
        assertNull(series.streamUrl)

        val episode = catalog.items.first { it.id == "episode-1" }
        assertEquals("series-1", episode.seriesId)
        assertEquals("Together", episode.seriesName)
        assertEquals(1, episode.seasonNumber)
        assertEquals(2, episode.episodeNumber)
        assertEquals(1800, episode.durationSeconds)
        assertTrue(episode.streamUrl.orEmpty().contains("/Videos/episode-1/stream"))

        server.takeRequest()
        server.takeRequest()
        val itemsRequest = server.takeRequest()
        assertTrue(itemsRequest.path.orEmpty().contains("SeriesId"))
        assertTrue(itemsRequest.path.orEmpty().contains("ParentIndexNumber"))
        assertTrue(itemsRequest.path.orEmpty().contains("IndexNumber"))
    }

    @Test
    fun getCatalog_throwsTypedExceptionForHttpErrors() = runTest {
        server.enqueueJson("""[{"Id":"u1","Name":"demo"}]""")
        server.enqueue(MockResponse().setResponseCode(500))

        val error = assertEmbyApiError(EmbyApiException.Kind.HTTP) {
            repository(apiKey = "api-key").getCatalog()
        }

        assertTrue(error.message.orEmpty().contains("HTTP 500"))
    }

    @Test
    fun getCatalog_throwsTypedExceptionForEmptyApiKeyUserResponse() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))

        assertEmbyApiError(EmbyApiException.Kind.API) {
            repository(apiKey = "api-key").getCatalog()
        }
    }

    @Test
    fun getCatalog_throwsTypedExceptionForEmptyPasswordAuthenticationResponse() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))

        assertEmbyApiError(EmbyApiException.Kind.API) {
            repository(apiKey = "").getCatalog()
        }
    }

    @Test
    fun getCatalog_throwsTypedExceptionForEmptyLibraryResponse() = runTest {
        server.enqueueJson("""[{"Id":"u1","Name":"demo"}]""")
        server.enqueue(MockResponse().setResponseCode(200))

        assertEmbyApiError(EmbyApiException.Kind.API) {
            repository(apiKey = "api-key").getCatalog()
        }
    }

    @Test
    fun getCatalog_throwsTypedExceptionForEmptyItemListResponse() = runTest {
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
        server.enqueue(MockResponse().setResponseCode(200))

        assertEmbyApiError(EmbyApiException.Kind.API) {
            repository(apiKey = "api-key").getCatalog()
        }
    }

    @Test
    fun syncPlaybackProgress_sendsProgressRequestWithPositionTicksAndPausedState() = runTest {
        server.enqueueJson("""[{"Id":"u1","Name":"demo"}]""")
        server.enqueue(MockResponse().setResponseCode(204))

        repository(apiKey = "api-key").syncPlaybackProgress(
            video = video(id = "movie-1", durationSeconds = 120),
            positionSeconds = 45,
            isPaused = false
        )

        server.takeRequest()
        val progressRequest = server.takeRequest()
        val progressBody = progressRequest.body.readUtf8()
        assertEquals("/Sessions/Playing/Progress", progressRequest.path)
        assertEquals("api-key", progressRequest.getHeader("X-Emby-Token"))
        assertTrue(progressBody.contains(""""ItemId":"movie-1""""))
        assertTrue(progressBody.contains(""""PositionTicks":450000000"""))
        assertTrue(progressBody.contains(""""IsPaused":false"""))
    }

    @Test
    fun stopPlaybackProgress_sendsStoppedRequestWithClampedPositionTicks() = runTest {
        server.enqueueJson("""[{"Id":"u1","Name":"demo"}]""")
        server.enqueue(MockResponse().setResponseCode(204))

        repository(apiKey = "api-key").stopPlaybackProgress(
            video = video(id = "movie-1", durationSeconds = 120),
            positionSeconds = 150
        )

        server.takeRequest()
        val stoppedRequest = server.takeRequest()
        val stoppedBody = stoppedRequest.body.readUtf8()
        assertEquals("/Sessions/Playing/Stopped", stoppedRequest.path)
        assertEquals("api-key", stoppedRequest.getHeader("X-Emby-Token"))
        assertTrue(stoppedBody.contains(""""ItemId":"movie-1""""))
        assertTrue(stoppedBody.contains(""""PositionTicks":1200000000"""))
        assertTrue(stoppedBody.contains(""""IsPaused":true"""))
    }

    @Test
    fun resolveEmbyPlaybackPositionTicks_clampsKnownDurationAndKeepsUnknownDurationPositions() {
        assertEquals(
            0L,
            resolveEmbyPlaybackPositionTicks(positionSeconds = -10, durationSeconds = 120)
        )
        assertEquals(
            1_200_000_000L,
            resolveEmbyPlaybackPositionTicks(positionSeconds = 150, durationSeconds = 120)
        )
        assertEquals(
            3_000_000_000L,
            resolveEmbyPlaybackPositionTicks(positionSeconds = 300, durationSeconds = 0)
        )
    }

    private suspend fun assertEmbyApiError(
        kind: EmbyApiException.Kind,
        block: suspend () -> Unit
    ): EmbyApiException {
        val error = try {
            block()
            null
        } catch (error: EmbyApiException) {
            error
        }

        requireNotNull(error)
        assertEquals(kind, error.kind)
        return error
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

    private fun video(id: String, durationSeconds: Int): VideoItem {
        return VideoItem(
            id = id,
            libraryId = "library-1",
            title = "Video",
            type = "Movie",
            durationSeconds = durationSeconds,
            streamUrl = "https://example.test/video.mp4"
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
