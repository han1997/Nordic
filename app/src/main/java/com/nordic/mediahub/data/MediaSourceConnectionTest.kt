package com.nordic.mediahub.data

import kotlinx.coroutines.CancellationException
import java.util.UUID

/** Diagnostics must never replace a saved source's media credentials. */
internal suspend fun testMediaSourceConnection(source: MediaSource): String {
    val testId = "test-${UUID.randomUUID()}"
    try {
        val candidate = saveMediaSource(MediaSourceState(), source.copy(id = testId)).source
        return when (candidate.kind) {
            MediaSourceKind.NAVIDROME -> {
                require(candidate.navidromeConfig().isReadyForMusicSync()) { "请填写用户名和密码" }
                NavidromeRepository(candidate.navidromeConfig()).testConnection()
                "连接成功"
            }
            MediaSourceKind.AUDIOBOOKSHELF -> {
                require(candidate.audiobookConfig().isReadyForAudiobookSync()) { "请填写用户名和密码" }
                "连接成功，${AudiobookShelfRepository(candidate.audiobookConfig()).testConnection()} 个书库"
            }
            MediaSourceKind.EMBY -> {
                require(candidate.videoConfig().isReadyForVideoSync()) { "请填写用户名密码或 API Key" }
                "连接成功，${EmbyRepository(candidate.videoConfig()).testConnection()} 个媒体库"
            }
            MediaSourceKind.WEBDAV -> "目录可读取，${WebDavRepository(candidate.videoConfig()).testConnection()} 个项目"
        }
    } catch (error: CancellationException) { throw error
    } finally { ScopedMediaRegistry.remove(testId) }
}