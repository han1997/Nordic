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
import com.nordic.mediahub.data.AppPreferences
import com.nordic.mediahub.data.TrackLanguage
import com.nordic.mediahub.data.VideoServerType
import com.nordic.mediahub.data.ScopedMediaRegistry
import com.nordic.mediahub.data.WebDavRangeException
import com.nordic.mediahub.data.forMediaSource
import com.nordic.mediahub.data.MediaAuthHeaderInterceptor
import com.nordic.mediahub.data.VideoIntroRange
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
    val playWhenReady: Boolean = false,
    val hasEnded: Boolean = false,
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
    val selectedAudioStream: VideoStreamInfo? = null,
    val introRange: VideoIntroRange? = null,
    val subtitlesEnabled: Boolean = false,
    val canRestartFromBeginning: Boolean = false
)

/**
 * Pure intro-skip decision: whether the current playback position qualifies for
 * an intro skip. A seek into the range (position above the start with no prior
 * forward passage) still triggers so resuming inside an intro also skips; a
 * manual seek past the range never pulls the user back.
 */
internal fun shouldSkipVideoIntro(
    positionSeconds: Int,
    introRange: VideoIntroRange?,
    alreadySkipped: Boolean
): Boolean {
    if (alreadySkipped || introRange == null) return false
    if (introRange.endSeconds <= introRange.startSeconds) return false
    return positionSeconds >= introRange.startSeconds && positionSeconds < introRange.endSeconds
}

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
            .setMimeType(descriptor.mimeType)
            .build()
    }
}

internal data class ExternalSubtitleDescriptor(
    val url: String,
    val id: String,
    val label: String,
    val language: String?,
    val mimeType: String = androidx.media3.common.MimeTypes.TEXT_VTT
)

/** Pure (Uri-free) descriptor builder so the mapping stays unit-testable. */
internal fun externalSubtitleDescriptors(video: VideoItem): List<ExternalSubtitleDescriptor> {
    if (video.sourceType == VideoServerType.WEBDAV) return video.externalSubtitles.mapIndexed { index, subtitle ->
        ExternalSubtitleDescriptor(subtitle.url, "webdav-subtitle-$index", subtitle.label, subtitle.language, subtitle.mimeType)
    }
    val baseUrl = video.streamUrl ?: return emptyList()
    val streamBase = baseUrl.toHttpUrlOrNull() ?: return emptyList()
    // Rebuild from the server origin so leftover stream path segments do not
    // leak into the subtitle delivery path.
    val originBase = ScopedMediaRegistry.get(video.sourceId)?.root ?: streamBase.newBuilder()
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
                url = url.forMediaSource(video.sourceId),
                id = "emby-subtitle-${stream.index}",
                label = stream.displayTitle ?: stream.language ?: "字幕 ${stream.index}",
                language = stream.language
            )
        }
}

@androidx.annotation.OptIn(UnstableApi::class)
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
    fun skipIntro()
    fun stop()
    fun release()
}

@androidx.annotation.OptIn(UnstableApi::class)
class VideoPlaybackEngine(context: Context) : VideoPlaybackBackend {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var preferences = AppPreferences()
    private var webDavRetryUsed = false

    fun applyPreferences(value: AppPreferences) {
        preferences = value
        _state.update { it.copy(aspectRatioMode = AspectRatioMode.valueOf(value.videoAspect)) }
    }

    private fun applyTrackDefaults() {
        _state.update { it.copy(subtitlesEnabled = preferences.subtitleLanguage != TrackLanguage.OFF) }
        fun language(value: TrackLanguage): String? = when (value) {
            TrackLanguage.SYSTEM -> java.util.Locale.getDefault().language
            else -> value.language
        }
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT).clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, preferences.subtitleLanguage == TrackLanguage.OFF)
            .setPreferredTextLanguage(language(preferences.subtitleLanguage))
            .setPreferredAudioLanguage(language(preferences.audioLanguage))
            .setSelectUndeterminedTextLanguage(preferences.subtitleLanguage != TrackLanguage.OFF)
            .build()
    }
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(MediaAuthHeaderInterceptor())
                        .addNetworkInterceptor(com.nordic.mediahub.data.ScopedMediaNetworkInterceptor())
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

    /**
     * Whether the intro of the current media item has already been skipped in
     * this playback session. Reset on every media-item replacement so a replay
     * of the same episode can auto-skip again.
     */
    @Volatile
    private var introSkippedForCurrentItem: Boolean = false

    /**
     * Whether auto-skip is currently enabled (mirrors the persisted preference).
     * Manual [skipIntro] works regardless of this flag.
     */
    @Volatile
    private var autoSkipIntroEnabled: Boolean = true

    /**
     * The direct-play URL of the current item when the engine is playing a
     * transcoded stream. Null when playing direct — used to fall back once on
     * playback errors instead of surfacing them immediately.
     */
    @Volatile
    private var directPlayFallbackUrl: String? = null

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

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            publishPlayerState()
        }

        override fun onPlayerError(error: PlaybackException) {
            val currentVideo = _state.value.video
            val causes = generateSequence(error as Throwable) { it.cause }.take(12).toList()
            val http = causes.filterIsInstance<androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException>().firstOrNull()
            if (currentVideo?.sourceType == VideoServerType.WEBDAV && !webDavRetryUsed && http?.responseCode in listOf(401, 403, 410)) {
                webDavRetryUsed = true
                val position = player.currentPosition.coerceAtLeast(0L)
                _state.update { it.copy(isBuffering = true, errorMessage = null) }
                player.setMediaItem(currentVideo.toMediaItem())
                player.prepare()
                if (position > 0L) player.seekTo(position)
                player.play()
                return
            }
            val fallbackUrl = directPlayFallbackUrl
            if (fallbackUrl != null) {
                // Transcoded stream failed: retry once with the direct stream,
                // resuming at the last published position. A second failure
                // surfaces as a normal playback error.
                Log.e("VideoPlayback", "Transcoded playback failed, retrying direct stream", error)
                directPlayFallbackUrl = null
                val resumePositionMs = player.currentPosition.coerceAtLeast(0L)
                val video = _state.value.video
                if (video != null) {
                    val directVideo = video.copy(streamUrl = fallbackUrl)
                    _state.update { it.copy(isBuffering = true, errorMessage = null) }
                    player.setMediaItem(directVideo.toMediaItem())
                    player.prepare()
                    if (resumePositionMs > 0L) player.seekTo(resumePositionMs)
                    player.play()
                    return
                }
            }
            Log.e("VideoPlayback", "Playback error: ${error.errorCodeName}")
            stopPositionUpdates()
            _state.update {
                it.copy(
                    isPlaying = false,
                    isBuffering = false,
                    canRestartFromBeginning = causes.any { cause -> cause is WebDavRangeException },
                    errorMessage = when {
                        causes.any { cause -> cause is WebDavRangeException } -> "服务器不支持此位置的拖动或续播，请从头播放"
                        http?.responseCode == 401 || http?.responseCode == 403 -> "视频访问被拒绝，请检查账号权限或重新测试连接"
                        http?.responseCode == 404 -> "视频文件不存在或已被移动"
                        error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> if (currentVideo?.sourceType == VideoServerType.WEBDAV) "设备不支持此视频编码，WebDAV 不提供转码" else "设备不支持当前编码，可尝试 Emby 转码清晰度"
                        else -> "视频播放失败，请检查网络或文件格式后重试"
                    }
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
            val audio = video.availableStreamsFor(VideoStreamKind.Audio, tracks)
            val text = video.availableStreamsFor(VideoStreamKind.Subtitle, tracks)
            _state.update { it.copy(availableAudioStreams = audio, availableSubtitleStreams = text,
                selectedAudioStream = selectedVideoStream(audio, tracks),
                selectedSubtitleStream = if (it.subtitlesEnabled) selectedVideoStream(text, tracks) else null) }
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
                playbackSpeed = persistedPlaybackSpeed,
                introRange = video.introRange,
                aspectRatioMode = AspectRatioMode.valueOf(preferences.videoAspect)
            )
            introSkippedForCurrentItem = false
            webDavRetryUsed = false
            directPlayFallbackUrl = null
            applyTrackDefaults()
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
                playbackSpeed = persistedPlaybackSpeed,
                introRange = video.introRange,
                aspectRatioMode = AspectRatioMode.valueOf(preferences.videoAspect)
            )
            introSkippedForCurrentItem = false
            webDavRetryUsed = false
            directPlayFallbackUrl = null
            applyTrackDefaults()
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

    /**
     * Plays [video] through a transcoded HLS stream while keeping the original
     * direct URL as a one-shot error fallback. The displayed video keeps its
     * identity (id/metadata); only the media source URL differs.
     */
    fun playTranscoded(video: VideoItem, transcodeUrl: String, directUrl: String) {
        if (transcodeUrl.isBlank()) {
            play(video)
            return
        }
        directPlayFallbackUrl = directUrl.takeIf { it.isNotBlank() }
        val transcodedVideo = video.copy(streamUrl = transcodeUrl)
        play(transcodedVideo)
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
                applyTrackDefaults()
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
        _state.update { it.copy(selectedSubtitleStream = stream, subtitlesEnabled = stream != null) }
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
            .firstOrNull { it.mediaTrackGroup.id == (stream.trackGroupId ?: stream.index.toString()) || it.mediaTrackGroup.id.contains("emby-subtitle-${stream.index}") }
            ?.let { TrackSelectionOverride(it.mediaTrackGroup, stream.trackIndex) }
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
            .firstOrNull { it.mediaTrackGroup.id == (stream.trackGroupId ?: stream.index.toString()) || it.mediaTrackGroup.id.contains("emby-subtitle-${stream.index}") }
            ?.let { TrackSelectionOverride(it.mediaTrackGroup, stream.trackIndex) }
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

    fun applyAutoSkipIntro(enabled: Boolean) {
        autoSkipIntroEnabled = enabled
    }

    override fun skipIntro() {
        val intro = _state.value.introRange ?: return
        if (introSkippedForCurrentItem) return
        introSkippedForCurrentItem = true
        seekTo(intro.endSeconds)
    }

    override fun stop() {
        stopPositionUpdates()
        directPlayFallbackUrl = null
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

        val positionSeconds = (player.currentPosition.coerceAtLeast(0L) / 1000L).toInt()

        // Auto intro-skip: evaluated on every position publish (1s cadence while
        // playing). The skip itself is a seek, so the next publish sees the
        // position past the intro and the once-per-item flag prevents repeats.
        val introRange = _state.value.introRange
        if (introRange != null && autoSkipIntroEnabled &&
            shouldSkipVideoIntro(
                positionSeconds = positionSeconds,
                introRange = introRange,
                alreadySkipped = introSkippedForCurrentItem
            )
        ) {
            introSkippedForCurrentItem = true
            player.seekTo(introRange.endSeconds * 1000L)
        }

        _state.update {
            it.copy(
                video = video,
                isPlaying = player.isPlaying,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                playWhenReady = player.playWhenReady,
                hasEnded = player.playbackState == Player.STATE_ENDED,
                positionSeconds = positionSeconds,
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
private fun VideoItem.availableStreamsFor(kind: VideoStreamKind, tracks: Tracks): List<VideoStreamInfo> =
    availableVideoStreams(this, kind, videoTrackCandidates(tracks))

@androidx.annotation.OptIn(UnstableApi::class)
private fun selectedVideoStream(streams: List<VideoStreamInfo>, tracks: Tracks): VideoStreamInfo? =
    selectedVideoStream(streams, videoTrackCandidates(tracks))
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
