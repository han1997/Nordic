package com.nordic.mediahub.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class ConfigRepository(context: Context) {
    private val store = EncryptedConfigStore(context)

    val navidromeConfig: Flow<NavidromeConfig> = store.navidromeConfig

    val audiobookConfig: Flow<AudiobookShelfConfig> = store.audiobookConfig

    val lastAudiobookItemId: Flow<String?> = store.lastAudiobookItemId

    val videoConfig: Flow<VideoServerConfig> = store.videoConfig

    val videoPlaybackSpeed: Flow<Float?> = store.videoPlaybackSpeed

    suspend fun saveNavidromeConfig(config: NavidromeConfig) {
        store.saveNavidromeConfig(config)
    }

    suspend fun saveAudiobookConfig(config: AudiobookShelfConfig) {
        store.saveAudiobookConfig(config)
    }

    suspend fun saveLastAudiobookItemId(itemId: String) {
        store.saveLastAudiobookItemId(itemId)
    }

    suspend fun saveVideoConfig(config: VideoServerConfig) {
        store.saveVideoConfig(config)
    }

    suspend fun saveVideoPlaybackSpeed(speed: Float) {
        store.saveVideoPlaybackSpeed(speed)
    }
}

internal fun String?.toVideoServerType(): VideoServerType {
    val normalizedType = this?.trim()?.uppercase()
    return normalizedType
        ?.let { rawType -> VideoServerType.entries.firstOrNull { it.name == rawType } }
        ?: VideoServerType.EMBY
}
