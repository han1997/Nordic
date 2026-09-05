package com.nordic.mediahub.playback

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nordic.mediahub.data.ConfigRepository
import com.nordic.mediahub.data.EmbyRepository
import com.nordic.mediahub.data.VideoItem
import com.nordic.mediahub.data.isReadyForVideoSync
import com.nordic.mediahub.resolveVideoProgressSyncBaselineSeconds
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

class VideoPlaybackViewModel(application: Application) : AndroidViewModel(application) {
    private val engine = VideoPlaybackEngine(application)
    private val configRepository = ConfigRepository(application)

    val state: StateFlow<VideoPlaybackState> = engine.state

    private val _repository = MutableStateFlow<EmbyRepository?>(null)
    val repository: StateFlow<EmbyRepository?> = _repository.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * Progress-sync failures (network, auth) reported here, separate from the
     * player [error] channel: a failed progress report must never surface as
     * "播放异常" inside the player UI.
     */
    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    /**
     * In-memory episode context: the catalog list captured when playback was
     * started from the browse screen, used to resolve the "next episode"
     * target. Not persisted; cleared on stop.
     */
    private val _catalogVideos = MutableStateFlow<List<VideoItem>>(emptyList())
    val catalogVideos: StateFlow<List<VideoItem>> = _catalogVideos.asStateFlow()

    fun setEpisodeContext(videos: List<VideoItem>) {
        _catalogVideos.value = videos
    }

    private var syncJob: Job? = null

    init {
        configRepository.videoConfig
            .map { if (it.isReadyForVideoSync()) it else null }
            .distinctUntilChanged()
            .map { config -> config?.let { EmbyRepository(it) } }
            .onEach { _repository.value = it }
            .launchIn(viewModelScope)

        combine(state.map { it.video?.id }, _repository) { videoId, repo ->
            videoId to repo
        }.distinctUntilChanged().onEach { (_, repo) ->
            syncJob?.cancel()
            val initialVideo = engine.state.value.video ?: return@onEach
            val repoInstance = repo ?: return@onEach
            if (initialVideo.streamUrl.isNullOrBlank()) return@onEach
            val baseline = resolveVideoProgressSyncBaselineSeconds(
                statePositionSeconds = engine.state.value.positionSeconds,
                video = initialVideo
            )
            syncJob = viewModelScope.launch {
                runPeriodicProgressSync(
                    initialBaselineSeconds = baseline,
                    nextStep = {
                        val currentState = engine.state.value
                        val currentVideo = currentState.video
                        if (currentVideo == null ||
                            currentVideo.id != initialVideo.id ||
                            currentVideo.streamUrl.isNullOrBlank() ||
                            currentState.errorMessage != null
                        ) {
                            null
                        } else {
                            PeriodicSyncStep(
                                positionSeconds = currentState.positionSeconds,
                                isPlaying = currentState.isPlaying,
                                deltaSeconds = null,
                                doSync = { position, isPlaying, _ ->
                                    repoInstance.syncPlaybackProgress(
                                        video = currentVideo,
                                        positionSeconds = position,
                                        isPaused = !isPlaying
                                    )
                                }
                            )
                        }
                    },
                    onFailure = { error ->
                        _syncError.value = error.message ?: "同步视频进度失败"
                        Log.e("VideoPlayback", "同步视频进度失败", error)
                    }
                )
            }
        }.launchIn(viewModelScope)

        // Safety net: when an active, playable video transitions from playing to
        // paused, push an immediate progress sync so a quick open/pause session
        // reports position without waiting for the 30s periodic loop.
        var lastIsPlaying = false
        engine.state
            .onEach { state ->
                val video = state.video
                val nowPlaying = state.isPlaying
                val wasPlaying = lastIsPlaying
                lastIsPlaying = nowPlaying
                if (wasPlaying && !nowPlaying && video != null &&
                    !video.streamUrl.isNullOrBlank() && state.errorMessage == null
                ) {
                    val repoInstance = _repository.value
                    if (repoInstance != null) {
                        val position = resolveVideoProgressSyncBaselineSeconds(state.positionSeconds, video)
                        viewModelScope.launch {
                            runCatching {
                                repoInstance.syncPlaybackProgress(video, position, isPaused = true)
                            }.onFailure { error ->
                                _syncError.value = error.message ?: "同步视频进度失败"
                                Log.e("VideoPlayback", "暂停时同步视频进度失败", error)
                            }
                        }
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    fun setError(message: String) {
        _error.value = message
    }

    fun clearError() {
        _error.value = null
    }

    fun closeVideoPlayback(
        onClosed: () -> Unit = {},
        onFailed: (message: String) -> Unit = {}
    ) {
        closeVideoPlaybackInternal(
            closeAnyway = false,
            onClosed = onClosed,
            onFailed = onFailed
        )
    }

    /**
     * Close video playback without blocking on the Emby stopped-progress sync.
     * If the network is down, stop local playback so the user can leave, then
     * attempt a best-effort background stopped report and drop failures.
     */
    fun closeVideoPlaybackAnyway(
        onClosed: () -> Unit = {},
        onFailed: (message: String) -> Unit = {}
    ) {
        closeVideoPlaybackInternal(
            closeAnyway = true,
            onClosed = onClosed,
            onFailed = onFailed
        )
    }

    private fun closeVideoPlaybackInternal(
        closeAnyway: Boolean,
        onClosed: () -> Unit = {},
        onFailed: (message: String) -> Unit = {}
    ) {
        _error.value = null
        _syncError.value = null
        val currentState = engine.state.value
        val video = currentState.video
        val repo = _repository.value

        if (video != null && repo != null && !video.streamUrl.isNullOrBlank()) {
            val positionSeconds = resolveVideoProgressSyncBaselineSeconds(
                statePositionSeconds = currentState.positionSeconds,
                video = video
            )
            viewModelScope.launch {
                runCatching { repo.stopPlaybackProgress(video, positionSeconds) }
                    .onSuccess {
                        engine.stop()
                        onClosed()
                    }
                    .onFailure { error ->
                        Log.e("VideoPlayback", "保存视频进度失败", error)
                        if (closeAnyway) {
                            // Stop local playback and close immediately so the
                            // user is not trapped by a slow network, then attempt
                            // a best-effort background stopped report. Clear the
                            // error so the player layer does not stay open.
                            _error.value = null
                            _syncError.value = null
                            engine.stop()
                            onClosed()
                            viewModelScope.launch {
                                runCatching { repo.stopPlaybackProgress(video, positionSeconds) }
                            }
                        } else {
                            // Sync failure is not a player error: report it on the
                            // sync channel and still close (the close path must
                            // never trap the user in the player).
                            _syncError.value = error.message ?: "保存视频进度失败"
                            engine.stop()
                            onClosed()
                            viewModelScope.launch {
                                runCatching { repo.stopPlaybackProgress(video, positionSeconds) }
                            }
                        }
                    }
            }
        } else {
            engine.stop()
            onClosed()
        }
    }

    fun play(video: VideoItem) {
        _error.value = null
        engine.play(video)
    }

    fun playFromStart(video: VideoItem) {
        _error.value = null
        engine.playFromStart(video)
    }

    fun stop() {
        _catalogVideos.value = emptyList()
        engine.stop()
    }

    fun seekTo(positionSeconds: Int) = engine.seekTo(positionSeconds)

    fun seekBackBy(intervalSeconds: Int = 10) = engine.seekBackBy(intervalSeconds)

    fun seekForwardBy(intervalSeconds: Int = 30) = engine.seekForwardBy(intervalSeconds)

    fun togglePlayPause() = engine.togglePlayPause()

    fun cycleAspectRatio() = engine.cycleAspectRatio()

    fun setPlaybackSpeed(speed: Float) = engine.setPlaybackSpeed(speed)

    fun attachSurface(surfaceView: android.view.SurfaceView) = engine.attachSurface(surfaceView)

    fun detachSurface(surfaceView: android.view.SurfaceView) = engine.detachSurface(surfaceView)

    override fun onCleared() {
        engine.release()
    }
}
