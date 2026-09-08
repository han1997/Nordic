package com.nordic.mediahub.playback

import android.content.Context
import android.util.Log
import android.view.SurfaceView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.nordic.mediahub.data.MediaAuthHeaderInterceptor
import com.nordic.mediahub.data.VideoItem
import com.nordic.mediahub.data.VideoStreamInfo
import com.nordic.mediahub.data.VideoStreamKind
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
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

internal const val VIDEO_SKIP_BACK_SECONDS = 10
internal const val VIDEO_SKIP_FORWARD_SECONDS = 30

internal fun shouldReplaceCurrentVideoItem(
    currentVideo: VideoItem?,
    requestedVideo: VideoItem
): Boolean {
    if (currentVideo == null) return true

    return currentVideo.id != requestedVideo.id ||
        currentVideo.streamUrl.orEmpty() != requestedVideo.streamUrl.orEmpty()
}

enum class AspectRatioMode(val label: String) {
    FIT("Fit"),
    CROP("Crop"),
    FILL("Fill")
}

data class VideoPlaybackState(
    val video: VideoItem? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionSeconds: Int = 0,
    val bufferedPositionSeconds: Int = 0,
    val durationSeconds: Int = 0,
    val errorMessage: String? = null,
    val aspectRatioMode: AspectRatioMode = AspectRatioMode.FIT,
    val videoAspectRatio: Float = 16f / 9f,
    val playbackSpeed: Float = 1f,
    val availableAudioStreams: List<VideoStreamInfo> = emptyList(),
    val availableSubtitleStreams: List<VideoStreamInfo> = emptyList(),
    val selectedSubtitleStream: VideoStreamInfo? = null,
    val selectedAudioStream: VideoStreamInfo? = null
)

/**
 * Resolves the Emby external-subtitle delivery URL for a stream index. The
 * request itself is authenticated through the shared OkHttp interceptor, so
 * no token is embedded in the URL.
 */
internal fun resolveExternalSubtitleUrl(baseUrl: String, itemId: String, streamIndex: Int): String? {
    val base = baseUrl.toHttpUrlOrNull() ?: return null
    val safeItemId = itemId.trim().takeIf { it.isNotBlank() } ?: return null
    if (streamIndex < 0) return null
    return base.newBuilder()
        .addPathSegment("Videos")
        .addPathSegment(safeItemId)
        .addPathSegment(streamIndex.toString())
        .addPathSegment("Subtitles")
        .addQueryParameter("format", "vtt")
        .build()
        .toString()
}

/**
 * Builds the Media3 subtitle configurations for external subtitle streams of
 * a video. Selection stays off by default; the user opts in from the tracks
 * panel.
 */
@androidx.annotation.OptIn(UnstableApi::class)
internal fun externalSubtitleConfigurations(video: VideoItem): List<MediaItem.SubtitleConfiguration> {
    return externalSubtitleDescriptors(video).map { descriptor ->
        MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(descriptor.url))
            .setId(descriptor.id)
            .setLabel(descriptor.label)
            .setLanguage(descriptor.language)
            .setMimeType(androidx.media3.common.MimeTypes.TEXT_VTT)
            .build()
    }
}

internal data class ExternalSubtitleDescriptor(
    val url: String,
    val id: String,
    val label: String,
    val language: String?
)

/** Pure (Uri-free) descriptor builder so the mapping stays unit-testable. */
internal fun externalSubtitleDescriptors(video: VideoItem): List<ExternalSubtitleDescriptor> {
    val baseUrl = video.streamUrl ?: return emptyList()
    val streamBase = baseUrl.toHttpUrlOrNull() ?: return emptyList()
    // Rebuild from the server origin so leftover stream path segments do not
    // leak into the subtitle delivery path.
    val originBase = streamBase.newBuilder()
        .encodedPath("/")
        .query(null)
        .fragment(null)
        .build()
    return video.mediaStreams
        .filter { it.kind == VideoStreamKind.Subtitle && it.isExternal }
        .mapNotNull { stream ->
            val url = resolveExternalSubtitleUrl(
                baseUrl = originBase.toString(),
                itemId = video.id,
                streamIndex = stream.index
            ) ?: return@mapNotNull null
            ExternalSubtitleDescriptor(
                url = url,
                id = "emby-subtitle-${stream.index}",
                label = stream.displayTitle ?: stream.language ?: "字幕 ${stream.index}",
                language = stream.language
            )
        }
}

interface VideoPlaybackBackend {
    val state: StateFlow<VideoPlaybackState>

    fun attachSurface(surfaceView: SurfaceView)
    fun detachSurface(surfaceView: SurfaceView)
    fun setSubtitleView(view: androidx.media3.ui.SubtitleView?)
    fun play(video: VideoItem)
    fun playFromStart(video: VideoItem)
    fun togglePlayPause()
    fun seekTo(positionSeconds: Int)
    fun seekBackBy(intervalSeconds: Int = VIDEO_SKIP_BACK_SECONDS)
    fun seekForwardBy(intervalSeconds: Int = VIDEO_SKIP_FORWARD_SECONDS)
    fun cycleAspectRatio()
    fun setPreferredTextTrack(stream: VideoStreamInfo?)
    fun setPreferredAudioTrack(stream: VideoStreamInfo?)
    fun stop()
    fun release()
}

@androidx.annotation.OptIn(UnstableApi::class)
class VideoPlaybackEngine(context: Context) : VideoPlaybackBackend {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(MediaAuthHeaderInterceptor())
        .build()
    private val httpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
        .setUserAgent("Nordic")
    private val player = ExoPlayer.Builder(appContext)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(appContext).setDataSourceFactory(httpDataSourceFactory)
        )
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true
        )
        .setHandleAudioBecomingNoisy(true)
        .build()
    private var positionUpdateJob: Job? = null

    /** Playback speed restored from persisted preferences; applied on each new media item. */
    @Volatile
    private var persistedPlaybackSpeed: Float = 1f

    private val _state = MutableStateFlow(VideoPlaybackState())
    override val state: StateFlow<VideoPlaybackState> = _state.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            publishPlayerState()
            if (isPlaying) startPositionUpdates() else stopPositionUpdates()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            publishPlayerState()
            if (!player.isPlaying) {
                stopPositionUpdates()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e("VideoPlayback", "Playback error", error)
            stopPositionUpdates()
            _state.update {
                it.copy(
                    isPlaying = false,
                    isBuffering = false,
                    errorMessage = "视频播放失败: ${error.localizedMessage ?: error.errorCodeName}"
                )
            }
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            _state.update {
                it.copy(
                    videoAspectRatio = resolveVideoAspectRatio(
                        width = videoSize.width,
                        height = videoSize.height,
                        pixelWidthHeightRatio = videoSize.pixelWidthHeightRatio
                    )
                )
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            val video = _state.value.video ?: return
            _state.update {
                it.copy(
                    availableAudioStreams = video.availableStreamsFor(VideoStreamKind.Audio, tracks),
                    availableSubtitleStreams = video.availableStreamsFor(VideoStreamKind.Subtitle, tracks),
                    selectedSubtitleStream = it.selectedSubtitleStream?.takeIf { selected ->
                        video.availableStreamsFor(VideoStreamKind.Subtitle, tracks).any { s -> s.index == selected.index }
                    },
                    selectedAudioStream = it.selectedAudioStream?.takeIf { selected ->
                        video.availableStreamsFor(VideoStreamKind.Audio, tracks).any { s -> s.index == selected.index }
                    }
                )
            }
        }

        override fun onCues(cues: MutableList<androidx.media3.common.text.Cue>) {
            currentCues = cues.toList()
            subtitleView?.setCues(currentCues)
        }
    }

    init {
        // Subtitles stay off until the user picks a track from the panel.
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
        player.addListener(playerListener)
    }

    override fun attachSurface(surfaceView: SurfaceView) {
        player.setVideoSurfaceView(surfaceView)
    }

    override fun detachSurface(surfaceView: SurfaceView) {
        player.clearVideoSurfaceView(surfaceView)
    }

    override fun setSubtitleView(view: androidx.media3.ui.SubtitleView?) {
        subtitleView = view
        view?.setCues(currentCues)
    }

    private var subtitleView: androidx.media3.ui.SubtitleView? = null
    private var currentCues: List<androidx.media3.common.text.Cue> = emptyList()

    override fun play(video: VideoItem) {
        if (video.streamUrl.isNullOrBlank()) {
            _state.value = VideoPlaybackState(
                video = video,
                durationSeconds = video.durationSeconds,
                errorMessage = "这个视频缺少播放地址"
            )
            return
        }

        if (shouldReplaceCurrentVideoItem(_state.value.video, video)) {
            _state.value = VideoPlaybackState(
                video = video,
                durationSeconds = video.durationSeconds,
                isBuffering = true,
                playbackSpeed = persistedPlaybackSpeed
            )
            player.setMediaItem(video.toMediaItem())
            player.setPlaybackSpeed(persistedPlaybackSpeed)
            player.prepare()
            val startPositionMs = resolveVideoInitialStartPositionMs(video)
            if (startPositionMs > 0L) {
                player.seekTo(startPositionMs)
            }
        } else {
            _state.update { it.copy(errorMessage = null) }
        }

        // A replaced item is already prepared above; only a prior ended item
        // (same replay) needs to restart from zero. Avoid a redundant second
        // prepare while the async state transition is still IDLE/BUFFERING.
        if (player.playbackState == Player.STATE_ENDED) {
            player.seekTo(0L)
        }
        player.play()
        publishPlayerState()
    }

    override fun playFromStart(video: VideoItem) {
        if (video.streamUrl.isNullOrBlank()) {
            _state.value = VideoPlaybackState(
                video = video,
                durationSeconds = video.durationSeconds,
                errorMessage = "这个视频缺少播放地址"
            )
            return
        }

        if (shouldReplaceCurrentVideoItem(_state.value.video, video)) {
            _state.value = VideoPlaybackState(
                video = video,
                durationSeconds = video.durationSeconds,
                isBuffering = true,
                playbackSpeed = persistedPlaybackSpeed
            )
            player.setMediaItem(video.toMediaItem())
            player.setPlaybackSpeed(persistedPlaybackSpeed)
            player.prepare()
        } else {
            _state.update { it.copy(errorMessage = null) }
        }

        player.seekTo(0L)
        player.play()
        publishPlayerState()
    }

    override fun togglePlayPause() {
        val video = _state.value.video ?: return
        if (video.streamUrl.isNullOrBlank()) return

        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_ENDED) {
                player.seekTo(0L)
            }
            if (player.playbackState == Player.STATE_IDLE) {
                player.setMediaItem(video.toMediaItem())
                player.prepare()
            }
            player.play()
        }
        publishPlayerState()
    }

    override fun seekTo(positionSeconds: Int) {
        player.seekTo(positionSeconds.coerceAtLeast(0) * 1000L)
        publishPlayerState()
    }

    override fun seekBackBy(intervalSeconds: Int) {
        seekBy(-intervalSeconds)
    }

    override fun seekForwardBy(intervalSeconds: Int) {
        seekBy(intervalSeconds)
    }

    override fun cycleAspectRatio() {
        _state.update { it.copy(aspectRatioMode = resolveNextAspectRatioMode(it.aspectRatioMode)) }
    }

    fun setPlaybackSpeed(speed: Float) {
        val safeSpeed = resolveSafePlaybackSpeed(speed)
        player.setPlaybackSpeed(safeSpeed)
        publishPlayerState()
    }

    override fun setPreferredTextTrack(stream: VideoStreamInfo?) {
        _state.update { it.copy(selectedSubtitleStream = stream) }
        if (stream == null) {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .build()
            return
        }
        val override = player.currentTracks.groups
            .filter { it.type == C.TRACK_TYPE_TEXT }
            .firstOrNull { it.mediaTrackGroup.id == stream.index.toString() }
            ?.let { TrackSelectionOverride(it.mediaTrackGroup, 0) }
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .apply { override?.let { addOverride(it) } }
            .build()
    }

    override fun setPreferredAudioTrack(stream: VideoStreamInfo?) {
        _state.update { it.copy(selectedAudioStream = stream) }
        if (stream == null) return
        val override = player.currentTracks.groups
            .filter { it.type == C.TRACK_TYPE_AUDIO }
            .firstOrNull { it.mediaTrackGroup.id == stream.index.toString() }
            ?.let { TrackSelectionOverride(it.mediaTrackGroup, 0) }
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .apply { override?.let { addOverride(it) } }
            .build()
    }

    fun applyPersistedPlaybackSpeed(speed: Float) {
        val safeSpeed = resolveSafePlaybackSpeed(speed)
        persistedPlaybackSpeed = safeSpeed
        player.setPlaybackSpeed(safeSpeed)
        publishPlayerState()
    }

    override fun stop() {
        stopPositionUpdates()
        player.pause()
        player.stop()
        player.clearMediaItems()
        _state.value = VideoPlaybackState()
    }

    override fun release() {
        stopPositionUpdates()
        scope.cancel()
        player.removeListener(playerListener)
        player.release()
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
        val video = _state.value.video ?: return
        val playerDuration = player.duration
            .takeIf { duration -> duration != C.TIME_UNSET }
            ?.coerceAtLeast(0L)

        _state.update {
            it.copy(
                video = video,
                isPlaying = player.isPlaying,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                positionSeconds = (player.currentPosition.coerceAtLeast(0L) / 1000L).toInt(),
                bufferedPositionSeconds = player.bufferedPosition
                    .takeIf { buffered -> buffered != C.TIME_UNSET }
                    ?.coerceAtLeast(0L)
                    ?.div(1000L)
                    ?.toInt() ?: 0,
                durationSeconds = (playerDuration?.div(1000L)?.toInt() ?: video.durationSeconds)
                    .coerceAtLeast(video.durationSeconds),
                playbackSpeed = player.playbackParameters.speed,
                errorMessage = when (player.playbackState) {
                    Player.STATE_READY, Player.STATE_ENDED -> null
                    else -> it.errorMessage
                }
            )
        }
    }

    private fun seekBy(deltaSeconds: Int) {
        val state = _state.value
        if (state.video == null) return
        seekTo(
            resolveVideoRelativeSeekPositionSeconds(
                positionSeconds = state.positionSeconds,
                durationSeconds = state.durationSeconds,
                deltaSeconds = deltaSeconds
            )
        )
    }
}

internal fun resolveVideoInitialStartPositionMs(video: VideoItem): Long {
    if (video.isPlayed) return 0L

    val resumeSeconds = video.playbackPositionSeconds.coerceAtLeast(0)
    if (resumeSeconds <= 0) return 0L

    val durationSeconds = video.durationSeconds.coerceAtLeast(0)
    if (durationSeconds > 0 && resumeSeconds >= durationSeconds) return 0L

    return resumeSeconds * 1000L
}

internal fun resolveVideoRelativeSeekPositionSeconds(
    positionSeconds: Int,
    durationSeconds: Int,
    deltaSeconds: Int
): Int {
    if (durationSeconds > 0) {
        val maxPosition = durationSeconds
        val safePosition = positionSeconds.coerceIn(0, maxPosition)
        val target = safePosition.toLong() + deltaSeconds.toLong()
        return target.coerceIn(0L, maxPosition.toLong()).toInt()
    }

    val safePosition = positionSeconds.coerceAtLeast(0)
    val target = safePosition.toLong() + deltaSeconds.toLong()
    return target.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
}

internal fun resolveNextAspectRatioMode(current: AspectRatioMode): AspectRatioMode {
    val modes = AspectRatioMode.entries
    return modes[(current.ordinal + 1) % modes.size]
}

internal fun resolveVideoAspectRatio(
    width: Int,
    height: Int,
    pixelWidthHeightRatio: Float
): Float {
    if (width <= 0 || height <= 0) return 16f / 9f

    val safePixelRatio = if (pixelWidthHeightRatio > 0f) pixelWidthHeightRatio else 1f
    return width.toFloat() / height.toFloat() * safePixelRatio
}

/**
 * Filters the video's declared streams of [kind] down to the ones actually
 * exposed to the player (embedded tracks appear in [tracks]; external
 * subtitle tracks are always offered since Media3 loads them lazily).
 */
@androidx.annotation.OptIn(UnstableApi::class)
private fun VideoItem.availableStreamsFor(kind: VideoStreamKind, tracks: Tracks): List<VideoStreamInfo> {
    val declared = mediaStreams.filter { it.kind == kind }
    val externalSubtitle = kind == VideoStreamKind.Subtitle
    return declared.filter { stream ->
        externalSubtitle || tracks.groups.any { group ->
            group.type == trackTypeFor(kind) && stream.index.toString() == group.mediaTrackGroup.id
        }
    }
}

private fun trackTypeFor(kind: VideoStreamKind): Int = when (kind) {
    VideoStreamKind.Audio -> C.TRACK_TYPE_AUDIO
    VideoStreamKind.Subtitle -> C.TRACK_TYPE_TEXT
}

private fun VideoItem.toMediaItem(): MediaItem {
    val builder = MediaItem.Builder()
        .setUri(streamUrl)
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setDescription(overview)
                .build()
        )
    val subtitleConfigurations = externalSubtitleConfigurations(this)
    if (subtitleConfigurations.isNotEmpty()) {
        builder.setSubtitleConfigurations(subtitleConfigurations)
    }
    return builder.build()
}
