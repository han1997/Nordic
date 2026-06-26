package com.nordic.mediahub.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.compose.runtime.Stable
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
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
    val currentChapterIndex: Int = -1,
    val speed: Float = 1.0f,
    val sleepTimerRemainingSeconds: Int? = null,
    val errorMessage: String? = null
)

internal fun resolveInitialAudiobookSyncPositionSeconds(
    session: AudiobookPlaybackSession,
    statePositionSeconds: Int
): Int {
    return maxOf(
        statePositionSeconds,
        session.currentTimeSeconds,
        session.startTimeSeconds
    ).coerceAtLeast(0)
}

internal fun resolveAudiobookSyncDeltaSeconds(
    lastSyncedPositionSeconds: Int,
    currentPositionSeconds: Int
): Int {
    return (currentPositionSeconds - lastSyncedPositionSeconds).coerceAtLeast(0)
}

internal fun resolveCurrentChapterIndex(
    chapters: List<AudiobookChapter>,
    positionSeconds: Int
): Int {
    if (chapters.isEmpty()) return -1
    return chapters.indexOfLast { it.startSeconds <= positionSeconds }.coerceAtLeast(0)
}

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
    private var sleepTimerJob: Job? = null
    private var sleepTimerEndOfChapterJob: Job? = null
    private var lastChapterIndexBeforeChapterMonitor: Int = -1

    private val _state = MutableStateFlow(AudiobookPlaybackState())
    val state: StateFlow<AudiobookPlaybackState> = _state.asStateFlow()

    private val _speed = MutableStateFlow(1.0f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

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

    fun setSpeed(speed: Float) {
        val activeController = controller ?: return
        val safeSpeed = speed.coerceIn(0.25f, 5f)
        activeController.playbackParameters = PlaybackParameters(safeSpeed)
        _speed.value = safeSpeed
        _state.update { it.copy(speed = safeSpeed) }
    }

    fun skipForward(seconds: Int) {
        val activeController = controller ?: return
        val targetMs = activeController.currentPosition + seconds * 1000L
        activeController.seekTo(targetMs)
        publishPlayerState()
    }

    fun skipBackward(seconds: Int) {
        val activeController = controller ?: return
        val targetMs = (activeController.currentPosition - seconds * 1000L).coerceAtLeast(0L)
        activeController.seekTo(targetMs)
        publishPlayerState()
    }

    fun jumpToNextChapter() {
        val chapters = _state.value.chapters
        val currentIndex = _state.value.currentChapterIndex
        if (chapters.isEmpty() || currentIndex < 0) return
        val nextIndex = (currentIndex + 1).coerceAtMost(chapters.size - 1)
        seekTo(chapters[nextIndex].startSeconds)
    }

    fun jumpToPreviousChapter() {
        val chapters = _state.value.chapters
        val currentIndex = _state.value.currentChapterIndex
        if (chapters.isEmpty() || currentIndex < 0) return
        val prevIndex = (currentIndex - 1).coerceAtLeast(0)
        seekTo(chapters[prevIndex].startSeconds)
    }

    fun jumpToChapter(chapterIndex: Int) {
        val chapters = _state.value.chapters
        if (chapterIndex !in chapters.indices) return
        seekTo(chapters[chapterIndex].startSeconds)
    }

    fun startSleepTimer(durationMinutes: Int) {
        cancelSleepTimer()
        val durationSeconds = durationMinutes * 60
        var remaining = durationSeconds
        _state.update { it.copy(sleepTimerRemainingSeconds = remaining) }
        sleepTimerJob = scope.launch {
            while (isActive && remaining > 0) {
                delay(1000L)
                remaining -= 1
                _state.update { it.copy(sleepTimerRemainingSeconds = remaining) }
            }
            if (remaining <= 0) {
                controller?.pause()
                cancelSleepTimer()
                publishPlayerState()
            }
        }
    }

    fun startSleepTimerEndOfChapter() {
        cancelSleepTimer()
        val chapters = _state.value.chapters
        val currentIndex = _state.value.currentChapterIndex
        if (chapters.isEmpty() || currentIndex < 0 || currentIndex >= chapters.size - 1) {
            return
        }
        lastChapterIndexBeforeChapterMonitor = currentIndex
        _state.update { it.copy(sleepTimerRemainingSeconds = -1) }
        sleepTimerEndOfChapterJob = scope.launch {
            while (isActive) {
                delay(1000L)
                val currentChapterIdx = _state.value.currentChapterIndex
                if (currentChapterIdx > lastChapterIndexBeforeChapterMonitor && currentChapterIdx >= 0) {
                    controller?.pause()
                    cancelSleepTimer()
                    publishPlayerState()
                    break
                }
                lastChapterIndexBeforeChapterMonitor = currentChapterIdx
            }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepTimerEndOfChapterJob?.cancel()
        sleepTimerEndOfChapterJob = null
        lastChapterIndexBeforeChapterMonitor = -1
        _state.update { it.copy(sleepTimerRemainingSeconds = null) }
    }

    fun stop() {
        stopPositionUpdates()
        cancelSleepTimer()
        controller?.run {
            pause()
            stop()
            clearMediaItems()
        }
        pendingSession = null
        _state.value = AudiobookPlaybackState()
        _speed.value = 1.0f
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
                currentChapterIndex = resolveCurrentChapterIndex(session.chapters, currentAbsolutePosition),
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
