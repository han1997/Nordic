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
                            currentVideo.streamUrl.isNullOrBlank()
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
                        _error.value = error.message ?: "同步视频进度失败"
                        Log.e("VideoPlayback", "同步视频进度失败", error)
                    }
                )
            }
        }.launchIn(viewModelScope)
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
        _error.value = null
        val currentState = engine.state.value
        val video = currentState.video
        val repo = _repository.value

        onClosed()

        if (video != null && repo != null && !video.streamUrl.isNullOrBlank()) {
            val positionSeconds = resolveVideoProgressSyncBaselineSeconds(
                statePositionSeconds = currentState.positionSeconds,
                video = video
            )
            viewModelScope.launch {
                runCatching { repo.stopPlaybackProgress(video, positionSeconds) }
                    .onFailure { error ->
                        _error.value = error.message ?: "保存视频进度失败"
                        Log.e("VideoPlayback", "保存视频进度失败", error)
                        onFailed(error.message ?: "保存视频进度失败")
                    }
            }
        }
        engine.stop()
    }

    fun play(video: VideoItem) {
        _error.value = null
        engine.play(video)
    }

    fun stop() = engine.stop()

    fun seekTo(positionSeconds: Int) = engine.seekTo(positionSeconds)

    fun seekBackBy(intervalSeconds: Int = 10) = engine.seekBackBy(intervalSeconds)

    fun seekForwardBy(intervalSeconds: Int = 30) = engine.seekForwardBy(intervalSeconds)

    fun togglePlayPause() = engine.togglePlayPause()

    fun cycleAspectRatio() = engine.cycleAspectRatio()

    fun attachSurface(surfaceView: android.view.SurfaceView) = engine.attachSurface(surfaceView)

    fun detachSurface(surfaceView: android.view.SurfaceView) = engine.detachSurface(surfaceView)

    override fun onCleared() {
        engine.release()
    }
}
