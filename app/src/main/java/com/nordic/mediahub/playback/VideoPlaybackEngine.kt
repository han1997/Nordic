package com.nordic.mediahub.playback

import android.net.Uri
import android.content.Context
import androidx.compose.runtime.Stable
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.nordic.mediahub.data.VideoMediaTrack
import com.nordic.mediahub.data.VideoPlaybackInfo
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
data class VideoPlaybackState(
    val playbackInfo: VideoPlaybackInfo? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionSeconds: Int = 0,
    val durationSeconds: Int = 0,
    val speed: Float = 1.0f,
    val selectedAudioTrackIndex: Int? = null,
    val selectedSubtitleTrackIndex: Int? = null,
    val subtitleScale: Float = 1.0f,
    val subtitleOffsetSeconds: Int = 0,
    val errorMessage: String? = null
)

class VideoPlaybackEngine(
    context: Context,
    private val onPlaybackStart: (suspend (itemId: String, mediaSourceId: String, playSessionId: String, positionSeconds: Int) -> Unit)? = null,
    private val onPlaybackProgress: (suspend (itemId: String, mediaSourceId: String, playSessionId: String, positionSeconds: Int) -> Unit)? = null,
    private val onPlaybackStopped: (suspend (itemId: String, mediaSourceId: String, playSessionId: String, positionSeconds: Int) -> Unit)? = null
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val exoPlayer = ExoPlayer.Builder(appContext).build()
    private var positionUpdateJob: Job? = null
    private var progressReportJob: Job? = null

    private val _state = MutableStateFlow(VideoPlaybackState())
    val state: StateFlow<VideoPlaybackState> = _state.asStateFlow()
    val player: Player = exoPlayer

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            publishPlayerState()
            if (isPlaying) startPositionUpdates() else stopPositionUpdates()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            publishPlayerState()
            if (playbackState == Player.STATE_ENDED || !exoPlayer.isPlaying) {
                stopPositionUpdates()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            stopPositionUpdates()
            _state.update {
                it.copy(
                    isPlaying = false,
                    isBuffering = false,
                    errorMessage = "视频播放失败: ${error.localizedMessage ?: error.errorCodeName}"
                )
            }
        }
    }

    init {
        exoPlayer.addListener(playerListener)
    }

    fun play(playbackInfo: VideoPlaybackInfo) {
        val defaultAudioTrack = playbackInfo.audioTracks.firstOrNull { it.isDefault }
            ?: playbackInfo.audioTracks.firstOrNull()
        val defaultSubtitleTrack = playbackInfo.subtitleTracks.firstOrNull { it.isDefault || it.isForced }
        _state.value = VideoPlaybackState(
            playbackInfo = playbackInfo,
            isBuffering = true,
            durationSeconds = playbackInfo.durationSeconds,
            selectedAudioTrackIndex = defaultAudioTrack?.index,
            selectedSubtitleTrackIndex = defaultSubtitleTrack?.index
        )
        exoPlayer.setMediaItem(playbackInfo.toMediaItem())
        exoPlayer.prepare()
        applyTrackSelection(defaultAudioTrack, defaultSubtitleTrack)
        exoPlayer.play()
        publishPlayerState()
        val info = playbackInfo
        scope.launch { runCatching { onPlaybackStart?.invoke(info.itemId, info.mediaSourceId, info.playSessionId, 0) } }
        startProgressReporting()
    }

    fun togglePlayPause() {
        if (exoPlayer.currentMediaItem == null) return

        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            if (exoPlayer.playbackState == Player.STATE_ENDED) {
                exoPlayer.seekTo(0L)
            }
            if (exoPlayer.playbackState == Player.STATE_IDLE) {
                exoPlayer.prepare()
            }
            exoPlayer.play()
        }
        publishPlayerState()
    }

    fun seekTo(positionSeconds: Int) {
        exoPlayer.seekTo(positionSeconds.coerceAtLeast(0) * 1000L)
        publishPlayerState()
    }

    fun setSpeed(speed: Float) {
        exoPlayer.playbackParameters = PlaybackParameters(speed)
        publishPlayerState()
    }

    fun selectAudioTrack(trackIndex: Int?) {
        val info = _state.value.playbackInfo ?: return
        val track = trackIndex?.let { index -> info.audioTracks.firstOrNull { it.index == index } }
        applyTrackSelection(track, info.subtitleTracks.firstOrNull { it.index == _state.value.selectedSubtitleTrackIndex })
        _state.update { it.copy(selectedAudioTrackIndex = track?.index) }
    }

    fun selectSubtitleTrack(trackIndex: Int?) {
        val info = _state.value.playbackInfo ?: return
        val track = trackIndex?.let { index -> info.subtitleTracks.firstOrNull { it.index == index } }
        applyTrackSelection(info.audioTracks.firstOrNull { it.index == _state.value.selectedAudioTrackIndex }, track)
        _state.update { it.copy(selectedSubtitleTrackIndex = track?.index) }
    }

    fun setSubtitleScale(scale: Float) {
        _state.update { it.copy(subtitleScale = scale.coerceIn(0.75f, 1.75f)) }
    }

    fun adjustSubtitleOffset(deltaSeconds: Int) {
        _state.update {
            it.copy(subtitleOffsetSeconds = (it.subtitleOffsetSeconds + deltaSeconds).coerceIn(-30, 30))
        }
    }

    fun stop() {
        stopPositionUpdates()
        stopProgressReporting()
        val info = _state.value.playbackInfo
        val position = _state.value.positionSeconds
        exoPlayer.pause()
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        if (info != null) {
            scope.launch { runCatching { onPlaybackStopped?.invoke(info.itemId, info.mediaSourceId, info.playSessionId, position) } }
        }
        _state.value = VideoPlaybackState()
    }

    fun release() {
        stopPositionUpdates()
        stopProgressReporting()
        val info = _state.value.playbackInfo
        val position = _state.value.positionSeconds
        if (info != null) {
            scope.launch { runCatching { onPlaybackStopped?.invoke(info.itemId, info.mediaSourceId, info.playSessionId, position) } }
        }
        scope.cancel()
        exoPlayer.removeListener(playerListener)
        exoPlayer.release()
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

    private fun startProgressReporting() {
        stopProgressReporting()
        if (_state.value.playbackInfo == null) return
        progressReportJob = scope.launch {
            while (isActive) {
                delay(10_000)
                val currentInfo = _state.value.playbackInfo ?: break
                runCatching {
                    onPlaybackProgress?.invoke(
                        currentInfo.itemId, currentInfo.mediaSourceId, currentInfo.playSessionId,
                        _state.value.positionSeconds
                    )
                }
            }
        }
    }

    private fun stopProgressReporting() {
        progressReportJob?.cancel()
        progressReportJob = null
    }

    private fun publishPlayerState() {
        val playbackInfo = _state.value.playbackInfo ?: return
        val playerDuration = exoPlayer.duration
            .takeIf { it != C.TIME_UNSET }
            ?.coerceAtLeast(0L)
            ?.div(1000L)
            ?.toInt()
        val fallbackDuration = playbackInfo.durationSeconds.coerceAtLeast(0)

        _state.update {
            it.copy(
                playbackInfo = playbackInfo,
                isPlaying = exoPlayer.isPlaying,
                isBuffering = exoPlayer.playbackState == Player.STATE_BUFFERING,
                positionSeconds = (exoPlayer.currentPosition.coerceAtLeast(0L) / 1000L).toInt(),
                durationSeconds = (playerDuration ?: fallbackDuration).coerceAtLeast(fallbackDuration),
                speed = exoPlayer.playbackParameters.speed,
                errorMessage = when (exoPlayer.playbackState) {
                    Player.STATE_READY, Player.STATE_ENDED -> null
                    else -> it.errorMessage
                }
            )
        }
    }

    private fun VideoPlaybackInfo.toMediaItem(): MediaItem {
        return MediaItem.Builder()
            .setUri(streamUrl)
            .setMediaId(itemId)
            .setSubtitleConfigurations(subtitleTracks.mapNotNull { it.toSubtitleConfiguration() })
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setDescription(overview)
                    .build()
            )
            .build()
    }

    private fun VideoMediaTrack.toSubtitleConfiguration(): MediaItem.SubtitleConfiguration? {
        val url = deliveryUrl ?: return null
        return MediaItem.SubtitleConfiguration.Builder(Uri.parse(url))
            .setMimeType(resolveSubtitleMimeType(codec))
            .setLanguage(language)
            .setSelectionFlags(if (isDefault || isForced) C.SELECTION_FLAG_DEFAULT else 0)
            .build()
    }

    private fun applyTrackSelection(audioTrack: VideoMediaTrack?, subtitleTrack: VideoMediaTrack?) {
        val params = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setPreferredAudioLanguage(audioTrack?.language)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, subtitleTrack == null)
            .setPreferredTextLanguage(subtitleTrack?.language)
            .build()
        exoPlayer.trackSelectionParameters = params
    }

    private fun resolveSubtitleMimeType(codec: String?): String {
        return when (codec?.lowercase()) {
            "ass", "ssa" -> MimeTypes.TEXT_SSA
            "vtt", "webvtt" -> MimeTypes.TEXT_VTT
            "pgs" -> MimeTypes.APPLICATION_PGS
            "subrip", "srt" -> MimeTypes.APPLICATION_SUBRIP
            else -> MimeTypes.APPLICATION_SUBRIP
        }
    }
}
