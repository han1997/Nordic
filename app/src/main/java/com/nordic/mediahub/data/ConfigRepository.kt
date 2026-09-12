package com.nordic.mediahub.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.flowOn

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
private val sourceDataMigrationMutex = Mutex()
private val configurationReload = MutableStateFlow(0)
@OptIn(ExperimentalCoroutinesApi::class)
private fun <T> reloadableConfiguration(source: Flow<T>): Flow<T> = configurationReload.flatMapLatest { source }

@OptIn(ExperimentalCoroutinesApi::class)
class ConfigRepository(context: Context) {
    private val appContext = context.applicationContext
    private val store = EncryptedConfigStore(appContext)
    private val _storageError = MutableStateFlow<String?>(null)
    val storageError = _storageError.asStateFlow()
    val sourceState: Flow<MediaSourceState> = reloadableConfiguration(store.sources.onEach { state ->
        _storageError.value = null
        try { sourceDataMigrationMutex.withLock {
            migrateLegacySourceCaches(appContext.dataStore, state)
            val marker = booleanPreferencesKey("source_download_metadata_hygiene_v1")
            if (appContext.dataStore.data.first()[marker] != true) {
                withContext(Dispatchers.IO) { sanitizeLegacyMusicMetadata(musicDownloadRoot(appContext)) }
                appContext.dataStore.edit { it[marker] = true }
            }
        } }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { _storageError.value = "旧数据安全迁移未完成，请检查存储空间后重试" }
    }.catch { error ->
        if (error is CancellationException) throw error
        _storageError.value = error.message ?: "服务器配置读取失败，原数据已保留"
        emit(MediaSourceState())
    }.flowOn(Dispatchers.IO))
    val preferences: Flow<AppPreferences> = reloadableConfiguration(store.preferences.onEach { _storageError.value = null }.catch { error ->
        if (error is CancellationException) throw error
        _storageError.value = error.message ?: "设置读取失败"
        emit(AppPreferences())
    })
    val navidromeConfig: Flow<NavidromeConfig> = sourceState.map {
        it.active(MediaDomain.MUSIC)?.navidromeConfig() ?: NavidromeConfig()
    }.distinctUntilChanged()
    val audiobookConfig: Flow<AudiobookShelfConfig> = sourceState.map {
        it.active(MediaDomain.AUDIOBOOK)?.audiobookConfig() ?: AudiobookShelfConfig()
    }.distinctUntilChanged()
    val videoConfig: Flow<VideoServerConfig> = sourceState.map {
        it.active(MediaDomain.VIDEO)?.videoConfig() ?: VideoServerConfig()
    }.distinctUntilChanged()
    val lastAudiobookItemId: Flow<String?> = audiobookConfig.flatMapLatest {
        if (it.sourceId.isBlank()) flowOf(null) else store.lastAudiobookItem(it.sourceId)
    }
    val videoPlaybackSpeed: Flow<Float?> = preferences.map { it.videoSpeed }.distinctUntilChanged()
    val videoPipEnabled: Flow<Boolean> = preferences.map { it.videoPip }.distinctUntilChanged()
    val videoAutoPlayNext: Flow<Boolean> = preferences.map { it.videoAutoPlayNext }.distinctUntilChanged()
    val videoAutoSkipIntro: Flow<Boolean> = preferences.map { it.videoAutoSkipIntro }.distinctUntilChanged()
    val videoQualityMode: Flow<VideoQualityMode> = preferences.map { it.videoQuality }.distinctUntilChanged()

    suspend fun saveSource(draft: MediaSource): MediaSource {
        var saved = draft
        var replacedId: String? = null
        store.updateSources {
            val result = saveMediaSource(it, draft)
            if (result.source.id != draft.id && it.sources.any { source -> source.id == draft.id }) replacedId = draft.id
            saved = result.source
            result.state
        }
        replacedId?.let { ScopedMediaRegistry.revoke(it); MusicDownloadManagers.cancel(it) }
        return saved
    }
    suspend fun selectSource(domain: MediaDomain, id: String) {
        store.updateSources { it.select(domain, id) }
    }
    suspend fun deleteSource(id: String) {
        store.updateSources { it.remove(id) }
        ScopedMediaRegistry.revoke(id)
        MusicDownloadManagers.cancel(id)
    }
    suspend fun updatePreferences(transform: (AppPreferences) -> AppPreferences) = store.updatePreferences(transform)
    suspend fun resetPreferences() { store.resetPreferences(); _storageError.value = null; configurationReload.update { it + 1 } }

    suspend fun saveNavidromeConfig(config: NavidromeConfig) {
        val current = sourceState.first().active(MediaDomain.MUSIC)
        saveSource((current ?: MediaSource(kind = MediaSourceKind.NAVIDROME)).copy(
            serverUrl = config.serverUrl, username = config.username, password = config.password,
            allowInsecureHttp = current?.allowInsecureHttp == true))
    }
    suspend fun saveAudiobookConfig(config: AudiobookShelfConfig) {
        val current = sourceState.first().active(MediaDomain.AUDIOBOOK)
        saveSource((current ?: MediaSource(kind = MediaSourceKind.AUDIOBOOKSHELF)).copy(
            serverUrl = config.serverUrl, username = config.username, password = config.password,
            allowInsecureHttp = current?.allowInsecureHttp == true))
    }
    suspend fun saveVideoConfig(config: VideoServerConfig) {
        require(config.type != VideoServerType.PLEX) { "Plex 尚未接入" }
        val current = sourceState.first().active(MediaDomain.VIDEO)
        saveSource((current ?: MediaSource()).copy(
            kind = if (config.type == VideoServerType.WEBDAV) MediaSourceKind.WEBDAV else MediaSourceKind.EMBY,
            serverUrl = config.serverUrl, username = config.username, password = config.password, apiKey = config.apiKey,
            startDirectory = config.startDirectory,
            allowInsecureHttp = current?.allowInsecureHttp == true || config.allowInsecureHttp))
    }
    suspend fun saveLastAudiobookItemId(itemId: String) {
        audiobookConfig.first().sourceId.takeIf { it.isNotBlank() }?.let { store.saveLastAudiobookItem(it, itemId) }
    }
    suspend fun saveVideoPlaybackSpeed(speed: Float) = updatePreferences { it.copy(videoSpeed = speed) }
    suspend fun saveVideoPipEnabled(enabled: Boolean) = updatePreferences { it.copy(videoPip = enabled) }
    suspend fun saveVideoAutoPlayNext(enabled: Boolean) = updatePreferences { it.copy(videoAutoPlayNext = enabled) }
    suspend fun saveVideoAutoSkipIntro(enabled: Boolean) = updatePreferences { it.copy(videoAutoSkipIntro = enabled) }
    suspend fun saveVideoQualityMode(mode: VideoQualityMode) = updatePreferences { it.copy(videoQuality = mode) }
}

internal fun String?.toVideoServerType(): VideoServerType {
    val normalizedType = this?.trim()?.uppercase()
    return normalizedType?.let { raw -> VideoServerType.entries.firstOrNull { it.name == raw } } ?: VideoServerType.EMBY
}