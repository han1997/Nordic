package com.nordic.mediahub.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal const val LYRICS_FOLLOW_RESUME_MILLIS = 2_000L

internal data class MusicLyricsFollowState(val following: Boolean = true, val revision: Long = 0)

internal enum class LyricScrollActivity { User, Automatic, Idle }

internal fun lyricScrollActivity(dragging: Boolean, scrolling: Boolean, automatic: Boolean): LyricScrollActivity = when {
    dragging || (scrolling && !automatic) -> LyricScrollActivity.User
    !scrolling -> LyricScrollActivity.Idle
    else -> LyricScrollActivity.Automatic
}

/** Only user scrolling enters browsing mode; animations never call [onUserScrollStarted]. */
internal class MusicLyricsFollowController(
    private val scope: CoroutineScope,
    private var isPlaying: Boolean
) {
    private val _state = MutableStateFlow(MusicLyricsFollowState())
    val state = _state.asStateFlow()
    private var userScrolling = false
    private var resumeJob: Job? = null
    private var closed = false

    fun onUserScrollStarted() {
        if (closed || userScrolling) return
        userScrolling = true
        resumeJob?.cancel()
        _state.value = _state.value.copy(following = false)
    }

    fun onUserScrollStopped() {
        if (closed || !userScrolling) return
        userScrolling = false
        if (isPlaying && !_state.value.following) {
            resumeJob?.cancel()
            resumeJob = scope.launch {
                delay(LYRICS_FOLLOW_RESUME_MILLIS)
                resumeJob = null
                if (isPlaying && !userScrolling) returnToCurrent()
            }
        }
    }

    fun setPlaying(playing: Boolean) {
        if (closed || isPlaying == playing) return
        isPlaying = playing
        resumeJob?.cancel()
        if (playing && !userScrolling) returnToCurrent()
    }

    fun returnToCurrent() {
        if (closed) return
        resumeJob?.cancel()
        resumeJob = null
        _state.value = MusicLyricsFollowState(following = true, revision = _state.value.revision + 1)
    }

    fun close() {
        closed = true
        resumeJob?.cancel()
    }
}

/** Layout offsets already include LazyColumn's before-content padding coordinate system. */
internal fun lyricAlignmentDelta(itemOffset: Int, itemSize: Int, viewportStart: Int, viewportEnd: Int): Float {
    val viewportSize = (viewportEnd - viewportStart).coerceAtLeast(0)
    if (viewportSize == 0) return 0f
    val desired = if (itemSize >= viewportSize) viewportStart.toFloat()
        else viewportStart + (viewportSize - itemSize) / 2f
    return itemOffset - desired
}
