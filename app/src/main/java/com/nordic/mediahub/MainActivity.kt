package com.nordic.mediahub

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Velocity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nordic.mediahub.data.ConfigRepository
import com.nordic.mediahub.data.MediaAuthHeaderInterceptor
import com.nordic.mediahub.data.AudiobookShelfConfig
import com.nordic.mediahub.data.AudiobookPlaybackSession
import com.nordic.mediahub.data.VideoItem
import com.nordic.mediahub.data.isReadyForAudiobookSync
import com.nordic.mediahub.playback.AudiobookPlaybackViewModel
import com.nordic.mediahub.playback.MusicPlaybackViewModel
import com.nordic.mediahub.playback.VideoPlaybackViewModel
import com.nordic.mediahub.ui.*
import com.nordic.mediahub.ui.theme.*
import coil.Coil
import coil.ImageLoader
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
                        .addInterceptor(MediaAuthHeaderInterceptor())
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(35, TimeUnit.SECONDS)
                        .callTimeout(45, TimeUnit.SECONDS)
                        .build()
                }
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
fun MainScreen(isDark: Boolean, onThemeToggle: (Boolean) -> Unit) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    var showAudiobookPlayer by rememberSaveable { mutableStateOf(false) }
    var showVideoPlayer by rememberSaveable { mutableStateOf(false) }
    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    var showQueueSheet by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val musicVM: MusicPlaybackViewModel = viewModel()
    val audiobookVM: AudiobookPlaybackViewModel = viewModel()
    val videoVM: VideoPlaybackViewModel = viewModel()
    val configRepository = remember { ConfigRepository(context) }
    val audiobookConfig by configRepository.audiobookConfig.collectAsStateWithLifecycle(AudiobookShelfConfig())
    val playbackState by musicVM.state.collectAsStateWithLifecycle()
    val audiobookPlaybackState by audiobookVM.state.collectAsStateWithLifecycle()
    val videoPlaybackState by videoVM.state.collectAsStateWithLifecycle()
    val audiobookPlaybackError by audiobookVM.error.collectAsStateWithLifecycle()
    val videoPlaybackError by videoVM.error.collectAsStateWithLifecycle()
    val lyrics by musicVM.lyrics.collectAsStateWithLifecycle()
    val isLyricsLoading by musicVM.isLyricsLoading.collectAsStateWithLifecycle()
    val lyricsError by musicVM.lyricsError.collectAsStateWithLifecycle()
    val currentSong = playbackState.currentSong
    val isPlaying = playbackState.isPlaying
    val colorScheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val onPlayPause = {
        if (currentSong == null) {
            showPlayer = true
        } else {
            musicVM.togglePlayPause()
        }
    }
    val playbackStatus = when {
        playbackState.errorMessage != null -> playbackState.errorMessage
        playbackState.isBuffering -> "正在缓冲"
        else -> null
    }

    LaunchedEffect(showAudiobookPlayer) {
        audiobookVM.setPlayerVisible(showAudiobookPlayer)
    }

    fun closeAudiobookPlayback(reopenPlayerOnFailure: Boolean = false) {
        showAudiobookPlayer = false
        audiobookVM.closeAudiobookPlayback(
            reopenPlayerOnFailure = reopenPlayerOnFailure,
            onClosed = { },
            onFailed = { reopen -> if (reopen) showAudiobookPlayer = true }
        )
    }

    fun closeVideoPlayback() {
        videoVM.closeVideoPlayback(
            onClosed = { showVideoPlayer = false; isFullscreen = false },
            onFailed = { }
        )
    }

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

    LaunchedEffect(isFullscreen) {
        val activity = context as? ComponentActivity ?: return@LaunchedEffect
        val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        if (isFullscreen) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    DisposableEffect(context) {
        onDispose {
            bottomDockRevealController.revealJob?.cancel()
            val activity = context as? ComponentActivity
            if (activity != null) {
                WindowInsetsControllerCompat(activity.window, activity.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
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
                targetState = showPlayer,
                transitionSpec = {
                    fadeIn(tween(300, easing = FastOutSlowInEasing)) togetherWith
                        fadeOut(tween(200))
                }
            ) { playerVisible ->
                if (playerVisible) {
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
                        onSeek = musicVM::seekTo,
                        onPlayPause = onPlayPause,
                        onClose = { showPlayer = false },
                        onSeekToNext = musicVM::seekToNext,
                        onSeekToPrevious = musicVM::seekToPrevious,
                        onToggleRepeat = musicVM::toggleRepeatMode,
                        onToggleShuffle = musicVM::toggleShuffleMode,
                        onOpenQueue = { showQueueSheet = true },
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
                                        closeAudiobookPlayback(reopenPlayerOnFailure = false)
                                        closeVideoPlayback()
                                        musicVM.playQueue(
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
                                            audiobookVM.setError("未配置 AudiobookShelf")
                                            return@AudiobookScreen
                                        }
                                        when (
                                            resolveAudiobookPlayRequestAction(
                                                currentSession = audiobookVM.state.value.session,
                                                requestedLibraryItemId = item.id
                                            )
                                        ) {
                                            AudiobookPlayRequestAction.ReuseCurrentSession -> {
                                                audiobookVM.clearError()
                                                musicVM.stop()
                                                closeVideoPlayback()
                                                showAudiobookPlayer = true
                                                showVideoPlayer = false
                                                showPlayer = false
                                                return@AudiobookScreen
                                            }
                                            AudiobookPlayRequestAction.CloseCurrentSessionBeforeStart -> {
                                                closeAudiobookPlayback(reopenPlayerOnFailure = false)
                                            }
                                            AudiobookPlayRequestAction.StartNewSession -> Unit
                                        }
                                        audiobookVM.startPlayback(item.id) { result ->
                                            result.onSuccess {
                                                musicVM.stop()
                                                closeVideoPlayback()
                                                showAudiobookPlayer = true
                                                showVideoPlayer = false
                                                showPlayer = false
                                            }
                                        }
                                    }
                                )
                                2 -> VideoScreen(
                                    colorScheme = colorScheme,
                                    isDark = isDark,
                                    onThemeToggle = onThemeToggle,
                                    onPlayVideo = { video ->
                                        closeAudiobookPlayback(reopenPlayerOnFailure = false)
                                        musicVM.stop()
                                        val currentVideo = videoVM.state.value.video
                                        if (currentVideo != null && currentVideo.id != video.id) {
                                            closeVideoPlayback()
                                        }
                                        videoVM.clearError()
                                        videoVM.play(video)
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

    BackHandler(enabled = showVideoPlayer || videoPlaybackState.video != null) {
        closeVideoPlayback()
    }

    BackHandler(enabled = isFullscreen) {
        isFullscreen = false
    }

    BackHandler(enabled = showAudiobookPlayer || audiobookPlaybackError != null) {
        closeAudiobookPlayback(reopenPlayerOnFailure = true)
    }

    BackHandler(enabled = showPlayer) {
        showPlayer = false
    }

    if (showAudiobookPlayer || audiobookPlaybackError != null) {
        AudiobookPlayerScreen(
            state = audiobookPlaybackState,
            colorScheme = colorScheme,
            externalError = audiobookPlaybackError,
            onSeek = audiobookVM::seekTo,
            onSeekBack = { audiobookVM.seekBackBy() },
            onSeekForward = { audiobookVM.seekForwardBy() },
            onSeekToPreviousChapter = audiobookVM::seekToPreviousChapter,
            onSeekToNextChapter = audiobookVM::seekToNextChapter,
            onCyclePlaybackSpeed = audiobookVM::cyclePlaybackSpeed,
            onPlayPause = audiobookVM::togglePlayPause,
            onClose = { closeAudiobookPlayback(reopenPlayerOnFailure = true) }
        )
    }

    if (showVideoPlayer || videoPlaybackState.video != null) {
        VideoPlayerScreen(
            state = videoPlaybackState,
            colorScheme = colorScheme,
            externalError = videoPlaybackError,
            onSurfaceReady = videoVM::attachSurface,
            onSurfaceDisposed = videoVM::detachSurface,
            onSeek = videoVM::seekTo,
            onSeekBack = { videoVM.seekBackBy() },
            onSeekForward = { videoVM.seekForwardBy() },
            onPlayPause = videoVM::togglePlayPause,
            onCycleAspectRatio = videoVM::cycleAspectRatio,
            onToggleFullscreen = { isFullscreen = !isFullscreen },
            isFullscreen = isFullscreen,
            onClose = { closeVideoPlayback() },
            modifier = Modifier.fillMaxSize()
        )
    }

    if (showQueueSheet) {
        MusicQueueSheet(
            queue = playbackState.queue,
            currentIndex = playbackState.queueIndex,
            colorScheme = colorScheme,
            onSeekToIndex = { index ->
                musicVM.seekToQueueIndex(index)
                showQueueSheet = false
            },
            onPlayNext = musicVM::moveQueueItemToPlayNext,
            onRemoveFromQueue = musicVM::removeQueueItem,
            onClearUpcoming = musicVM::clearUpcomingQueueItems,
            onDismiss = { showQueueSheet = false }
        )
    }
}
