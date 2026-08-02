package com.nordic.mediahub.playback

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nordic.mediahub.data.ConfigRepository
import com.nordic.mediahub.data.MusicLyrics
import com.nordic.mediahub.data.NavidromeRepository
import com.nordic.mediahub.data.NavidromeSong
import com.nordic.mediahub.data.isReadyForMusicSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn

class MusicPlaybackViewModel(application: Application) : AndroidViewModel(application) {
    private val engine = MusicPlaybackEngine(application)
    private val configRepository = ConfigRepository(application)

    val state: StateFlow<MusicPlaybackState> = engine.state

    private val _lyrics = MutableStateFlow<MusicLyrics?>(null)
    val lyrics: StateFlow<MusicLyrics?> = _lyrics.asStateFlow()

    private val _isLyricsLoading = MutableStateFlow(false)
    val isLyricsLoading: StateFlow<Boolean> = _isLyricsLoading.asStateFlow()

    private val _lyricsError = MutableStateFlow<String?>(null)
    val lyricsError: StateFlow<String?> = _lyricsError.asStateFlow()

    private val _repository = MutableStateFlow<NavidromeRepository?>(null)
    val repository: StateFlow<NavidromeRepository?> = _repository.asStateFlow()

    init {
        configRepository.navidromeConfig
            .map { if (it.isReadyForMusicSync()) it else null }
            .distinctUntilChanged()
            .map { config -> config?.let { NavidromeRepository(it) } }
            .onEach { _repository.value = it }
            .launchIn(viewModelScope)

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

    fun seekToNext() = engine.seekToNext()

    fun seekToPrevious() = engine.seekToPrevious()

    fun togglePlayPause() = engine.togglePlayPause()

    fun toggleRepeatMode() = engine.toggleRepeatMode()

    fun toggleShuffleMode() = engine.toggleShuffleMode()

    fun seekToQueueIndex(index: Int) = engine.seekToQueueIndex(index)

    fun moveQueueItemToPlayNext(index: Int) = engine.moveQueueItemToPlayNext(index)

    fun removeQueueItem(index: Int) = engine.removeQueueItem(index)

    fun clearUpcomingQueueItems() = engine.clearUpcomingQueueItems()

    override fun onCleared() {
        engine.release()
    }
}
