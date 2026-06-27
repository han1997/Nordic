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
                      "ImageTags":{"Primary":"tag-1"}
                    }
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
                      "Id":"resume-1",
                      "Name":"Half Watched",
                      "ParentId":"lib-movie",
                      "Type":"Movie",
                      "RunTimeTicks":60000000000,
                      "ImageTags":{"Primary":"resume-tag"},
                      "UserData":{
                        "PlaybackPositionTicks":24000000000,
                        "PlayedPercentage":40.0,
                        "Played":false,
                        "LastPlayedDate":"2026-06-27T10:00:00Z"
                      }
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
        assertEquals(1, catalog.resumeItems.size)
        val resumeItem = catalog.resumeItems.single()
        assertEquals("resume-1", resumeItem.id)
        assertEquals(2400, resumeItem.progress?.currentTimeSeconds)
        assertEquals(40f, resumeItem.progress?.playedPercentage ?: 0f, 0.001f)

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

        val resumeRequest = server.takeRequest()
        assertTrue(resumeRequest.path.orEmpty().startsWith("/Users/u1/Items/Resume?"))
        assertTrue(resumeRequest.path.orEmpty().contains("MediaTypes=Video"))
        assertTrue(resumeRequest.path.orEmpty().contains("IncludeItemTypes=Movie%2CEpisode%2CVideo"))
        assertEquals("api-key", resumeRequest.getHeader("X-Emby-Token"))
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
    fun getResumeItems_mapsOnlyUnfinishedResumableItems() = runTest {
        server.enqueueJson("""[{"Id":"u1","Name":"demo"}]""")
        server.enqueueJson(
            """
                {
                  "Items": [
                    {
                      "Id":"movie-resume",
                      "Name":"Resume Movie",
                      "ParentId":"lib-movie",
                      "Type":"Movie",
                      "Overview":"Continue this.",
                      "ProductionYear":2024,
                      "RunTimeTicks":72000000000,
                      "ImageTags":{"Primary":"resume-tag"},
                      "UserData":{
                        "PlaybackPositionTicks":18000000000,
                        "PlayedPercentage":25.0,
                        "Played":false,
                        "LastPlayedDate":"2026-06-27T10:00:00Z"
                      }
                    },
                    {
                      "Id":"movie-played",
                      "Name":"Already Played",
                      "Type":"Movie",
                      "RunTimeTicks":72000000000,
                      "UserData":{
                        "PlaybackPositionTicks":72000000000,
                        "PlayedPercentage":100.0,
                        "Played":true
                      }
                    },
                    {
                      "Id":"movie-zero",
                      "Name":"No Progress",
                      "Type":"Movie",
                      "RunTimeTicks":72000000000,
                      "UserData":{
                        "PlaybackPositionTicks":0,
                        "PlayedPercentage":0.0,
                        "Played":false
                      }
                    }
                  ],
                  "TotalRecordCount": 3
                }
            """.trimIndent()
        )

        val resumeItems = repository(apiKey = "api-key").getResumeItems()

        assertEquals(1, resumeItems.size)
        val item = resumeItems.single()
        assertEquals("movie-resume", item.id)
        assertEquals("lib-movie", item.libraryId)
        assertEquals("Resume Movie", item.title)
        assertEquals(7200, item.durationSeconds)
        assertEquals(1800, item.progress?.currentTimeSeconds)
        assertEquals(25f, item.progress?.playedPercentage ?: 0f, 0.001f)
        assertEquals(false, item.progress?.isPlayed)
        assertEquals("2026-06-27T10:00:00Z", item.progress?.lastPlayedDate)
        assertTrue(item.imageUrl.orEmpty().contains("/Items/movie-resume/Images/Primary"))

        server.takeRequest()
        val resumeRequest = server.takeRequest()
        assertTrue(resumeRequest.path.orEmpty().startsWith("/Users/u1/Items/Resume?"))
        assertTrue(resumeRequest.path.orEmpty().contains("MediaTypes=Video"))
        assertTrue(resumeRequest.path.orEmpty().contains("IncludeItemTypes=Movie%2CEpisode%2CVideo"))
        assertEquals("api-key", resumeRequest.getHeader("X-Emby-Token"))
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
                    {"Id":"ep-1","Name":"Pilot","Type":"Episode","IndexNumber":1,"ParentIndexNumber":1,"Overview":"First episode","RunTimeTicks":25800000000,"ImageTags":{"Primary":"ep1-tag"}},
                    {"Id":"ep-2","Name":"Fallout","Type":"Episode","IndexNumber":2,"ParentIndexNumber":1,"Overview":"Second episode","RunTimeTicks":26400000000}
                  ],
                  "TotalRecordCount": 2
                }
            """.trimIndent()
        )

        val episodes = repository(apiKey = "api-key").getEpisodes("season-1")

        assertEquals(2, episodes.size)
        assertEquals("Pilot", episodes[0].name)
        assertEquals(1, episodes[0].seasonNumber)
        assertEquals(1, episodes[0].episodeNumber)
        assertEquals("First episode", episodes[0].overview)
        assertEquals(2580, episodes[0].durationSeconds)
        assertTrue(episodes[0].imageUrl.orEmpty().contains("/Items/ep-1/Images/Primary"))
        assertNull(episodes[1].imageUrl) // no ImageTags.Primary

        server.takeRequest() // auth
        val episodesRequest = server.takeRequest()
        assertTrue(episodesRequest.path.orEmpty().contains("/Users/u1/Items"))
        assertTrue(episodesRequest.path.orEmpty().contains("ParentId=season-1"))
        assertTrue(episodesRequest.path.orEmpty().contains("Recursive=true"))
        assertTrue(episodesRequest.path.orEmpty().contains("IncludeItemTypes=Episode"))
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

    private fun sampleVideoItem(): VideoItem {
        return VideoItem(
            id = "movie-1",
            libraryId = "lib-movie",
            title = "Arrival",
            type = "Movie",
            overview = "First contact story.",
            year = 2016,
            durationSeconds = 6960,
            imageUrl = server.url("/Items/movie-1/Images/Primary").toString()
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
