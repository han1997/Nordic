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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nordic.mediahub.data.ConfigRepository
import com.nordic.mediahub.data.MediaAuthHeaderInterceptor
import com.nordic.mediahub.data.AudiobookShelfConfig
import com.nordic.mediahub.data.AudiobookItemSummary
import com.nordic.mediahub.data.AudiobookPlaybackSession
import com.nordic.mediahub.data.NavidromeSong
import com.nordic.mediahub.data.VideoItem
import com.nordic.mediahub.data.isReadyForAudiobookSync
import com.nordic.mediahub.playback.AudiobookPlaybackViewModel
import com.nordic.mediahub.playback.MusicPlaybackViewModel
import com.nordic.mediahub.playback.VideoPlaybackViewModel
import com.nordic.mediahub.ui.*
import com.nordic.mediahub.ui.theme.*
import coil.Coil
import coil.ImageLoader
import coil.memory.MemoryCache
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import android.graphics.Color as AndroidColor

private const val BOTTOM_DOCK_ENTER_ANIMATION_MS = 260
private const val BOTTOM_DOCK_EXIT_ANIMATION_MS = 150
private const val BOTTOM_DOCK_ENTER_FADE_DELAY_MS = 40

internal enum class BottomDockPresentation {
    Hidden,
    Handle,
    Dock
}

internal fun resolveBottomDockPresentation(
    hasPlayerLayer: Boolean,
    fullDockVisible: Boolean
): BottomDockPresentation {
    return when {
        hasPlayerLayer -> BottomDockPresentation.Hidden
        fullDockVisible -> BottomDockPresentation.Dock
        else -> BottomDockPresentation.Handle
    }
}

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
    // The local player position is authoritative for progress reporting. The
    // server record is only a fallback when nothing has played locally yet
    // (e.g. an immediate close before the first position tick); taking the
    // max of both would over-report when the server progress is ahead (the
    // item was watched further on another device).
    return if (statePositionSeconds > 0) {
        statePositionSeconds
    } else {
        video.playbackPositionSeconds.coerceAtLeast(0)
    }
}

/**
 * Resolves the requested Activity orientation for the video player shell.
 * Dual-lock model: gravity never changes playback orientation. While the
 * video player is visible the orientation is always locked (portrait when
 * outside fullscreen, landscape while fullscreen); the lock follows the
 * fullscreen state only. Anything else restores system control.
 */
internal fun resolveVideoOrientationRequest(
    showVideoPlayer: Boolean,
    lockedLandscape: Boolean
): Int {
    return when {
        showVideoPlayer && lockedLandscape -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        showVideoPlayer && !lockedLandscape -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
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

internal enum class MediaPlaybackKind {
    Music,
    Audiobook,
    Video
}

internal fun resolveMediaHandoffCloseSteps(
    target: MediaPlaybackKind,
    hasMusic: Boolean,
    hasAudiobook: Boolean,
    hasVideo: Boolean,
    replaceTargetPlayback: Boolean = false
): List<MediaPlaybackKind> {
    return buildList {
        if (hasAudiobook && (target != MediaPlaybackKind.Audiobook || replaceTargetPlayback)) {
            add(MediaPlaybackKind.Audiobook)
        }
        if (hasVideo && (target != MediaPlaybackKind.Video || replaceTargetPlayback)) {
            add(MediaPlaybackKind.Video)
        }
        if (hasMusic && target != MediaPlaybackKind.Music) {
            add(MediaPlaybackKind.Music)
        }
    }
}

internal fun runMediaHandoffCloseSteps(
    steps: List<MediaPlaybackKind>,
    closeStep: (
        kind: MediaPlaybackKind,
        onClosed: () -> Unit,
        onFailed: () -> Unit
    ) -> Unit,
    onReady: () -> Unit,
    onFailed: () -> Unit
) {
    var handoffFinished = false

    fun failHandoff() {
        if (handoffFinished) return
        handoffFinished = true
        onFailed()
    }

    fun closeAt(index: Int) {
        if (handoffFinished) return
        if (index >= steps.size) {
            handoffFinished = true
            onReady()
            return
        }
        var stepFinished = false
        closeStep(
            steps[index],
            {
                if (!stepFinished) {
                    stepFinished = true
                    closeAt(index + 1)
                }
            },
            {
                if (!stepFinished) {
                    stepFinished = true
                    failHandoff()
                }
            }
        )
    }
    closeAt(0)
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
                .crossfade(160)
                .memoryCache {
                    MemoryCache.Builder(this)
                        .maxSizePercent(0.25)
                        .build()
                }
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
    // Orientation lock while the video player is visible: true = locked
    // landscape (set on fullscreen enter), false = locked portrait (set on
    // fullscreen exit or when opening outside fullscreen). Gravity never
    // rotates playback on its own; the lock follows fullscreen state only.
    var orientationLockedLandscape by rememberSaveable { mutableStateOf(false) }
    var showQueueSheet by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val density = LocalDensity.current
    val musicVM: MusicPlaybackViewModel = viewModel()
    val audiobookVM: AudiobookPlaybackViewModel = viewModel()
    val videoVM: VideoPlaybackViewModel = viewModel()
    val configRepository = remember { ConfigRepository(context) }
    val audiobookConfig by configRepository.audiobookConfig.collectAsStateWithLifecycle(AudiobookShelfConfig())
    val colorScheme = MaterialTheme.colorScheme
    var bottomDockVisible by remember { mutableStateOf(true) }

    val closeAudiobookPlayback = remember(audiobookVM) {
        { onClosed: () -> Unit, onFailed: () -> Unit ->
            audiobookVM.closeAudiobookPlayback(
                onClosed = {
                    showAudiobookPlayer = false
                    onClosed()
                },
                onFailed = {
                    showAudiobookPlayer = true
                    onFailed()
                }
            )
        }
    }
    val closeVideoPlayback = remember(videoVM) {
        { onClosed: () -> Unit, onFailed: () -> Unit ->
            videoVM.closeVideoPlayback(
                onClosed = {
                    showVideoPlayer = false
                    isFullscreen = false
                    onClosed()
                },
                onFailed = {
                    showVideoPlayer = true
                    onFailed()
                }
            )
        }
    }
    val mediaSwitchInProgress = remember { AtomicBoolean(false) }

    val runMediaHandoff = remember(
        musicVM,
        audiobookVM,
        videoVM,
        closeAudiobookPlayback,
        closeVideoPlayback
    ) {
        {
            target: MediaPlaybackKind,
            replaceTargetPlayback: Boolean,
            onReady: (releaseSwitch: () -> Unit) -> Unit,
            onFailed: () -> Unit ->
            if (mediaSwitchInProgress.compareAndSet(false, true)) {
                val steps = resolveMediaHandoffCloseSteps(
                    target = target,
                    hasMusic = musicVM.state.value.currentSong != null,
                    hasAudiobook = audiobookVM.state.value.session != null,
                    hasVideo = videoVM.state.value.video != null,
                    replaceTargetPlayback = replaceTargetPlayback
                )
                runMediaHandoffCloseSteps(
                    steps = steps,
                    closeStep = { kind, onClosed, onStepFailed ->
                        when (kind) {
                            MediaPlaybackKind.Music -> {
                                musicVM.stop()
                                onClosed()
                            }
                            MediaPlaybackKind.Audiobook -> closeAudiobookPlayback(onClosed, onStepFailed)
                            MediaPlaybackKind.Video -> closeVideoPlayback(onClosed, onStepFailed)
                        }
                    },
                    onReady = {
                        onReady { mediaSwitchInProgress.set(false) }
                    },
                    onFailed = {
                        onFailed()
                        mediaSwitchInProgress.set(false)
                    }
                )
            }
        }
    }

    val closeCurrentAudiobookPlayback = remember {
        {
            // Minimize: hide the full-screen player but keep the audiobook
            // session playing so it can be reopened from the dock now-playing bar.
            bottomDockVisible = true
            showAudiobookPlayer = false
        }
    }
    val closeCurrentAudiobookPlaybackAnyway = remember(audiobookVM) {
        {
            audiobookVM.closeAudiobookPlaybackAnyway(
                onClosed = {
                    showAudiobookPlayer = false
                    bottomDockVisible = true
                }
            )
        }
    }
    val closeCurrentVideoPlayback = remember(videoVM) {
        {
            // Closing the video player stops playback entirely (no background
            // playback / PiP): sync progress, stop the engine, then hide.
            videoVM.closeVideoPlayback(
                onClosed = {
                    showVideoPlayer = false
                    isFullscreen = false
                    bottomDockVisible = true
                },
                onFailed = {
                    // Sync failure must not trap the user in the player; the
                    // VM already stops the engine and retries in background.
                    showVideoPlayer = false
                    isFullscreen = false
                    bottomDockVisible = true
                }
            )
        }
    }
    val closeCurrentVideoPlaybackAnyway = remember(videoVM, closeCurrentVideoPlayback) {
        {
            videoVM.closeVideoPlaybackAnyway(
                onClosed = {
                    showVideoPlayer = false
                    isFullscreen = false
                    bottomDockVisible = true
                }
            )
        }
    }

    val onSongSelected = remember(musicVM, runMediaHandoff) {
        { songs: List<NavidromeSong>, index: Int, allowUnplayableStartFallback: Boolean ->
            runMediaHandoff(
                MediaPlaybackKind.Music,
                false,
                { releaseSwitch ->
                    musicVM.playQueue(
                        songs = songs,
                        startIndex = index,
                        allowUnplayableStartFallback = allowUnplayableStartFallback
                    )
                    showAudiobookPlayer = false
                    showVideoPlayer = false
                    showPlayer = true
                    releaseSwitch()
                },
                { }
            )
        }
    }
    val onPlayAudiobook = remember(audiobookVM, runMediaHandoff, audiobookConfig) {
        { item: AudiobookItemSummary ->
            if (!audiobookConfig.isReadyForAudiobookSync()) {
                audiobookVM.setError("未配置 AudiobookShelf")
            } else {
                val action = resolveAudiobookPlayRequestAction(
                    currentSession = audiobookVM.state.value.session,
                    requestedLibraryItemId = item.id
                )
                runMediaHandoff(
                    MediaPlaybackKind.Audiobook,
                    action == AudiobookPlayRequestAction.CloseCurrentSessionBeforeStart,
                    { releaseSwitch ->
                        if (action == AudiobookPlayRequestAction.ReuseCurrentSession) {
                            audiobookVM.clearError()
                            showAudiobookPlayer = true
                            showVideoPlayer = false
                            showPlayer = false
                            releaseSwitch()
                        } else {
                            audiobookVM.startPlayback(item.id) { result ->
                                result.onSuccess {
                                    showAudiobookPlayer = true
                                    showVideoPlayer = false
                                    showPlayer = false
                                }
                                releaseSwitch()
                            }
                        }
                    },
                    { }
                )
            }
        }
    }
    val onPlayVideo = remember(videoVM, runMediaHandoff) {
        { video: VideoItem ->
            val currentVideo = videoVM.state.value.video
            val keepFullscreen = showVideoPlayer && isFullscreen
            runMediaHandoff(
                MediaPlaybackKind.Video,
                currentVideo != null && currentVideo.id != video.id,
                { releaseSwitch ->
                    videoVM.clearError()
                    videoVM.play(video)
                    showPlayer = false
                    showAudiobookPlayer = false
                    showVideoPlayer = true
                    isFullscreen = keepFullscreen
                    orientationLockedLandscape = keepFullscreen
                    releaseSwitch()
                },
                { }
            )
        }
    }
    val onPlayVideoFromStart = remember(videoVM, runMediaHandoff) {
        { video: VideoItem ->
            val currentVideo = videoVM.state.value.video
            runMediaHandoff(
                MediaPlaybackKind.Video,
                currentVideo != null,
                { releaseSwitch ->
                    videoVM.clearError()
                    videoVM.playFromStart(video)
                    showPlayer = false
                    showAudiobookPlayer = false
                    showVideoPlayer = true
                    releaseSwitch()
                },
                { }
            )
        }
    }
    val openNowPlayingPlayer = remember(audiobookVM, videoVM) {
        {
            when {
                audiobookVM.state.value.session != null -> showAudiobookPlayer = true
                videoVM.state.value.video != null -> showVideoPlayer = true
                else -> showPlayer = true
            }
        }
    }

    LaunchedEffect(showAudiobookPlayer) {
        audiobookVM.setPlayerVisible(showAudiobookPlayer)
    }

    val hasPlayerLayer = showPlayer || showAudiobookPlayer || showVideoPlayer
    val bottomDockPresentation = resolveBottomDockPresentation(
        hasPlayerLayer = hasPlayerLayer,
        fullDockVisible = bottomDockVisible
    )

    fun hideBottomDockForScroll() {
        if (showPlayer || showAudiobookPlayer || showVideoPlayer) return
        if (bottomDockVisible) {
            bottomDockVisible = false
        }
    }

    val bottomDockScrollConnection = remember(showPlayer, showAudiobookPlayer, showVideoPlayer) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y != 0f) {
                    hideBottomDockForScroll()
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (consumed.y != 0f || available.y != 0f) {
                    hideBottomDockForScroll()
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (available.y != 0f) {
                    hideBottomDockForScroll()
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (consumed.y != 0f || available.y != 0f) {
                    hideBottomDockForScroll()
                }
                return Velocity.Zero
            }
        }
    }

    LaunchedEffect(selectedTab, showPlayer, showAudiobookPlayer, showVideoPlayer) {
        bottomDockVisible = true
    }

    // Single fullscreen controller: fullscreen is only meaningful while the
    // video player layer is visible. Keyed on both flags so a restored
    // `isFullscreen=true` without a player (process death) self-heals to
    // portrait with system bars shown instead of a stuck landscape shell.
    // Orientation follows the dual-lock model: landscape while fullscreen,
    // portrait otherwise, system-controlled when the player is closed.
    LaunchedEffect(isFullscreen, showVideoPlayer, orientationLockedLandscape) {
        val activity = context as? ComponentActivity ?: return@LaunchedEffect
        val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        if (isFullscreen && showVideoPlayer) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
        activity.requestedOrientation = resolveVideoOrientationRequest(
            showVideoPlayer = showVideoPlayer,
            lockedLandscape = orientationLockedLandscape
        )
    }

    // Self-heal: closing the player while fullscreen must drop the fullscreen
    // flag so the controller above restores portrait + system bars.
    LaunchedEffect(showVideoPlayer) {
        if (!showVideoPlayer && isFullscreen) {
            isFullscreen = false
        }
    }

    DisposableEffect(context) {
        onDispose {
            val activity = context as? ComponentActivity
            if (activity != null) {
                WindowInsetsControllerCompat(activity.window, activity.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    var measuredDockHeight by remember { mutableStateOf(0.dp) }

    Scaffold(
        containerColor = colorScheme.background
    ) { padding ->
        val dockBottomPadding by animateDpAsState(
            targetValue = if (bottomDockPresentation == BottomDockPresentation.Dock) {
                (measuredDockHeight - padding.calculateBottomPadding()).coerceAtLeast(0.dp)
            } else {
                0.dp
            },
            animationSpec = tween(
                NordicMotion.durationShort,
                easing = NordicMotion.easingStandard
            ),
            label = "dock-bottom-padding"
        )

        Box(
            Modifier
                .fillMaxSize()
                .nestedScroll(bottomDockScrollConnection)
        ) {
            AnimatedContent(
                targetState = showPlayer,
                transitionSpec = {
                    NordicMotion.enterSlideUp togetherWith NordicMotion.exitSlideDown
                },
                label = "music-player-toggle"
            ) { playerVisible ->
                if (playerVisible) {
                    MusicPlayerLayer(
                        musicVM = musicVM,
                        colorScheme = colorScheme,
                        onClose = { showPlayer = false },
                        onOpenQueue = { showQueueSheet = true },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(bottom = dockBottomPadding)
                    ) {
                        Crossfade(
                            targetState = selectedTab,
                            animationSpec = tween(
                                NordicMotion.durationMedium,
                                easing = NordicMotion.easingStandard
                            ),
                            label = "main-tab-crossfade"
                        ) { tab ->
                            when (tab) {
                                0 -> MusicScreenV2(
                                    isDark = isDark,
                                    onThemeToggle = onThemeToggle,
                                    onSongSelected = onSongSelected
                                )
                                1 -> AudiobookScreen(
                                    colorScheme = colorScheme,
                                    isDark = isDark,
                                    onThemeToggle = onThemeToggle,
                                    onPlayAudiobook = onPlayAudiobook
                                )
                                2 -> VideoScreen(
                                    colorScheme = colorScheme,
                                    isDark = isDark,
                                    onThemeToggle = onThemeToggle,
                                    onPlayVideo = onPlayVideo,
                                    onPlayVideoFromStart = onPlayVideoFromStart,
                                    onCatalogChanged = videoVM::setEpisodeContext
                                )
                                3 -> ServerConfigScreen(
                                    colorScheme = colorScheme,
                                    isDark = isDark,
                                    onThemeToggle = onThemeToggle
                                )
                            }
                        }
                    }
                }
            }

            // Bottom dock — overlaid on content via BottomCenter so Scaffold's
            // containerColor does not fill a full-width bottom bar area.
            Box(
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                AnimatedBottomDock(
                    visible = bottomDockPresentation == BottomDockPresentation.Dock
                ) {
                    Box(
                        modifier = Modifier.onSizeChanged { size ->
                            measuredDockHeight = with(density) { size.height.toDp() }
                        }
                    ) {
                        PlaybackDockSlot(
                            musicVM = musicVM,
                            audiobookVM = audiobookVM,
                            videoVM = videoVM,
                            selectedTab = selectedTab,
                            colorScheme = colorScheme,
                            onOpenPlayer = openNowPlayingPlayer,
                            onSelect = { selectedTab = it }
                        )
                    }
                }
                AnimatedBottomDock(
                    visible = bottomDockPresentation == BottomDockPresentation.Handle
                ) {
                    BottomDockHandle(
                        colorScheme = colorScheme,
                        onClick = { bottomDockVisible = true }
                    )
                }
            }
        }
    }

    VideoPlayerLayer(
        videoVM = videoVM,
        showVideoPlayer = showVideoPlayer,
        isFullscreen = isFullscreen,
        colorScheme = colorScheme,
        closeVideoPlayback = closeCurrentVideoPlayback,
        closeVideoPlaybackAnyway = closeCurrentVideoPlaybackAnyway,
        onPlayEpisode = onPlayVideo,
        onToggleFullscreen = {
            // Fullscreen locks landscape; leaving fullscreen restores the
            // portrait lock. No manual rotation button exists.
            orientationLockedLandscape = !isFullscreen
            isFullscreen = !isFullscreen
        }
    )

AudiobookPlayerLayer(
        audiobookVM = audiobookVM,
        showAudiobookPlayer = showAudiobookPlayer,
        colorScheme = colorScheme,
        closeAudiobookPlayback = closeCurrentAudiobookPlayback,
        closeAudiobookPlaybackAnyway = closeCurrentAudiobookPlaybackAnyway
    )

    BackHandler(enabled = showPlayer) {
        showPlayer = false
    }

    if (showQueueSheet) {
        MusicQueueLayer(
            musicVM = musicVM,
            colorScheme = colorScheme,
            onSeekToIndex = { index ->
                musicVM.seekToQueueIndex(index)
                showQueueSheet = false
            },
            onDismiss = { showQueueSheet = false }
        )
    }
}

@Composable
private fun PlaybackDockSlot(
    musicVM: MusicPlaybackViewModel,
    audiobookVM: AudiobookPlaybackViewModel,
    videoVM: VideoPlaybackViewModel,
    selectedTab: Int,
    colorScheme: ColorScheme,
    onOpenPlayer: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val musicState by musicVM.state.collectAsStateWithLifecycle()
    val audiobookState by audiobookVM.state.collectAsStateWithLifecycle()
    val videoState by videoVM.state.collectAsStateWithLifecycle()

    val audiobookSession = audiobookState.session
    val currentVideo = videoState.video
    val currentSong = musicState.currentSong

    val nowPlaying: DockNowPlayingContent? = when {
        audiobookSession != null -> DockNowPlayingContent.Audiobook(
            title = audiobookSession.displayTitle,
            author = audiobookSession.displayAuthor,
            coverUrl = audiobookSession.coverUrl
        )
        currentVideo != null -> DockNowPlayingContent.Video(currentVideo.title)
        currentSong != null -> DockNowPlayingContent.Music(currentSong)
        else -> null
    }

    val isPlaying = when {
        audiobookSession != null -> audiobookState.isPlaying
        currentVideo != null -> videoState.isPlaying
        else -> musicState.isPlaying
    }

    val playbackStatus = when {
        audiobookState.errorMessage != null -> audiobookState.errorMessage
        audiobookState.isBuffering -> "正在缓冲"
        videoState.errorMessage != null -> videoState.errorMessage
        videoState.isBuffering -> "正在缓冲"
        musicState.errorMessage != null -> musicState.errorMessage
        musicState.isBuffering -> "正在缓冲"
        else -> null
    }

    val currentOnOpenPlayer by rememberUpdatedState(onOpenPlayer)
    val onPlayPause = remember(musicVM, audiobookVM, videoVM, currentOnOpenPlayer) {
        {
            // Read live state at click time so a newly started session is not
            // shadowed by the composition-time snapshot.
            when {
                audiobookVM.state.value.session != null -> audiobookVM.togglePlayPause()
                videoVM.state.value.video != null -> videoVM.togglePlayPause()
                musicVM.state.value.currentSong != null -> musicVM.togglePlayPause()
                else -> currentOnOpenPlayer()
            }
        }
    }
    PolishedPlaybackDock(
        selected = selectedTab,
        colorScheme = colorScheme,
        nowPlaying = nowPlaying,
        isPlaying = isPlaying,
        playbackStatus = playbackStatus,
        onOpenPlayer = onOpenPlayer,
        onPlayPause = onPlayPause,
        onSelect = onSelect
    )
}

@Composable
private fun MusicPlayerLayer(
    musicVM: MusicPlaybackViewModel,
    colorScheme: ColorScheme,
    onClose: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playbackState by musicVM.state.collectAsStateWithLifecycle()
    val lyrics by musicVM.lyrics.collectAsStateWithLifecycle()
    val isLyricsLoading by musicVM.isLyricsLoading.collectAsStateWithLifecycle()
    val lyricsError by musicVM.lyricsError.collectAsStateWithLifecycle()
    MusicPlayerScreen(
        song = playbackState.currentSong,
        colorScheme = colorScheme,
        isPlaying = playbackState.isPlaying,
        isBuffering = playbackState.isBuffering,
        playbackError = playbackState.errorMessage,
        positionSeconds = playbackState.positionSeconds,
        positionMillisFlow = musicVM.positionMillis,
        durationSeconds = playbackState.durationSeconds,
        lyrics = lyrics,
        isLyricsLoading = isLyricsLoading,
        lyricsError = lyricsError,
        repeatMode = playbackState.repeatMode,
        shuffleModeEnabled = playbackState.shuffleModeEnabled,
        playbackSpeed = playbackState.playbackSpeed,
        onSeek = musicVM::seekTo,
        onPlayPause = musicVM::togglePlayPause,
        onClose = onClose,
        onSeekToNext = musicVM::seekToNext,
        onSeekToPrevious = musicVM::seekToPrevious,
        onToggleRepeat = musicVM::toggleRepeatMode,
        onToggleShuffle = musicVM::toggleShuffleMode,
        onOpenQueue = onOpenQueue,
        onToggleFavorite = musicVM::toggleFavorite,
        onSetPlaybackSpeed = musicVM::setPlaybackSpeed,
        favoriteError = musicVM.favoriteError,
        modifier = modifier
    )
}

@Composable
private fun VideoPlayerLayer(
    videoVM: VideoPlaybackViewModel,
    showVideoPlayer: Boolean,
    isFullscreen: Boolean,
    colorScheme: ColorScheme,
    closeVideoPlayback: () -> Unit,
    closeVideoPlaybackAnyway: () -> Unit,
    onPlayEpisode: (VideoItem) -> Unit,
    onToggleFullscreen: () -> Unit
) {
    val videoPlaybackState by videoVM.state.collectAsStateWithLifecycle()
    val videoPlaybackError by videoVM.error.collectAsStateWithLifecycle()
    val catalogVideos by videoVM.catalogVideos.collectAsStateWithLifecycle()

    // Fullscreen orientation/system-bars control lives in MainScreen's single
    // LaunchedEffect(isFullscreen, showVideoPlayer); no per-layer controller
    // here (duplicate writers caused unstable fullscreen behavior).

    val nextEpisode = remember(videoPlaybackState.video, catalogVideos) {
        videoPlaybackState.video?.let { current -> resolveNextVideoEpisode(current, catalogVideos) }
    }

    // Lifecycle safety net: when the player layer goes to the background with
    // a video active, push an immediate progress sync so the last position
    // survives process death before the 30s periodic loop fires.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (showVideoPlayer) {
            videoVM.syncNow()
        }
    }

    BackHandler(enabled = showVideoPlayer || videoPlaybackError != null) {
        closeVideoPlayback()
    }

    AnimatedVisibility(
        visible = showVideoPlayer || videoPlaybackError != null,
        enter = NordicMotion.enterSlideUp,
        exit = NordicMotion.exitSlideDown
    ) {
        VideoPlayerScreen(
            state = videoPlaybackState,
            colorScheme = colorScheme,
            externalError = videoPlaybackError,
            onSurfaceReady = videoVM::attachSurface,
            onSurfaceDisposed = videoVM::detachSurface,
            onSeek = videoVM::seekTo,
            onSeekRelative = { delta ->
                if (delta < 0) videoVM.seekBackBy(-delta) else videoVM.seekForwardBy(delta)
            },
            onPlayPause = videoVM::togglePlayPause,
            onCycleAspectRatio = videoVM::cycleAspectRatio,
            onSetPlaybackSpeed = videoVM::setPlaybackSpeed,
            nextEpisode = nextEpisode,
            episodeContext = catalogVideos,
            onPlayEpisode = onPlayEpisode,
            onPlayNextEpisode = {
                val target = nextEpisode ?: return@VideoPlayerScreen
                onPlayEpisode(target)
            },
            onToggleFullscreen = onToggleFullscreen,
            isFullscreen = isFullscreen,
            onClose = { closeVideoPlayback() },
            onCloseAnyway = closeVideoPlaybackAnyway,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun AudiobookPlayerLayer(
    audiobookVM: AudiobookPlaybackViewModel,
    showAudiobookPlayer: Boolean,
    colorScheme: ColorScheme,
    closeAudiobookPlayback: () -> Unit,
    closeAudiobookPlaybackAnyway: () -> Unit
) {
    val audiobookPlaybackState by audiobookVM.state.collectAsStateWithLifecycle()
    val audiobookPlaybackError by audiobookVM.error.collectAsStateWithLifecycle()
    val audiobookBookmarks by audiobookVM.bookmarks.collectAsStateWithLifecycle()

    BackHandler(enabled = showAudiobookPlayer || audiobookPlaybackError != null) {
        closeAudiobookPlayback()
    }

    AnimatedVisibility(
        visible = showAudiobookPlayer || audiobookPlaybackError != null,
        enter = NordicMotion.enterSlideUp,
        exit = NordicMotion.exitSlideDown
    ) {
        AudiobookPlayerScreen(
            state = audiobookPlaybackState,
            colorScheme = colorScheme,
            externalError = audiobookPlaybackError,
            bookmarks = audiobookBookmarks,
            onAddBookmark = { audiobookVM.addBookmarkAtCurrentPosition() },
            onDeleteBookmark = { bookmarkId -> audiobookVM.deleteBookmark(bookmarkId) },
            onSetSleepTimer = { minutes, atChapterEnd -> audiobookVM.setSleepTimer(minutes, atChapterEnd) },
            onCancelSleepTimer = { audiobookVM.cancelSleepTimer() },
            onSeek = audiobookVM::seekTo,
            onSeekBack = { audiobookVM.seekBackBy() },
            onSeekForward = { audiobookVM.seekForwardBy() },
            onSeekToPreviousChapter = audiobookVM::seekToPreviousChapter,
            onSeekToNextChapter = audiobookVM::seekToNextChapter,
            onSetPlaybackSpeed = audiobookVM::setPlaybackSpeed,
            onPlayPause = audiobookVM::togglePlayPause,
            onClose = closeAudiobookPlayback,
            onCloseAnyway = closeAudiobookPlaybackAnyway
        )
    }
}

@Composable
private fun MusicQueueLayer(
    musicVM: MusicPlaybackViewModel,
    colorScheme: ColorScheme,
    onSeekToIndex: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val playbackState by musicVM.state.collectAsStateWithLifecycle()
    MusicQueueSheet(
        queue = playbackState.queue,
        currentIndex = playbackState.queueIndex,
        colorScheme = colorScheme,
        onSeekToIndex = onSeekToIndex,
        onPlayNext = musicVM::moveQueueItemToPlayNext,
        onRemoveFromQueue = musicVM::removeQueueItem,
        onClearUpcoming = musicVM::clearUpcomingQueueItems,
        onMoveQueueItem = musicVM::moveQueueItem,
        onDismiss = onDismiss
    )
}
