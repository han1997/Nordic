package com.nordic.mediahub.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
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

private const val AUDIOBOOK_SKIP_INTERVAL_SECONDS = 30
private val AUDIOBOOK_PLAYBACK_SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

data class AudiobookPlaybackState(
    val session: AudiobookPlaybackSession? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionSeconds: Int = 0,
    val durationSeconds: Int = 0,
    val playbackSpeed: Float = 1f,
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

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            publishPlayerState()
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

    fun seekToPreviousChapter() {
        val state = _state.value
        val targetPosition = resolvePreviousAudiobookChapterStartSeconds(
            chapters = state.chapters,
            positionSeconds = state.positionSeconds
        ) ?: return
        seekTo(targetPosition)
    }

    fun seekToNextChapter() {
        val state = _state.value
        val targetPosition = resolveNextAudiobookChapterStartSeconds(
            chapters = state.chapters,
            positionSeconds = state.positionSeconds
        ) ?: return
        seekTo(targetPosition)
    }

    fun seekBackBy(intervalSeconds: Int = AUDIOBOOK_SKIP_INTERVAL_SECONDS) {
        seekBy(-intervalSeconds)
    }

    fun seekForwardBy(intervalSeconds: Int = AUDIOBOOK_SKIP_INTERVAL_SECONDS) {
        seekBy(intervalSeconds)
    }

    fun cyclePlaybackSpeed() {
        val activeController = controller ?: return
        val nextSpeed = resolveNextAudiobookPlaybackSpeed(activeController.playbackParameters.speed)
        activeController.setPlaybackSpeed(nextSpeed)
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
                playbackSpeed = activeController.playbackParameters.speed,
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

    private fun seekBy(deltaSeconds: Int) {
        val state = _state.value
        if (state.session == null) return
        seekTo(
            resolveAudiobookRelativeSeekPositionSeconds(
                positionSeconds = state.positionSeconds,
                durationSeconds = state.durationSeconds,
                deltaSeconds = deltaSeconds
            )
        )
    }
}

internal fun resolveAudiobookAbsolutePositionSeconds(
    tracks: List<AudiobookAudioTrack>,
    currentIndex: Int,
    currentPositionMs: Long
): Int {
    val currentTrack = tracks.getOrNull(currentIndex) ?: return (currentPositionMs.coerceAtLeast(0L) / 1000L).toInt()
    return currentTrack.startOffsetSeconds + (currentPositionMs.coerceAtLeast(0L) / 1000L).toInt()
}

internal fun resolvePreviousAudiobookChapterStartSeconds(
    chapters: List<AudiobookChapter>,
    positionSeconds: Int,
    restartThresholdSeconds: Int = 5
): Int? {
    if (chapters.isEmpty()) return null

    val orderedChapters = chapters.sortedBy { it.startSeconds }
    val safePosition = positionSeconds.coerceAtLeast(0)
    val currentIndex = orderedChapters.indexOfLast { chapter -> chapter.startSeconds <= safePosition }
    if (currentIndex < 0) return null

    val currentChapter = orderedChapters[currentIndex]
    if (safePosition - currentChapter.startSeconds > restartThresholdSeconds) {
        return currentChapter.startSeconds
    }

    return orderedChapters.getOrNull(currentIndex - 1)?.startSeconds
}

internal fun resolveNextAudiobookChapterStartSeconds(
    chapters: List<AudiobookChapter>,
    positionSeconds: Int
): Int? {
    if (chapters.isEmpty()) return null

    val safePosition = positionSeconds.coerceAtLeast(0)
    return chapters
        .sortedBy { it.startSeconds }
        .firstOrNull { chapter -> chapter.startSeconds > safePosition }
        ?.startSeconds
}

internal fun resolveAudiobookRelativeSeekPositionSeconds(
    positionSeconds: Int,
    durationSeconds: Int,
    deltaSeconds: Int
): Int {
    val maxPosition = durationSeconds.coerceAtLeast(0)
    val safePosition = positionSeconds.coerceIn(0, maxPosition)
    val target = safePosition.toLong() + deltaSeconds.toLong()
    return target.coerceIn(0L, maxPosition.toLong()).toInt()
}

internal fun resolveNextAudiobookPlaybackSpeed(
    currentSpeed: Float,
    speeds: List<Float> = AUDIOBOOK_PLAYBACK_SPEEDS
): Float {
    if (speeds.isEmpty()) return 1f

    val currentIndex = speeds.indexOfFirst { speed -> kotlin.math.abs(speed - currentSpeed) < 0.001f }
    return if (currentIndex >= 0) {
        speeds[(currentIndex + 1) % speeds.size]
    } else {
        speeds.firstOrNull { speed -> speed > currentSpeed } ?: speeds.first()
    }
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
