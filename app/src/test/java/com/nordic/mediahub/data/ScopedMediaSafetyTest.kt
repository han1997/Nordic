package com.nordic.mediahub.data

import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class ScopedMediaSafetyTest {
    @Test fun sameHostAccountsCannotOverwriteEachOthersAuthentication() {
        val server = MockWebServer(); server.start()
        try {
            repeat(2) { server.enqueue(MockResponse().setBody("ok")) }
            ScopedMediaRegistry.registerHeader("account-a", server.url("/media/").toString(), "X-Emby-Token", "token-a")
            ScopedMediaRegistry.registerHeader("account-b", server.url("/media/").toString(), "X-Emby-Token", "token-b")
            val client = OkHttpClient.Builder().addInterceptor(MediaAuthHeaderInterceptor()).addNetworkInterceptor(ScopedMediaNetworkInterceptor()).build()
            listOf("account-a", "account-b").forEach { source ->
                client.newCall(Request.Builder().url(server.url("/media/movie").toString().forMediaSource(source)).build()).execute().close()
            }
            assertEquals("token-a", server.takeRequest().getHeader("X-Emby-Token"))
            assertEquals("token-b", server.takeRequest().getHeader("X-Emby-Token"))
        } finally { server.shutdown(); ScopedMediaRegistry.remove("account-a"); ScopedMediaRegistry.remove("account-b") }
    }
    @Test fun incorrectRangeResponseDoesNotDownloadAndDiscardBytesToSeek() {
        val server = MockWebServer(); server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(200).setBody("whole-file"))
            ScopedMediaRegistry.registerWebDav(VideoServerConfig(serverUrl = server.url("/dav/").toString(), sourceId = "range-source"))
            val client = OkHttpClient.Builder().addInterceptor(MediaAuthHeaderInterceptor()).addNetworkInterceptor(ScopedMediaNetworkInterceptor()).build()
            assertThrows(WebDavRangeException::class.java) {
                client.newCall(Request.Builder().url(server.url("/dav/a.mp4").toString().forMediaSource("range-source"))
                    .header("Range", "bytes=500-").build()).execute().close()
            }
            assertEquals(1, server.requestCount)
        } finally { server.shutdown(); ScopedMediaRegistry.remove("range-source") }
    }
    @Test fun cancellingDirectoryReadCancelsTheBodyNotJustTheHeaders() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            // Delay only the response body: throttleBody also throttles PROPFIND request upload.
            server.enqueue(MockResponse().setResponseCode(207).setBody("<multistatus xmlns=\"DAV:\">" + " ".repeat(8192) + "</multistatus>")
                .setBodyDelay(3, TimeUnit.SECONDS))
            val repo = WebDavRepository(VideoServerConfig(type = VideoServerType.WEBDAV, serverUrl = server.url("/dav/").toString(),
                sourceId = "cancel-source", allowInsecureHttp = true),
                OkHttpClient.Builder().proxy(java.net.Proxy.NO_PROXY).build())
            assertEquals(server.url("/dav/"), repo.paths.root)
            val request = launch(start = CoroutineStart.UNDISPATCHED) { repo.listDirectory("/") }
            assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(2, TimeUnit.SECONDS) })
            delay(100)
            withTimeout(2000) { request.cancelAndJoin() }
            assertTrue(request.isCancelled)
        } finally { server.shutdown(); ScopedMediaRegistry.remove("cancel-source") }
    }
}