package com.nordic.mediahub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nordic.mediahub.api.NavidromeSong
import com.nordic.mediahub.data.AudiobookBookmark
import com.nordic.mediahub.data.AudiobookBookmarkRepository
import com.nordic.mediahub.data.ConfigRepository
import com.nordic.mediahub.data.AudiobookShelfConfig
import com.nordic.mediahub.data.AudiobookPlaybackSession
import com.nordic.mediahub.data.AudiobookShelfRepository
import com.nordic.mediahub.data.EmbyRepository
import com.nordic.mediahub.data.MusicLyrics
import com.nordic.mediahub.data.NavidromeConfig
import com.nordic.mediahub.data.NavidromeRepository
import com.nordic.mediahub.data.VideoItem
import com.nordic.mediahub.data.VideoServerConfig
import com.nordic.mediahub.data.isReadyForAudiobookSync
import com.nordic.mediahub.data.isReadyForMusicSync
import com.nordic.mediahub.data.isReadyForVideoSync
import com.nordic.mediahub.playback.AudiobookPlaybackEngine
import com.nordic.mediahub.playback.MusicPlaybackEngine
import com.nordic.mediahub.playback.VideoPlaybackEngine
import com.nordic.mediahub.ui.*
import com.nordic.mediahub.ui.theme.*
import coil.Coil
import coil.ImageLoader
import coil.request.CachePolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import android.graphics.Color as AndroidColor

private const val BOTTOM_DOCK_REVEAL_DELAY_MS = 650L
private const val BOTTOM_DOCK_ENTER_ANIMATION_MS = 260
private const val BOTTOM_DOCK_EXIT_ANIMATION_MS = 150
private const val BOTTOM_DOCK_ENTER_FADE_DELAY_MS = 40

internal fun resolveAudiobookProgressSyncBaselineSeconds(
    statePositionSeconds: Int,
    session: AudiobookPlaybackSession
): Int {
    return maxOf(
        0,
        statePositionSeconds,
        session.startTimeSeconds,
        session.currentTimeSeconds
    )
}

internal fun resolveAudiobookProgressSyncPositionSeconds(
    statePositionSeconds: Int,
    lastSyncedPositionSeconds: Int
): Int {
    return maxOf(
        0,
        statePositionSeconds,
        lastSyncedPositionSeconds
    )
}

internal fun resolveVideoProgressSyncBaselineSeconds(
    statePositionSeconds: Int,
    video: VideoItem
): Int {
    return maxOf(
        0,
        statePositionSeconds,
        video.playbackPositionSeconds
    )
}

internal enum class AudiobookPlayRequestAction {
    StartNewSession,
    ReuseCurrentSession,
    CloseCurrentSessionBeforeStart
}

internal fun resolveAudiobookPlayRequestAction(
    currentSession: AudiobookPlaybackSession?,
    requestedLibraryItemId: String
): AudiobookPlayRequestAction {
    return when {
        currentSession == null -> AudiobookPlayRequestAction.StartNewSession
        currentSession.libraryItemId == requestedLibraryItemId -> AudiobookPlayRequestAction.ReuseCurrentSession
        else -> AudiobookPlayRequestAction.CloseCurrentSessionBeforeStart
    }
}

internal data class AudiobookCloseFailurePresentation(
    val showPlayer: Boolean,
    val errorMessage: String?
)

internal fun resolveAudiobookCloseFailurePresentation(
    closeFailureMessage: String,
    reopenPlayerOnFailure: Boolean
): AudiobookCloseFailurePresentation {
    return if (reopenPlayerOnFailure) {
        AudiobookCloseFailurePresentation(
            showPlayer = true,
            errorMessage = closeFailureMessage
        )
    } else {
        AudiobookCloseFailurePresentation(
            showPlayer = false,
            errorMessage = null
        )
    }
}

private class BottomDockRevealController {
    var revealJob: Job? = null
}

@Composable
private fun AnimatedBottomDock(
    visible: Boolean,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            tween(
                durationMillis = BOTTOM_DOCK_ENTER_ANIMATION_MS,
                delayMillis = BOTTOM_DOCK_ENTER_FADE_DELAY_MS,
                easing = FastOutSlowInEasing
            )
        ) + slideInVertically(
            animationSpec = tween(
                durationMillis = BOTTOM_DOCK_ENTER_ANIMATION_MS,
                easing = FastOutSlowInEasing
            ),
            initialOffsetY = { it / 3 }
        ) + scaleIn(
            animationSpec = tween(
                durationMillis = BOTTOM_DOCK_ENTER_ANIMATION_MS,
                easing = FastOutSlowInEasing
            ),
            initialScale = 0.985f,
            transformOrigin = TransformOrigin(0.5f, 1f)
        ) + expandVertically(
            animationSpec = tween(
                durationMillis = BOTTOM_DOCK_ENTER_ANIMATION_MS,
                easing = FastOutSlowInEasing
            ),
            expandFrom = Alignment.Bottom
        ),
        exit = fadeOut(
            tween(
                durationMillis = BOTTOM_DOCK_EXIT_ANIMATION_MS,
                easing = FastOutSlowInEasing
            )
        ) + slideOutVertically(
            animationSpec = tween(
                durationMillis = BOTTOM_DOCK_EXIT_ANIMATION_MS,
                easing = FastOutSlowInEasing
            ),
            targetOffsetY = { (it * 2) / 3 }
        ) + scaleOut(
            animationSpec = tween(
                durationMillis = BOTTOM_DOCK_EXIT_ANIMATION_MS,
                easing = FastOutSlowInEasing
            ),
            targetScale = 0.985f,
            transformOrigin = TransformOrigin(0.5f, 1f)
        ) + shrinkVertically(
            animationSpec = tween(
                durationMillis = BOTTOM_DOCK_EXIT_ANIMATION_MS,
                easing = FastOutSlowInEasing
            ),
            shrinkTowards = Alignment.Bottom
        )
    ) {
        content()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .okHttpClient {
                    OkHttpClient.Builder()
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(35, TimeUnit.SECONDS)
                        .callTimeout(45, TimeUnit.SECONDS)
                        .build()
                }
                .memoryCachePolicy(CachePolicy.ENABLED)
                .crossfade(true)
                .build()
        )
        enableEdgeToEdge()
        setContent {
            val isSystemDark = isSystemInDarkTheme()
            var isDark by remember { mutableStateOf(isSystemDark) }
            SideEffect {
                val barStyle = if (isDark) {
                    SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                } else {
                    SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                }
                enableEdgeToEdge(
                    statusBarStyle = barStyle,
                    navigationBarStyle = barStyle
                )
            }
            NordicTheme(darkTheme = isDark) {
                MainScreen(isDark) { isDark = it }
            }
        }
    }
}


@Composable
private fun TabContent(
    tab: Int,
    isDark: Boolean,
    onThemeToggle: (Boolean) -> Unit,
    colorScheme: ColorScheme,
    onSongSelected: (List<NavidromeSong>, Int) -> Unit,
    audiobookConfig: AudiobookShelfConfig,
    audiobookRepository: AudiobookShelfRepository?,
    audiobookPlaybackEngine: AudiobookPlaybackEngine,
    playbackEngine: MusicPlaybackEngine,
    scope: CoroutineScope,
    onAudiobookPlaybackError: (String?) -> Unit,
    onShowAudiobookPlayer: () -> Unit,
    onHidePlayer: () -> Unit,
    onHideVideoPlayer: () -> Unit,
    onAudiobookSeekToChapter: (Int) -> Unit = {},
    onShowVideoDetail: (VideoItem) -> Unit = {},
    downloadManager: MusicDownloadManager = MusicDownloadManager(LocalContext.current),
    currentPlayingSong: NavidromeSong? = null,
    isMusicPlaying: Boolean = false,
    onPlayPause: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    onOpenPlayer: (() -> Unit)? = null,
    playHistoryEntries: List<PlayHistoryEntry> = emptyList()
) {
    val tabContext = LocalContext.current
    val tabConfigRepository = remember(tabContext) { ConfigRepository(tabContext) }
    when (tab) {
        0 -> MusicScreenV2(
            isDark = isDark,
            onThemeToggle = onThemeToggle,
            onSongSelected = onSongSelected,
            downloadManager = downloadManager,
            onAddToQueue = { song -> playbackEngine.addToQueue(song) },
            currentPlayingSong = currentPlayingSong,
            isMusicPlaying = isMusicPlaying,
            onPlayPause = onPlayPause,
            onNext = { playbackEngine.seekToNext() },
            onOpenPlayer = onOpenPlayer,
            playHistoryEntries = playHistoryEntries
        )
        1 -> AudiobookScreen(
            colorScheme = colorScheme,
            isDark = isDark,
            onThemeToggle = onThemeToggle,
            onPlayAudiobook = { item ->
                if (!audiobookConfig.isReadyForAudiobookSync()) {
                    onAudiobookPlaybackError("未配置 AudiobookShelf")
                    return@AudiobookScreen
                }
                scope.launch {
                    onAudiobookPlaybackError(null)
                    runCatching {
                        audiobookRepository?.startPlayback(item.id)
                            ?: error("未配置 AudiobookShelf")
                    }.onSuccess { session ->
                        scope.launch { tabConfigRepository.saveLastAudiobookItemId(item.id) }
                        playbackEngine.stop()
                        onHideVideoPlayer()
                        audiobookPlaybackEngine.play(session)
                        onShowAudiobookPlayer()
                        onHidePlayer()
                    }.onFailure { error ->
                        onAudiobookPlaybackError(error.message ?: "启动有声书播放失败")
                    }
                }
            },
            onSeekToChapter = onAudiobookSeekToChapter
        )
        2 -> VideoScreen(colorScheme, isDark, onThemeToggle, onShowVideoDetail = onShowVideoDetail)
    }
}
@Composable
fun MainScreen(isDark: Boolean, onThemeToggle: (Boolean) -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }
    var showPlayer by remember { mutableStateOf(false) }
    var showAudiobookPlayer by remember { mutableStateOf(false) }
    var showVideoPlayer by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showEqualizerSheet by remember { mutableStateOf(false) }
    var smartRadioMessage by remember { mutableStateOf<String?>(null) }
    var audiobookPlaybackError by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val playbackEngine = remember { MusicPlaybackEngine(context) }
    val audiobookPlaybackEngine = remember { AudiobookPlaybackEngine(context) }
    val videoPlaybackEngine = remember { VideoPlaybackEngine(context) }
    val configRepository = remember { ConfigRepository(context) }
    val videoConfig by configRepository.videoConfig.collectAsStateWithLifecycle(VideoServerConfig())
    val embyRepository = remember(videoConfig) {
        if (videoConfig.isReadyForVideoSync()) EmbyRepository(videoConfig) else null
    }
    val musicDownloadManager = remember { MusicDownloadManager(context) }
    val playbackEngine = remember { MusicPlaybackEngine(context, musicDownloadManager) }
    val audiobookPlaybackEngine = remember { AudiobookPlaybackEngine(context) }
    var autoPlayNextTriggered by remember { mutableStateOf(false) }
    var videoEpisodeQueue by remember { mutableStateOf<VideoEpisodeQueue?>(null) }
    var isLoadingNextVideoEpisode by remember { mutableStateOf(false) }
    var videoPlaybackError by remember { mutableStateOf<String?>(null) }

    val videoPlaybackEngine = remember {
        VideoPlaybackEngine(
            context,
            onPlaybackStart = { itemId, mediaSourceId, playSessionId, _ ->
                autoPlayNextTriggered = false
                embyRepository?.reportPlaybackStart(itemId, mediaSourceId, playSessionId)
            },
            onPlaybackProgress = { itemId, mediaSourceId, playSessionId, posSec ->
                embyRepository?.reportPlaybackProgress(itemId, mediaSourceId, playSessionId, posSec)
            },
            onPlaybackStopped = { itemId, mediaSourceId, playSessionId, posSec ->
                embyRepository?.reportPlaybackStopped(itemId, mediaSourceId, playSessionId, posSec)
            }
            // Auto-play next episode is handled via VideoPlaybackState observation
            // rather than a direct callback, to avoid forward-reference issues.
        )
    }
    val navidromeConfig by configRepository.navidromeConfig.collectAsStateWithLifecycle(NavidromeConfig())
    val audiobookConfig by configRepository.audiobookConfig.collectAsStateWithLifecycle(AudiobookShelfConfig())
    val videoConfig by configRepository.videoConfig.collectAsStateWithLifecycle(VideoServerConfig())
    val navidromeRepository = remember(navidromeConfig) {
        if (navidromeConfig.isReadyForMusicSync()) {
            NavidromeRepository(navidromeConfig)
        } else {
            null
        }
    }
    val audiobookRepository = remember(audiobookConfig) {
        if (audiobookConfig.isReadyForAudiobookSync()) {
            AudiobookShelfRepository(audiobookConfig)
        } else {
            null
        }
    }
    val embyRepository = remember(videoConfig) {
        if (videoConfig.isReadyForVideoSync()) {
            EmbyRepository(videoConfig)
        } else {
            null
        }
    }
    DisposableEffect(playbackEngine) {
        onDispose { playbackEngine.release() }
    }
    DisposableEffect(audiobookPlaybackEngine) {
        onDispose { audiobookPlaybackEngine.release() }
    }
    DisposableEffect(videoPlaybackEngine) {
        onDispose { videoPlaybackEngine.release() }
    }
    val playbackState by playbackEngine.state.collectAsStateWithLifecycle()
    val audiobookPlaybackState by audiobookPlaybackEngine.state.collectAsStateWithLifecycle()
    val videoPlaybackState by videoPlaybackEngine.state.collectAsStateWithLifecycle()
    val currentSong = playbackState.currentSong
    val isPlaying = playbackState.isPlaying
    var lyrics by remember { mutableStateOf<MusicLyrics?>(null) }
    var isLyricsLoading by remember { mutableStateOf(false) }
    var lyricsError by remember { mutableStateOf<String?>(null) }
    val colorScheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    val playHistoryRepository = remember { PlayHistoryRepository(context) }
    var playHistoryEntries by remember { mutableStateOf(emptyList<PlayHistoryEntry>()) }
    val audiobookBookmarkRepository = remember { AudiobookBookmarkRepository(context) }
    var audiobookBookmarks by remember { mutableStateOf(emptyList<AudiobookBookmark>()) }

    LaunchedEffect(Unit) {
        while (true) {
            playHistoryEntries = runCatching { playHistoryRepository.load() }.getOrDefault(emptyList())
            delay(30_000)
        }
    }

    val onPlayPause = {
        if (currentSong == null) {
            showPlayer = true
        } else {
            playbackEngine.togglePlayPause()
        }
    }
    val playbackStatus by remember { derivedStateOf {
        when {
            playbackState.errorMessage != null -> playbackState.errorMessage
            playbackState.isBuffering -> "正在缓冲"
            else -> null
        }
    } }

    var bottomDockVisible by remember { mutableStateOf(true) }
    val bottomDockRevealController = remember { BottomDockRevealController() }

    fun scheduleBottomDockReveal() {
        bottomDockRevealController.revealJob?.cancel()
        bottomDockRevealController.revealJob = scope.launch {
            delay(BOTTOM_DOCK_REVEAL_DELAY_MS)
            bottomDockVisible = true
        }
    }

    fun hideBottomDockForScroll(scheduleReveal: Boolean) {
        if (showPlayer || showAudiobookPlayer || showVideoPlayer) return
        if (bottomDockVisible) {
            bottomDockVisible = false
        }
        if (scheduleReveal) {
            scheduleBottomDockReveal()
        } else {
            bottomDockRevealController.revealJob?.cancel()
        }
    }

    val bottomDockScrollConnection = remember(showPlayer, showAudiobookPlayer, showVideoPlayer) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y != 0f) {
                    hideBottomDockForScroll(scheduleReveal = true)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (consumed.y != 0f || available.y != 0f) {
                    hideBottomDockForScroll(scheduleReveal = true)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (available.y != 0f) {
                    hideBottomDockForScroll(scheduleReveal = false)
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (consumed.y != 0f || available.y != 0f) {
                    scheduleBottomDockReveal()
                }
                return Velocity.Zero
            }
        }
    }

    LaunchedEffect(selectedTab, showPlayer, showAudiobookPlayer, showVideoPlayer) {
        bottomDockRevealController.revealJob?.cancel()
        bottomDockVisible = true
    }

    DisposableEffect(Unit) {
        onDispose {
            bottomDockRevealController.revealJob?.cancel()
        }
    }

    fun closeAudiobookPlayback() {
        val currentState = audiobookPlaybackEngine.state.value
        val session = currentState.session
        val positionSeconds = if (session != null) {
            resolveAudiobookProgressSyncBaselineSeconds(
                statePositionSeconds = currentState.positionSeconds,
                session = session
            )
        } else {
            currentState.positionSeconds.coerceAtLeast(0)
        }
        val repo = audiobookRepository
        audiobookPlaybackError = null

        if (session != null && repo != null) {
            scope.launch {
                runCatching {
                    repo.syncAndCloseSession(session, positionSeconds)
                }.onFailure { error ->
                    val presentation = resolveAudiobookCloseFailurePresentation(
                        closeFailureMessage = error.message ?: "关闭有声书播放会话失败",
                        reopenPlayerOnFailure = false
                    )
                    audiobookPlaybackError = presentation.errorMessage
                    showAudiobookPlayer = presentation.showPlayer
                }
            }
        }
    }

    fun closeAudiobookPlaybackAfterSync() {
        val currentState = audiobookPlaybackEngine.state.value
        val session = currentState.session
        val positionSeconds = if (session != null) {
            resolveAudiobookProgressSyncBaselineSeconds(
                statePositionSeconds = currentState.positionSeconds,
                session = session
            )
        } else {
            currentState.positionSeconds.coerceAtLeast(0)
        }
        val repo = audiobookRepository
        showAudiobookPlayer = false
        audiobookPlaybackError = null

        if (session == null || repo == null) {
            audiobookPlaybackEngine.stop()
            return
        }

        scope.launch {
            runCatching {
                repo.syncAndCloseSession(session, positionSeconds)
            }.onSuccess {
                audiobookPlaybackEngine.stop()
            }.onFailure { error ->
                val presentation = resolveAudiobookCloseFailurePresentation(
                    closeFailureMessage = error.message ?: "Close audiobook playback session failed",
                    reopenPlayerOnFailure = true
                )
                audiobookPlaybackError = presentation.errorMessage
                showAudiobookPlayer = presentation.showPlayer
            }
        }
    }

    fun closeVideoPlayback() {
        val currentState = videoPlaybackEngine.state.value
        val video = currentState.video
        val repo = embyRepository
        showVideoPlayer = false

        if (video != null && repo != null && !video.streamUrl.isNullOrBlank()) {
            val positionSeconds = resolveVideoProgressSyncBaselineSeconds(
                statePositionSeconds = currentState.positionSeconds,
                video = video
            )
            scope.launch {
                runCatching {
                    repo.stopPlaybackProgress(video, positionSeconds)
                }
            }
        }
        videoPlaybackEngine.stop()
    }

    LaunchedEffect(
        currentSong?.id,
        navidromeConfig.serverUrl,
        navidromeConfig.username,
        navidromeConfig.password
    ) {
        val song = currentSong
        lyrics = null
        lyricsError = null

        if (song == null) {
            isLyricsLoading = false
            return@LaunchedEffect
        }

        val repo = navidromeRepository
        if (repo == null) {
            isLyricsLoading = false
            lyricsError = "未配置 Navidrome"
            return@LaunchedEffect
        }

        isLyricsLoading = true
        val loadedLyrics = runCatching {
            repo.getLyrics(song)
        }.getOrNull()
        lyrics = loadedLyrics
        lyricsError = if (loadedLyrics == null) "暂无歌词" else null
        isLyricsLoading = false
    }

    // Scrobbling: now-playing + submission when >=50% or >=4min played
    LaunchedEffect(currentSong?.id, navidromeRepository) {
        val song = currentSong ?: return@LaunchedEffect
        val songId = song.id
        val repo = navidromeRepository ?: return@LaunchedEffect

        // Mark now-playing
        runCatching { repo.scrobble(songId, submission = false) }

        var hasSubmitted = false
        while (true) {
            delay(10_000)
            if (playbackState.currentSong?.id != songId) return@LaunchedEffect
            if (hasSubmitted) continue

            val position = playbackState.positionSeconds
            val duration = playbackState.durationSeconds.takeIf { it > 0 } ?: song.duration
            val playedRatio = if (duration > 0) {
                position.toFloat() / duration.toFloat()
            } else {
                0f
            }
            if (playedRatio >= 0.5f || position >= 240) {
                runCatching { repo.scrobble(songId, submission = true) }
                playHistoryRepository.recordPlay(songId)
                playHistoryEntries = runCatching { playHistoryRepository.load() }.getOrDefault(playHistoryEntries)
                hasSubmitted = true
            }
        }
    }

    LaunchedEffect(
        audiobookPlaybackState.session?.sessionId,
        audiobookRepository
    ) {
        val initialSession = audiobookPlaybackState.session ?: return@LaunchedEffect
        val repo = audiobookRepository ?: return@LaunchedEffect
        var lastSyncedPosition = resolveAudiobookProgressSyncBaselineSeconds(
            statePositionSeconds = audiobookPlaybackState.positionSeconds,
            session = initialSession
        )

        while (true) {
            delay(30_000)
            val currentState = audiobookPlaybackEngine.state.value
            val currentSession = currentState.session ?: return@LaunchedEffect
            if (currentSession.sessionId != initialSession.sessionId) {
                return@LaunchedEffect
            }

            val currentPosition = resolveAudiobookProgressSyncPositionSeconds(
                statePositionSeconds = currentState.positionSeconds,
                lastSyncedPositionSeconds = lastSyncedPosition
            )
            val deltaSeconds = (currentPosition - lastSyncedPosition).coerceAtLeast(0)
            runCatching {
                repo.syncProgress(currentSession, currentPosition, deltaSeconds)
            }.onSuccess {
                lastSyncedPosition = currentPosition
            }.onFailure { error ->
                if (showAudiobookPlayer) {
                    audiobookPlaybackError = error.message ?: "同步有声书进度失败"
                }
            }
        }
    }

    LaunchedEffect(
        videoPlaybackState.video?.id,
        embyRepository
    ) {
        val initialVideo = videoPlaybackState.video ?: return@LaunchedEffect
        val repo = embyRepository ?: return@LaunchedEffect
        if (initialVideo.streamUrl.isNullOrBlank()) return@LaunchedEffect
        var lastSyncedPosition = resolveVideoProgressSyncBaselineSeconds(
            statePositionSeconds = videoPlaybackState.positionSeconds,
            video = initialVideo
        )

        while (true) {
            delay(30_000)
            val currentState = videoPlaybackEngine.state.value
            val currentVideo = currentState.video ?: return@LaunchedEffect
            if (currentVideo.id != initialVideo.id) {
                return@LaunchedEffect
            }
            if (currentVideo.streamUrl.isNullOrBlank()) {
                return@LaunchedEffect
            }

            val currentPosition = maxOf(currentState.positionSeconds, lastSyncedPosition)
            runCatching {
                repo.syncPlaybackProgress(
                    video = currentVideo,
                    positionSeconds = currentPosition,
                    isPaused = !currentState.isPlaying
                )
            }.onSuccess {
                lastSyncedPosition = currentPosition
            }
        }
    }

    Scaffold(
        containerColor = colorScheme.background,
        bottomBar = {
            AnimatedBottomDock(
                visible = !showPlayer && !showAudiobookPlayer && !showVideoPlayer && bottomDockVisible
            ) {
                PolishedPlaybackDock(
                    selected = selectedTab,
                    colorScheme = colorScheme,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    playbackStatus = playbackStatus,
                    onOpenPlayer = { showPlayer = true },
                    onPlayPause = onPlayPause,
                    onSelect = { selectedTab = it }
                )
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .nestedScroll(bottomDockScrollConnection)
        ) {
            AnimatedContent(
                targetState = when {
                    showVideoPlayer -> "video"
                    showPlayer -> "music"
                    else -> "tabs"
                },
                transitionSpec = {
                    fadeIn(tween(300, easing = FastOutSlowInEasing)) togetherWith
                        fadeOut(tween(200))
                }
            ) { playerSurface ->
                if (playerSurface == "video") {
                    VideoPlayerScreen(
                        state = videoPlaybackState,
                        player = videoPlaybackEngine.player,
                        colorScheme = colorScheme,
                        onSeek = videoPlaybackEngine::seekTo,
                        onPlayPause = videoPlaybackEngine::togglePlayPause,
                        onSpeedChange = videoPlaybackEngine::setSpeed,
                        onSelectAudioTrack = videoPlaybackEngine::selectAudioTrack,
                        onSelectSubtitleTrack = videoPlaybackEngine::selectSubtitleTrack,
                        onSubtitleScaleChange = videoPlaybackEngine::setSubtitleScale,
                        onSeekBy = videoPlaybackEngine::seekBy,
                        onSeekIntervalChange = videoPlaybackEngine::setSeekInterval,
                        hasPreviousEpisode = videoEpisodeQueue?.hasPrevious == true,
                        hasNextEpisode = videoEpisodeQueue?.hasNext == true,
                        isLoadingNextEpisode = isLoadingNextVideoEpisode,
                        externalError = videoPlaybackError,
                        onPlayPreviousEpisode = ::playPreviousVideoEpisode,
                        onPlayNextEpisode = ::playNextVideoEpisode,
                        onClose = {
                            showVideoPlayer = false
                            videoEpisodeQueue = null
                            videoPlaybackError = null
                            autoPlayNextTriggered = false
                            videoPlaybackEngine.stop()
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (playerSurface == "music") {
                    MusicPlayerScreen(
                        song = currentSong,
                        colorScheme = colorScheme,
                        isPlaying = isPlaying,
                        isBuffering = playbackState.isBuffering,
                        playbackError = playbackState.errorMessage,
                        positionSeconds = playbackState.positionSeconds,
                        durationSeconds = playbackState.durationSeconds,
                        lyrics = lyrics,
                        isLyricsLoading = isLyricsLoading,
                        lyricsError = lyricsError,
                        repeatMode = playbackState.repeatMode,
                        shuffleModeEnabled = playbackState.shuffleModeEnabled,
                        onSeek = playbackEngine::seekTo,
                        onPlayPause = onPlayPause,
                        onClose = { showPlayer = false },
                        onSeekToNext = playbackEngine::seekToNext,
                        onSeekToPrevious = playbackEngine::seekToPrevious,
                        onToggleRepeat = playbackEngine::toggleRepeatMode,
                        onToggleShuffle = playbackEngine::toggleShuffleMode,
                        onOpenQueue = { showQueueSheet = true },
                        onOpenEqualizer = { showEqualizerSheet = true },
                        onSmartRadio = {
                            val song = currentSong
                            if (song != null) {
                                scope.launch {
                                    val repo = navidromeRepository
                                    if (repo == null) {
                                        smartRadioMessage = "未配置 Navidrome"
                                        return@launch
                                    }
                                    try {
                                        var radioSongs = repo.getSimilarSongs(song.id)
                                        if (radioSongs.isEmpty()) {
                                            radioSongs = repo.getRandomSongs(20)
                                        }
                                        if (radioSongs.isNotEmpty()) {
                                            playbackEngine.addToQueue(radioSongs)
                                            smartRadioMessage = "已添加 ${radioSongs.size} 首相似歌曲到队列"
                                        } else {
                                            smartRadioMessage = "未找到相似歌曲"
                                        }
                                    } catch (e: Exception) {
                                        smartRadioMessage = "获取相似歌曲失败: ${e.message}"
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        AnimatedContent(
                            targetState = selectedTab,
                            transitionSpec = {
                                fadeIn(tween(300, easing = FastOutSlowInEasing)) togetherWith
                                    fadeOut(tween(200))
                            }
                        ) { tab ->
                            when (tab) {
                                0 -> MusicScreenV2(
                                    isDark = isDark,
                                    onThemeToggle = onThemeToggle,
                                    onSongSelected = { songs, index, allowUnplayableStartFallback ->
                                        closeAudiobookPlayback()
                                        closeVideoPlayback()
                                        playbackEngine.playQueue(
                                            songs = songs,
                                            startIndex = index,
                                            allowUnplayableStartFallback = allowUnplayableStartFallback
                                        )
                                        showPlayer = true
                                    }
                                )
                                1 -> AudiobookScreen(
                                    colorScheme = colorScheme,
                                    isDark = isDark,
                                    onThemeToggle = onThemeToggle,
                                    onPlayAudiobook = { item ->
                                        if (!audiobookConfig.isReadyForAudiobookSync()) {
                                            audiobookPlaybackError = "未配置 AudiobookShelf"
                                            return@AudiobookScreen
                                        }
                                        when (
                                            resolveAudiobookPlayRequestAction(
                                                currentSession = audiobookPlaybackEngine.state.value.session,
                                                requestedLibraryItemId = item.id
                                            )
                                        ) {
                                            AudiobookPlayRequestAction.ReuseCurrentSession -> {
                                                audiobookPlaybackError = null
                                                playbackEngine.stop()
                                                closeVideoPlayback()
                                                showAudiobookPlayer = true
                                                showVideoPlayer = false
                                                showPlayer = false
                                                return@AudiobookScreen
                                            }
                                            AudiobookPlayRequestAction.CloseCurrentSessionBeforeStart -> {
                                                closeAudiobookPlayback()
                                            }
                                            AudiobookPlayRequestAction.StartNewSession -> Unit
                                        }
                                        scope.launch {
                                            audiobookPlaybackError = null
                                            runCatching {
                                                audiobookRepository?.startPlayback(item.id)
                                                    ?: error("未配置 AudiobookShelf")
                                            }.onSuccess { session ->
                                                playbackEngine.stop()
                                                closeVideoPlayback()
                                                audiobookPlaybackEngine.play(session)
                                                showAudiobookPlayer = true
                                                showVideoPlayer = false
                                                showPlayer = false
                                            }.onFailure { error ->
                                                audiobookPlaybackError = error.message ?: "启动有声书播放失败"
                                            }
                                        }
                                    }
                                )
                                2 -> VideoScreen(
                                    colorScheme = colorScheme,
                                    isDark = isDark,
                                    onThemeToggle = onThemeToggle,
                                    onPlayVideo = { video ->
                                        closeAudiobookPlayback()
                                        playbackEngine.stop()
                                        val currentVideo = videoPlaybackEngine.state.value.video
                                        if (currentVideo != null && currentVideo.id != video.id) {
                                            closeVideoPlayback()
                                        }
                                        videoPlaybackEngine.play(video)
                                        showPlayer = false
                                        showVideoPlayer = true
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAudiobookPlayer || audiobookPlaybackError != null) {
        AudiobookPlayerScreen(
            state = audiobookPlaybackState,
            colorScheme = colorScheme,
            externalError = audiobookPlaybackError,
            speed = audiobookPlaybackState.speed,
            currentChapterIndex = audiobookPlaybackState.currentChapterIndex,
            sleepTimerRemainingSeconds = audiobookPlaybackState.sleepTimerRemainingSeconds,
            onSeek = audiobookPlaybackEngine::seekTo,
            onSeekBack = { audiobookPlaybackEngine.seekBackBy() },
            onSeekForward = { audiobookPlaybackEngine.seekForwardBy() },
            onSeekToPreviousChapter = audiobookPlaybackEngine::seekToPreviousChapter,
            onSeekToNextChapter = audiobookPlaybackEngine::seekToNextChapter,
            onCyclePlaybackSpeed = audiobookPlaybackEngine::cyclePlaybackSpeed,
            onPlayPause = audiobookPlaybackEngine::togglePlayPause,
            onClose = { closeAudiobookPlaybackAfterSync() }
        )
    }

    if (showVideoPlayer || videoPlaybackState.video != null) {
        VideoPlayerScreen(
            state = videoPlaybackState,
            colorScheme = colorScheme,
            onSurfaceReady = videoPlaybackEngine::attachSurface,
            onSurfaceDisposed = videoPlaybackEngine::detachSurface,
            onSeek = videoPlaybackEngine::seekTo,
            onSeekBack = { videoPlaybackEngine.seekBackBy() },
            onSeekForward = { videoPlaybackEngine.seekForwardBy() },
            onPlayPause = videoPlaybackEngine::togglePlayPause,
            onClose = { closeVideoPlayback() },
            modifier = Modifier.fillMaxSize()
        )
    }

    videoDetailItem?.let { detailItem ->
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(tween(300)) + expandVertically(tween(300)),
            exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
        ) {
            VideoDetailScreen(
                videoItem = detailItem,
                colorScheme = colorScheme,
                onPlay = { playbackInfo ->
                    closeAudiobookPlayback()
                    playbackEngine.stop()
                    videoEpisodeQueue = null
                    videoPlaybackError = null
                    autoPlayNextTriggered = false
                    videoPlaybackEngine.stop()
                    videoPlaybackEngine.play(playbackInfo)
                    showVideoPlayer = true
                    showPlayer = false
                    showAudiobookPlayer = false
                    videoDetailItem = null
                },
                onPlayEpisode = { playbackInfo, episodeQueue ->
                    closeAudiobookPlayback()
                    playbackEngine.stop()
                    videoEpisodeQueue = episodeQueue
                    videoPlaybackError = null
                    autoPlayNextTriggered = false
                    videoPlaybackEngine.stop()
                    videoPlaybackEngine.play(playbackInfo)
                    showVideoPlayer = true
                    showPlayer = false
                    showAudiobookPlayer = false
                    videoDetailItem = null
                },
                onBack = { videoDetailItem = null },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    if (showQueueSheet) {
        MusicQueueSheet(
            queue = playbackState.queue,
            currentIndex = playbackState.queueIndex,
            colorScheme = colorScheme,
            onSeekToIndex = { index ->
                playbackEngine.seekToQueueIndex(index)
                showQueueSheet = false
            },
            onPlayNext = playbackEngine::moveQueueItemToPlayNext,
            onRemoveFromQueue = playbackEngine::removeQueueItem,
            onClearUpcoming = playbackEngine::clearUpcomingQueueItems,
            onMoveQueueItem = playbackEngine::moveQueueItem,
            onDismiss = { showQueueSheet = false }
        )
    }

    if (showEqualizerSheet) {
        MusicEqualizerSheet(
            audioSessionId = playbackEngine.audioSessionId,
            colorScheme = colorScheme,
            onDismiss = { showEqualizerSheet = false }
        )
    }

    // Smart radio snackbar
    val currentRadioMessage = smartRadioMessage
    if (currentRadioMessage != null) {
        Snackbar(
            modifier = Modifier
                .padding(bottom = 80.dp)
                .padding(horizontal = 16.dp),
            containerColor = colorScheme.surfaceVariant,
            contentColor = colorScheme.onSurface
        ) {
            Text(currentRadioMessage, fontSize = 14.sp)
        }
        LaunchedEffect(currentRadioMessage) {
            delay(3000)
            smartRadioMessage = null
        }
    }
}
