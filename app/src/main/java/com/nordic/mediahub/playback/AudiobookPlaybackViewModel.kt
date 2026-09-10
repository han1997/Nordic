package com.nordic.mediahub.playback

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nordic.mediahub.data.AudiobookBookmark
import com.nordic.mediahub.data.AudiobookBookmarkRepository
import com.nordic.mediahub.data.AudiobookPlaybackSession
import com.nordic.mediahub.data.AudiobookShelfRepository
import com.nordic.mediahub.data.ConfigRepository
import com.nordic.mediahub.data.isReadyForAudiobookSync
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
    private fun bookmarkRepository(sourceId: String) = AudiobookBookmarkRepository(getApplication(), sourceId)

    val state: StateFlow<AudiobookPlaybackState> = engine.state

    private val _repository = MutableStateFlow<AudiobookShelfRepository?>(null)
    private val sessionRepository = MutableStateFlow<AudiobookShelfRepository?>(null)
    private var preferences = com.nordic.mediahub.data.AppPreferences()
    val repository: StateFlow<AudiobookShelfRepository?> = _repository.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _bookmarks = MutableStateFlow<List<AudiobookBookmark>>(emptyList())
    val bookmarks: StateFlow<List<AudiobookBookmark>> = _bookmarks.asStateFlow()

    private val _isPlayerVisible = MutableStateFlow(false)

    private var syncJob: Job? = null

    init {
        configRepository.preferences.onEach {
            preferences = it
            engine.applyPreferredSpeed(it.audiobookSpeed)
        }.launchIn(viewModelScope)
        configRepository.audiobookConfig
            .map { if (it.isReadyForAudiobookSync()) it else null }
            .distinctUntilChanged()
            .map { config -> config?.let { AudiobookShelfRepository(it) } }
            .onEach { _repository.value = it }
            .launchIn(viewModelScope)

        combine(state.map { it.session?.sessionId }, sessionRepository) { sessionId, repo ->
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
                        if (currentSession == null ||
                            currentSession.sessionId != initialSession.sessionId ||
                            currentState.errorMessage != null
                        ) {
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

        // Safety net: when an active session transitions from playing to paused,
        // push an immediate progress sync so a quick pause/close reports the
        // current absolute position without waiting for the 30s periodic loop.
        var lastIsPlaying = false
        engine.state
            .onEach { state ->
                val currentSession = state.session
                val nowPlaying = state.isPlaying
                val wasPlaying = lastIsPlaying
                lastIsPlaying = nowPlaying
                if (wasPlaying && !nowPlaying && currentSession != null && state.errorMessage == null) {
                    val repoInstance = sessionRepository.value
                    if (repoInstance != null) {
                        val position = resolveAudiobookProgressSyncBaselineSeconds(state.positionSeconds, currentSession)
                        viewModelScope.launch {
                            runCatching { repoInstance.syncProgress(currentSession, position, 0) }
                        }
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    fun setPlayerVisible(visible: Boolean) {
        _isPlayerVisible.value = visible
    }

    /**
     * Immediate one-shot progress sync from the current engine state. Used as
     * a lifecycle safety net (app backgrounded mid-playback) so the last
     * position is not lost to process death before the 30s periodic loop
     * fires. Playback continues in the background; failures are silent.
     */
    fun syncNow() {
        val currentState = engine.state.value
        val session = currentState.session ?: return
        val repo = sessionRepository.value ?: return
        if (currentState.errorMessage != null) return
        val position = resolveAudiobookProgressSyncBaselineSeconds(currentState.positionSeconds, session)
        viewModelScope.launch {
            runCatching { repo.syncProgress(session, position, 0) }
        }
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
                    sessionRepository.value = repo
                    engine.play(session)
                    refreshBookmarks()
                    onResult(Result.success(session))
                }
                .onFailure { error ->
                    _error.value = error.message ?: "启动有声书播放失败"
                    onResult(Result.failure(error))
                }
        }
    }

    fun closeAudiobookPlayback(
        onClosed: () -> Unit = {},
        onFailed: (message: String) -> Unit = {}
    ) {
        closeAudiobookPlaybackInternal(
            closeAnyway = false,
            onClosed = onClosed,
            onFailed = onFailed
        )
    }

    /**
     * Close audiobook playback without blocking on the AudiobookShelf close
     * sync. If the network is down (the usual reason the close sync fails),
     * the player must not trap the user: stop local playback, then try to
     * report the final position in the background and drop failures silently.
     */
    fun closeAudiobookPlaybackAnyway(
        onClosed: () -> Unit = {},
        onFailed: (message: String) -> Unit = {}
    ) {
        closeAudiobookPlaybackInternal(
            closeAnyway = true,
            onClosed = onClosed,
            onFailed = onFailed
        )
    }

    private fun closeAudiobookPlaybackInternal(
        closeAnyway: Boolean,
        onClosed: () -> Unit = {},
        onFailed: (message: String) -> Unit = {}
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
        val repo = sessionRepository.value

        if (session == null || repo == null) {
            _bookmarks.value = emptyList()
            engine.stop()
            onClosed()
            return
        }

        viewModelScope.launch {
            runCatching { repo.syncAndCloseSession(session, positionSeconds) }
                .onSuccess {
                    _bookmarks.value = emptyList()
                    engine.stop()
                    onClosed()
                }
                .onFailure { error ->
                    val message = error.message ?: "关闭有声书播放会话失败"
                    if (closeAnyway) {
                        // Stop local playback and close immediately so the user
                        // is not trapped by a slow network, then attempt a
                        // best-effort final sync in the background. Clear the
                        // error so the player layer does not stay open.
                        _error.value = null
                        _bookmarks.value = emptyList()
                        engine.stop()
                        onClosed()
                        viewModelScope.launch {
                            runCatching { repo.syncAndCloseSession(session, positionSeconds) }
                        }
                    } else {
                        _error.value = message
                        onFailed(message)
                    }
                }
        }
    }

    fun play(session: AudiobookPlaybackSession) {
        sessionRepository.value = _repository.value
        engine.play(session)
        refreshBookmarks()
    }

    fun stop() = engine.stop()

    fun seekTo(positionSeconds: Int) = engine.seekTo(positionSeconds)

    fun seekBackBy(intervalSeconds: Int = preferences.audiobookSkipBack) = engine.seekBackBy(intervalSeconds)

    fun seekForwardBy(intervalSeconds: Int = preferences.audiobookSkipForward) = engine.seekForwardBy(intervalSeconds)

    fun seekToPreviousChapter() = engine.seekToPreviousChapter()

    fun seekToNextChapter() = engine.seekToNextChapter()

    fun cyclePlaybackSpeed() { engine.cyclePlaybackSpeed(); setPlaybackSpeed(engine.state.value.playbackSpeed) }

    fun setPlaybackSpeed(speed: Float) {
        engine.setPlaybackSpeed(speed)
        viewModelScope.launch {
            runCatching { configRepository.updatePreferences { it.copy(audiobookSpeed = speed) } }
                .onFailure { _error.value = "有声书速度设置保存失败" }
        }
    }

    fun setSleepTimer(minutes: Int, atChapterEnd: Boolean = false) =
        engine.setSleepTimer(minutes, atChapterEnd)

    fun cancelSleepTimer() = engine.cancelSleepTimer()

    fun togglePlayPause() = engine.togglePlayPause()

    /**
     * Reload saved bookmarks for the currently active session's library item.
     * No-ops when no session is active.
     */
    fun refreshBookmarks() {
        val session = engine.state.value.session ?: return
        viewModelScope.launch {
            val rows = bookmarkRepository(session.sourceId).loadForItem(session.libraryItemId)
            if (engine.state.value.session?.let { it.sessionId == session.sessionId && it.sourceId == session.sourceId } == true) _bookmarks.value = rows
        }
    }

    /**
     * Add a bookmark at [positionSeconds] for the active session's library item.
     * Returns the updated bookmark list, or [Result.failure] when no session is active.
     */
    fun addBookmarkAtCurrentPosition(
        label: String = "",
        onResult: (Result<List<AudiobookBookmark>>) -> Unit = {}
    ) {
        val session = engine.state.value.session
        if (session == null) {
            onResult(Result.failure(IllegalStateException("没有正在播放的有声书")))
            return
        }
        val position = engine.state.value.positionSeconds.coerceAtLeast(0)
        viewModelScope.launch {
            runCatching { bookmarkRepository(session.sourceId).addBookmark(session.libraryItemId, position, label) }
                .onSuccess { bookmarks ->
                    if (engine.state.value.session?.let { it.sessionId == session.sessionId && it.sourceId == session.sourceId } == true) _bookmarks.value = bookmarks
                    onResult(Result.success(bookmarks))
                }
                .onFailure { error ->
                    _error.value = error.message ?: "添加书签失败"
                    onResult(Result.failure(error))
                }
        }
    }

    /**
     * Delete a saved bookmark by id.
     */
    fun deleteBookmark(
        bookmarkId: String,
        onResult: (Result<List<AudiobookBookmark>>) -> Unit = {}
    ) {
        val session = engine.state.value.session ?: return
        val libraryItemId = session.libraryItemId
        viewModelScope.launch {
            runCatching { bookmarkRepository(session.sourceId).deleteBookmark(bookmarkId) }
                .onSuccess { bookmarks ->
                    if (engine.state.value.session?.let { it.sessionId == session.sessionId && it.sourceId == session.sourceId } == true) {
                        _bookmarks.value = bookmarks.filter { it.libraryItemId == libraryItemId }
                    }
                    onResult(Result.success(bookmarks))
                }
                .onFailure { error ->
                    _error.value = error.message ?: "删除书签失败"
                    onResult(Result.failure(error))
                }
        }
    }

    override fun onCleared() {
        engine.release()
    }
}
