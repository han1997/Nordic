package com.nordic.mediahub.data

import android.content.SharedPreferences
import java.io.IOException

internal const val MEDIA_SOURCES_KEY = "media_sources_v1"
internal const val APP_PREFERENCES_KEY = "app_preferences_v1"
internal val ENCRYPTED_CONFIG_LOCK = Any()

/** Called under the process-wide lock after the legacy credential migration. */
internal fun migrateMediaSources(prefs: SharedPreferences): MediaSourceState {
    prefs.getString(MEDIA_SOURCES_KEY, null)?.let { return MediaSourceCodec.decode(it) }
    fun value(key: String) = prefs.getString(key, null).orEmpty()
    val entries = buildList {
        fun addLegacy(kind: MediaSourceKind, url: String, user: String, pass: String, apiKey: String = "") {
            if (url.isBlank() && user.isBlank() && pass.isBlank() && apiKey.isBlank()) return
            add(MediaSource(name = kind.label, kind = kind, serverUrl = url, username = user,
                password = pass, apiKey = apiKey, allowInsecureHttp = url.startsWith("http://", true)))
        }
        addLegacy(MediaSourceKind.NAVIDROME, value(EncryptedConfigKeys.NAVIDROME_URL),
            value(EncryptedConfigKeys.NAVIDROME_USER), value(EncryptedConfigKeys.NAVIDROME_PASS))
        addLegacy(MediaSourceKind.AUDIOBOOKSHELF, value(EncryptedConfigKeys.AUDIOBOOK_URL),
            value(EncryptedConfigKeys.AUDIOBOOK_USER), value(EncryptedConfigKeys.AUDIOBOOK_PASS))
        val videoType = value(EncryptedConfigKeys.VIDEO_TYPE).toVideoServerType()
        if (videoType != VideoServerType.PLEX) addLegacy(
            if (videoType == VideoServerType.WEBDAV) MediaSourceKind.WEBDAV else MediaSourceKind.EMBY,
            value(EncryptedConfigKeys.VIDEO_URL), value(EncryptedConfigKeys.VIDEO_USER),
            value(EncryptedConfigKeys.VIDEO_PASS), value(EncryptedConfigKeys.VIDEO_API_KEY))
    }
    var state = MediaSourceState(sources = entries)
    MediaDomain.entries.forEach { domain -> state = state.select(domain, entries.firstOrNull { it.domain == domain }?.id) }
    val migratedKeys = EncryptedConfigKeys.ALL.filter { key ->
        key != EncryptedConfigKeys.AUDIOBOOK_LAST_ITEM_ID &&
            !(value(EncryptedConfigKeys.VIDEO_TYPE).toVideoServerType() == VideoServerType.PLEX && key.startsWith("video_"))
    }
    val original = migratedKeys.associateWith { prefs.getString(it, null) }
    val editor = prefs.edit().putString(MEDIA_SOURCES_KEY, MediaSourceCodec.encode(state))
    migratedKeys.forEach { editor.remove(it) }
    if (!editor.commit()) {
        val rollback = prefs.edit().remove(MEDIA_SOURCES_KEY)
        original.forEach { (key, old) -> if (old == null) rollback.remove(key) else rollback.putString(key, old) }
        rollback.commit()
        throw IOException("保存服务器迁移失败，原配置已保留")
    }
    return state
}

internal fun readAppPreferences(prefs: SharedPreferences): AppPreferences {
    val base = AppPreferencesCodec.decode(prefs.getString(APP_PREFERENCES_KEY, null))
    return base.copy(
        videoSpeed = prefs.getString(EncryptedConfigKeys.VIDEO_PLAYBACK_SPEED, null)?.toFloatOrNull() ?: base.videoSpeed,
        videoPip = prefs.getString(EncryptedConfigKeys.VIDEO_PIP_ENABLED, null)?.toBooleanStrictOrNull() ?: base.videoPip,
        videoAutoSkipIntro = prefs.getString(EncryptedConfigKeys.VIDEO_AUTO_SKIP_INTRO, null)?.toBooleanStrictOrNull() ?: base.videoAutoSkipIntro,
        videoQuality = prefs.getString(EncryptedConfigKeys.VIDEO_QUALITY_MODE, null)?.let(VideoQualityMode::fromName) ?: base.videoQuality
    ).validated()
}

internal fun SharedPreferences.Editor.putAppPreferences(p: AppPreferences): SharedPreferences.Editor =
    putString(APP_PREFERENCES_KEY, AppPreferencesCodec.encode(p))
        .putString(EncryptedConfigKeys.VIDEO_PLAYBACK_SPEED, p.videoSpeed.toString())
        .putString(EncryptedConfigKeys.VIDEO_PIP_ENABLED, p.videoPip.toString())
        .putString(EncryptedConfigKeys.VIDEO_AUTO_SKIP_INTRO, p.videoAutoSkipIntro.toString())
        .putString(EncryptedConfigKeys.VIDEO_QUALITY_MODE, p.videoQuality.name)