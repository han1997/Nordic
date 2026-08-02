package com.nordic.mediahub.playback

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nordic.mediahub.data.AudiobookPlaybackSession
import com.nordic.mediahub.data.AudiobookShelfRepository
import com.nordic.mediahub.data.ConfigRepository
import com.nordic.mediahub.data.isReadyForAudiobookSync
import com.nordic.mediahub.resolveAudiobookCloseFailurePresentation
import com.nordic.mediahub.resolveAudiobookProgressSyncBaselineSeconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch

class AudiobookPlaybackViewModel(application: Application) : AndroidViewModel(application) {
    private val engine = AudiobookPlaybackEngine(application)
    private val configRepository = ConfigRepository(application)

    val state: StateFlow<AudiobookPlaybackState> = engine.state

    private val _repository = MutableStateFlow<AudiobookShelfRepository?>(null)
    val repository: StateFlow<AudiobookShelfRepository?> = _repository.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isPlayerVisible = MutableStateFlow(false)

    private var syncJob: Job? = null

    init {
        configRepository.audiobookConfig
            .map { if (it.isReadyForAudiobookSync()) it else null }
            .distinctUntilChanged()
            .map { config -> config?.let { AudiobookShelfRepository(it) } }
            .onEach { _repository.value = it }
            .launchIn(viewModelScope)

        combine(state.map { it.session?.sessionId }, _repository) { sessionId, repo ->
            sessionId to repo
        }.distinctUntilChanged().onEach { (_, repo) ->
            syncJob?.cancel()
            val initialSession = engine.state.value.session ?: return@onEach
            val repoInstance = repo ?: return@onEach
            val baseline = resolveAudiobookProgressSyncBaselineSeconds(
                statePositionSeconds = engine.state.value.positionSeconds,
                session = initialSession
            )
            syncJob = viewModelScope.launch {
                runPeriodicProgressSync(
                    initialBaselineSeconds = baseline,
                    nextStep = {
                        val currentState = engine.state.value
                        val currentSession = currentState.session
                        if (currentSession == null || currentSession.sessionId != initialSession.sessionId) {
                            null
                        } else {
                            PeriodicSyncStep(
                                positionSeconds = currentState.positionSeconds,
                                isPlaying = currentState.isPlaying,
                                deltaSeconds = null,
                                doSync = { position, _, delta ->
                                    repoInstance.syncProgress(currentSession, position, delta)
                                }
                            )
                        }
                    },
                    onFailure = { error ->
                        if (_isPlayerVisible.value) {
                            _error.value = error.message ?: "同步有声书进度失败"
                        }
                    }
                )
            }
        }.launchIn(viewModelScope)
    }

    fun setPlayerVisible(visible: Boolean) {
        _isPlayerVisible.value = visible
    }

    fun setError(message: String) {
        _error.value = message
    }

    fun clearError() {
        _error.value = null
    }

    fun startPlayback(libraryItemId: String, onResult: (Result<AudiobookPlaybackSession>) -> Unit) {
        val repo = _repository.value
        if (repo == null) {
            _error.value = "未配置 AudiobookShelf"
            onResult(Result.failure(IllegalStateException("未配置 AudiobookShelf")))
            return
        }
        _error.value = null
        viewModelScope.launch {
            runCatching { repo.startPlayback(libraryItemId) }
                .onSuccess { session ->
                    engine.play(session)
                    onResult(Result.success(session))
                }
                .onFailure { error ->
                    _error.value = error.message ?: "启动有声书播放失败"
                    onResult(Result.failure(error))
                }
        }
    }

    fun closeAudiobookPlayback(
        reopenPlayerOnFailure: Boolean = false,
        onClosed: () -> Unit = {},
        onFailed: (reopenPlayer: Boolean) -> Unit = {}
    ) {
        _error.value = null
        val currentState = engine.state.value
        val session = currentState.session
        val positionSeconds = if (session != null) {
            resolveAudiobookProgressSyncBaselineSeconds(
                statePositionSeconds = currentState.positionSeconds,
                session = session
            )
        } else {
            currentState.positionSeconds.coerceAtLeast(0)
        }
        val repo = _repository.value

        if (session == null || repo == null) {
            engine.stop()
            onClosed()
            return
        }

        if (!reopenPlayerOnFailure) {
            engine.stop()
            viewModelScope.launch {
                runCatching { repo.syncAndCloseSession(session, positionSeconds) }
                    .onSuccess { onClosed() }
                    .onFailure { error ->
                        val presentation = resolveAudiobookCloseFailurePresentation(
                            closeFailureMessage = error.message ?: "关闭有声书播放会话失败",
                            reopenPlayerOnFailure = false
                        )
                        if (presentation.errorMessage != null) _error.value = presentation.errorMessage
                        onFailed(presentation.showPlayer)
                    }
            }
        } else {
            viewModelScope.launch {
                runCatching { repo.syncAndCloseSession(session, positionSeconds) }
                    .onSuccess {
                        engine.stop()
                        onClosed()
                    }
                    .onFailure { error ->
                        val presentation = resolveAudiobookCloseFailurePresentation(
                            closeFailureMessage = error.message ?: "关闭有声书播放会话失败",
                            reopenPlayerOnFailure = true
                        )
                        if (presentation.errorMessage != null) _error.value = presentation.errorMessage
                        onFailed(presentation.showPlayer)
                    }
            }
        }
    }

    fun play(session: AudiobookPlaybackSession) = engine.play(session)

    fun stop() = engine.stop()

    fun seekTo(positionSeconds: Int) = engine.seekTo(positionSeconds)

    fun seekBackBy(intervalSeconds: Int = 30) = engine.seekBackBy(intervalSeconds)

    fun seekForwardBy(intervalSeconds: Int = 30) = engine.seekForwardBy(intervalSeconds)

    fun seekToPreviousChapter() = engine.seekToPreviousChapter()

    fun seekToNextChapter() = engine.seekToNextChapter()

    fun cyclePlaybackSpeed() = engine.cyclePlaybackSpeed()

    fun togglePlayPause() = engine.togglePlayPause()

    override fun onCleared() {
        engine.release()
    }
}
