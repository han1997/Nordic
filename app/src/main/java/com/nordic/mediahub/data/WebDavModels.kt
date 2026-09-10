package com.nordic.mediahub.data

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class WebDavException(val kind: Kind, message: String, val httpCode: Int? = null, cause: Throwable? = null) : IOException(message, cause) {
    enum class Kind { AUTH, PERMISSION, NOT_FOUND, UNSUPPORTED, NETWORK, CERTIFICATE, XML, LIMIT, HTTP }
}

data class WebDavEntry(
    /** Canonical root-relative encoded path, never a signed playback URL. */
    val path: String,
    val name: String,
    val directory: Boolean,
    val size: Long? = null,
    val modifiedAtMillis: Long? = null,
    val etag: String? = null,
    val contentType: String? = null
) {
    val extension: String get() = name.substringAfterLast('.', "").lowercase()
    val isVideo: Boolean get() = !directory && extension in WEB_DAV_VIDEO_EXTENSIONS
    val isSubtitle: Boolean get() = !directory && extension in setOf("srt", "ass", "ssa", "vtt")
    val hidden: Boolean get() = name.startsWith('.')
}

data class WebDavDirectory(val path: String, val entries: List<WebDavEntry>, val fetchedAtMillis: Long)
internal val WEB_DAV_VIDEO_EXTENSIONS = setOf("mp4", "mkv", "m4v", "mov", "avi", "webm", "ts", "m2ts", "mts", "mpeg", "mpg", "wmv", "flv", "3gp")

internal class WebDavPaths(address: String) {
    val root: HttpUrl = (address.trim().toHttpUrlOrNull()
        ?: throw WebDavException(WebDavException.Kind.UNSUPPORTED, "WebDAV 地址格式不正确"))
        .newBuilder().query(null).fragment(null).build().let {
            it.newBuilder().encodedPath(it.encodedPath.trimEnd('/') + "/").build()
        }

    fun url(path: String): HttpUrl {
        require(path.startsWith('/') && !path.startsWith("//")) { "目录路径格式不正确" }
        val target = root.newBuilder().encodedPath(root.encodedPath + path.removePrefix("/")).build()
        require(relative(target) != null) { "目录不能超出 WebDAV 根路径" }
        return target
    }
    fun fromDisplayPath(path: String): String {
        val normalized = normalizeWebDavDirectory(path)
        val builder = root.newBuilder()
        normalized.split('/').filter { it.isNotEmpty() }.forEach { builder.addPathSegment(it) }
        if (normalized != "/") builder.addPathSegment("")
        return relative(builder.build()) ?: "/"
    }
    fun relative(url: HttpUrl): String? {
        if (url.scheme != root.scheme || url.host != root.host || url.port != root.port) return null
        if (url.encodedPath.trimEnd('/') == root.encodedPath.trimEnd('/')) return "/"
        if (!url.encodedPath.startsWith(root.encodedPath)) return null
        return "/" + url.encodedPath.removePrefix(root.encodedPath)
    }
    fun resolveHref(requestUrl: HttpUrl, href: String): Pair<HttpUrl, String>? {
        val resolved = requestUrl.resolve(href.trim()) ?: return null
        if (resolved.username.isNotEmpty() || resolved.password.isNotEmpty()) return null
        val path = relative(resolved) ?: return null
        return resolved.newBuilder().query(null).fragment(null).build() to path
    }
    fun display(path: String): String = url(path).pathSegments
        .drop(root.pathSegments.filter { it.isNotEmpty() }.size).filter { it.isNotEmpty() }.joinToString("/")
}

internal fun webDavParent(path: String): String = path.trimEnd('/').substringBeforeLast('/', "").let {
    if (it.isEmpty()) "/" else "$it/"
}
internal fun parseWebDavDate(value: String?): Long? = value?.let {
    runCatching { ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
        .getOrElse { _ -> runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
}

private val NATURAL_PARTS = Regex("[0-9]+|[^0-9]+")
internal fun compareNaturalNames(a: String, b: String): Int {
    val left = NATURAL_PARTS.findAll(a.lowercase()).map { it.value }.toList()
    val right = NATURAL_PARTS.findAll(b.lowercase()).map { it.value }.toList()
    for (i in 0 until minOf(left.size, right.size)) {
        val l = left[i]; val r = right[i]
        val result = if (l.first().isDigit() && r.first().isDigit()) {
            val ln = l.trimStart('0').ifEmpty { "0" }; val rn = r.trimStart('0').ifEmpty { "0" }
            ln.length.compareTo(rn.length).takeIf { it != 0 } ?: ln.compareTo(rn)
        } else l.compareTo(r)
        if (result != 0) return result
    }
    return left.size.compareTo(right.size).takeIf { it != 0 } ?: a.compareTo(b)
}

internal fun visibleWebDavEntries(entries: List<WebDavEntry>, query: String, preferences: AppPreferences): List<WebDavEntry> {
    val filtered = entries.filter { entry ->
        (preferences.webDavShowHidden || !entry.hidden) &&
            (!preferences.webDavOnlyVideos || entry.directory || entry.isVideo) &&
            (query.isBlank() || entry.name.contains(query.trim(), ignoreCase = true))
    }
    return filtered.sortedWith { a, b ->
        if (a.directory != b.directory) if (a.directory) -1 else 1 else {
            val primary = when (preferences.webDavSort) {
                WebDavSort.NAME -> compareNaturalNames(a.name, b.name)
                WebDavSort.MODIFIED -> (a.modifiedAtMillis ?: 0).compareTo(b.modifiedAtMillis ?: 0)
                WebDavSort.SIZE -> (a.size ?: 0).compareTo(b.size ?: 0)
            }
            val compared = if (preferences.webDavSortDescending) -primary else primary
            compared.takeIf { it != 0 } ?: compareNaturalNames(a.name, b.name)
        }
    }
}