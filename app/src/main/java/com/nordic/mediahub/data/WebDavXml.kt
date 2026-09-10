package com.nordic.mediahub.data

import okhttp3.HttpUrl
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.ext.DefaultHandler2
import java.io.ByteArrayInputStream
import javax.xml.parsers.SAXParserFactory

private data class DavProperties(
    var name: String? = null, var directory: Boolean = false, var size: Long? = null,
    var modified: String? = null, var etag: String? = null, var contentType: String? = null
)

/** Namespace-aware, shallow response parser; failed propstat blocks never become valid metadata. */
internal fun parseWebDavMultistatus(bytes: ByteArray, paths: WebDavPaths, requestUrl: HttpUrl): List<WebDavEntry> {
    if (bytes.size > 8 * 1024 * 1024) throw WebDavException(WebDavException.Kind.LIMIT, "目录响应过大，请缩小起始目录")
    val inspection = bytes.toString(Charsets.UTF_8).replace("\u0000", "")
    if (inspection.contains("<!DOCTYPE", true) || inspection.contains("<!ENTITY", true)) {
        throw WebDavException(WebDavException.Kind.XML, "服务器返回了不安全的 XML 内容")
    }
    val current = paths.relative(requestUrl)?.let { if (it.endsWith('/')) it else "$it/" } ?: "/"
    val result = linkedMapOf<String, WebDavEntry>()
    var isMultistatus = false
    val handler = object : DefaultHandler2() {
        private val stack = mutableListOf<String>()
        private var text = StringBuilder()
        private var href: String? = null
        private var responseStatus: String? = null
        private var properties = DavProperties()
        private var block: DavProperties? = null
        private var blockStatus: String? = null
        private var inResponse = false
        private var hadSuccessfulProperties = false

        override fun startDTD(name: String?, publicId: String?, systemId: String?) { throw SAXException("DTD disabled") }
        override fun resolveEntity(publicId: String?, systemId: String?): InputSource = throw SAXException("External entities disabled")
        override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
            val name = if (uri == "DAV:" || uri.isEmpty()) localName.ifEmpty { qName.substringAfter(':') } else "_other"
            stack.add(name)
            text = StringBuilder()
            if (stack.size == 1) isMultistatus = name == "multistatus"
            when (name) {
                "response" -> { inResponse = true; href = null; responseStatus = null; properties = DavProperties(); hadSuccessfulProperties = false }
                "propstat" -> if (inResponse) { block = DavProperties(); blockStatus = null }
                "collection" -> if (stack.dropLast(1).lastOrNull() == "resourcetype") block?.directory = true
            }
        }
        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (text.length < 16384) text.append(ch, start, length.coerceAtMost(16384 - text.length))
        }
        override fun endElement(uri: String, localName: String, qName: String) {
            val name = stack.lastOrNull()
            val parent = stack.dropLast(1).lastOrNull()
            val value = text.toString().trim()
            when (name) {
                "href" -> if (parent == "response") href = value
                "status" -> if (parent == "propstat") blockStatus = value else if (parent == "response") responseStatus = value
                "displayname" -> block?.name = value.takeIf { it.isNotBlank() }
                "getcontentlength" -> block?.size = value.toLongOrNull()?.takeIf { it >= 0 }
                "getlastmodified" -> block?.modified = value
                "getetag" -> block?.etag = value
                "getcontenttype" -> block?.contentType = value
                "propstat" -> {
                    val code = blockStatus?.split(Regex("\\s+"))?.getOrNull(1)?.toIntOrNull()
                    if (code != null && code in 200..299) {
                        hadSuccessfulProperties = true
                        block?.let { p ->
                            properties = DavProperties(p.name ?: properties.name, p.directory || properties.directory,
                                p.size ?: properties.size, p.modified ?: properties.modified,
                                p.etag ?: properties.etag, p.contentType ?: properties.contentType)
                        }
                    }
                    block = null
                }
                "response" -> {
                    inResponse = false
                    val status = responseStatus?.split(Regex("\\s+"))?.getOrNull(1)?.toIntOrNull()
                    val target = href?.let { paths.resolveHref(requestUrl, it) }
                    if (target != null && hadSuccessfulProperties && (status == null || status in 200..299)) {
                        val (url, relative) = target
                        val normalized = relative.trimEnd('/') + if (properties.directory) "/" else ""
                        val suffix = normalized.removePrefix(current).trimEnd('/')
                        if (normalized.trimEnd('/') != current.trimEnd('/') && normalized.startsWith(current) &&
                            suffix.isNotEmpty() && !suffix.contains('/')) {
                            val label = properties.name ?: url.pathSegments.lastOrNull { it.isNotEmpty() }.orEmpty()
                            if (label.isNotBlank()) result[normalized] = WebDavEntry(normalized, label, properties.directory,
                                properties.size, parseWebDavDate(properties.modified), properties.etag, properties.contentType)
                        }
                    }
                    if (result.size > 20000) throw SAXException("Too many entries")
                }
            }
            if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
            text = StringBuilder()
        }
    }
    try {
        val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = true }
        listOf("http://xml.org/sax/features/external-general-entities", "http://xml.org/sax/features/external-parameter-entities",
            "http://apache.org/xml/features/nonvalidating/load-external-dtd").forEach { feature ->
            runCatching { factory.setFeature(feature, false) }
        }
        val reader = factory.newSAXParser().xmlReader
        reader.setProperty("http://xml.org/sax/properties/lexical-handler", handler)
        reader.contentHandler = handler
        reader.entityResolver = handler
        reader.errorHandler = handler
        reader.parse(InputSource(ByteArrayInputStream(bytes)))
        if (!isMultistatus) throw SAXException("Not a DAV multistatus")
        return result.values.toList()
    } catch (error: Exception) {
        throw WebDavException(WebDavException.Kind.XML, "无法解析 WebDAV 目录，请确认填写的是 WebDAV 地址而不是网页地址")
    }
}