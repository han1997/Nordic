package com.nordic.mediahub.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class ConfigRepository(private val context: Context) {
    private val NAVIDROME_URL = stringPreferencesKey("navidrome_url")
    private val NAVIDROME_USER = stringPreferencesKey("navidrome_user")
    private val NAVIDROME_PASS = stringPreferencesKey("navidrome_pass")

    private val AUDIOBOOK_URL = stringPreferencesKey("audiobook_url")
    private val AUDIOBOOK_USER = stringPreferencesKey("audiobook_user")
    private val AUDIOBOOK_PASS = stringPreferencesKey("audiobook_pass")

    private val VIDEO_TYPE = stringPreferencesKey("video_type")
    private val VIDEO_URL = stringPreferencesKey("video_url")
    private val VIDEO_USER = stringPreferencesKey("video_user")
    private val VIDEO_PASS = stringPreferencesKey("video_pass")
    private val VIDEO_API_KEY = stringPreferencesKey("video_api_key")

    val navidromeConfig: Flow<NavidromeConfig> = context.dataStore.data.map {
        NavidromeConfig(
            serverUrl = it[NAVIDROME_URL] ?: "",
            username = it[NAVIDROME_USER] ?: "",
            password = it[NAVIDROME_PASS] ?: ""
        )
    }

    val audiobookConfig: Flow<AudiobookShelfConfig> = context.dataStore.data.map {
        AudiobookShelfConfig(
            serverUrl = it[AUDIOBOOK_URL] ?: "",
            username = it[AUDIOBOOK_USER] ?: "",
            password = it[AUDIOBOOK_PASS] ?: ""
        )
    }

    val videoConfig: Flow<VideoServerConfig> = context.dataStore.data.map {
        VideoServerConfig(
            type = VideoServerType.valueOf(it[VIDEO_TYPE] ?: "EMBY"),
            serverUrl = it[VIDEO_URL] ?: "",
            username = it[VIDEO_USER] ?: "",
            password = it[VIDEO_PASS] ?: "",
            apiKey = it[VIDEO_API_KEY] ?: ""
        )
    }

    suspend fun saveNavidromeConfig(config: NavidromeConfig) {
        context.dataStore.edit {
            it[NAVIDROME_URL] = config.serverUrl
            it[NAVIDROME_USER] = config.username
            it[NAVIDROME_PASS] = config.password
        }
    }

    suspend fun saveAudiobookConfig(config: AudiobookShelfConfig) {
        context.dataStore.edit {
            it[AUDIOBOOK_URL] = config.serverUrl
            it[AUDIOBOOK_USER] = config.username
            it[AUDIOBOOK_PASS] = config.password
        }
    }

    suspend fun saveVideoConfig(config: VideoServerConfig) {
        context.dataStore.edit {
            it[VIDEO_TYPE] = config.type.name
            it[VIDEO_URL] = config.serverUrl
            it[VIDEO_USER] = config.username
            it[VIDEO_PASS] = config.password
            it[VIDEO_API_KEY] = config.apiKey
        }
    }
}
