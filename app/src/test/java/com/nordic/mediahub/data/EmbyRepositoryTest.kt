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

        val resumeRequest = server.takeRequest()
        assertTrue(resumeRequest.path.orEmpty().startsWith("/Users/u1/Items/Resume?"))
        assertTrue(resumeRequest.path.orEmpty().contains("MediaTypes=Video"))
        assertTrue(resumeRequest.path.orEmpty().contains("IncludeItemTypes=Movie%2CEpisode%2CVideo"))
        assertEquals("api-key", resumeRequest.getHeader("X-Emby-Token"))

        val nextUpRequest = server.takeRequest()
        assertTrue(nextUpRequest.path.orEmpty().startsWith("/Shows/NextUp?"))
        assertTrue(nextUpRequest.path.orEmpty().contains("UserId=u1"))
        assertEquals("api-key", nextUpRequest.getHeader("X-Emby-Token"))
    }

    @Test
    fun getCatalog_usesFirstUsableApiKeyUserWhenMatchingUserIdIsMissing() = runTest {
        server.enqueueJson("""[{"Name":"demo"},{"Id":"u-fallback","Name":"fallback"}]""")
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
        server.enqueueJson("""{"Items":[],"TotalRecordCount":0}""")

        val catalog = repository(apiKey = "api-key").getCatalog()

        assertEquals("lib-movie", catalog.selectedLibraryId)
        server.takeRequest()
        assertEquals("/Users/u-fallback/Views", server.takeRequest().path)
    }

    @Test
    fun getCatalog_throwsTypedAuthExceptionWhenApiKeyUsersHaveNoUsableIds() = runTest {
        server.enqueueJson(
            """
                [
                  {"Name":"demo"},
                  {"Id":null,"Name":"null-id"},
                  {"Id":"","Name":"empty-id"},
                  {"Id":"   ","Name":"blank-id"}
                ]
            """.trimIndent()
        )

        assertEmbyApiError(EmbyApiException.Kind.AUTH) {
            repository(apiKey = "api-key").getCatalog()
        }

        assertEquals("/Users", server.takeRequest().path)
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
        server.enqueueJson("""{"Items":[],"TotalRecordCount":0}""")
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
    fun getCatalog_throwsTypedAuthExceptionForInvalidPasswordUserIds() = runTest {
        listOf(
            """
                {
                  "AccessToken": "token-123"
                }
            """.trimIndent(),
            """
                {
                  "User": null,
                  "AccessToken": "token-123"
                }
            """.trimIndent(),
            """
                {
                  "User": {"Name":"demo"},
                  "AccessToken": "token-123"
                }
            """.trimIndent(),
            """
                {
                  "User": {"Id":null,"Name":"demo"},
                  "AccessToken": "token-123"
                }
            """.trimIndent(),
            """
                {
                  "User": {"Id":"   ","Name":"demo"},
                  "AccessToken": "token-123"
                }
            """.trimIndent()
        ).forEach { response ->
            server.enqueueJson(response)

            assertEmbyApiError(EmbyApiException.Kind.AUTH) {
                repository(apiKey = "").getCatalog()
            }
        }

        repeat(5) {
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
    fun getCatalog_mapsMissingAndNullViewItemsToEmptyLibraries() = runTest {
        listOf(
            """{"TotalRecordCount":0}""",
            """{"Items":null,"TotalRecordCount":0}"""
        ).forEach { viewsResponse ->
            server.enqueueJson("""[{"Id":"u1","Name":"demo"}]""")
            server.enqueueJson(viewsResponse)

            val catalog = repository(apiKey = "api-key").getCatalog()

            assertEquals(emptyList<VideoLibrary>(), catalog.libraries)
            assertNull(catalog.selectedLibraryId)
            assertEquals(emptyList<VideoItem>(), catalog.items)
        }
    }

    @Test
    fun getCatalog_skipsLibraryRowsWithMissingNullOrBlankIdentity() = runTest {
        server.enqueueJson("""[{"Id":"u1","Name":"demo"}]""")
        server.enqueueJson(
            """
                {
                  "Items": [
                    {"Name":"Missing Id","Type":"CollectionFolder","CollectionType":"movies"},
                    {"Id":null,"Name":"Null Id","Type":"CollectionFolder","CollectionType":"movies"},
                    {"Id":"","Name":"Empty Id","Type":"CollectionFolder","CollectionType":"movies"},
                    {"Id":"   ","Name":"Blank Id","Type":"CollectionFolder","CollectionType":"movies"},
                    {"Id":"lib-missing-name","Type":"CollectionFolder","CollectionType":"movies"},
                    {"Id":"lib-null-name","Name":null,"Type":"CollectionFolder","CollectionType":"movies"},
                    {"Id":"lib-empty-name","Name":"","Type":"CollectionFolder","CollectionType":"movies"},
                    {"Id":"lib-blank-name","Name":"   ","Type":"CollectionFolder","CollectionType":"movies"},
                    {"Id":" lib-valid ","Name":" Valid Movies ","Type":"CollectionFolder","CollectionType":"Movies","ChildCount":4}
                  ],
                  "TotalRecordCount": 9
                }
            """.trimIndent()
        )
        server.enqueueJson("""{"Items":[],"TotalRecordCount":0}""")

        val catalog = repository(apiKey = "api-key").getCatalog()

        assertEquals(listOf("lib-valid"), catalog.libraries.map { it.id })
        assertEquals(listOf("Valid Movies"), catalog.libraries.map { it.name })
        assertEquals("lib-valid", catalog.selectedLibraryId)
        assertEquals(4, catalog.libraries.single().itemCount)

        server.takeRequest()
        server.takeRequest()
        val itemsRequest = server.takeRequest()
        assertTrue(itemsRequest.path.orEmpty().contains("ParentId=lib-valid"))
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
    fun getCatalog_mapsMissingAndNullLibraryItemsToEmptyItems() = runTest {
        listOf(
            """{"TotalRecordCount":0}""",
            """{"Items":null,"TotalRecordCount":0}"""
        ).forEach { itemsResponse ->
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
            server.enqueueJson(itemsResponse)

            val catalog = repository(apiKey = "api-key").getCatalog()

            assertEquals("lib-movie", catalog.selectedLibraryId)
            assertEquals(emptyList<VideoItem>(), catalog.items)
        }
    }

    @Test
    fun getCatalog_skipsItemRowsWithMissingNullOrBlankIdentity() = runTest {
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
                    {"Name":"Missing Id","Type":"Movie"},
                    {"Id":null,"Name":"Null Id","Type":"Movie"},
                    {"Id":"","Name":"Empty Id","Type":"Movie"},
                    {"Id":"   ","Name":"Blank Id","Type":"Movie"},
                    {"Id":"missing-name","Type":"Movie"},
                    {"Id":"null-name","Name":null,"Type":"Movie"},
                    {"Id":"empty-name","Name":"","Type":"Movie"},
                    {"Id":"blank-name","Name":"   ","Type":"Movie"},
                    {
                      "Id":" movie-1 ",
                      "Name":" Arrival ",
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
                  "TotalRecordCount": 9
                }
            """.trimIndent()
        )

        val catalog = repository(apiKey = "api-key").getCatalog()

        assertEquals(1, catalog.items.size)
        val item = catalog.items.single()
        assertEquals("movie-1", item.id)
        assertEquals("Arrival", item.title)
        assertEquals("Movie", item.type)
        assertEquals("First contact story.", item.overview)
        assertEquals(2016, item.year)
        assertEquals(6960, item.durationSeconds)
        assertEquals(120, item.playbackPositionSeconds)
        assertFalse(item.isPlayed)
        assertEquals(8.6f, item.communityRating ?: 0f, 0.001f)
        assertTrue(item.imageUrl.orEmpty().contains("/Items/movie-1/Images/Primary"))
        assertTrue(item.streamUrl.orEmpty().contains("/Videos/movie-1/stream"))
    }

    @Test
    fun getCatalog_stopsWhenFetchedItemRowsReachTotalEvenIfRowsAreSkipped() = runTest {
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
        val validRows = (1..99).joinToString(",") { index ->
            validEmbyMovieJson(index)
        }
        server.enqueueJson(
            """
                {
                  "Items": [
                    $validRows,
                    {"Name":"Missing Id","Type":"Movie"}
                  ],
                  "TotalRecordCount": 100
                }
            """.trimIndent()
        )

        val catalog = repository(apiKey = "api-key").getCatalog()

        assertEquals(99, catalog.items.size)
        assertEquals("Movie 1", catalog.items.first().title)
        assertEquals("Movie 99", catalog.items.last().title)

        server.takeRequest()
        server.takeRequest()
        val itemsRequest = server.takeRequest()
        assertTrue(itemsRequest.path.orEmpty().contains("StartIndex=0"))
        assertEquals(3, server.requestCount)
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

    @Test
    fun getPlaybackInfo_mapsDirectStreamUrlFromPlaybackInfo() = runTest {
        server.enqueueJson("""[{"Id":"u1","Name":"demo"}]""")
        server.enqueueJson(
            """
                {
                  "PlaySessionId": "play-session-1",
                  "MediaSources": [
                    {
                      "Id": "source-1",
                      "Name": "1080p",
                      "Container": "mp4",
                      "RunTimeTicks": 72000000000,
                      "SupportsDirectStream": true,
                      "MediaStreams": [
                        {
                          "Index": 1,
                          "Type": "Audio",
                          "Codec": "aac",
                          "Language": "eng",
                          "DisplayTitle": "English AAC",
                          "IsDefault": true
                        },
                        {
                          "Index": 2,
                          "Type": "Subtitle",
                          "Codec": "srt",
                          "Language": "zho",
                          "DisplayTitle": "Chinese SRT",
                          "IsExternal": true,
                          "DeliveryUrl": "/Videos/movie-1/source-1/Subtitles/2/Stream.srt"
                        }
                      ]
                    }
                  ]
                }
            """.trimIndent()
        )

        val playbackInfo = repository(apiKey = "api-key").getPlaybackInfo(sampleVideoItem())

        assertEquals("movie-1", playbackInfo.itemId)
        assertEquals("Arrival", playbackInfo.title)
        assertEquals("source-1", playbackInfo.mediaSourceId)
        assertEquals("play-session-1", playbackInfo.playSessionId)
        assertEquals(7200, playbackInfo.durationSeconds)
        assertEquals(0, playbackInfo.resumePositionSeconds)
        assertTrue(playbackInfo.streamUrl.contains("/Videos/movie-1/stream"))
        assertTrue(playbackInfo.streamUrl.contains("static=true"))
        assertTrue(playbackInfo.streamUrl.contains("MediaSourceId=source-1"))
        assertTrue(playbackInfo.streamUrl.contains("PlaySessionId=play-session-1"))
        assertTrue(playbackInfo.streamUrl.contains("api_key=api-key"))
        assertEquals(1, playbackInfo.audioTracks.size)
        assertEquals(1, playbackInfo.audioTracks.single().index)
        assertEquals("English AAC", playbackInfo.audioTracks.single().label)
        assertEquals("eng", playbackInfo.audioTracks.single().language)
        assertTrue(playbackInfo.audioTracks.single().isDefault)
        assertEquals(1, playbackInfo.subtitleTracks.size)
        assertEquals(2, playbackInfo.subtitleTracks.single().index)
        assertEquals("Chinese SRT", playbackInfo.subtitleTracks.single().label)
        assertTrue(playbackInfo.subtitleTracks.single().isExternal)
        assertTrue(playbackInfo.subtitleTracks.single().deliveryUrl.orEmpty().contains("api_key=api-key"))

        server.takeRequest()
        val playbackRequest = server.takeRequest()
        assertTrue(playbackRequest.path.orEmpty().startsWith("/Items/movie-1/PlaybackInfo?"))
        assertTrue(playbackRequest.path.orEmpty().contains("UserId=u1"))
        assertEquals("api-key", playbackRequest.getHeader("X-Emby-Token"))
    }

    @Test
    fun getPlaybackInfo_carriesResumePositionFromVideoItem() = runTest {
        server.enqueueJson("""[{"Id":"u1","Name":"demo"}]""")
        server.enqueueJson(
            """
                {
                  "PlaySessionId": "play-session-1",
                  "MediaSources": [
                    {
                      "Id": "source-1",
                      "RunTimeTicks": 72000000000,
                      "SupportsDirectStream": true
                    }
                  ]
                }
            """.trimIndent()
        )
        val item = sampleVideoItem().copy(
            progress = VideoProgress(
                currentTimeSeconds = 1800,
                playedPercentage = 25f,
                isPlayed = false
            )
        )

        val playbackInfo = repository(apiKey = "api-key").getPlaybackInfo(item)

        assertEquals(1800, playbackInfo.resumePositionSeconds)
    }

    @Test
    fun getPlaybackInfo_throwsTypedApiErrorWhenNoDirectSourceExists() = runTest {
        server.enqueueJson("""[{"Id":"u1","Name":"demo"}]""")
        server.enqueueJson(
            """
                {
                  "PlaySessionId": "play-session-1",
                  "MediaSources": [
                    {
                      "Id": "source-1",
                      "SupportsDirectStream": false
                    }
                  ]
                }
            """.trimIndent()
        )

        val error = try {
            repository(apiKey = "api-key").getPlaybackInfo(sampleVideoItem())
            null
        } catch (error: EmbyApiException) {
            error
        }

        requireNotNull(error)
        assertEquals(EmbyApiException.Kind.API, error.kind)
    }

    @Test
    fun getPlaybackInfo_throwsTypedHttpErrorForNon2xx() = runTest {
        server.enqueueJson("""[{"Id":"u1","Name":"demo"}]""")
        server.enqueue(MockResponse().setResponseCode(500))

        val error = try {
            repository(apiKey = "api-key").getPlaybackInfo(sampleVideoItem())
            null
        } catch (error: EmbyApiException) {
            error
        }

        requireNotNull(error)
        assertEquals(EmbyApiException.Kind.HTTP, error.kind)
        assertTrue(error.message.orEmpty().contains("HTTP 500"))
    }

    @Test
    fun getPlaybackInfo_throwsTypedApiErrorWhenPlaySessionIdIsMissing() = runTest {
        server.enqueueJson("""[{"Id":"u1","Name":"demo"}]""")
        server.enqueueJson(
            """
                {
                  "PlaySessionId": "",
                  "MediaSources": [
                    {
                      "Id": "source-1",
                      "SupportsDirectStream": true
                    }
                  ]
                }
            """.trimIndent()
        )

        val error = try {
            repository(apiKey = "api-key").getPlaybackInfo(sampleVideoItem())
            null
        } catch (error: EmbyApiException) {
            error
        }

        requireNotNull(error)
        assertEquals(EmbyApiException.Kind.API, error.kind)
        assertTrue(error.message.orEmpty().contains("播放会话"))
    }

    @Test
    fun getSeasons_mapsSeasonsFromEmbyItems() = runTest {
        server.enqueueJson("""[{"Id":"u1","Name":"demo"}]""")
        server.enqueueJson(
            """
                {
                  "Items": [
                    {"Id":"season-1","Name":"Season 1","Type":"Season","IndexNumber":1,"ChildCount":8,"ParentIndexNumber":1,"ImageTags":{"Primary":"s1-tag"}},
                    {"Id":"season-2","Name":"Season 2","Type":"Season","IndexNumber":2,"ChildCount":10,"ParentIndexNumber":2,"ImageTags":{"Primary":"s2-tag"}}
                  ],
                  "TotalRecordCount": 2
                }
            """.trimIndent()
        )

        val seasons = repository(apiKey = "api-key").getSeasons("series-1")

        assertEquals(2, seasons.size)
        assertEquals("Season 1", seasons[0].name)
        assertEquals(1, seasons[0].indexNumber)
        assertEquals(8, seasons[0].episodeCount)
        assertTrue(seasons[0].imageUrl.orEmpty().contains("/Items/season-1/Images/Primary"))
        assertTrue(seasons[0].imageUrl.orEmpty().contains("tag=s1-tag"))

        server.takeRequest() // auth
        val seasonsRequest = server.takeRequest()
        assertTrue(seasonsRequest.path.orEmpty().contains("/Users/u1/Items"))
        assertTrue(seasonsRequest.path.orEmpty().contains("ParentId=series-1"))
        assertTrue(seasonsRequest.path.orEmpty().contains("Recursive=true"))
        assertTrue(seasonsRequest.path.orEmpty().contains("IncludeItemTypes=Season"))
    }

    @Test
    fun getEpisodes_mapsEpisodesFromEmbyItems() = runTest {
        server.enqueueJson("""[{"Id":"u1","Name":"demo"}]""")
        server.enqueueJson(
            """
                {
                  "Items": [
                    {
                      "Id":"ep-1",
                      "Name":"Pilot",
                      "Type":"Episode",
                      "IndexNumber":1,
                      "ParentIndexNumber":1,
                      "Overview":"First episode",
                      "RunTimeTicks":25800000000,
                      "ImageTags":{"Primary":"ep1-tag"},
                      "UserData":{
                        "PlaybackPositionTicks":6000000000,
                        "PlayedPercentage":23.5,
                        "Played":false,
                        "LastPlayedDate":"2026-06-27T11:00:00Z"
                      }
                    },
                    {
                      "Id":"ep-2",
                      "Name":"Fallout",
                      "Type":"Episode",
                      "IndexNumber":2,
                      "ParentIndexNumber":1,
                      "Overview":"Second episode",
                      "RunTimeTicks":26400000000,
                      "UserData":{
                        "PlaybackPositionTicks":26400000000,
                        "PlayedPercentage":100.0,
                        "Played":true
                      }
                    }
                  ],
                  "TotalRecordCount": 2
                }
            """.trimIndent()
        )

        val episodes = repository(apiKey = "api-key").getEpisodes("season-1")

        assertEquals(2, episodes.size)
        // ep-1: has progress, not played, has image
        assertEquals("Pilot", episodes[0].name)
        assertEquals(1, episodes[0].seasonNumber)
        assertEquals(1, episodes[0].episodeNumber)
        assertEquals("First episode", episodes[0].overview)
        assertEquals(2580, episodes[0].durationSeconds)
        assertTrue(episodes[0].imageUrl.orEmpty().contains("/Items/ep-1/Images/Primary"))
        assertEquals(600, episodes[0].progress?.currentTimeSeconds)
        assertEquals(23.5f, episodes[0].progress?.playedPercentage ?: 0f, 0.001f)
        assertEquals(false, episodes[0].progress?.isPlayed)
        assertEquals("2026-06-27T11:00:00Z", episodes[0].progress?.lastPlayedDate)
        // ep-2: fully played, no image, no lastPlayedDate
        assertEquals("Fallout", episodes[1].name)
        assertEquals(2, episodes[1].episodeNumber)
        assertNull(episodes[1].imageUrl)
        assertEquals(2640, episodes[1].progress?.currentTimeSeconds)
        assertEquals(100f, episodes[1].progress?.playedPercentage ?: 0f, 0.001f)
        assertEquals(true, episodes[1].progress?.isPlayed)
        assertNull(episodes[1].progress?.lastPlayedDate)

        server.takeRequest() // auth
        val episodesRequest = server.takeRequest()
        assertTrue(episodesRequest.path.orEmpty().contains("/Users/u1/Items"))
        assertTrue(episodesRequest.path.orEmpty().contains("ParentId=season-1"))
        assertTrue(episodesRequest.path.orEmpty().contains("Recursive=true"))
        assertTrue(episodesRequest.path.orEmpty().contains("IncludeItemTypes=Episode"))
        assertTrue(episodesRequest.path.orEmpty().contains("Fields=Overview%2CProductionYear%2CRunTimeTicks%2CChildCount%2CImageTags%2CUserData"))
    }

    @Test
    fun reportPlaybackStart_sendsRequestWithTokenAndPosition() = runTest {
        server.enqueueJson("""[{"Id":"u1","Name":"demo"}]""")
        server.enqueueJson("""""")

        repository(apiKey = "api-key").reportPlaybackStart(
            itemId = "movie-1",
            mediaSourceId = "source-1",
            playSessionId = "session-1",
            positionSeconds = 120
        )

        server.takeRequest() // auth
        val reportRequest = server.takeRequest()
        assertEquals("/Sessions/Playing", reportRequest.path)
        assertEquals("api-key", reportRequest.getHeader("X-Emby-Token"))
        val body = reportRequest.body.readUtf8()
        assertTrue(body.contains(""""ItemId":"movie-1""""))
        assertTrue(body.contains(""""SessionId":"session-1""""))
        assertTrue(body.contains(""""MediaSourceId":"source-1""""))
        assertTrue(body.contains(""""PositionTicks":1200000000"""))
    }

    @Test
    fun reportPlaybackProgress_sendsRequestWithPositionTicks() = runTest {
        server.enqueueJson("""[{"Id":"u1","Name":"demo"}]""")
        server.enqueueJson("""""")

        repository(apiKey = "api-key").reportPlaybackProgress(
            itemId = "movie-1",
            mediaSourceId = "source-1",
            playSessionId = "session-1",
            positionSeconds = 300
        )

        server.takeRequest() // auth
        val reportRequest = server.takeRequest()
        assertEquals("/Sessions/Playing/Progress", reportRequest.path)
        assertEquals("api-key", reportRequest.getHeader("X-Emby-Token"))
        val body = reportRequest.body.readUtf8()
        assertTrue(body.contains(""""PositionTicks":3000000000"""))
    }

    @Test
    fun reportPlaybackStopped_sendsRequestWithPositionTicks() = runTest {
        server.enqueueJson("""[{"Id":"u1","Name":"demo"}]""")
        server.enqueueJson("""""")

        repository(apiKey = "api-key").reportPlaybackStopped(
            itemId = "movie-1",
            mediaSourceId = "source-1",
            playSessionId = "session-1",
            positionSeconds = 500
        )

        server.takeRequest() // auth
        val reportRequest = server.takeRequest()
        assertEquals("/Sessions/Playing/Stopped", reportRequest.path)
        assertEquals("api-key", reportRequest.getHeader("X-Emby-Token"))
        val body = reportRequest.body.readUtf8()
        assertTrue(body.contains(""""PositionTicks":5000000000"""))
    }

    @Test
    fun reportPlaybackStart_throwsTypedHttpErrorForNon2xx() = runTest {
        server.enqueueJson("""[{"Id":"u1","Name":"demo"}]""")
        server.enqueue(MockResponse().setResponseCode(500))

        val error = try {
            repository(apiKey = "api-key").reportPlaybackStart(
                itemId = "movie-1", mediaSourceId = "source-1", playSessionId = "session-1"
            )
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

    private fun validEmbyMovieJson(index: Int): String {
        return """
            {
              "Id":"movie-$index",
              "Name":"Movie $index",
              "Type":"Movie",
              "RunTimeTicks":600000000,
              "ImageTags":{"Primary":"tag-$index"}
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
