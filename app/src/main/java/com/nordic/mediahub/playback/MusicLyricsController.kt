package com.nordic.mediahub.playback

import androidx.compose.runtime.Stable
import com.nordic.mediahub.data.MusicLyrics
import com.nordic.mediahub.data.NavidromeSong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

@Stable
sealed interface MusicLyricsUiState {
    val songId: String?

    data object Idle : MusicLyricsUiState { override val songId: String? = null }
    data class Loading(override val songId: String) : MusicLyricsUiState
    data class Content(
        override val songId: String,
        val requestId: Long,
        val lyrics: MusicLyrics
    ) : MusicLyricsUiState
    data class Empty(override val songId: String) : MusicLyricsUiState
    data class Error(
        override val songId: String,
        val message: String,
        val canRetry: Boolean
    ) : MusicLyricsUiState
}

/** Session-owned state. No Android dependencies or persisted display preference. */
internal class MusicLyricsController<Source : Any>(
    private val scope: CoroutineScope,
    private val load: suspend (Source, NavidromeSong) -> MusicLyrics?
) {
    private data class RequestKey<Source>(
        val source: Source?,
        val songId: String?,
        val artist: String?,
        val title: String?
    )

    private val _state = MutableStateFlow<MusicLyricsUiState>(MusicLyricsUiState.Idle)
    val state = _state.asStateFlow()
    private val _showLyrics = MutableStateFlow(false)
    val showLyrics = _showLyrics.asStateFlow()
    private var key: RequestKey<Source>? = null
    private var song: NavidromeSong? = null
    private var source: Source? = null
    private var requestId = 0L
    private var job: Job? = null

    fun select(song: NavidromeSong?, source: Source?) {
        this.song = song
        this.source = source
        val next = RequestKey(source, song?.id, song?.artist, song?.title)
        if (key == next) return
        key = next
        startRequest()
    }

    fun toggleDisplay() { _showLyrics.value = !_showLyrics.value }

    fun retry() {
        val current = _state.value
        if (current is MusicLyricsUiState.Error && current.canRetry) startRequest()
    }

    private fun startRequest() {
        val id = ++requestId
        job?.cancel()
        val selectedSong = song
        val selectedSource = source
        if (selectedSong == null) {
            _state.value = MusicLyricsUiState.Idle
            return
        }
        if (selectedSource == null) {
            _state.value = MusicLyricsUiState.Error(selectedSong.id, "未配置 Navidrome", canRetry = false)
            return
        }
        _state.value = MusicLyricsUiState.Loading(selectedSong.id)
        job = scope.launch {
            try {
                val lyrics = load(selectedSource, selectedSong)
                coroutineContext.ensureActive()
                if (requestId != id) return@launch
                _state.value = if (lyrics == null) {
                    MusicLyricsUiState.Empty(selectedSong.id)
                } else {
                    MusicLyricsUiState.Content(selectedSong.id, id, lyrics)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (requestId == id) {
                    _state.value = MusicLyricsUiState.Error(selectedSong.id, "加载歌词失败", canRetry = true)
                }
            }
        }
    }

    fun close() {
        requestId++
        job?.cancel()
    }
}
