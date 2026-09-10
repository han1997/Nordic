package com.nordic.mediahub.data

import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

private const val SOURCE_FRAGMENT = "nordic-source="

/** The fragment is local routing metadata: never sent to the server, never a credential. */
internal fun String.forMediaSource(sourceId: String): String {
    if (sourceId.isBlank()) return this
    return toHttpUrlOrNull()?.newBuilder()?.fragment(SOURCE_FRAGMENT + sourceId)?.build()?.toString() ?: this
}
internal fun HttpUrl.mediaSourceId(): String? = fragment?.takeIf { it.startsWith(SOURCE_FRAGMENT) }?.removePrefix(SOURCE_FRAGMENT)

internal data class MediaRequestContext(
    val root: HttpUrl,
    val header: MediaAuthHeader? = null,
    val navidrome: NavidromeConfig? = null,
    val webDav: Boolean = false
) {
    fun contains(url: HttpUrl): Boolean = url.scheme == root.scheme && url.host == root.host && url.port == root.port &&
        (url.encodedPath == root.encodedPath.trimEnd('/') || url.encodedPath.startsWith(root.encodedPath.trimEnd('/') + "/"))

    fun apply(request: Request): Request {
        if (root.isHttps && !request.url.isHttps) throw IOException("已阻止 HTTPS 连接降级")
        val builder = request.newBuilder().removeHeader("Authorization").removeHeader("X-Emby-Token")
        if (contains(request.url)) {
            header?.let { builder.header(it.headerName, it.headerValue) }
            navidrome?.let { config ->
                val clean = stripAuthQuery(request.url.toString()).toHttpUrlOrNull()!!
                builder.url(clean.newBuilder().addNavidromeAuth(config).build())
            }
        }
        return builder.build()
    }
}

/** Source-scoped snapshots. Connection tests use a disposable ID, not a saved source ID. */
internal object ScopedMediaRegistry {
    private val contexts = ConcurrentHashMap<String, MediaRequestContext>()
    private val revoked = ConcurrentHashMap.newKeySet<String>()
    @Synchronized
    fun registerHeader(sourceId: String, baseUrl: String, name: String, value: String) {
        if (sourceId in revoked) return
        val root = baseUrl.toHttpUrlOrNull() ?: return
        if (sourceId.isBlank()) {
            MediaAuthHeaderRegistry.register(root.originKey(), name, value)
        } else contexts[sourceId] = MediaRequestContext(root, MediaAuthHeader(name, value))
    }
    @Synchronized
    fun registerNavidrome(config: NavidromeConfig) {
        if (config.sourceId.isBlank() || config.sourceId in revoked) return
        val root = config.normalizedBaseUrl().toHttpUrlOrNull() ?: return
        contexts[config.sourceId] = MediaRequestContext(root, navidrome = config)
    }
    @Synchronized
    fun registerWebDav(config: VideoServerConfig) {
        if (config.sourceId in revoked) return
        val root = config.serverUrl.toHttpUrlOrNull() ?: return
        val auth = config.username.takeIf { it.isNotBlank() }?.let {
            MediaAuthHeader("Authorization", Credentials.basic(it, config.password, Charsets.UTF_8))
        }
        contexts[config.sourceId] = MediaRequestContext(root, auth, webDav = true)
    }
    @Synchronized
    fun get(id: String): MediaRequestContext? = if (id in revoked) null else contexts[id]
    @Synchronized
    fun remove(id: String) { contexts.remove(id) }
    @Synchronized
    fun revoke(id: String) { revoked.add(id); contexts.remove(id) }
}

class WebDavRangeException : java.net.ProtocolException("服务器不支持此位置的拖动或续播，请从头播放")

/** Runs for EVERY redirect hop; OkHttp's built-in guard only strips Authorization, not Emby tokens. */
internal class ScopedMediaNetworkInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val context = original.tag(MediaRequestContext::class.java) ?: return chain.proceed(original)
        val request = context.apply(original)
        val response = chain.proceed(request)
        if (context.webDav) {
            val start = request.header("Range")?.substringAfter("bytes=")?.substringBefore('-')?.toLongOrNull() ?: 0L
            val actualStart = response.header("Content-Range")?.substringAfter("bytes ")?.substringBefore('-')?.toLongOrNull()
            if (start > 0 && (response.code == 200 || response.code == 416 || (response.code == 206 && actualStart != start))) {
                response.close()
                throw WebDavRangeException()
            }
        }
        return response
    }
}