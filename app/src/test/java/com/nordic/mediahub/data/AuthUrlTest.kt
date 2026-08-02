package com.nordic.mediahub.data

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AuthUrlTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        MediaAuthHeaderRegistry.clear()
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        MediaAuthHeaderRegistry.clear()
        server.shutdown()
    }

    @Test
    fun stripAuthQuery_removesEmbyApiKeyAndKeepsImageParams() {
        val stripped = stripAuthQuery(
            "https://emby.example.test/Items/movie-1/Images/Primary?maxWidth=640&quality=90&tag=tag-1&api_key=secret"
        )
        assertEquals(
            "https://emby.example.test/Items/movie-1/Images/Primary?maxWidth=640&quality=90&tag=tag-1",
            stripped
        )
    }

    @Test
    fun stripAuthQuery_removesEmbyStreamApiKeyAndKeepsStaticParam() {
        val stripped = stripAuthQuery(
            "https://emby.example.test/Videos/movie-1/stream?Static=true&api_key=secret"
        )
        assertEquals(
            "https://emby.example.test/Videos/movie-1/stream?Static=true",
            stripped
        )
    }

    @Test
    fun stripAuthQuery_removesAudiobookShelfTokenAndKeepsDownloadParam() {
        val stripped = stripAuthQuery(
            "https://abs.example.test/audio/book-1.mp3?download=0&token=bearer-123"
        )
        assertEquals(
            "https://abs.example.test/audio/book-1.mp3?download=0",
            stripped
        )
    }

    @Test
    fun stripAuthQuery_removesNavidromeSubsonicAuthParams() {
        val stripped = stripAuthQuery(
            "https://nav.example.test/rest/stream?id=song-1&u=demo&t=abc123&s=salt123&v=1.16.1&c=Nordic"
        )
        assertEquals(
            "https://nav.example.test/rest/stream?id=song-1",
            stripped
        )
    }

    @Test
    fun stripAuthQuery_stripsAuthCaseInsensitively() {
        val stripped = stripAuthQuery(
            "https://emby.example.test/Items/item/Images/Primary?API_KEY=secret&tag=t"
        )
        assertEquals(
            "https://emby.example.test/Items/item/Images/Primary?tag=t",
            stripped
        )
    }

    @Test
    fun stripAuthQuery_preservesTokenInPathWhileRemovingTokenQuery() {
        val stripped = stripAuthQuery(
            "https://abs.example.test/audio/token=placeholder/book-1.mp3?download=0&token=bearer-123"
        )
        assertEquals(
            "https://abs.example.test/audio/token=placeholder/book-1.mp3?download=0",
            stripped
        )
    }

    @Test
    fun stripAuthQuery_handlesUrlWithNoQuery() {
        val url = "https://example.test/path"
        assertEquals(url, stripAuthQuery(url))
    }

    @Test
    fun stripAuthQuery_handlesAlreadyCleanUrl() {
        val url = "https://example.test/path?maxWidth=640&download=0"
        assertEquals(url, stripAuthQuery(url))
    }

    @Test
    fun stripAuthQuery_returnsBlankForBlankInput() {
        assertEquals("", stripAuthQuery(""))
        assertEquals("   ", stripAuthQuery("   "))
    }

    @Test
    fun stripAuthQuery_returnsNonUrlInputUnchanged() {
        assertEquals("not a url", stripAuthQuery("not a url"))
    }

    @Test
    fun mediaAuthHeaderRegistry_registerLookupOverwriteAndClear() {
        assertNull(MediaAuthHeaderRegistry.headerFor("emby.example.test:80"))

        MediaAuthHeaderRegistry.register("emby.example.test:80", "X-Emby-Token", "tok")
        val first = MediaAuthHeaderRegistry.headerFor("emby.example.test:80")
        assertNotNull(first)
        assertEquals("X-Emby-Token", first!!.headerName)
        assertEquals("tok", first.headerValue)

        MediaAuthHeaderRegistry.register("emby.example.test:80", "X-Emby-Token", "tok2")
        assertEquals("tok2", MediaAuthHeaderRegistry.headerFor("emby.example.test:80")!!.headerValue)

        MediaAuthHeaderRegistry.clear()
        assertNull(MediaAuthHeaderRegistry.headerFor("emby.example.test:80"))
    }

    @Test
    fun mediaAuthHeaderRegistry_keepsSeparateOriginsIsolated() {
        MediaAuthHeaderRegistry.register("emby.host:8096", "X-Emby-Token", "emby-tok")
        MediaAuthHeaderRegistry.register("abs.host:13378", "Authorization", "Bearer abs-tok")

        assertEquals("emby-tok", MediaAuthHeaderRegistry.headerFor("emby.host:8096")!!.headerValue)
        assertEquals("Bearer abs-tok", MediaAuthHeaderRegistry.headerFor("abs.host:13378")!!.headerValue)
        assertNull(MediaAuthHeaderRegistry.headerFor("other.host:443"))
    }

    @Test
    fun mediaAuthHeaderInterceptor_injectsRegisteredHeaderForMatchingOrigin() {
        val origin = server.url("/").originKey()
        MediaAuthHeaderRegistry.register(origin, "X-Emby-Token", "injected-token")

        server.enqueue(MockResponse().setResponseCode(200))
        val client = OkHttpClient.Builder().addInterceptor(MediaAuthHeaderInterceptor()).build()
        client.newCall(Request.Builder().url(server.url("/")).build()).execute().close()

        assertEquals("injected-token", server.takeRequest().getHeader("X-Emby-Token"))
    }

    @Test
    fun mediaAuthHeaderInterceptor_skipsInjectionWhenNoHeaderRegisteredForOrigin() {
        server.enqueue(MockResponse().setResponseCode(200))
        val client = OkHttpClient.Builder().addInterceptor(MediaAuthHeaderInterceptor()).build()
        client.newCall(Request.Builder().url(server.url("/")).build()).execute().close()

        assertNull(server.takeRequest().getHeader("X-Emby-Token"))
    }

    @Test
    fun mediaAuthHeaderInterceptor_doesNotOverwriteCallerProvidedHeader() {
        val origin = server.url("/").originKey()
        MediaAuthHeaderRegistry.register(origin, "X-Emby-Token", "injected-token")

        server.enqueue(MockResponse().setResponseCode(200))
        val client = OkHttpClient.Builder().addInterceptor(MediaAuthHeaderInterceptor()).build()
        client.newCall(
            Request.Builder().url(server.url("/")).header("X-Emby-Token", "caller-token").build()
        ).execute().close()

        assertEquals("caller-token", server.takeRequest().getHeader("X-Emby-Token"))
    }

    @Test
    fun originKey_usesHostAndPort() {
        val url = okhttp3.HttpUrl.Builder()
            .scheme("http")
            .host("example.test")
            .port(8096)
            .build()
        assertEquals("example.test:8096", url.originKey())
        assertNotEquals(
            url.originKey(),
            okhttp3.HttpUrl.Builder().scheme("http").host("example.test").port(8097).build().originKey()
        )
    }
}
