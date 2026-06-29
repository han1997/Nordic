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

private const val VIDEO_SKIP_BACK_SECONDS = 10
private const val VIDEO_SKIP_FORWARD_SECONDS = 30

internal fun shouldReplaceCurrentVideoItem(
    currentVideo: VideoItem?,
    requestedVideo: VideoItem
): Boolean {
    if (currentVideo == null) return true

    return currentVideo.id != requestedVideo.id ||
        currentVideo.streamUrl.orEmpty() != requestedVideo.streamUrl.orEmpty()
}

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

        if (shouldReplaceCurrentVideoItem(_state.value.video, video)) {
            _state.value = VideoPlaybackState(
                video = video,
                durationSeconds = video.durationSeconds,
                isBuffering = true
            )
            player.setMediaItem(video.toMediaItem())
            player.prepare()
            val startPositionMs = resolveVideoInitialStartPositionMs(video)
            if (startPositionMs > 0L) {
                player.seekTo(startPositionMs)
            }
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

    fun seekBackBy(intervalSeconds: Int = VIDEO_SKIP_BACK_SECONDS) {
        seekBy(-intervalSeconds)
    }

    fun seekForwardBy(intervalSeconds: Int = VIDEO_SKIP_FORWARD_SECONDS) {
        seekBy(intervalSeconds)
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

    private fun seekBy(deltaSeconds: Int) {
        val state = _state.value
        if (state.video == null) return
        seekTo(
            resolveVideoRelativeSeekPositionSeconds(
                positionSeconds = state.positionSeconds,
                durationSeconds = state.durationSeconds,
                deltaSeconds = deltaSeconds
            )
        )
    }
}

internal fun resolveVideoInitialStartPositionMs(video: VideoItem): Long {
    if (video.isPlayed) return 0L

    val resumeSeconds = video.playbackPositionSeconds.coerceAtLeast(0)
    if (resumeSeconds <= 0) return 0L

    val durationSeconds = video.durationSeconds.coerceAtLeast(0)
    if (durationSeconds > 0 && resumeSeconds >= durationSeconds) return 0L

    return resumeSeconds * 1000L
}

internal fun resolveVideoRelativeSeekPositionSeconds(
    positionSeconds: Int,
    durationSeconds: Int,
    deltaSeconds: Int
): Int {
    if (durationSeconds > 0) {
        val maxPosition = durationSeconds
        val safePosition = positionSeconds.coerceIn(0, maxPosition)
        val target = safePosition.toLong() + deltaSeconds.toLong()
        return target.coerceIn(0L, maxPosition.toLong()).toInt()
    }

    val safePosition = positionSeconds.coerceAtLeast(0)
    val target = safePosition.toLong() + deltaSeconds.toLong()
    return target.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
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
