package com.nordic.mediahub.data

import com.google.gson.Gson
import com.google.gson.JsonParser
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import java.util.UUID

enum class MediaDomain(val label: String) { MUSIC("音乐"), AUDIOBOOK("有声书"), VIDEO("视频") }

enum class MediaSourceKind(val label: String, val domain: MediaDomain) {
    NAVIDROME("Navidrome", MediaDomain.MUSIC),
    AUDIOBOOKSHELF("AudiobookShelf", MediaDomain.AUDIOBOOK),
    EMBY("Emby", MediaDomain.VIDEO),
    WEBDAV("WebDAV", MediaDomain.VIDEO)
}

/** Credentials are serialized only by the encrypted configuration store. */
data class MediaSource(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val kind: MediaSourceKind = MediaSourceKind.WEBDAV,
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val apiKey: String = "",
    val startDirectory: String = "/",
    val allowInsecureHttp: Boolean = false
) {
    val domain: MediaDomain get() = kind.domain
    fun navidromeConfig() = NavidromeConfig(serverUrl, username, password, id)
    fun audiobookConfig() = AudiobookShelfConfig(serverUrl, username, password, id)
    fun videoConfig() = VideoServerConfig(
        type = if (kind == MediaSourceKind.WEBDAV) VideoServerType.WEBDAV else VideoServerType.EMBY,
        serverUrl = serverUrl, username = username, password = password, apiKey = apiKey,
        sourceId = id, startDirectory = startDirectory, allowInsecureHttp = allowInsecureHttp
    )
    override fun toString(): String = "MediaSource(id=$id, kind=$kind)"
}

data class MediaSourceState(
    val schemaVersion: Int = 1,
    val sources: List<MediaSource> = emptyList(),
    val activeMusicId: String? = null,
    val activeAudiobookId: String? = null,
    val activeVideoId: String? = null
) {
    fun activeId(domain: MediaDomain): String? = when (domain) {
        MediaDomain.MUSIC -> activeMusicId
        MediaDomain.AUDIOBOOK -> activeAudiobookId
        MediaDomain.VIDEO -> activeVideoId
    }
    fun active(domain: MediaDomain): MediaSource? = sources.firstOrNull {
        it.id == activeId(domain) && it.domain == domain
    }
    fun select(domain: MediaDomain, id: String?): MediaSourceState {
        require(id == null || sources.any { it.id == id && it.domain == domain })
        return when (domain) {
            MediaDomain.MUSIC -> copy(activeMusicId = id)
            MediaDomain.AUDIOBOOK -> copy(activeAudiobookId = id)
            MediaDomain.VIDEO -> copy(activeVideoId = id)
        }
    }
    fun remove(id: String): MediaSourceState {
        val source = sources.firstOrNull { it.id == id } ?: return this
        val next = copy(sources = sources.filterNot { it.id == id })
        return if (activeId(source.domain) == id) next.select(source.domain, null) else next
    }
}

internal data class SavedMediaSource(val state: MediaSourceState, val source: MediaSource)

internal fun sameMediaSourceIdentity(a: MediaSource, b: MediaSource): Boolean =
    a.kind == b.kind && a.serverUrl.trimEnd('/') == b.serverUrl.trimEnd('/') &&
        a.username == b.username && a.apiKey == b.apiKey && a.startDirectory == b.startDirectory

internal fun saveMediaSource(state: MediaSourceState, draft: MediaSource): SavedMediaSource {
    val url = draft.serverUrl.trim().toHttpUrlOrNull()
        ?: throw IllegalArgumentException("请输入完整的 HTTP 或 HTTPS 服务器地址")
    require(url.username.isEmpty() && url.password.isEmpty()) { "请在账号字段填写认证信息，不要放入地址" }
    require(url.query == null && url.fragment == null) { "服务器地址不能包含查询参数或片段" }
    require(url.isHttps || draft.allowInsecureHttp) { "HTTP 会明文传输数据，请先确认允许不安全连接" }
    require(draft.kind != MediaSourceKind.WEBDAV || draft.username.isNotBlank() || draft.password.isEmpty()) {
        "请填写用户名，或同时留空用户名和密码以匿名连接"
    }
    val normalized = draft.copy(
        serverUrl = url.toString().trimEnd('/'),
        name = draft.name.trim().ifBlank { "${draft.kind.label} · ${url.host}" },
        username = draft.username.trim(),
        startDirectory = if (draft.kind == MediaSourceKind.WEBDAV) normalizeWebDavDirectory(draft.startDirectory) else "/"
    )
    val old = state.sources.firstOrNull { it.id == normalized.id }
    val source = if (old != null && !sameMediaSourceIdentity(old, normalized)) {
        normalized.copy(id = UUID.randomUUID().toString())
    } else normalized
    val entries = if (old == null) state.sources + source else state.sources.map { if (it.id == old.id) source else it }
    var next = state.copy(sources = entries)
    if (state.active(source.domain) == null || old?.id == state.activeId(source.domain)) {
        next = next.select(source.domain, source.id)
    }
    return SavedMediaSource(next, source)
}

internal fun normalizeWebDavDirectory(path: String): String {
    val parts = path.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
    require(parts.none { it == ".." }) { "起始目录不能包含上级路径" }
    return if (parts.isEmpty()) "/" else "/${parts.joinToString("/")}/"
}

internal object MediaSourceCodec {
    private val gson = Gson()
    fun encode(state: MediaSourceState): String = gson.toJson(state)
    fun decode(json: String): MediaSourceState = try {
        val root = JsonParser.parseString(json).asJsonObject
        require(root.get("schemaVersion")?.asInt == 1)
        val state = gson.fromJson(root, MediaSourceState::class.java)
        require(state.sources.all { source ->
            source.id.matches(Regex("[A-Za-z0-9_-]{1,100}")) &&
                MediaSourceKind.entries.contains(source.kind)
        })
        require(state.sources.map { it.id }.distinct().size == state.sources.size)
        state
    } catch (error: Exception) {
        throw IOException("服务器配置无法读取，原数据未被修改", error)
    }
}