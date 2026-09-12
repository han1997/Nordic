package com.nordic.mediahub.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Headers
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resumeWithException

private const val PROPFIND_BODY = """<?xml version="1.0" encoding="utf-8"?><d:propfind xmlns:d="DAV:"><d:prop><d:displayname/><d:resourcetype/><d:getcontentlength/><d:getlastmodified/><d:getetag/><d:getcontenttype/></d:prop></d:propfind>"""

class WebDavRepository(config: VideoServerConfig, client: OkHttpClient = OkHttpClient()) {
    private val config = if (config.sourceId.isBlank()) config.copy(sourceId = "webdav-${UUID.randomUUID()}") else config
    internal val paths = WebDavPaths(config.serverUrl)
    val sourceId: String get() = this.config.sourceId
    val initialDirectory: String get() = paths.fromDisplayPath(config.startDirectory)
    private val client = client.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(35, TimeUnit.SECONDS).callTimeout(45, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false)
        .addInterceptor(MediaAuthHeaderInterceptor()).addNetworkInterceptor(ScopedMediaNetworkInterceptor()).build()

    init {
        require(config.type == VideoServerType.WEBDAV) { "来源不是 WebDAV" }
        require(paths.root.isHttps || config.allowInsecureHttp) { "HTTP 连接需要明确确认" }
        ScopedMediaRegistry.registerWebDav(this.config)
    }

    suspend fun testConnection(): Int = listDirectory(initialDirectory).entries.size

    suspend fun listDirectory(path: String): WebDavDirectory = withContext(Dispatchers.IO) {
        try {
            val target = paths.url(if (path.endsWith('/')) path else "$path/")
            var url = target
            var response: WebDavResponseData? = null
            for (hop in 0..5) {
                coroutineContext.ensureActive()
                val request = Request.Builder().url(url.toString().forMediaSource(sourceId))
                    .header("Depth", "1").header("Accept", "application/xml, text/xml")
                    .method("PROPFIND", PROPFIND_BODY.toRequestBody("application/xml; charset=utf-8".toMediaType())).build()
                val received = client.newCall(request).awaitWebDavResponseData()
                if (received.code in listOf(301, 302, 307, 308)) {
                    val destination = received.header("Location")?.let(url::resolve)
                    if (destination == null || paths.relative(destination) == null || hop == 5) {
                        throw WebDavException(WebDavException.Kind.UNSUPPORTED, "目录被重定向到 WebDAV 根目录之外，请检查连接地址")
                    }
                    url = destination
                } else { response = received; break }
            }
            requireNotNull(response).let { result ->
                if (result.code == 401 && result.headers.values("WWW-Authenticate").any { it.startsWith("Digest", true) } &&
                    result.headers.values("WWW-Authenticate").none { it.startsWith("Basic", true) }) {
                    throw WebDavException(WebDavException.Kind.UNSUPPORTED, "此服务仅提供 Digest 认证；当前支持 Basic 或匿名 WebDAV，请调整服务端认证方式")
                }
                if (result.code != 207) throw webDavHttpError(result.code)
                val bytes = result.bytes ?: throw WebDavException(WebDavException.Kind.XML, "WebDAV 目录响应为空")
                val canonical = paths.relative(url) ?: path
                WebDavDirectory(canonical, parseWebDavMultistatus(bytes, paths, url), System.currentTimeMillis())
            }
        } catch (error: CancellationException) { throw error
        } catch (error: WebDavException) { throw error
        } catch (error: SSLException) {
            throw WebDavException(WebDavException.Kind.CERTIFICATE, "服务器证书验证失败，请使用有效的 HTTPS 证书")
        } catch (error: Exception) {
            throw WebDavException(WebDavException.Kind.NETWORK, "无法连接 WebDAV，请检查网络、地址与服务器状态", cause = error)
        }
    }

    fun preparePlayback(entry: WebDavEntry, siblings: List<WebDavEntry>): VideoItem {
        require(entry.isVideo) { "这个文件不是支持的视频类型" }
        ScopedMediaRegistry.registerWebDav(config)
        return playableItem(entry, siblings)
    }

    /** Capture a playable directory queue, including hidden subtitles and source-local resume data. */
    internal fun prepareEpisodeContext(
        siblings: List<WebDavEntry>,
        history: List<WebDavProgress>
    ): List<VideoItem> {
        ScopedMediaRegistry.registerWebDav(config)
        return siblings.filter { it.isVideo }.map { resumeWebDavVideo(playableItem(it, siblings), history) }
    }

    private fun playableItem(entry: WebDavEntry, siblings: List<WebDavEntry>): VideoItem {
        val baseName = entry.name.substringBeforeLast('.')
        val subtitles = siblings.filter { it.isSubtitle && it.name.startsWith("$baseName.") && webDavParent(it.path) == webDavParent(entry.path) }
            .map { subtitle ->
                val suffix = subtitle.name.removePrefix(baseName).lowercase()
                val language = when {
                    listOf(".zh", ".chi", ".chs", ".cht", ".cn").any { suffix.contains(it) } -> "zh"
                    suffix.contains(".en") -> "en"
                    else -> null
                }
                ExternalVideoSubtitle(paths.url(subtitle.path).toString().forMediaSource(sourceId),
                    when (subtitle.extension) { "srt" -> "application/x-subrip"; "vtt" -> "text/vtt"; else -> "text/x-ssa" },
                    subtitle.name, language)
            }
        return VideoItem(id = entry.path, libraryId = webDavParent(entry.path), title = entry.name, type = "Video",
            sourceId = sourceId, sourceType = VideoServerType.WEBDAV,
            contentVersion = entry.etag ?: entry.modifiedAtMillis?.let { "${entry.size}:$it" },
            streamUrl = paths.url(entry.path).toString().forMediaSource(sourceId), externalSubtitles = subtitles)
    }
}

internal fun webDavHttpError(code: Int): WebDavException = when (code) {
    401 -> WebDavException(WebDavException.Kind.AUTH, "认证失败，请检查用户名、密码或应用专用密码", code)
    403 -> WebDavException(WebDavException.Kind.PERMISSION, "账号没有访问此目录的权限", code)
    404 -> WebDavException(WebDavException.Kind.NOT_FOUND, "WebDAV 目录不存在，请检查完整路径", code)
    200, 405, 501 -> WebDavException(WebDavException.Kind.UNSUPPORTED, "此地址未提供 WebDAV 目录服务，请勿填写普通网页地址", code)
    else -> WebDavException(WebDavException.Kind.HTTP, "WebDAV 请求失败：HTTP $code", code)
}

private data class WebDavResponseData(val code: Int, val headers: Headers, val bytes: ByteArray?) {
    fun header(name: String): String? = headers[name]
}

/** Keep cancellation attached until the response body is fully consumed, not just until headers arrive. */
private suspend fun Call.awaitWebDavResponseData(): WebDavResponseData = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
        override fun onResponse(call: Call, response: Response) {
            try {
                val result = response.use {
                    val bytes = if (it.code == 207) it.body?.byteStream()?.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(32768)
                        while (true) {
                            if (call.isCanceled()) throw IOException("请求已取消")
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (output.size() + count > 8 * 1024 * 1024) throw WebDavException(WebDavException.Kind.LIMIT, "目录过大，请选择更小的起始目录")
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    } else null
                    WebDavResponseData(it.code, it.headers, bytes)
                }
                if (continuation.isActive) continuation.resumeWith(Result.success(result))
            } catch (error: Exception) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }
    })
}