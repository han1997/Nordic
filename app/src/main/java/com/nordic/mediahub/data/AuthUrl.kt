package com.nordic.mediahub.data

import android.net.Uri
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

private val AUTH_QUERY_PARAMS = setOf("api_key", "token", "u", "t", "s", "v", "c")

internal fun stripAuthQuery(url: String): String {
    if (url.isBlank()) return url
    val httpUrl = url.toHttpUrlOrNull() ?: return url
    val builder = httpUrl.newBuilder()
    val authParamNames = httpUrl.queryParameterNames.filter { name ->
        name.lowercase() in AUTH_QUERY_PARAMS
    }
    authParamNames.forEach { name -> builder.removeAllQueryParameters(name) }
    return builder.build().toString()
}

internal fun stripAuthQuery(uri: Uri): String = stripAuthQuery(uri.toString())

internal data class MediaAuthHeader(val headerName: String, val headerValue: String)

internal object MediaAuthHeaderRegistry {
    private val headers = mutableMapOf<String, MediaAuthHeader>()

    @Synchronized
    fun register(originKey: String, headerName: String, headerValue: String) {
        headers[originKey] = MediaAuthHeader(headerName, headerValue)
    }

    @Synchronized
    fun headerFor(originKey: String): MediaAuthHeader? = headers[originKey]

    @Synchronized
    fun clear() {
        headers.clear()
    }
}

internal fun HttpUrl.originKey(): String = "$host:$port"

internal class MediaAuthHeaderInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val sourceId = original.url.mediaSourceId()
        if (sourceId != null) {
            val context = ScopedMediaRegistry.get(sourceId)
                ?: throw java.io.IOException("媒体认证尚未就绪，请刷新来源后重试")
            val request = original.newBuilder()
                .url(original.url.newBuilder().fragment(null).build())
                .tag(MediaRequestContext::class.java, context).build()
            return chain.proceed(context.apply(request))
        }
        val request = original
        val originKey = request.url.originKey()
        val header = MediaAuthHeaderRegistry.headerFor(originKey)
        val newRequest = if (header != null && request.header(header.headerName) == null) {
            request.newBuilder().header(header.headerName, header.headerValue).build()
        } else {
            request
        }
        return chain.proceed(newRequest)
    }
}
