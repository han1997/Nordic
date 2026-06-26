package com.nordic.mediahub.playback

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.nordic.mediahub.data.VideoPlaybackInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Stable
data class VideoPlaybackState(
    val playbackInfo: VideoPlaybackInfo? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionSeconds: Int = 0,
    val durationSeconds: Int = 0,
    val speed: Float = 1.0f,
    val errorMessage: String? = null
)

class VideoPlaybackEngine(
    context: Context,
    private val onPlaybackStart: (suspend (itemId: String, mediaSourceId: String, playSessionId: String, positionSeconds: Int) -> Unit)? = null,
    private val onPlaybackProgress: (suspend (itemId: String, mediaSourceId: String, playSessionId: String, positionSeconds: Int) -> Unit)? = null,
    private val onPlaybackStopped: (suspend (itemId: String, mediaSourceId: String, playSessionId: String, positionSeconds: Int) -> Unit)? = null
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val exoPlayer = ExoPlayer.Builder(appContext).build()
    private var positionUpdateJob: Job? = null
    private var progressReportJob: Job? = null

    private val _state = MutableStateFlow(VideoPlaybackState())
    val state: StateFlow<VideoPlaybackState> = _state.asStateFlow()
    val player: Player = exoPlayer

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            publishPlayerState()
            if (isPlaying) startPositionUpdates() else stopPositionUpdates()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            publishPlayerState()
            if (playbackState == Player.STATE_ENDED || !exoPlayer.isPlaying) {
                stopPositionUpdates()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            stopPositionUpdates()
            _state.update {
                it.copy(
                    isPlaying = false,
                    isBuffering = false,
                    errorMessage = "视频播放失败: ${error.localizedMessage ?: error.errorCodeName}"
                )
            }
        }
    }

    init {
        exoPlayer.addListener(playerListener)
    }

    fun play(playbackInfo: VideoPlaybackInfo) {
        _state.value = VideoPlaybackState(
            playbackInfo = playbackInfo,
            isBuffering = true,
            durationSeconds = playbackInfo.durationSeconds
        )
        exoPlayer.setMediaItem(playbackInfo.toMediaItem())
        exoPlayer.prepare()
        exoPlayer.play()
        publishPlayerState()
        val info = playbackInfo
        scope.launch { runCatching { onPlaybackStart?.invoke(info.itemId, info.mediaSourceId, info.playSessionId, 0) } }
        startProgressReporting()
    }

    fun togglePlayPause() {
        if (exoPlayer.currentMediaItem == null) return

        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            if (exoPlayer.playbackState == Player.STATE_ENDED) {
                exoPlayer.seekTo(0L)
            }
            if (exoPlayer.playbackState == Player.STATE_IDLE) {
                exoPlayer.prepare()
            }
            exoPlayer.play()
        }
        publishPlayerState()
    }

    fun seekTo(positionSeconds: Int) {
        exoPlayer.seekTo(positionSeconds.coerceAtLeast(0) * 1000L)
        publishPlayerState()
    }

    fun setSpeed(speed: Float) {
        exoPlayer.playbackParameters = PlaybackParameters(speed)
        publishPlayerState()
    }

    fun stop() {
        stopPositionUpdates()
        stopProgressReporting()
        val info = _state.value.playbackInfo
        val position = _state.value.positionSeconds
        exoPlayer.pause()
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        if (info != null) {
            scope.launch { runCatching { onPlaybackStopped?.invoke(info.itemId, info.mediaSourceId, info.playSessionId, position) } }
        }
        _state.value = VideoPlaybackState()
    }

    fun release() {
        stopPositionUpdates()
        stopProgressReporting()
        val info = _state.value.playbackInfo
        val position = _state.value.positionSeconds
        if (info != null) {
            scope.launch { runCatching { onPlaybackStopped?.invoke(info.itemId, info.mediaSourceId, info.playSessionId, position) } }
        }
        scope.cancel()
        exoPlayer.removeListener(playerListener)
        exoPlayer.release()
    }

    private fun startPositionUpdates() {
        stopPositionUpdates()
        positionUpdateJob = scope.launch {
            while (isActive) {
                publishPlayerState()
                delay(1000)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    private fun startProgressReporting() {
        stopProgressReporting()
        if (_state.value.playbackInfo == null) return
        progressReportJob = scope.launch {
            while (isActive) {
                delay(10_000)
                val currentInfo = _state.value.playbackInfo ?: break
                runCatching {
                    onPlaybackProgress?.invoke(
                        currentInfo.itemId, currentInfo.mediaSourceId, currentInfo.playSessionId,
                        _state.value.positionSeconds
                    )
                }
            }
        }
    }

    private fun stopProgressReporting() {
        progressReportJob?.cancel()
        progressReportJob = null
    }

    private fun publishPlayerState() {
        val playbackInfo = _state.value.playbackInfo ?: return
        val playerDuration = exoPlayer.duration
            .takeIf { it != C.TIME_UNSET }
            ?.coerceAtLeast(0L)
            ?.div(1000L)
            ?.toInt()
        val fallbackDuration = playbackInfo.durationSeconds.coerceAtLeast(0)

        _state.update {
            it.copy(
                playbackInfo = playbackInfo,
                isPlaying = exoPlayer.isPlaying,
                isBuffering = exoPlayer.playbackState == Player.STATE_BUFFERING,
                positionSeconds = (exoPlayer.currentPosition.coerceAtLeast(0L) / 1000L).toInt(),
                durationSeconds = (playerDuration ?: fallbackDuration).coerceAtLeast(fallbackDuration),
                speed = exoPlayer.playbackParameters.speed,
                errorMessage = when (exoPlayer.playbackState) {
                    Player.STATE_READY, Player.STATE_ENDED -> null
                    else -> it.errorMessage
                }
            )
        }
    }

    private fun VideoPlaybackInfo.toMediaItem(): MediaItem {
        return MediaItem.Builder()
            .setUri(streamUrl)
            .setMediaId(itemId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setDescription(overview)
                    .build()
            )
            .build()
    }
}
