package com.nordic.mediahub.playback

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nordic.mediahub.data.ConfigRepository
import com.nordic.mediahub.data.MusicLyrics
import com.nordic.mediahub.data.NavidromeRepository
import com.nordic.mediahub.data.NavidromeSong
import com.nordic.mediahub.data.isReadyForMusicSync
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

class MusicPlaybackViewModel(application: Application) : AndroidViewModel(application) {
    private val engine = MusicPlaybackEngine(application)
    private val configRepository = ConfigRepository(application)

    val state: StateFlow<MusicPlaybackState> = engine.state

    val positionMillis: StateFlow<Long> = flow {
        while (coroutineContext.isActive) {
            emit(engine.currentPositionMillis())
            delay(POSITION_MILLIS_SAMPLE_INTERVAL_MS)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(POSITION_MILLIS_SUBSCRIPTION_TIMEOUT_MS),
        initialValue = 0L
    )

    private val _lyrics = MutableStateFlow<MusicLyrics?>(null)
    val lyrics: StateFlow<MusicLyrics?> = _lyrics.asStateFlow()

    private val _isLyricsLoading = MutableStateFlow(false)
    val isLyricsLoading: StateFlow<Boolean> = _isLyricsLoading.asStateFlow()

    private val _lyricsError = MutableStateFlow<String?>(null)
    val lyricsError: StateFlow<String?> = _lyricsError.asStateFlow()

    private val _repository = MutableStateFlow<NavidromeRepository?>(null)
    val repository: StateFlow<NavidromeRepository?> = _repository.asStateFlow()

    /**
     * One-shot event fired when an optimistic favorite (star/unstar) toggle
     * fails and the playback state is silently reverted. UI collects this and
     * shows a brief pill notification so the user understands why the ♥
     * "jumped back" instead of staying in the requested state.
     *
     * `replay = 0` so emits are only delivered to current collectors; missed
     * emits (no collector attached) are dropped, which is the intended UX —
     * we should not show a stale error from an old screen.
     */
    private val _favoriteError = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1
    )
    val favoriteError: SharedFlow<Unit> = _favoriteError.asSharedFlow()

    /**
     * Scrobble state machine (Subsonic two-phase semantics): now-playing on
     * song start, one submission per song once it has played past half (or on
     * leaving the song). Failures are silent — a missed scrobble must never
     * surface as a player error.
     */
    private var nowPlayingSongId: String? = null
    private var submittedSongId: String? = null

    init {
        configRepository.navidromeConfig
            .map { if (it.isReadyForMusicSync()) it else null }
            .distinctUntilChanged()
            .map { config -> config?.let { NavidromeRepository(it) } }
            .onEach { _repository.value = it }
            .launchIn(viewModelScope)

        // Scrobble: on song change, submit the previous song (if not already
        // submitted) and fire now-playing for the new one. Both fire-and-forget
        // and silent on failure.
        state.map { it.currentSong?.id }.distinctUntilChanged().onEach { songId ->
            val previousSongId = nowPlayingSongId
            val repo = _repository.value
            if (previousSongId != null && previousSongId != songId && previousSongId != submittedSongId) {
                submittedSongId = previousSongId
                submitScrobble(repo, previousSongId, submission = true)
            }
            nowPlayingSongId = songId
            if (songId != null && songId != submittedSongId) {
                submitScrobble(repo, songId, submission = false)
            }
        }.launchIn(viewModelScope)

        // Scrobble: submit at the halfway point without waiting for a song
        // change. Evaluated on the coarse playback state cadence (position
        // updates each second while playing).
        state.onEach { playbackState ->
            val song = playbackState.currentSong ?: return@onEach
            val songId = song.id
            if (submittedSongId == songId) return@onEach
            if (shouldScrobbleMusicSubmission(
                    positionSeconds = playbackState.positionSeconds,
                    durationSeconds = playbackState.durationSeconds,
                    alreadySubmitted = submittedSongId == songId
                )
            ) {
                submittedSongId = songId
                submitScrobble(_repository.value, songId, submission = true)
            }
        }.launchIn(viewModelScope)

        combine(state.map { it.currentSong }, _repository) { song, repo ->
            song to repo
        }.distinctUntilChanged().onEach { (song, repo) ->
            _lyrics.value = null
            _lyricsError.value = null
            if (song == null) {
                _isLyricsLoading.value = false
                return@onEach
            }
            if (repo == null) {
                _isLyricsLoading.value = false
                _lyricsError.value = "未配置 Navidrome"
                return@onEach
            }
            _isLyricsLoading.value = true
            runCatching { repo.getLyrics(song) }
                .onSuccess { lyrics ->
                    _lyrics.value = lyrics
                    _lyricsError.value = if (lyrics == null) "暂无歌词" else null
                }
                .onFailure { _lyricsError.value = "加载歌词失败" }
            _isLyricsLoading.value = false
        }.launchIn(viewModelScope)
    }

    fun playQueue(
        songs: List<NavidromeSong>,
        startIndex: Int = 0,
        allowUnplayableStartFallback: Boolean = true
    ) = engine.playQueue(songs, startIndex, allowUnplayableStartFallback)

    fun play(song: NavidromeSong) = engine.play(song)

    fun stop() = engine.stop()

    fun seekTo(positionSeconds: Int) = engine.seekTo(positionSeconds)

    fun seekBackBy(intervalSeconds: Int = MUSIC_SKIP_BACK_SECONDS) = engine.seekBackBy(intervalSeconds)

    fun seekForwardBy(intervalSeconds: Int = MUSIC_SKIP_FORWARD_SECONDS) = engine.seekForwardBy(intervalSeconds)

    fun seekToNext() = engine.seekToNext()

    fun seekToPrevious() = engine.seekToPrevious()

    fun togglePlayPause() = engine.togglePlayPause()

    fun toggleRepeatMode() = engine.toggleRepeatMode()

    fun toggleShuffleMode() = engine.toggleShuffleMode()

    fun setPlaybackSpeed(speed: Float) = engine.setPlaybackSpeed(speed)

    fun seekToQueueIndex(index: Int) = engine.seekToQueueIndex(index)

    fun moveQueueItemToPlayNext(index: Int) = engine.moveQueueItemToPlayNext(index)

    fun moveQueueItem(fromIndex: Int, targetIndex: Int) = engine.moveQueueItem(fromIndex, targetIndex)

    fun removeQueueItem(index: Int) = engine.removeQueueItem(index)

    fun clearUpcomingQueueItems() = engine.clearUpcomingQueueItems()

    /**
     * Toggles the favorite (star) state of the current song. Optimistically
     * updates the playback state's `currentSong.starred` so the UI reflects the
     * change immediately, then calls the repository's `star`/`unstar`. Reverts
     * the optimistic state on failure so the ♥ returns to its previous value.
     */
    fun toggleFavorite(songId: String, starred: Boolean) {
        viewModelScope.launch {
            engine.setCurrentSongStarred(starred)
            val repo = repository.value
            if (repo == null) {
                engine.setCurrentSongStarred(!starred)
                _favoriteError.tryEmit(Unit)
                return@launch
            }
            runCatching {
                if (starred) repo.star(id = songId) else repo.unstar(id = songId)
            }.onFailure {
                engine.setCurrentSongStarred(!starred)
                _favoriteError.tryEmit(Unit)
            }
        }
    }

    /**
     * Fire-and-forget scrobble; failures are logged and dropped. A missed
     * scrobble is retried naturally by the next play of the same song.
     */
    private fun submitScrobble(repo: NavidromeRepository?, songId: String, submission: Boolean) {
        if (repo == null || songId.isBlank()) return
        viewModelScope.launch {
            runCatching { repo.scrobble(songId, submission) }
                .onFailure { error ->
                    Log.w("MusicPlayback", "scrobble(submission=$submission) failed", error)
                }
        }
    }

    override fun onCleared() {
        engine.release()
    }

    companion object {
        private const val POSITION_MILLIS_SAMPLE_INTERVAL_MS = 100L
        private const val POSITION_MILLIS_SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}

/**
 * Whether the current song has played far enough to submit a scrobble
 * (Subsonic "submission"). Half the track is the standard threshold; an
 * unknown duration defers the decision to the song-change path.
 */
internal fun shouldScrobbleMusicSubmission(
    positionSeconds: Int,
    durationSeconds: Int,
    alreadySubmitted: Boolean
): Boolean {
    if (alreadySubmitted) return false
    if (durationSeconds <= 0) return false
    return positionSeconds >= durationSeconds / 2
}
