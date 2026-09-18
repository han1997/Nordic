package com.nordic.mediahub.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.Credentials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupWebDavClientTest {
    private fun config(url: String, allowInsecure: Boolean = true) = BackupWebDavConfig(
        serverUrl = url, username = "user", password = "secret", directory = "nordic-backup",
        allowInsecureHttp = allowInsecure
    )

    private fun propfindBody(fileName: String): String = """
        <?xml version="1.0"?><d:multistatus xmlns:d="DAV:">
        <d:response><d:href>/dav/nordic-backup/</d:href><d:propstat><d:prop>
        <d:displayname>nordic-backup</d:displayname><d:resourcetype><d:collection/></d:resourcetype>
        </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
        <d:response><d:href>/dav/nordic-backup/$fileName</d:href><d:propstat><d:prop>
        <d:displayname>$fileName</d:displayname><d:resourcetype/><d:getcontentlength>456</d:getcontentlength>
        </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
        </d:multistatus>
    """.trimIndent()

    @Test fun httpRefusesWithoutExplicitInsecureConfirmation() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupWebDavClient(config("http://example.com/dav/", allowInsecure = false))
        }
    }

    @Test fun uploadPutsBodyWithBasicAuthToConfiguredDirectory() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(201))
            val client = BackupWebDavClient(config(server.url("/dav/").toString()))
            client.upload(backupFileName(1726963200000L), byteArrayOf(1, 2, 3))
            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/dav/nordic-backup/" + backupFileName(1726963200000L), request.path)
            assertEquals(Credentials.basic("user", "secret", Charsets.UTF_8), request.getHeader("Authorization"))
            assertTrue(request.body.readByteArray().contentEquals(byteArrayOf(1, 2, 3)))
        } finally { server.shutdown() }
    }

    @Test fun ensureDirectoryToleratesExistingTarget() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(405))
            val client = BackupWebDavClient(config(server.url("/dav/").toString()))
            client.ensureDirectory()
            val request = server.takeRequest()
            assertEquals("MKCOL", request.method)
            assertEquals("/dav/nordic-backup/", request.path)
        } finally { server.shutdown() }
    }

    @Test fun ensureDirectoryCreatesMissingParents() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(201))
            server.enqueue(MockResponse().setResponseCode(201))
            val client = BackupWebDavClient(BackupWebDavConfig(serverUrl = server.url("/dav/").toString(),
                username = "user", password = "secret", directory = "app/backup", allowInsecureHttp = true))
            client.ensureDirectory()
            assertEquals("/dav/app/", server.takeRequest().path)
            assertEquals("/dav/app/backup/", server.takeRequest().path)
        } finally { server.shutdown() }
    }

    @Test fun listParsesOnlyFileEntriesInsideDirectory() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(207).setBody(propfindBody(backupFileName(1726963200000L))))
            val client = BackupWebDavClient(config(server.url("/dav/").toString()))
            val entries = client.list()
            assertEquals(1, entries.size)
            assertEquals(backupFileName(1726963200000L), entries.single().name)
            assertEquals(456L, entries.single().size)
            val request = server.takeRequest()
            assertEquals("PROPFIND", request.method)
            assertEquals("1", request.getHeader("Depth"))
            assertEquals("/dav/nordic-backup/", request.path)
        } finally { server.shutdown() }
    }

    @Test fun downloadReturnsBytesAndHeadersUseRangeRequest() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            val archive = BackupArchiveCodec.encode(BackupArchiveHeader(1L, "0.1.21"), ByteArray(128) { it.toByte() })
            server.enqueue(MockResponse().setResponseCode(200).setBody(okio.Buffer().write(archive)))
            server.enqueue(MockResponse().setResponseCode(200).setBody(okio.Buffer().write(archive)))
            val client = BackupWebDavClient(config(server.url("/dav/").toString()))
            val name = backupFileName(1L)
            assertTrue(client.download(name).contentEquals(archive))
            val header = client.downloadHeader(name)
            assertEquals(BackupArchiveHeader(1L, "0.1.21"), BackupArchiveCodec.decodeHeader(header))
            assertEquals("GET", server.takeRequest().method)
            val ranged = server.takeRequest()
            assertEquals("bytes=0-65535", ranged.getHeader("Range"))
        } finally { server.shutdown() }
    }

    @Test fun httpErrorsMapToTypedWebDavExceptions() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            val client = BackupWebDavClient(config(server.url("/dav/").toString()))
            server.enqueue(MockResponse().setResponseCode(401))
            val auth = assertThrows(WebDavException::class.java) { runBlocking { client.download("missing.nbk") } }
            assertEquals(WebDavException.Kind.AUTH, auth.kind)
            server.enqueue(MockResponse().setResponseCode(401).setHeader("WWW-Authenticate", "Digest realm=\"x\""))
            val digest = assertThrows(WebDavException::class.java) { runBlocking { client.testConnection() } }
            assertEquals(WebDavException.Kind.UNSUPPORTED, digest.kind)
            server.enqueue(MockResponse().setResponseCode(404))
            val missing = assertThrows(WebDavException::class.java) { runBlocking { client.download("missing.nbk") } }
            assertEquals(WebDavException.Kind.NOT_FOUND, missing.kind)
        } finally { server.shutdown() }
    }

    @Test fun deleteTreatsMissingFileAsSuccess() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(204))
            server.enqueue(MockResponse().setResponseCode(404))
            val client = BackupWebDavClient(config(server.url("/dav/").toString()))
            val name = backupFileName(1726963200000L)
            client.delete(name)
            client.delete(name)
            assertEquals("DELETE", server.takeRequest().method)
            assertEquals("DELETE", server.takeRequest().method)
        } finally { server.shutdown() }
    }
}
