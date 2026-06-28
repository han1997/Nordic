package com.nordic.mediahub.playback

import android.content.Context
import android.view.SurfaceView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.nordic.mediahub.data.VideoItem
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

data class VideoPlaybackState(
    val video: VideoItem? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionSeconds: Int = 0,
    val durationSeconds: Int = 0,
    val errorMessage: String? = null
)

class VideoPlaybackEngine(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val player = ExoPlayer.Builder(appContext).build()
    private var positionUpdateJob: Job? = null

    private val _state = MutableStateFlow(VideoPlaybackState())
    val state: StateFlow<VideoPlaybackState> = _state.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            publishPlayerState()
            if (isPlaying) startPositionUpdates() else stopPositionUpdates()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            publishPlayerState()
            if (!player.isPlaying) {
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
        player.addListener(playerListener)
    }

    fun attachSurface(surfaceView: SurfaceView) {
        player.setVideoSurfaceView(surfaceView)
    }

    fun detachSurface(surfaceView: SurfaceView) {
        player.clearVideoSurfaceView(surfaceView)
    }

    fun play(video: VideoItem) {
        if (video.streamUrl.isNullOrBlank()) {
            _state.value = VideoPlaybackState(
                video = video,
                durationSeconds = video.durationSeconds,
                errorMessage = "这个视频缺少播放地址"
            )
            return
        }

        if (_state.value.video?.id != video.id) {
            _state.value = VideoPlaybackState(
                video = video,
                durationSeconds = video.durationSeconds,
                isBuffering = true
            )
            player.setMediaItem(video.toMediaItem())
            player.prepare()
        } else {
            _state.update { it.copy(errorMessage = null) }
        }

        if (player.playbackState == Player.STATE_ENDED) {
            player.seekTo(0L)
        }
        if (player.playbackState == Player.STATE_IDLE) {
            player.prepare()
        }
        player.play()
        publishPlayerState()
    }

    fun togglePlayPause() {
        val video = _state.value.video ?: return
        if (video.streamUrl.isNullOrBlank()) return

        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_ENDED) {
                player.seekTo(0L)
            }
            if (player.playbackState == Player.STATE_IDLE) {
                player.setMediaItem(video.toMediaItem())
                player.prepare()
            }
            player.play()
        }
        publishPlayerState()
    }

    fun seekTo(positionSeconds: Int) {
        player.seekTo(positionSeconds.coerceAtLeast(0) * 1000L)
        publishPlayerState()
    }

    fun stop() {
        stopPositionUpdates()
        player.pause()
        player.stop()
        player.clearMediaItems()
        _state.value = VideoPlaybackState()
    }

    fun release() {
        stopPositionUpdates()
        scope.cancel()
        player.removeListener(playerListener)
        player.release()
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

    private fun publishPlayerState() {
        val video = _state.value.video ?: return
        val playerDuration = player.duration
            .takeIf { duration -> duration != C.TIME_UNSET }
            ?.coerceAtLeast(0L)

        _state.update {
            it.copy(
                video = video,
                isPlaying = player.isPlaying,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                positionSeconds = (player.currentPosition.coerceAtLeast(0L) / 1000L).toInt(),
                durationSeconds = (playerDuration?.div(1000L)?.toInt() ?: video.durationSeconds)
                    .coerceAtLeast(video.durationSeconds),
                errorMessage = when (player.playbackState) {
                    Player.STATE_READY, Player.STATE_ENDED -> null
                    else -> it.errorMessage
                }
            )
        }
    }
}

private fun VideoItem.toMediaItem(): MediaItem {
    return MediaItem.Builder()
        .setUri(streamUrl)
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setDescription(overview)
                .build()
        )
        .build()
}
