package com.nordic.mediahub.playback

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.nordic.mediahub.data.NavidromeSong
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

internal const val MUSIC_SKIP_BACK_SECONDS = 10
internal const val MUSIC_SKIP_FORWARD_SECONDS = 30

data class MusicPlaybackState(
    val currentSong: NavidromeSong? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionSeconds: Int = 0,
    val durationSeconds: Int = 0,
    val errorMessage: String? = null,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val shuffleModeEnabled: Boolean = false,
    val playbackSpeed: Float = 1f,
    val queue: List<NavidromeSong> = emptyList(),
    val queueIndex: Int = 0
)

internal data class PlayableMusicQueue(
    val songs: List<NavidromeSong>,
    val startIndex: Int
)

internal fun resolvePlayNextTargetIndex(index: Int, currentIndex: Int, itemCount: Int): Int? {
    if (itemCount <= 1 || index !in 0 until itemCount || currentIndex !in 0 until itemCount || index == currentIndex) {
        return null
    }

    val targetIndex = if (index < currentIndex) currentIndex else currentIndex + 1
    return targetIndex
        .coerceIn(0, itemCount - 1)
        .takeUnless { it == index }
}

internal fun <T> List<T>.moveItemToIndex(fromIndex: Int, targetIndex: Int): List<T> {
    if (fromIndex !in indices) return this
    val mutable = toMutableList()
    val item = mutable.removeAt(fromIndex)
    mutable.add(targetIndex.coerceIn(0, mutable.size), item)
    return mutable
}

internal fun resolveCurrentIndexAfterMove(fromIndex: Int, targetIndex: Int, currentIndex: Int, itemCount: Int): Int {
    if (itemCount <= 0 || fromIndex !in 0 until itemCount || currentIndex !in 0 until itemCount) {
        return currentIndex
    }
    if (fromIndex == currentIndex) {
        return targetIndex.coerceIn(0, itemCount - 1)
    }

    val insertionIndex = targetIndex.coerceIn(0, itemCount - 1)
    return when {
        fromIndex < currentIndex && insertionIndex >= currentIndex -> currentIndex - 1
        fromIndex > currentIndex && insertionIndex <= currentIndex -> currentIndex + 1
        else -> currentIndex
    }.coerceIn(0, itemCount - 1)
}

internal fun resolveQueueStartIndex(itemCount: Int, startIndex: Int): Int? {
    if (itemCount <= 0) return null
    return startIndex.coerceIn(0, itemCount - 1)
}

internal fun resolvePlayableMusicQueue(
    songs: List<NavidromeSong>,
    startIndex: Int,
    allowUnplayableStartFallback: Boolean = true
): PlayableMusicQueue? {
    val requestedStartIndex = resolveQueueStartIndex(songs.size, startIndex) ?: return null
    val playableSongs = songs.mapIndexedNotNull { index, song ->
        if (song.streamUrl.isNullOrBlank()) null else index to song
    }
    if (playableSongs.isEmpty()) return null

    val requestedPlayableIndex = playableSongs.indexOfFirst { (index, _) -> index == requestedStartIndex }
        .takeIf { index -> index >= 0 }
    if (requestedPlayableIndex == null && !allowUnplayableStartFallback) return null

    val nextPlayableIndex = playableSongs.indexOfFirst { (index, _) -> index > requestedStartIndex }
        .takeIf { index -> index >= 0 }
    val previousPlayableIndex = playableSongs.indexOfLast { (index, _) -> index < requestedStartIndex }
        .takeIf { index -> index >= 0 }

    return PlayableMusicQueue(
        songs = playableSongs.map { (_, song) -> song },
        startIndex = requestedPlayableIndex ?: nextPlayableIndex ?: previousPlayableIndex ?: 0
    )
}

internal fun shouldReplaceCurrentMusicItem(
    currentMediaId: String?,
    currentStreamUrl: String?,
    requestedSong: NavidromeSong
): Boolean {
    return currentMediaId != requestedSong.id ||
        currentStreamUrl != requestedSong.streamUrl.orEmpty()
}

internal fun resolveMusicSeekByPosition(
    currentPositionSeconds: Int,
    deltaSeconds: Int,
    durationSeconds: Int
): Int {
    if (durationSeconds > 0) {
        val safePosition = currentPositionSeconds.coerceIn(0, durationSeconds)
        val target = safePosition.toLong() + deltaSeconds.toLong()
        return target.coerceIn(0L, durationSeconds.toLong()).toInt()
    }

    val safePosition = currentPositionSeconds.coerceAtLeast(0)
    val target = safePosition.toLong() + deltaSeconds.toLong()
    return target.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
}

internal fun resolveSafePlaybackSpeed(speed: Float): Float {
    return if (speed.isFinite() && speed > 0f) speed else 1f
}

internal fun resolvePlaybackSpeedLabel(speed: Float): String {
    val safeSpeed = resolveSafePlaybackSpeed(speed)
    val rounded = kotlin.math.round(safeSpeed * 100f) / 100f
    return when (rounded) {
        1f -> "1.0x"
        1.5f -> "1.5x"
        2f -> "2.0x"
        0.75f -> "0.75x"
        0.5f -> "0.5x"
        else -> "${rounded}x"
    }
}

internal val PLAYBACK_SPEED_OPTIONS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

@androidx.annotation.OptIn(UnstableApi::class)
class MusicPlaybackEngine(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val sessionToken = SessionToken(
        appContext,
        ComponentName(appContext, MusicPlaybackService::class.java)
    )
    private var controller: MediaController? = null
    private var pendingSong: NavidromeSong? = null
    private var pendingQueue: List<NavidromeSong>? = null
    private var pendingQueueStartIndex: Int = 0
    private var positionUpdateJob: Job? = null
    private var cachedTimelineGeneration: Int = -1
    private var cachedQueue: List<NavidromeSong> = emptyList()

    private val controllerListener = object : MediaController.Listener {
        override fun onDisconnected(controller: MediaController) {
            Log.e("MusicPlayback", "MediaController disconnected, attempting reconnect")
            controller.removeListener(playerListener)
            this@MusicPlaybackEngine.controller = null
            cachedTimelineGeneration = -1
            val currentSong = _state.value.currentSong
            if (pendingSong == null && pendingQueue == null && currentSong != null) {
                pendingSong = currentSong
            }
            _state.update { it.copy(isPlaying = false, isBuffering = false) }
            scope.launch {
                delay(500)
                connectController()
            }
        }
    }

    private var controllerFuture = MediaController.Builder(appContext, sessionToken)
        .setListener(controllerListener)
        .buildAsync()

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

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            publishPlayerState()
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e("MusicPlayback", "Playback error", error)
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
        connectController()
    }

    private fun connectController() {
        controllerFuture = MediaController.Builder(appContext, sessionToken)
            .setListener(controllerListener)
            .buildAsync()
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

        PlaybackDomain.activeDomain = MediaDomain.MUSIC
        val activeController = controller
        if (activeController == null) {
            pendingSong = song
            pendingQueue = null
            pendingQueueStartIndex = 0
            _state.value = MusicPlaybackState(
                currentSong = song,
                durationSeconds = song.duration,
                isBuffering = true
            )
            return
        }

        val currentMediaItem = activeController.currentMediaItem
        val currentStreamUrl = currentMediaItem?.localConfiguration?.uri?.toString()
        if (shouldReplaceCurrentMusicItem(currentMediaItem?.mediaId, currentStreamUrl, song)) {
            _state.value = MusicPlaybackState(
                currentSong = song,
                durationSeconds = song.duration,
                isBuffering = true
            )
            cachedTimelineGeneration = -1
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

    fun playQueue(
        songs: List<NavidromeSong>,
        startIndex: Int = 0,
        allowUnplayableStartFallback: Boolean = true
    ) {
        val requestedStartIndex = resolveQueueStartIndex(songs.size, startIndex)
        val requestedStartSong = requestedStartIndex?.let { songs.getOrNull(it) }
        val playableQueue = resolvePlayableMusicQueue(
            songs = songs,
            startIndex = startIndex,
            allowUnplayableStartFallback = allowUnplayableStartFallback
        )
        if (playableQueue == null) {
            pendingSong = null
            pendingQueue = null
            pendingQueueStartIndex = 0
            val errorMessage = if (
                !allowUnplayableStartFallback &&
                requestedStartSong != null &&
                requestedStartSong.streamUrl.isNullOrBlank()
            ) {
                "这首歌缺少播放地址"
            } else {
                "队列没有可播放曲目"
            }
            _state.update { it.copy(isBuffering = false, errorMessage = errorMessage) }
            return
        }

        PlaybackDomain.activeDomain = MediaDomain.MUSIC
        val activeController = controller
        if (activeController == null) {
            pendingSong = null
            pendingQueue = playableQueue.songs
            pendingQueueStartIndex = playableQueue.startIndex
            val startSong = playableQueue.songs[playableQueue.startIndex]
            _state.update {
                it.copy(
                    currentSong = startSong,
                    isBuffering = true,
                    queue = playableQueue.songs,
                    queueIndex = playableQueue.startIndex,
                    errorMessage = null
                )
            }
            return
        }

        cachedTimelineGeneration = -1
        val mediaItems = playableQueue.songs.map { it.toMediaItem() }
        activeController.setMediaItems(mediaItems, playableQueue.startIndex, 0L)
        activeController.prepare()
        activeController.play()
        publishPlayerState()
    }

    fun seekToNext() {
        controller?.seekToNext()
    }

    fun seekToPrevious() {
        controller?.seekToPrevious()
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

    fun toggleShuffleMode() {
        val activeController = controller ?: return
        activeController.shuffleModeEnabled = !activeController.shuffleModeEnabled
        publishPlayerState()
    }

    fun setPlaybackSpeed(speed: Float) {
        val activeController = controller ?: return
        val safeSpeed = resolveSafePlaybackSpeed(speed)
        activeController.setPlaybackSpeed(safeSpeed)
        publishPlayerState()
    }

    fun seekToQueueIndex(index: Int) {
        val activeController = controller ?: return
        if (index !in 0 until activeController.mediaItemCount) return
        activeController.seekToDefaultPosition(index)
        activeController.play()
        publishPlayerState()
    }

    fun moveQueueItemToPlayNext(index: Int) {
        val activeController = controller
        if (activeController == null) {
            movePendingQueueItemToPlayNext(index)
            return
        }

        val itemCount = activeController.mediaItemCount
        val currentIndex = activeController.currentMediaItemIndex
        val targetIndex = resolvePlayNextTargetIndex(index, currentIndex, itemCount) ?: return

        activeController.moveMediaItem(index, targetIndex)
        cachedTimelineGeneration = -1
        publishPlayerState()
    }

    fun moveQueueItem(fromIndex: Int, targetIndex: Int) {
        val activeController = controller
        if (activeController == null) {
            movePendingQueueItem(fromIndex, targetIndex)
            return
        }

        val itemCount = activeController.mediaItemCount
        if (itemCount <= 1 || fromIndex !in 0 until itemCount) return
        val resolvedTargetIndex = targetIndex.coerceIn(0, itemCount - 1)
        if (fromIndex == resolvedTargetIndex) return

        activeController.moveMediaItem(fromIndex, resolvedTargetIndex)
        cachedTimelineGeneration = -1
        publishPlayerState()
    }

    fun removeQueueItem(index: Int) {
        val activeController = controller
        if (activeController == null) {
            removePendingQueueItem(index)
            return
        }

        val itemCount = activeController.mediaItemCount
        if (index !in 0 until itemCount) return

        if (itemCount == 1) {
            stop()
            return
        }

        activeController.removeMediaItem(index)
        cachedTimelineGeneration = -1
        publishPlayerState()
    }

    fun clearUpcomingQueueItems() {
        val activeController = controller
        if (activeController == null) {
            clearPendingUpcomingQueueItems()
            return
        }

        val itemCount = activeController.mediaItemCount
        val currentIndex = activeController.currentMediaItemIndex
        if (currentIndex !in 0 until itemCount || currentIndex >= itemCount - 1) return

        for (index in itemCount - 1 downTo currentIndex + 1) {
            activeController.removeMediaItem(index)
        }
        cachedTimelineGeneration = -1
        publishPlayerState()
    }

    fun togglePlayPause() {
        val activeController = controller
        if (activeController == null) {
            if (pendingQueue == null) {
                _state.value.currentSong?.let { pendingSong = it }
            }
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

    fun seekBackBy(intervalSeconds: Int = MUSIC_SKIP_BACK_SECONDS) {
        seekBy(-intervalSeconds)
    }

    fun seekForwardBy(intervalSeconds: Int = MUSIC_SKIP_FORWARD_SECONDS) {
        seekBy(intervalSeconds)
    }

    fun stop() {
        stopPositionUpdates()
        controller?.run {
            pause()
            stop()
            clearMediaItems()
        }
        pendingSong = null
        pendingQueue = null
        pendingQueueStartIndex = 0
        cachedTimelineGeneration = -1
        cachedQueue = emptyList()
        _state.value = MusicPlaybackState()
    }

    /**
     * Optimistically updates the current song's `starred` field in playback
     * state. Used by the favorite toggle so the UI reflects the new state
     * immediately while the server request is in flight. Revert by calling
     * again with the opposite value on failure.
     */
    fun setCurrentSongStarred(starred: Boolean) {
        _state.update {
            it.copy(currentSong = it.currentSong?.copy(starred = if (starred) "" else null))
        }
    }

    fun currentPositionMillis(): Long {
        val activeController = controller
        return if (activeController != null) {
            activeController.currentPosition.coerceAtLeast(0L)
        } else {
            _state.value.positionSeconds.toLong() * 1000L
        }
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
        if (PlaybackDomain.activeDomain == MediaDomain.AUDIOBOOK) {
            _state.update { it.copy(isPlaying = false, isBuffering = false) }
            return
        }
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
                shuffleModeEnabled = activeController.shuffleModeEnabled,
                playbackSpeed = activeController.playbackParameters.speed,
                queue = cachedQueue,
                queueIndex = currentIndex
            )
        }
    }

    private fun seekBy(deltaSeconds: Int) {
        val currentState = _state.value
        val controllerPosition = controller?.currentPosition
            ?.coerceAtLeast(0L)
            ?.div(1000L)
            ?.toInt()
        val currentPosition = controllerPosition ?: currentState.positionSeconds
        val controllerDuration = controller?.duration
            ?.takeIf { it > 0 && it != C.TIME_UNSET }
            ?.div(1000L)
            ?.toInt()
        val duration = controllerDuration ?: currentState.durationSeconds
        seekTo(resolveMusicSeekByPosition(currentPosition, deltaSeconds, duration))
    }

    private fun movePendingQueueItem(fromIndex: Int, targetIndex: Int) {
        val queue = pendingQueue ?: _state.value.queue
        val currentIndex = _state.value.queueIndex
        if (queue.size <= 1 || fromIndex !in queue.indices || currentIndex !in queue.indices) {
            return
        }
        val resolvedTargetIndex = targetIndex.coerceIn(queue.indices)
        if (fromIndex == resolvedTargetIndex) return

        val nextQueue = queue.moveItemToIndex(fromIndex, resolvedTargetIndex)
        val nextIndex = resolveCurrentIndexAfterMove(
            fromIndex = fromIndex,
            targetIndex = resolvedTargetIndex,
            currentIndex = currentIndex,
            itemCount = queue.size
        )
        pendingQueue = nextQueue
        pendingQueueStartIndex = nextIndex
        _state.update {
            it.copy(
                currentSong = nextQueue.getOrNull(nextIndex),
                queue = nextQueue,
                queueIndex = nextIndex
            )
        }
    }

    private fun movePendingQueueItemToPlayNext(index: Int) {
        val queue = pendingQueue ?: _state.value.queue
        val currentIndex = _state.value.queueIndex
        if (queue.size <= 1 || index !in queue.indices || currentIndex !in queue.indices || index == currentIndex) {
            return
        }

        val targetIndex = resolvePlayNextTargetIndex(index, currentIndex, queue.size) ?: return
        val nextQueue = queue.moveItemToIndex(index, targetIndex)
        pendingQueue = nextQueue
        val nextIndex = resolveCurrentIndexAfterMove(
            fromIndex = index,
            targetIndex = targetIndex,
            currentIndex = currentIndex,
            itemCount = queue.size
        )
        pendingQueueStartIndex = nextIndex
        _state.update {
            it.copy(
                queue = nextQueue,
                queueIndex = nextIndex
            )
        }
    }

    private fun removePendingQueueItem(index: Int) {
        val queue = pendingQueue ?: _state.value.queue
        if (index !in queue.indices) return

        val nextQueue = queue.toMutableList().also { it.removeAt(index) }
        if (nextQueue.isEmpty()) {
            pendingQueue = null
            pendingQueueStartIndex = 0
            _state.value = MusicPlaybackState()
            return
        }

        val nextIndex = when {
            index < _state.value.queueIndex -> _state.value.queueIndex - 1
            index == _state.value.queueIndex -> _state.value.queueIndex.coerceAtMost(nextQueue.lastIndex)
            else -> _state.value.queueIndex
        }.coerceIn(nextQueue.indices)

        pendingQueue = nextQueue
        pendingQueueStartIndex = nextIndex
        _state.update {
            it.copy(
                currentSong = nextQueue.getOrNull(nextIndex),
                queue = nextQueue,
                queueIndex = nextIndex
            )
        }
    }

    private fun clearPendingUpcomingQueueItems() {
        val queue = pendingQueue ?: _state.value.queue
        val currentIndex = _state.value.queueIndex
        if (currentIndex !in queue.indices || currentIndex >= queue.lastIndex) return

        val nextQueue = queue.take(currentIndex + 1)
        pendingQueue = nextQueue
        pendingQueueStartIndex = currentIndex
        _state.update { it.copy(queue = nextQueue, queueIndex = currentIndex) }
    }

}
