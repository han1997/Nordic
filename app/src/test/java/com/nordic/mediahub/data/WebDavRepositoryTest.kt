package com.nordic.mediahub.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class WebDavRepositoryTest {
    private fun response(href: String, name: String, directory: Boolean = false, status: Int = 200): String = """
        <d:response><d:href>$href</d:href><d:propstat><d:prop><d:displayname>$name</d:displayname>
        <d:resourcetype>${if (directory) "<d:collection/>" else ""}</d:resourcetype><d:getcontentlength>123</d:getcontentlength>
        <d:getetag>version-1</d:getetag></d:prop><d:status>HTTP/1.1 $status OK</d:status></d:propstat></d:response>
    """.trimIndent()
    private fun xml(vararg rows: String) = """<?xml version="1.0"?><d:multistatus xmlns:d="DAV:">${rows.joinToString("")}</d:multistatus>"""

    @Test fun parsesNamespacesEncodedNamesAndOnlyImmediateChildren() {
        val paths = WebDavPaths("https://example.com/dav/")
        val body = xml(response("/dav/", "根", true), response("/dav/%E7%94%B5%E5%BD%B1%20A.mkv", "电影 A.mkv"),
            response("/dav/folder/", "folder", true), response("/dav/folder/deep.mp4", "deep"),
            response("https://evil.example/secret.mp4", "other"), response("/dav/denied.mp4", "denied", status = 403))
        val entries = parseWebDavMultistatus(body.toByteArray(), paths, paths.root)
        assertEquals(2, entries.size)
        assertEquals("电影 A.mkv", entries.first().name)
        assertTrue(entries.first().path.contains("%20"))
        assertTrue(entries.last().directory)
    }
    @Test fun pathsPreserveLiteralPercentAndRejectRootEscape() {
        val paths = WebDavPaths("https://example.com/prefix/dav/")
        assertEquals("/a%20b/", paths.fromDisplayPath("/a b"))
        assertEquals("/100%25/", paths.fromDisplayPath("/100%"))
        assertEquals("https://example.com/prefix/dav/a%2Fb.mkv", paths.url("/a%2Fb.mkv").toString())
        assertNull(paths.resolveHref(paths.root, "../../private/movie.mp4"))
        assertNull(paths.resolveHref(paths.root, "http://example.com/prefix/dav/a.mp4"))
    }
    @Test fun refusesEntitiesAndNonDavHtml() {
        val paths = WebDavPaths("https://example.com/dav/")
        assertThrows(WebDavException::class.java) {
            parseWebDavMultistatus("<!DOCTYPE x [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><x/>".toByteArray(), paths, paths.root)
        }
        assertThrows(WebDavException::class.java) { parseWebDavMultistatus("<html/>".toByteArray(), paths, paths.root) }
    }
    @Test fun directoryRequestUsesPropfindDepthAndBasicAuth() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(207).setBody(xml(response("/dav/movie.mp4", "movie.mp4"))))
            val repo = WebDavRepository(VideoServerConfig(type = VideoServerType.WEBDAV, serverUrl = server.url("/dav/").toString(),
                username = "user", password = "secret", sourceId = "test-dav", allowInsecureHttp = true))
            assertEquals(1, repo.testConnection())
            val request = server.takeRequest()
            assertEquals("PROPFIND", request.method)
            assertEquals("1", request.getHeader("Depth"))
            assertEquals(Credentials.basic("user", "secret", Charsets.UTF_8), request.getHeader("Authorization"))
            assertEquals("/dav/", request.path)
        } finally { server.shutdown(); ScopedMediaRegistry.remove("test-dav") }
    }
    @Test fun httpErrorsHaveTypedUserSafeMessages() {
        assertEquals(WebDavException.Kind.AUTH, webDavHttpError(401).kind)
        assertEquals(WebDavException.Kind.PERMISSION, webDavHttpError(403).kind)
        assertEquals(WebDavException.Kind.NOT_FOUND, webDavHttpError(404).kind)
        assertEquals(WebDavException.Kind.UNSUPPORTED, webDavHttpError(200).kind)
    }
    @Test fun naturalSortKeepsFoldersFirstAndFiltersOnlyCurrentListing() {
        val rows = listOf(WebDavEntry("/v10.mp4", "v10.mp4", false), WebDavEntry("/v2.mp4", "v2.mp4", false),
            WebDavEntry("/.private.mp4", ".private.mp4", false), WebDavEntry("/z/", "z", true), WebDavEntry("/note.txt", "note.txt", false))
        assertEquals(listOf("z", "v2.mp4", "v10.mp4"), visibleWebDavEntries(rows, "", AppPreferences()).map { it.name })
        assertEquals(1, visibleWebDavEntries(rows, "v2", AppPreferences()).size)
    }
    @Test fun preparesOnlyMatchingSidecarsAndNoEmbyPlaybackMetadata() {
        val repo = WebDavRepository(VideoServerConfig(type = VideoServerType.WEBDAV, serverUrl = "https://example.com/dav/", sourceId = "subtitle-source"))
        val movie = WebDavEntry("/film.mkv", "film.mkv", false)
        val video = repo.preparePlayback(movie, listOf(WebDavEntry("/film.zh.srt", "film.zh.srt", false),
            WebDavEntry("/other.srt", "other.srt", false)))
        assertEquals(VideoServerType.WEBDAV, video.sourceType)
        assertEquals(1, video.externalSubtitles.size)
        assertEquals("zh", video.externalSubtitles.single().language)
        assertTrue(video.chapters.isEmpty())
        assertTrue(video.streamUrl!!.contains("nordic-source=subtitle-source"))
    }
    @Test fun progressIsSourceScopedAndContentChangesResetResume() = runBlocking {
        val store = fakeDataStore()
        val a = WebDavLocalRepository(sourceId = "a", dataStoreProvider = { store })
        val b = WebDavLocalRepository(sourceId = "b", dataStoreProvider = { store })
        val video = VideoItem("/same.mp4", "/", "same", "Video", sourceId = "a", sourceType = VideoServerType.WEBDAV, contentVersion = "v1")
        a.record(video, 40, 100, false)
        assertTrue(b.progress.first().isEmpty())
        assertEquals(40, resumeWebDavVideo(video, a.progress.first()).playbackPositionSeconds)
        assertEquals(0, resumeWebDavVideo(video.copy(contentVersion = "v2"), a.progress.first()).playbackPositionSeconds)
        a.record(video, 100, 100, true)
        assertEquals(0, resumeWebDavVideo(video, a.progress.first()).playbackPositionSeconds)
    }
    @Test fun scopedCachesKeepTwoAccountsWithIdenticalItemIds() = runBlocking {
        val store = fakeDataStore()
        val repo = NavidromeMusicCacheRepository(dataStoreProvider = { store })
        val a = NavidromeConfig("https://example.com", "a", "secret", "source-a")
        val b = a.copy(username = "b", sourceId = "source-b")
        repo.save(a, NavidromeMusicCache(songs = listOf(NavidromeSong("1", "A"))))
        repo.save(b, NavidromeMusicCache(songs = listOf(NavidromeSong("1", "B"))))
        assertEquals("A", repo.load(a)!!.songs.single().title)
        assertEquals("B", repo.load(b)!!.songs.single().title)
        repo.clear(a)
        assertNotNull(repo.load(b))
    }
    @Test fun mediaRedirectStripsCredentialsAndPreservesSignedQueryAndRange() {
        val source = MockWebServer(); val cdn = MockWebServer()
        source.start(); cdn.start()
        try {
            source.enqueue(MockResponse().setResponseCode(302).addHeader("Location", cdn.url("/movie?sign=temporary")))
            cdn.enqueue(MockResponse().setResponseCode(206).addHeader("Content-Range", "bytes 50-51/100").setBody("ok"))
            ScopedMediaRegistry.registerWebDav(VideoServerConfig(serverUrl = source.url("/dav/").toString(),
                username = "user", password = "secret", sourceId = "redirect-source"))
            val client = OkHttpClient.Builder().addInterceptor(MediaAuthHeaderInterceptor()).addNetworkInterceptor(ScopedMediaNetworkInterceptor()).build()
            client.newCall(Request.Builder().url(source.url("/dav/movie").toString().forMediaSource("redirect-source"))
                .header("Range", "bytes=50-").build()).execute().use { assertEquals(206, it.code) }
            assertNotNull(source.takeRequest().getHeader("Authorization"))
            val target = cdn.takeRequest()
            assertNull(target.getHeader("Authorization"))
            assertEquals("bytes=50-", target.getHeader("Range"))
            assertEquals("/movie?sign=temporary", target.path)
        } finally { source.shutdown(); cdn.shutdown(); ScopedMediaRegistry.remove("redirect-source") }
    }
}