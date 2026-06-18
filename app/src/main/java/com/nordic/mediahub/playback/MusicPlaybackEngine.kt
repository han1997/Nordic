package com.nordic.mediahub.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.nordic.mediahub.api.NavidromeSong
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

data class MusicPlaybackState(
    val currentSong: NavidromeSong? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionSeconds: Int = 0,
    val durationSeconds: Int = 0,
    val errorMessage: String? = null,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<NavidromeSong> = emptyList(),
    val queueIndex: Int = 0
)

@androidx.annotation.OptIn(UnstableApi::class)
class MusicPlaybackEngine(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val sessionToken = SessionToken(
        appContext,
        ComponentName(appContext, MusicPlaybackService::class.java)
    )
    private val controllerFuture = MediaController.Builder(appContext, sessionToken).buildAsync()
    private var controller: MediaController? = null
    private var pendingSong: NavidromeSong? = null
    private var pendingQueue: List<NavidromeSong>? = null
    private var pendingQueueStartIndex: Int = 0
    private var positionUpdateJob: Job? = null
    private var cachedTimelineGeneration: Int = -1
    private var cachedQueue: List<NavidromeSong> = emptyList()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            publishPlayerState()
            if (isPlaying) startPositionUpdates() else stopPositionUpdates()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            publishPlayerState()
            val activeController = controller
            if (activeController != null && !activeController.isPlaying) {
                stopPositionUpdates()
            }
        }

        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            publishPlayerState()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            publishPlayerState()
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            publishPlayerState()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            publishPlayerState()
        }

        override fun onPlayerError(error: PlaybackException) {
            stopPositionUpdates()
            _state.update {
                it.copy(
                    currentSong = controller?.currentMediaItem?.toNavidromeSong() ?: it.currentSong,
                    isPlaying = false,
                    isBuffering = false,
                    errorMessage = "播放失败：${error.localizedMessage ?: error.errorCodeName}"
                )
            }
        }
    }

    private val _state = MutableStateFlow(MusicPlaybackState())
    val state: StateFlow<MusicPlaybackState> = _state.asStateFlow()

    init {
        controllerFuture.addListener(
            {
                runCatching {
                    controller = controllerFuture.get().also { it.addListener(playerListener) }
                    val song = pendingSong
                    val queue = pendingQueue
                    pendingSong = null
                    pendingQueue = null
                    if (song != null) {
                        play(song)
                    } else if (queue != null) {
                        playQueue(queue, pendingQueueStartIndex)
                    } else {
                        publishPlayerState()
                    }
                }.onFailure { error ->
                    _state.update {
                        it.copy(
                            isPlaying = false,
                            isBuffering = false,
                            errorMessage = "播放器连接失败：${error.message ?: error::class.java.simpleName}"
                        )
                    }
                }
            },
            ContextCompat.getMainExecutor(appContext)
        )
    }

    fun play(song: NavidromeSong) {
        if (song.streamUrl.isNullOrBlank()) {
            _state.value = MusicPlaybackState(
                currentSong = song,
                durationSeconds = song.duration,
                errorMessage = "这首歌缺少播放地址"
            )
            return
        }

        val activeController = controller
        if (activeController == null) {
            pendingSong = song
            _state.value = MusicPlaybackState(
                currentSong = song,
                durationSeconds = song.duration,
                isBuffering = true
            )
            return
        }

        if (activeController.currentMediaItem?.mediaId != song.id) {
            _state.value = MusicPlaybackState(
                currentSong = song,
                durationSeconds = song.duration,
                isBuffering = true
            )
            activeController.setMediaItem(song.toMediaItem())
            activeController.prepare()
        } else {
            _state.update { it.copy(errorMessage = null) }
        }

        if (activeController.playbackState == Player.STATE_ENDED) {
            activeController.seekTo(0L)
        }
        if (activeController.playbackState == Player.STATE_IDLE) {
            activeController.prepare()
        }
        activeController.play()
        publishPlayerState()
    }

    fun playQueue(songs: List<NavidromeSong>, startIndex: Int = 0) {
        if (songs.isEmpty()) return

        val activeController = controller
        if (activeController == null) {
            pendingQueue = songs
            pendingQueueStartIndex = startIndex
            val startSong = songs.getOrNull(startIndex)
            _state.update {
                it.copy(
                    currentSong = startSong,
                    isBuffering = true,
                    queue = songs,
                    queueIndex = startIndex.coerceIn(0, songs.lastIndex)
                )
            }
            return
        }

        val mediaItems = songs.map { it.toMediaItem() }
        val safeStartIndex = startIndex.coerceIn(0, songs.lastIndex)
        activeController.setMediaItems(mediaItems, safeStartIndex, 0L)
        activeController.prepare()
        activeController.play()
        publishPlayerState()
    }

    fun seekToNext() {
        controller?.seekToNext()
        publishPlayerState()
    }

    fun seekToPrevious() {
        controller?.seekToPrevious()
        publishPlayerState()
    }

    fun toggleRepeatMode() {
        val activeController = controller ?: return
        val nextMode = when (activeController.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
        activeController.repeatMode = nextMode
        publishPlayerState()
    }

    fun seekToQueueIndex(index: Int) {
        controller?.seekToDefaultPosition(index)
        controller?.play()
        publishPlayerState()
    }

    fun togglePlayPause() {
        val activeController = controller
        if (activeController == null) {
            _state.value.currentSong?.let { pendingSong = it }
            return
        }

        val song = _state.value.currentSong ?: activeController.currentMediaItem?.toNavidromeSong()
        if (activeController.currentMediaItem == null) {
            song?.let(::play)
            return
        }

        if (activeController.isPlaying) {
            activeController.pause()
        } else {
            if (activeController.playbackState == Player.STATE_ENDED) {
                activeController.seekTo(0L)
            }
            if (activeController.playbackState == Player.STATE_IDLE) {
                activeController.prepare()
            }
            activeController.play()
        }
        publishPlayerState()
    }

    fun seekTo(positionSeconds: Int) {
        controller?.seekTo(positionSeconds.coerceAtLeast(0) * 1000L)
        publishPlayerState()
    }

    fun release() {
        stopPositionUpdates()
        scope.cancel()
        controller?.removeListener(playerListener)
        MediaController.releaseFuture(controllerFuture)
        controller = null
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
        val activeController = controller ?: return
        val current = activeController.currentMediaItem?.toNavidromeSong() ?: _state.value.currentSong
        val playerDuration = activeController.duration
            .takeIf { it != C.TIME_UNSET }
            ?.coerceAtLeast(0L)
        val fallbackDuration = current?.duration?.coerceAtLeast(0) ?: 0

        val timelineGeneration = activeController.currentTimeline.hashCode()
        if (timelineGeneration != cachedTimelineGeneration) {
            cachedTimelineGeneration = timelineGeneration
            cachedQueue = (0 until activeController.mediaItemCount).mapNotNull { index ->
                activeController.getMediaItemAt(index).toNavidromeSong()
            }
        }
        val currentIndex = activeController.currentMediaItemIndex

        _state.update {
            it.copy(
                currentSong = current,
                isPlaying = activeController.isPlaying,
                isBuffering = activeController.playbackState == Player.STATE_BUFFERING,
                positionSeconds = (activeController.currentPosition.coerceAtLeast(0L) / 1000L).toInt(),
                durationSeconds = (playerDuration?.div(1000L)?.toInt() ?: fallbackDuration)
                    .coerceAtLeast(fallbackDuration),
                errorMessage = when (activeController.playbackState) {
                    Player.STATE_READY, Player.STATE_ENDED -> null
                    else -> it.errorMessage
                },
                repeatMode = activeController.repeatMode,
                queue = cachedQueue,
                queueIndex = currentIndex
            )
        }
    }
}
