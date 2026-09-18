package com.nordic.mediahub.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/**
 * Write-capable WebDAV client used exclusively by backup/restore. The media browsing contract
 * (read-only PROPFIND/GET) does not apply here, but the error classification and HTTPS-only
 * posture follow the same rules as [WebDavRepository].
 */
internal class BackupWebDavClient(config: BackupWebDavConfig, client: OkHttpClient = OkHttpClient()) {
    private val paths = WebDavPaths(config.serverUrl)
    private val directory = normalizeWebDavDirectory(config.directory)
    private val authorization = if (config.username.isBlank()) null
        else Credentials.basic(config.username, config.password, Charsets.UTF_8)
    private val client = client.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(35, TimeUnit.SECONDS).callTimeout(60, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false)
        .build()

    init {
        require(paths.root.isHttps || config.allowInsecureHttp) {
            "HTTP 会明文传输备份密码，请使用 HTTPS 或先确认允许不安全连接"
        }
    }

    suspend fun testConnection() = execute(directoryUrl(), configure = {
        header("Depth", "0")
        method("PROPFIND", PROPFIND_BODY.toRequestBody("application/xml; charset=utf-8".toMediaType()))
    }) { response ->
        rejectRedirect(response)
        if (response.code != 207) throw webDavHttpError(response.code)
        Unit
    }

    /** Creates the backup directory and missing parents; 405 means it already exists. */
    suspend fun ensureDirectory() {
        val segments = directory.split('/').filter { it.isNotEmpty() }
        var partial = ""
        segments.forEach { segment ->
            partial += "/$segment"
            execute(directoryUrl(partial), configure = { method("MKCOL", null) }) { response ->
                when (response.code) {
                    201, 405 -> Unit
                    in listOf(301, 302, 307, 308) ->
                        throw WebDavException(WebDavException.Kind.UNSUPPORTED, "备份目录被重定向，请检查备份目录设置")
                    else -> throw webDavHttpError(response.code)
                }
            }
        }
    }

    suspend fun upload(fileName: String, bytes: ByteArray) {
        execute(fileUrl(fileName), configure = {
            method("PUT", bytes.toRequestBody("application/octet-stream".toMediaType()))
        }) { response ->
            if (response.code !in 200..299) throw webDavHttpError(response.code)
            Unit
        }
    }

    suspend fun download(fileName: String): ByteArray =
        execute(fileUrl(fileName), configure = { method("GET", null) }) { response ->
            when {
                response.code == 404 -> throw WebDavException(WebDavException.Kind.NOT_FOUND, "云端备份文件不存在，请刷新列表", 404)
                response.code !in 200..299 -> throw webDavHttpError(response.code)
            }
            val bytes = response.body?.bytes() ?: throw IOException("备份下载内容为空")
            require(bytes.isNotEmpty()) { "备份下载内容为空" }
            bytes
        }

    /** Partial GET used to read archive headers without transferring the full payload. */
    suspend fun downloadHeader(fileName: String): ByteArray =
        execute(fileUrl(fileName), configure = {
            method("GET", null)
            header("Range", "bytes=0-${HEADER_DOWNLOAD_LIMIT - 1}")
        }) { response ->
            val bytes = when (response.code) {
                404 -> throw WebDavException(WebDavException.Kind.NOT_FOUND, "云端备份文件不存在，请刷新列表", 404)
                206, 200 -> response.body?.bytes()
                else -> throw webDavHttpError(response.code)
            } ?: throw IOException("备份下载内容为空")
            if (response.code == 206) bytes else bytes.copyOf(HEADER_DOWNLOAD_LIMIT.coerceAtMost(bytes.size))
        }

    suspend fun list(): List<WebDavEntry> =
        execute(directoryUrl(), configure = {
            header("Depth", "1")
            method("PROPFIND", PROPFIND_BODY.toRequestBody("application/xml; charset=utf-8".toMediaType()))
        }) { response ->
            rejectRedirect(response)
            if (response.code != 207) throw webDavHttpError(response.code)
            val bytes = response.body?.byteStream()?.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(32768)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > LIST_LIMIT) {
                        throw WebDavException(WebDavException.Kind.LIMIT, "备份目录内容过大，请整理备份目录")
                    }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            } ?: throw WebDavException(WebDavException.Kind.XML, "WebDAV 目录响应为空")
            parseWebDavMultistatus(bytes, paths, directoryUrl()).filterNot { it.directory }
        }

    suspend fun delete(fileName: String) {
        execute(fileUrl(fileName), configure = { method("DELETE", null) }) { response ->
            when {
                response.code == 404 -> Unit
                response.code !in 200..299 -> throw webDavHttpError(response.code)
                else -> Unit
            }
        }
    }

    private fun rejectRedirect(response: Response) {
        if (response.code in listOf(301, 302, 307, 308)) {
            throw WebDavException(WebDavException.Kind.UNSUPPORTED, "备份地址被重定向，请填写 WebDAV 服务的最终地址")
        }
    }

    private fun directoryUrl(path: String = directory): HttpUrl =
        paths.url(if (path.endsWith('/')) path else "$path/")

    private fun fileUrl(fileName: String): HttpUrl {
        require(fileName.isNotBlank() && !fileName.contains('/') && fileName == fileName.trim()) { "备份文件名不合法" }
        return paths.url(directory.trimEnd('/') + "/" + fileName)
    }

    private suspend fun <T> execute(
        url: HttpUrl,
        configure: Request.Builder.() -> Unit = {},
        read: (Response) -> T
    ): T = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(url)
        authorization?.let { builder.header("Authorization", it) }
        builder.configure()
        try {
            client.newCall(builder.build()).execute().use { response ->
                if (response.code == 401 && response.header("WWW-Authenticate")?.startsWith("Digest", true) == true &&
                    response.header("WWW-Authenticate")?.contains("Basic", true) != true) {
                    throw WebDavException(WebDavException.Kind.UNSUPPORTED,
                        "此服务仅提供 Digest 认证；当前支持 Basic 或匿名 WebDAV，请调整服务端认证方式")
                }
                read(response)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: WebDavException) {
            throw error
        } catch (error: SSLException) {
            throw WebDavException(WebDavException.Kind.CERTIFICATE, "服务器证书验证失败，请使用有效的 HTTPS 证书", cause = error)
        } catch (error: IllegalArgumentException) {
            throw WebDavException(WebDavException.Kind.UNSUPPORTED, "备份地址或目录格式不正确", cause = error)
        } catch (error: Exception) {
            throw WebDavException(WebDavException.Kind.NETWORK, "无法连接备份 WebDAV，请检查网络、地址与服务器状态", cause = error)
        }
    }

    private companion object {
        const val HEADER_DOWNLOAD_LIMIT = 64 * 1024
        const val LIST_LIMIT = 8 * 1024 * 1024
        val PROPFIND_BODY = """<?xml version="1.0" encoding="utf-8"?><d:propfind xmlns:d="DAV:"><d:prop><d:displayname/><d:resourcetype/><d:getcontentlength/><d:getlastmodified/></d:prop></d:propfind>"""
    }
}
