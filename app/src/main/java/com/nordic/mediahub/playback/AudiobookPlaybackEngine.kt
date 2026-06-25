package com.nordic.mediahub.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.compose.runtime.Stable
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.nordic.mediahub.data.AudiobookAudioTrack
import com.nordic.mediahub.data.AudiobookChapter
import com.nordic.mediahub.data.AudiobookPlaybackSession
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
data class AudiobookPlaybackState(
    val session: AudiobookPlaybackSession? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionSeconds: Int = 0,
    val durationSeconds: Int = 0,
    val chapters: List<AudiobookChapter> = emptyList(),
    val errorMessage: String? = null
)

@androidx.annotation.OptIn(UnstableApi::class)
class AudiobookPlaybackEngine(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val sessionToken = SessionToken(
        appContext,
        ComponentName(appContext, MusicPlaybackService::class.java)
    )
    private val controllerFuture = MediaController.Builder(appContext, sessionToken).buildAsync()
    private var controller: MediaController? = null
    private var pendingSession: AudiobookPlaybackSession? = null
    private var positionUpdateJob: Job? = null

    private val _state = MutableStateFlow(AudiobookPlaybackState())
    val state: StateFlow<AudiobookPlaybackState> = _state.asStateFlow()

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

        override fun onPlayerError(error: PlaybackException) {
            stopPositionUpdates()
            _state.update {
                it.copy(
                    isPlaying = false,
                    isBuffering = false,
                    errorMessage = "播放失败: ${error.localizedMessage ?: error.errorCodeName}"
                )
            }
        }
    }

    init {
        controllerFuture.addListener(
            {
                runCatching {
                    controller = controllerFuture.get().also { it.addListener(playerListener) }
                    pendingSession?.let {
                        pendingSession = null
                        play(it)
                    }
                    publishPlayerState()
                }.onFailure { error ->
                    _state.update {
                        it.copy(errorMessage = "播放控制器连接失败: ${error.message ?: error::class.java.simpleName}")
                    }
                }
            },
            ContextCompat.getMainExecutor(appContext)
        )
    }

    fun play(session: AudiobookPlaybackSession) {
        val tracks = session.audioTracks
        if (tracks.isEmpty()) {
            _state.value = AudiobookPlaybackState(
                session = session,
                chapters = session.chapters,
                durationSeconds = session.durationSeconds,
                errorMessage = "没有可播放音轨"
            )
            return
        }

        val activeController = controller
        if (activeController == null) {
            pendingSession = session
            _state.value = AudiobookPlaybackState(
                session = session,
                isBuffering = true,
                durationSeconds = session.durationSeconds,
                chapters = session.chapters
            )
            return
        }

        val mediaItems = tracks.map { it.toMediaItem(session) }
        activeController.setMediaItems(mediaItems)
        activeController.prepare()
        if (session.startTimeSeconds > 0) {
            seekToTrackPosition(activeController, tracks, session.startTimeSeconds)
        }
        activeController.play()

        _state.value = AudiobookPlaybackState(
            session = session,
            isBuffering = true,
            durationSeconds = session.durationSeconds,
            chapters = session.chapters
        )
        publishPlayerState()
    }

    fun togglePlayPause() {
        val activeController = controller ?: return
        if (activeController.isPlaying) {
            activeController.pause()
        } else {
            if (activeController.playbackState == Player.STATE_IDLE) {
                activeController.prepare()
            }
            activeController.play()
        }
        publishPlayerState()
    }

    fun seekTo(positionSeconds: Int) {
        val activeController = controller ?: return
        val session = _state.value.session ?: return
        seekToTrackPosition(activeController, session.audioTracks, positionSeconds)
        publishPlayerState()
    }

    fun stop() {
        stopPositionUpdates()
        controller?.run {
            pause()
            stop()
            clearMediaItems()
        }
        pendingSession = null
        _state.value = AudiobookPlaybackState()
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
        val session = _state.value.session ?: return
        val currentAbsolutePosition = resolveAbsolutePositionSeconds(
            session.audioTracks,
            activeController.currentMediaItemIndex,
            activeController.currentPosition
        )

        _state.update {
            it.copy(
                session = session,
                isPlaying = activeController.isPlaying,
                isBuffering = activeController.playbackState == Player.STATE_BUFFERING,
                positionSeconds = currentAbsolutePosition,
                durationSeconds = session.durationSeconds.coerceAtLeast(
                    (activeController.duration.takeIf { value -> value != C.TIME_UNSET }?.div(1000L)?.toInt()) ?: 0
                ),
                chapters = session.chapters,
                errorMessage = when (activeController.playbackState) {
                    Player.STATE_READY, Player.STATE_ENDED -> null
                    else -> it.errorMessage
                }
            )
        }
    }

    private fun seekToTrackPosition(
        controller: MediaController,
        tracks: List<AudiobookAudioTrack>,
        absolutePositionSeconds: Int
    ) {
        val safePosition = absolutePositionSeconds.coerceAtLeast(0)
        val targetTrack = tracks.lastOrNull { track ->
            track.startOffsetSeconds <= safePosition
        } ?: tracks.first()
        val index = tracks.indexOf(targetTrack).coerceAtLeast(0)
        val localOffset = (safePosition - targetTrack.startOffsetSeconds).coerceAtLeast(0)
        controller.seekTo(index, localOffset * 1000L)
    }

    private fun resolveAbsolutePositionSeconds(
        tracks: List<AudiobookAudioTrack>,
        currentIndex: Int,
        currentPositionMs: Long
    ): Int = resolveAudiobookAbsolutePositionSeconds(tracks, currentIndex, currentPositionMs)
}

internal fun resolveAudiobookAbsolutePositionSeconds(
    tracks: List<AudiobookAudioTrack>,
    currentIndex: Int,
    currentPositionMs: Long
): Int {
    val currentTrack = tracks.getOrNull(currentIndex) ?: return (currentPositionMs.coerceAtLeast(0L) / 1000L).toInt()
    return currentTrack.startOffsetSeconds + (currentPositionMs.coerceAtLeast(0L) / 1000L).toInt()
}

private fun AudiobookAudioTrack.toMediaItem(session: AudiobookPlaybackSession): MediaItem {
    return MediaItem.Builder()
        .setUri(contentUrl)
        .setMediaId("${session.sessionId}:$index")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(session.displayAuthor)
                .setAlbumTitle(session.displayTitle)
                .build()
        )
        .build()
}
