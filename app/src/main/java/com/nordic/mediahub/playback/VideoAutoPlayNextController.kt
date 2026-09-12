package com.nordic.mediahub.playback

import com.nordic.mediahub.data.VideoItem
import com.nordic.mediahub.data.playbackIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal const val VIDEO_AUTO_PLAY_NEXT_DELAY_MS = 5_000L

internal data class VideoAutoPlayNextRequest(val token: Long, val previous: VideoItem, val next: VideoItem)

internal sealed interface VideoAutoPlayNextState {
    data object Idle : VideoAutoPlayNextState
    data class Countdown(val request: VideoAutoPlayNextRequest, val secondsRemaining: Int) : VideoAutoPlayNextState
    data class Ready(val request: VideoAutoPlayNextRequest) : VideoAutoPlayNextState
    data class Switching(val request: VideoAutoPlayNextRequest) : VideoAutoPlayNextState
    data object Dismissed : VideoAutoPlayNextState
}

/**
 * Main-thread confined coordinator. Playback owns completion; UI only supplies foreground/panel
 * eligibility. A claimed request survives its own asynchronous close/save, but not user intent,
 * source changes, or lifecycle cancellation. The injected monotonic clock keeps tests deterministic.
 */
internal class VideoAutoPlayNextController(
    private val scope: CoroutineScope,
    private val nowMillis: () -> Long
) {
    private val _state = MutableStateFlow<VideoAutoPlayNextState>(VideoAutoPlayNextState.Idle)
    val state: StateFlow<VideoAutoPlayNextState> = _state.asStateFlow()
    private var enabled = false
    private var foreground = false
    private var panelOpen = false
    private var playback = VideoPlaybackState()
    private var next: VideoItem? = null
    private var dismissed = false
    private var nextToken = 0L
    private var timer: Job? = null

    fun setEnabled(value: Boolean) {
        enabled = value
        if (!value) cancelPending()
    }

    fun setForeground(value: Boolean) {
        foreground = value
        if (!value) cancelPending()
    }

    fun setPanelOpen(value: Boolean) {
        panelOpen = value
        if (value) cancelPending()
    }

    fun updatePlayback(snapshot: VideoPlaybackState, nextVideo: VideoItem?) {
        // runMediaHandoff stops the old engine before an asynchronous local save/quality handshake.
        // Only an explicitly claimed request may survive this empty intermediate engine state.
        val switching = _state.value as? VideoAutoPlayNextState.Switching
        if (switching != null && (snapshot.video == null ||
                snapshot.video.playbackIdentity() == switching.request.previous.playbackIdentity())) {
            next = nextVideo
            if (!snapshot.errorMessage.isNullOrBlank() || !isEligible(switching.request)) cancelPending()
            return
        }

        val previous = playback
        val sameItem = previous.video?.playbackIdentity() == snapshot.video?.playbackIdentity()
        val replayed = sameItem && previous.hasEnded && !snapshot.hasEnded
        if (!sameItem || replayed) resetPlaybackCycle()
        playback = snapshot
        next = nextVideo

        val request = activeRequest()
        if (request != null && !isEligible(request)) cancelPending()

        // A restored ended snapshot is not a new completion. Preference/UI updates cannot re-arm it.
        if (sameItem && snapshot.video != null && !previous.hasEnded && snapshot.hasEnded) {
            if (!dismissed && canCountDown()) startCountdown()
            else dismissed = true
        }
    }

    /** Explicit user cancellation also suppresses a pre-end manual Next prompt for this play cycle. */
    fun dismissCurrentPlayback() {
        dismissed = true
        timer?.cancel()
        timer = null
        _state.value = VideoAutoPlayNextState.Dismissed
    }

    /** A new play/replay, not a recomposition or toggled preference, starts a fresh cycle. */
    fun resetPlaybackCycle() {
        timer?.cancel()
        timer = null
        dismissed = false
        playback = VideoPlaybackState()
        next = null
        _state.value = VideoAutoPlayNextState.Idle
    }

    fun cancelPending() {
        if (activeRequest() != null) dismissCurrentPlayback()
    }

    fun playNow() {
        val countdown = _state.value as? VideoAutoPlayNextState.Countdown ?: return
        if (!isEligible(countdown.request)) { cancelPending(); return }
        timer?.cancel()
        timer = null
        _state.value = VideoAutoPlayNextState.Ready(countdown.request)
    }

    /** Atomically claim the one-shot event before entering the shared media handoff gate. */
    fun claim(request: VideoAutoPlayNextRequest): Boolean {
        val ready = _state.value as? VideoAutoPlayNextState.Ready ?: return false
        if (ready.request.token != request.token || !isEligible(request)) return false
        _state.value = VideoAutoPlayNextState.Switching(request)
        return true
    }

    fun canStart(request: VideoAutoPlayNextRequest): Boolean =
        (_state.value as? VideoAutoPlayNextState.Switching)?.request?.token == request.token &&
            isEligible(request)

    fun complete(request: VideoAutoPlayNextRequest) {
        if (activeRequest()?.token == request.token) resetPlaybackCycle()
    }

    private fun canCountDown(): Boolean = enabled && foreground && !panelOpen && playback.hasEnded &&
        playback.errorMessage.isNullOrBlank() && !playback.isBuffering &&
        playback.video != null && !next?.streamUrl.isNullOrBlank()

    private fun isEligible(request: VideoAutoPlayNextRequest): Boolean = canCountDown() && !dismissed &&
        playback.video?.playbackIdentity() == request.previous.playbackIdentity() &&
        next?.playbackIdentity() == request.next.playbackIdentity()

    private fun activeRequest(): VideoAutoPlayNextRequest? = when (val current = _state.value) {
        is VideoAutoPlayNextState.Countdown -> current.request
        is VideoAutoPlayNextState.Ready -> current.request
        is VideoAutoPlayNextState.Switching -> current.request
        else -> null
    }

    private fun startCountdown() {
        val request = VideoAutoPlayNextRequest(++nextToken, playback.video ?: return, next ?: return)
        val deadline = nowMillis() + VIDEO_AUTO_PLAY_NEXT_DELAY_MS
        _state.value = VideoAutoPlayNextState.Countdown(request, (VIDEO_AUTO_PLAY_NEXT_DELAY_MS / 1_000L).toInt())
        timer = scope.launch {
            while (activeRequest()?.token == request.token && isEligible(request)) {
                val remaining = deadline - nowMillis()
                if (remaining <= 0L) {
                    _state.value = VideoAutoPlayNextState.Ready(request)
                    return@launch
                }
                _state.value = VideoAutoPlayNextState.Countdown(request, ((remaining + 999L) / 1_000L).toInt())
                delay(minOf(1_000L, remaining))
            }
        }
    }
}
