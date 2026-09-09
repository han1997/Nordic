package com.nordic.mediahub.playback

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nordic.mediahub.data.ConfigRepository
import com.nordic.mediahub.data.EmbyRepository
import com.nordic.mediahub.data.VideoItem
import com.nordic.mediahub.data.VideoQualityMode
import com.nordic.mediahub.data.isReadyForVideoSync
import com.nordic.mediahub.data.resolveVideoPlaybackStreamUrl
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

    /** Picture-in-picture preference; enabled by default, persisted. */
    private val _pipEnabled = MutableStateFlow(true)
    val pipEnabled: StateFlow<Boolean> = _pipEnabled.asStateFlow()

    /** Auto intro-skip preference; enabled by default, persisted. */
    private val _autoSkipIntro = MutableStateFlow(true)
    val autoSkipIntro: StateFlow<Boolean> = _autoSkipIntro.asStateFlow()

    /** Video quality preference; AUTO (direct play) by default, persisted. */
    private val _qualityMode = MutableStateFlow(VideoQualityMode.AUTO)
    val qualityMode: StateFlow<VideoQualityMode> = _qualityMode.asStateFlow()

    /**
     * Server-issued identity for the current transcoded session, carried on
     * progress/stopped reports. Null during direct play.
     */
    @Volatile
    private var activePlaySessionId: String? = null

    /** Prevents concurrent quality handshakes for the same play request. */
    private var qualityHandshakeJob: Job? = null

    fun setEpisodeContext(videos: List<VideoItem>) {
        _catalogVideos.value = videos
    }

    /**
     * Immediate one-shot progress sync from the current engine state. Used as
     * a lifecycle safety net (e.g. app backgrounded mid-playback) so the last
     * position is not lost to process death before the 30s periodic loop
     * fires. Fire-and-forget; failures land on [syncError].
     */
    fun syncNow() {
        val currentState = engine.state.value
        val video = currentState.video
        val repo = _repository.value
        if (video == null || repo == null || video.streamUrl.isNullOrBlank() || currentState.errorMessage != null) {
            return
        }
        val position = resolveVideoProgressSyncBaselineSeconds(currentState.positionSeconds, video)
        viewModelScope.launch {
            runCatching {
                repo.syncPlaybackProgress(
                    video, position,
                    isPaused = !currentState.isPlaying,
                    playSessionId = activePlaySessionId
                )
            }.onFailure { error ->
                _syncError.value = error.message ?: "同步视频进度失败"
                Log.e("VideoPlayback", "即时同步视频进度失败", error)
            }
        }
    }

    private var syncJob: Job? = null

    init {
        configRepository.videoConfig
            .map { if (it.isReadyForVideoSync()) it else null }
            .distinctUntilChanged()
            .map { config -> config?.let { EmbyRepository(it) } }
            .onEach { _repository.value = it }
            .launchIn(viewModelScope)

        // Restore the persisted playback speed once at startup so every new
        // media item starts at the user's preferred rate.
        viewModelScope.launch {
            configRepository.videoPlaybackSpeed.collect { speed ->
                if (speed != null) engine.applyPersistedPlaybackSpeed(speed)
            }
        }

        // Restore the persisted picture-in-picture preference.
        viewModelScope.launch {
            configRepository.videoPipEnabled.collect { enabled ->
                _pipEnabled.value = enabled
            }
        }

        // Restore the persisted auto intro-skip preference and push it to the
        // engine so the skip decision follows the current setting.
        viewModelScope.launch {
            configRepository.videoAutoSkipIntro.collect { enabled ->
                _autoSkipIntro.value = enabled
                engine.applyAutoSkipIntro(enabled)
            }
        }

        // Restore the persisted video quality mode; takes effect on next play.
        viewModelScope.launch {
            configRepository.videoQualityMode.collect { mode ->
                _qualityMode.value = mode
            }
        }

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
                                        isPaused = !isPlaying,
                                        playSessionId = activePlaySessionId
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
                                repoInstance.syncPlaybackProgress(
                                    video, position,
                                    isPaused = true,
                                    playSessionId = activePlaySessionId
                                )
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

    /**
     * Close video playback. Local playback stops and the player dismisses
     * immediately; the Emby stopped-progress report runs in the background
     * (best-effort with one retry) so a slow server can never trap the user
     * in the player.
     */
    fun closeVideoPlayback(
        onClosed: () -> Unit = {},
        onFailed: (message: String) -> Unit = {}
    ) {
        closeVideoPlaybackInternal(
            onClosed = onClosed,
            onFailed = onFailed
        )
    }

    /**
     * Close video playback ignoring sync failures entirely. Kept for call
     * sites that need an explicit "close no matter what" semantic; behaves
     * the same as [closeVideoPlayback] since closing never blocks on sync.
     */
    fun closeVideoPlaybackAnyway(
        onClosed: () -> Unit = {},
        onFailed: (message: String) -> Unit = {}
    ) {
        closeVideoPlaybackInternal(
            onClosed = onClosed,
            onFailed = onFailed
        )
    }

    private fun closeVideoPlaybackInternal(
        onClosed: () -> Unit = {},
        onFailed: (message: String) -> Unit = {}
    ) {
        _error.value = null
        _syncError.value = null
        val currentState = engine.state.value
        val video = currentState.video
        val repo = _repository.value
        if (video != null) {
            _catalogVideos.value = updateVideoEpisodeProgress(
                _catalogVideos.value, video, currentState.positionSeconds
            )
        }

        // Closing must never block on the network: stop local playback and
        // dismiss the player immediately, then report the stopped position in
        // the background (best-effort, one retry). A slow/unreachable Emby
        // server previously kept the player on screen for the whole HTTP
        // round-trip, which felt like the app was frozen on close.
        if (video != null && repo != null && !video.streamUrl.isNullOrBlank()) {
            val positionSeconds = resolveVideoProgressSyncBaselineSeconds(
                statePositionSeconds = currentState.positionSeconds,
                video = video
            )
            engine.stop()
            onClosed()
            val closedPlaySessionId = activePlaySessionId
            activePlaySessionId = null
            viewModelScope.launch {
                runCatching { repo.stopPlaybackProgress(video, positionSeconds, closedPlaySessionId) }
                    .onFailure { firstError ->
                        Log.e("VideoPlayback", "保存视频进度失败，后台重试一次", firstError)
                        runCatching { repo.stopPlaybackProgress(video, positionSeconds, closedPlaySessionId) }
                            .onFailure { retryError ->
                                Log.e("VideoPlayback", "重试保存视频进度失败", retryError)
                                _syncError.value = retryError.message ?: "保存视频进度失败"
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
        startPlaybackWithQuality(video, fromStart = false)
    }

    fun playFromStart(video: VideoItem) {
        _error.value = null
        startPlaybackWithQuality(video, fromStart = true)
    }

    /**
     * Starts playback honoring the quality mode. AUTO/ORIGINAL play the direct
     * stream immediately; bitrate tiers run a PlaybackInfo handshake first and
     * play the transcoded HLS stream (falling back to direct on handshake
     * failure). The handshake never blocks playback start by more than the
     * request itself; failures degrade to direct play.
     */
    private fun startPlaybackWithQuality(video: VideoItem, fromStart: Boolean) {
        val mode = _qualityMode.value
        val bitrate = mode.bitrateBps
        val repo = _repository.value
        if (bitrate == null || repo == null || video.streamUrl.isNullOrBlank()) {
            activePlaySessionId = null
            if (fromStart) engine.playFromStart(video) else engine.play(video)
            return
        }

        qualityHandshakeJob?.cancel()
        qualityHandshakeJob = viewModelScope.launch {
            val session = runCatching { repo.getPlaybackInfo(video, bitrate) }
                .onFailure { error ->
                    Log.e("VideoPlayback", "PlaybackInfo 握手失败，回退直连播放", error)
                }
                .getOrNull()
            val transcodeUrl = session?.let {
                resolveVideoPlaybackStreamUrl(
                    video = video,
                    mode = mode,
                    baseUrl = repoBaseUrl(repo),
                    playbackSession = it
                )
            }
            if (session != null && transcodeUrl != null && transcodeUrl != video.streamUrl) {
                activePlaySessionId = session.playSessionId
                engine.playTranscoded(
                    video = video,
                    transcodeUrl = transcodeUrl,
                    directUrl = video.streamUrl.orEmpty()
                )
            } else {
                activePlaySessionId = null
                if (fromStart) engine.playFromStart(video) else engine.play(video)
            }
        }
    }

    private fun repoBaseUrl(repo: EmbyRepository): String = repo.baseUrlForStreamUrls()

    fun setQualityMode(mode: VideoQualityMode) {
        _qualityMode.value = mode
        viewModelScope.launch { configRepository.saveVideoQualityMode(mode) }
    }

    fun stop() {
        _catalogVideos.value = emptyList()
        activePlaySessionId = null
        engine.stop()
    }

    fun seekTo(positionSeconds: Int) = engine.seekTo(positionSeconds)

    fun seekBackBy(intervalSeconds: Int = 10) = engine.seekBackBy(intervalSeconds)

    fun seekForwardBy(intervalSeconds: Int = 30) = engine.seekForwardBy(intervalSeconds)

    fun togglePlayPause() = engine.togglePlayPause()

    fun cycleAspectRatio() = engine.cycleAspectRatio()

    fun setPlaybackSpeed(speed: Float) {
        engine.setPlaybackSpeed(speed)
        viewModelScope.launch { configRepository.saveVideoPlaybackSpeed(speed) }
    }

    fun setPreferredTextTrack(stream: com.nordic.mediahub.data.VideoStreamInfo?) =
        engine.setPreferredTextTrack(stream)

    fun setPreferredAudioTrack(stream: com.nordic.mediahub.data.VideoStreamInfo?) =
        engine.setPreferredAudioTrack(stream)

    fun attachSubtitleView(view: androidx.media3.ui.SubtitleView?) = engine.setSubtitleView(view)

    fun setPipEnabled(enabled: Boolean) {
        _pipEnabled.value = enabled
        viewModelScope.launch { configRepository.saveVideoPipEnabled(enabled) }
    }

    fun setAutoSkipIntro(enabled: Boolean) {
        _autoSkipIntro.value = enabled
        engine.applyAutoSkipIntro(enabled)
        viewModelScope.launch { configRepository.saveVideoAutoSkipIntro(enabled) }
    }

    fun skipIntro() = engine.skipIntro()

    fun attachSurface(surfaceView: android.view.SurfaceView) = engine.attachSurface(surfaceView)

    fun detachSurface(surfaceView: android.view.SurfaceView) = engine.detachSurface(surfaceView)

    override fun onCleared() {
        engine.release()
    }
}

/** Keep the in-player picker current even before the background Emby report/catalog refresh completes. */
internal fun updateVideoEpisodeProgress(
    videos: List<VideoItem>,
    current: VideoItem,
    positionSeconds: Int
): List<VideoItem> {
    if (!current.type.equals("Episode", ignoreCase = true)) return videos
    val snapshot = current.copy(
        playbackPositionSeconds = resolveVideoProgressSyncBaselineSeconds(positionSeconds, current)
    )
    return (videos.filterNot { it.id == current.id && it.libraryId == current.libraryId } + snapshot)
}
