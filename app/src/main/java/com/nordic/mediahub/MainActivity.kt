package com.nordic.mediahub

import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nordic.mediahub.data.AppPreferences
import com.nordic.mediahub.data.ThemeMode
import com.nordic.mediahub.data.MediaSourceState
import com.nordic.mediahub.data.MediaSourceKind
import com.nordic.mediahub.data.MediaDomain as SourceDomain
import kotlinx.coroutines.launch
import com.nordic.mediahub.data.ConfigRepository
import com.nordic.mediahub.data.MediaAuthHeaderInterceptor
import com.nordic.mediahub.data.AudiobookShelfConfig
import com.nordic.mediahub.data.AudiobookItemSummary
import com.nordic.mediahub.data.AudiobookPlaybackSession
import com.nordic.mediahub.data.NavidromeSong
import com.nordic.mediahub.data.VideoItem
import com.nordic.mediahub.data.resolveNextVideoEpisode
import com.nordic.mediahub.data.playbackIdentity
import com.nordic.mediahub.playback.VideoAutoPlayNextRequest
import com.nordic.mediahub.playback.VideoAutoPlayNextState
import com.nordic.mediahub.data.isReadyForAudiobookSync
import com.nordic.mediahub.data.visibleMediaDomains
import com.nordic.mediahub.data.resolveVisibleMediaTab
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
import android.view.Display

private const val BOTTOM_DOCK_ENTER_ANIMATION_MS = 260
private const val BOTTOM_DOCK_EXIT_ANIMATION_MS = 150
private const val BOTTOM_DOCK_ENTER_FADE_DELAY_MS = 40

/**
 * Picks the display mode to request via `preferredDisplayModeId`. OEM "smart
 * refresh rate" policies (ColorOS/OriginOS etc.) cap third-party apps at 60Hz
 * unless the app explicitly requests a high-refresh mode. Selecting the
 * highest-refresh mode at the current resolution bypasses that cap.
 *
 * Among modes sharing the current mode's resolution, the highest refresh rate
 * wins; ties resolve to the smaller mode id (stable). Falls back to the
 * current mode id when nothing is faster.
 *
 * Takes plain mode tuples (id, width, height, refreshRate) instead of
 * `Display.Mode` so the selection logic stays unit-testable on the JVM.
 */
internal fun resolvePreferredDisplayModeId(
    modes: List<DisplayModeSpec>,
    currentModeId: Int
): Int {
    val current = modes.firstOrNull { it.id == currentModeId } ?: return currentModeId
    val best = modes.asSequence()
        .filter { it.width == current.width && it.height == current.height }
        .maxWithOrNull(
            compareBy<DisplayModeSpec> { it.refreshRate }
                .thenBy { -it.id }
        )
        ?: return currentModeId
    return if (best.refreshRate > current.refreshRate + 0.01f) best.id else currentModeId
}

internal data class DisplayModeSpec(val id: Int, val width: Int, val height: Int, val refreshRate: Float)

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
    onFailed: () -> Unit,
    canProceed: () -> Boolean = { true }
) {
    var handoffFinished = false

    fun failHandoff() {
        if (handoffFinished) return
        handoffFinished = true
        onFailed()
    }

    fun closeAt(index: Int) {
        if (handoffFinished) return
        if (!canProceed()) { failHandoff(); return }
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
    private var pipBridge = VideoPipBridgeState()
    private var appliedPipBridge: VideoPipBridgeState? = null
    internal var isInVideoPipMode by mutableStateOf(false)
        private set

    private val supportsVideoPip: Boolean
        get() = packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    internal fun updateVideoPipState(state: VideoPipBridgeState) {
        pipBridge = state
        if (!supportsVideoPip || appliedPipBridge == state) return
        try {
            // Android 12+ must receive auto-enter eligibility BEFORE the Home gesture,
            // including false when paused, errored, closed, or disabled in settings.
            setPictureInPictureParams(videoPipParams())
            appliedPipBridge = state
        } catch (_: IllegalStateException) {
            // PiP may be unavailable during a system transition. ON_STOP still stops video.
        }
    }

    private fun videoPipParams(): PictureInPictureParams = PictureInPictureParams.Builder()
        .setAspectRatio(Rational(pipBridge.aspectRatioNumerator, pipBridge.aspectRatioDenominator))
        .apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setAutoEnterEnabled(pipBridge.shouldEnterPip)
            }
        }
        .build()

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S || !supportsVideoPip ||
            !pipBridge.shouldEnterPip || isInPictureInPictureMode
        ) return
        try {
            enterPictureInPictureMode(videoPipParams())
        } catch (_: IllegalStateException) {
            // Unsupported/rejected PiP falls back to the normal ON_STOP close path.
        }
    }

    override fun onResume() {
        super.onResume()
        // Retry params rejected during a transition, including revoking stale auto-enter.
        updateVideoPipState(pipBridge)
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        isInVideoPipMode = isInPictureInPictureMode
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        // false also means expanding back into the app. Only ON_STOP indicates
        // that the video is no longer visible and must stop.
    }

    internal fun dismissVideoPip() {
        updateVideoPipState(VideoPipBridgeState())
        if (isInPictureInPictureMode) {
            // Unpin without finishing the single Activity: keep the VM alive
            // long enough to send its best-effort stopped-progress report.
            moveTaskToBack(true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isInVideoPipMode = isInPictureInPictureMode
        // Request the highest refresh rate available at the current resolution.
        // Without this, ColorOS/OriginOS "smart refresh rate" pins the app to 60Hz.
        // Context.getDisplay() is API 30+; older devices fall back to the
        // deprecated WindowManager.defaultDisplay.
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display
        } else {
            @Suppress("DEPRECATION") windowManager.defaultDisplay
        }
        if (display != null) {
            val currentModeId = display.mode.modeId
            val preferredModeId = resolvePreferredDisplayModeId(
                modes = display.supportedModes.map {
                    DisplayModeSpec(it.modeId, it.physicalWidth, it.physicalHeight, it.refreshRate)
                },
                currentModeId = currentModeId
            )
            if (preferredModeId != currentModeId) {
                window.attributes = window.attributes.apply { preferredDisplayModeId = preferredModeId }
            }
        }
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
                        .addNetworkInterceptor(com.nordic.mediahub.data.ScopedMediaNetworkInterceptor())
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
            val repository = remember { ConfigRepository(this@MainActivity) }
            val savedPreferences: AppPreferences? by repository.preferences.collectAsStateWithLifecycle(initialValue = null)
            val preferenceStorageError by repository.storageError.collectAsStateWithLifecycle()
            val settings = savedPreferences ?: AppPreferences()
            val scope = rememberCoroutineScope()
            var preferenceError by remember { mutableStateOf<String?>(null) }
            val isDark = when (settings.theme) { ThemeMode.SYSTEM -> isSystemDark; ThemeMode.DARK -> true; ThemeMode.LIGHT -> false }
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
                if (savedPreferences == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else CompositionLocalProvider(LocalAppPreferences provides settings, LocalConfigurationError provides preferenceStorageError) {
                    MainScreen(isDark) { dark -> scope.launch {
                        runCatching { repository.updatePreferences { it.copy(theme = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) } }
                            .onFailure { preferenceError = "主题设置保存失败" }
                    } }
                }
                preferenceError?.let { message -> AlertDialog(onDismissRequest = { preferenceError = null },
                    title = { Text("设置未保存") }, text = { Text(message) },
                    confirmButton = { TextButton(onClick = { preferenceError = null }) { Text("知道了") } }) }
            }
        }
    }
}

@Composable
fun MainScreen(isDark: Boolean, onThemeToggle: (Boolean) -> Unit) {
    val preferences = LocalAppPreferences.current
    var selectedTab by rememberSaveable {
        mutableStateOf(
            preferences.resolveVisibleMediaTab(
                if (preferences.startupPage.tab < 0) preferences.lastMediaTab else preferences.startupPage.tab
            )
        )
    }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var openServersRequest by rememberSaveable { mutableIntStateOf(0) }
    val tabStateHolder = rememberSaveableStateHolder()
    val navigationGuard = remember { SettingsNavigationGuard() }
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
    val sourceState by configRepository.sourceState.collectAsStateWithLifecycle(MediaSourceState())
    val configurationError by configRepository.storageError.collectAsStateWithLifecycle()
    val inheritedConfigurationError = LocalConfigurationError.current
    val musicSettingsError by musicVM.settingsError.collectAsStateWithLifecycle()
    val visibleDomains = preferences.visibleMediaDomains()
    val showMediaNav = visibleDomains.size >= 2
    LaunchedEffect(selectedTab) {
        if (selectedTab in 0..2 && selectedTab != preferences.lastMediaTab) {
            runCatching { configRepository.updatePreferences { it.copy(lastMediaTab = selectedTab) } }
        }
    }
    // When a module is hidden, fall back to the first visible module (音乐 → 有声书 → 视频).
    // Re-showing a module never auto-switches the page or restarts playback.
    LaunchedEffect(preferences.showMusic, preferences.showAudiobook, preferences.showVideo) {
        val resolved = preferences.resolveVisibleMediaTab(selectedTab)
        if (resolved != selectedTab) selectedTab = resolved
    }
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
        { onClosed: () -> Unit, onFailed: () -> Unit, autoPlayRequest: VideoAutoPlayNextRequest? ->
            videoVM.closeVideoPlayback(
                onClosed = {
                    // Keep the foreground player/fullscreen stable across an automatic handoff.
                    if (autoPlayRequest == null) {
                        showVideoPlayer = false
                        isFullscreen = false
                    }
                    onClosed()
                },
                onFailed = {
                    if (autoPlayRequest == null) showVideoPlayer = true
                    onFailed()
                },
                autoPlayRequest = autoPlayRequest
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
            autoPlayRequest: VideoAutoPlayNextRequest?,
            onReady: (releaseSwitch: () -> Unit) -> Unit,
            onFailed: () -> Unit ->
            // Explicit media choices always supersede a pending automatic request, even when busy.
            if (autoPlayRequest == null) videoVM.cancelPendingAutoPlayNext()
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
                            MediaPlaybackKind.Video -> closeVideoPlayback(onClosed, onStepFailed, autoPlayRequest)
                        }
                    },
                    onReady = {
                        onReady { mediaSwitchInProgress.set(false) }
                    },
                    onFailed = {
                        if (autoPlayRequest != null) videoVM.cancelPendingAutoPlayNext()
                        onFailed()
                        mediaSwitchInProgress.set(false)
                    },
                    canProceed = { autoPlayRequest == null || videoVM.isAutoPlayNextRequestValid(autoPlayRequest) }
                )
            } else if (autoPlayRequest != null) {
                videoVM.cancelPendingAutoPlayNext()
                onFailed()
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
            // Explicit close (including PiP dismissal) stops playback. Entering
            // PiP is not a close and keeps the same engine/surface alive.
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

    // Stops a media module before it is hidden, using the existing progress-close
    // policy. Cancels in-flight preparation (audiobook start, video auto-play-next)
    // so a late callback cannot restart a hidden module. Only the target domain is
    // stopped; other audio types are untouched.
    val hideModule = remember(musicVM, audiobookVM, videoVM) {
        { domain: SourceDomain, onStopped: () -> Unit, onFailed: (String) -> Unit ->
            when (domain) {
                SourceDomain.MUSIC -> {
                    musicVM.stop()
                    showPlayer = false
                    onStopped()
                }
                SourceDomain.AUDIOBOOK -> {
                    audiobookVM.cancelPreparation()
                    audiobookVM.closeAudiobookPlayback(
                        onClosed = { showAudiobookPlayer = false; onStopped() },
                        onFailed = { message -> onFailed(message) }
                    )
                }
                SourceDomain.VIDEO -> {
                    videoVM.cancelPendingAutoPlayNext()
                    videoVM.closeVideoPlayback(
                        onClosed = { showVideoPlayer = false; isFullscreen = false; onStopped() },
                        onFailed = { message -> onFailed(message) }
                    )
                }
            }
        }
    }

    val musicVisible by rememberUpdatedState(preferences.showMusic)
    val onSongSelected = remember(musicVM, runMediaHandoff) {
        { songs: List<NavidromeSong>, index: Int, allowUnplayableStartFallback: Boolean ->
            // A hidden music module must not restart playback from the downloaded
            // music entry point; silently ignore the request.
            if (musicVisible) {
                runMediaHandoff(
                    MediaPlaybackKind.Music,
                    false,
                    null,
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
                    null,
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
    val onPlayVideoRequest = remember(videoVM, runMediaHandoff) {
        { video: VideoItem, autoPlayRequest: VideoAutoPlayNextRequest? ->
            val currentVideo = videoVM.state.value.video
            val keepFullscreen = showVideoPlayer && isFullscreen
            runMediaHandoff(
                MediaPlaybackKind.Video,
                currentVideo != null && currentVideo.playbackIdentity() != video.playbackIdentity(),
                autoPlayRequest,
                { releaseSwitch ->
                    try {
                        if (autoPlayRequest == null || videoVM.isAutoPlayNextRequestValid(autoPlayRequest)) {
                            videoVM.clearError()
                            if (autoPlayRequest == null) videoVM.play(video) else videoVM.playAutoPlayNext(autoPlayRequest)
                            showPlayer = false
                            showAudiobookPlayer = false
                            showVideoPlayer = true
                            isFullscreen = keepFullscreen
                            orientationLockedLandscape = keepFullscreen
                        }
                    } finally {
                        releaseSwitch()
                    }
                },
                {
                    if (autoPlayRequest != null && !videoVM.hasPlayback) {
                        showVideoPlayer = false
                        isFullscreen = false
                    }
                }
            )
        }
    }
    val onPlayVideo = remember(onPlayVideoRequest) {
        { video: VideoItem -> onPlayVideoRequest(video, null) }
    }
    val onAutoPlayVideo = remember(onPlayVideoRequest) {
        { request: VideoAutoPlayNextRequest -> onPlayVideoRequest(request.next, request) }
    }
    val onPlayVideoFromStart = remember(videoVM, runMediaHandoff) {
        { video: VideoItem ->
            val currentVideo = videoVM.state.value.video
            runMediaHandoff(
                MediaPlaybackKind.Video,
                currentVideo != null,
                null,
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

    val hasPlayerLayer = showPlayer || showAudiobookPlayer || showVideoPlayer || showSettings
    val bottomDockPresentation = resolveBottomDockPresentation(
        hasPlayerLayer = hasPlayerLayer,
        fullDockVisible = bottomDockVisible
    )

    // Gesture-driven dock visibility: a sustained scroll in one direction
    // crosses a dp threshold before hiding/re-showing the dock, so light
    // touches never dismiss it. Reversing direction resets the accumulator.
    // No timer reveal — restoring the dock is always an explicit gesture
    // (scroll back up, reach the bottom, or tap the handle).
    val dockScrollAccumulatedPx = remember { mutableStateOf(0f) }
    val dockScrollThresholdPx = with(LocalDensity.current) {
        BottomDockScrollThreshold.toPx()
    }

    fun applyDockScrollDelta(deltaPx: Float) {
        if (showPlayer || showAudiobookPlayer || showVideoPlayer || showSettings || !showMediaNav) return
        val dockVisible = bottomDockPresentation == BottomDockPresentation.Dock
        val accumulator = dockScrollAccumulatedPx
        // Direction reversal resets the accumulated distance so a short
        // down-then-up wiggle never crosses the threshold.
        val sameDirection = accumulator.value == 0f ||
            (accumulator.value > 0f) == (deltaPx > 0f)
        accumulator.value = if (sameDirection) accumulator.value + deltaPx else deltaPx
        when (resolveBottomDockScrollIntent(
            accumulatedDeltaPx = accumulator.value,
            thresholdPx = dockScrollThresholdPx,
            dockVisible = dockVisible
        )) {
            BottomDockScrollIntent.Hide -> {
                bottomDockVisible = false
                accumulator.value = 0f
            }
            BottomDockScrollIntent.Show -> {
                bottomDockVisible = true
                accumulator.value = 0f
            }
            BottomDockScrollIntent.None -> Unit
        }
    }

    val bottomDockScrollConnection = remember(showPlayer, showAudiobookPlayer, showVideoPlayer, showSettings, showMediaNav) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y != 0f) applyDockScrollDelta(available.y)
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (consumed.y != 0f || available.y != 0f) {
                    applyDockScrollDelta(consumed.y + available.y)
                }
                // Unconsumed upward scroll means the list hit its bottom edge:
                // a strong signal that the user may want navigation again.
                if (available.y > 0f &&
                    bottomDockPresentation == BottomDockPresentation.Handle
                ) {
                    bottomDockVisible = true
                    dockScrollAccumulatedPx.value = 0f
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (available.y != 0f) applyDockScrollDelta(available.y)
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (consumed.y != 0f || available.y != 0f) {
                    applyDockScrollDelta(consumed.y + available.y)
                }
                return Velocity.Zero
            }
        }
    }

    LaunchedEffect(selectedTab, showPlayer, showAudiobookPlayer, showVideoPlayer, showSettings) {
        bottomDockVisible = true
    }

    // Single fullscreen controller: fullscreen is only meaningful while the
    // video player layer is visible. Keyed on both flags so a restored
    // `isFullscreen=true` without a player (process death) self-heals to
    // portrait with system bars shown instead of a stuck landscape shell.
    // Orientation follows the dual-lock model: landscape while fullscreen,
    // portrait otherwise, system-controlled when the player is closed.
    val isInPipMode = (context as? MainActivity)?.isInVideoPipMode == true
    LaunchedEffect(isFullscreen, showVideoPlayer, orientationLockedLandscape, isInPipMode) {
        val activity = context as? ComponentActivity ?: return@LaunchedEffect
        if (isInPipMode) return@LaunchedEffect
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

    CompositionLocalProvider(LocalSettingsNavigationGuard provides navigationGuard, LocalConfigurationError provides (configurationError ?: inheritedConfigurationError)) {
    MediaSourceManagementHost(
        state = sourceState, repository = configRepository, gate = mediaSwitchInProgress,
        isPlaying = { domain -> when (domain) {
            SourceDomain.MUSIC -> musicVM.state.value.currentSong != null
            SourceDomain.AUDIOBOOK -> audiobookVM.state.value.session != null
            SourceDomain.VIDEO -> videoVM.hasPlayback
        } },
        closePlayback = { domain, closed, failed -> when (domain) {
            SourceDomain.MUSIC -> { musicVM.stop(); showPlayer = false; closed() }
            SourceDomain.AUDIOBOOK -> audiobookVM.closeAudiobookPlayback(onClosed = { showAudiobookPlayer = false; closed() }, onFailed = failed)
            SourceDomain.VIDEO -> videoVM.closeVideoPlayback(onClosed = { showVideoPlayer = false; isFullscreen = false; closed() }, onFailed = failed)
        } },
        onManage = { navigationGuard.navigate { openServersRequest++; showSettings = true } }
    ) {
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
                        if (showSettings) {
                            SettingsScreen(
                                openServersRequest = openServersRequest,
                                onPlaySong = { song -> onSongSelected(listOf(song), 0, false) },
                                onClose = { showSettings = false },
                                isModuleActive = { domain -> when (domain) {
                                    SourceDomain.MUSIC -> musicVM.state.value.currentSong != null
                                    SourceDomain.AUDIOBOOK -> audiobookVM.state.value.session != null || audiobookVM.isPreparing
                                    SourceDomain.VIDEO -> videoVM.hasPlayback
                                } },
                                onHideModule = { domain, onStopped, onFailed -> hideModule(domain, onStopped, onFailed) }
                            )
                        } else {
                            Crossfade(
                                targetState = selectedTab,
                                animationSpec = tween(
                                    NordicMotion.durationMedium,
                                    easing = NordicMotion.easingStandard
                                ),
                                label = "main-tab-crossfade"
                            ) { tab ->
                                // SaveableStateProvider keeps each tab's list scroll
                                // position and rememberSaveable state alive across
                                // switches instead of tearing down the whole screen.
                                tabStateHolder.SaveableStateProvider(key = tab) {
                                    val domain = when (tab) { 0 -> SourceDomain.MUSIC; 1 -> SourceDomain.AUDIOBOOK; 2 -> SourceDomain.VIDEO; else -> null }
                                    CompositionLocalProvider(LocalSourceDomain provides domain) {
                                        when (tab) {
                                            0 -> MusicScreenV2(isDark, onThemeToggle, onSongSelected, onOpenSettings = { navigationGuard.navigate { showSettings = true } })
                                            1 -> AudiobookScreen(colorScheme, isDark, onThemeToggle, onPlayAudiobook, onOpenSettings = { navigationGuard.navigate { showSettings = true } })
                                            2 -> {
                                                val source = sourceState.active(SourceDomain.VIDEO)
                                                if (source?.kind == MediaSourceKind.WEBDAV) {
                                                    WebDavScreen(source.videoConfig(), onPlayVideo, onPlayVideoFromStart, videoVM::setEpisodeContext, onOpenSettings = { navigationGuard.navigate { showSettings = true } })
                                                } else VideoScreen(colorScheme, isDark, onThemeToggle, onPlayVideo,
                                                    onPlayVideoFromStart, videoVM::setEpisodeContext, onOpenSettings = { navigationGuard.navigate { showSettings = true } })
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom dock — overlaid on content via BottomCenter so Scaffold's
            // containerColor does not fill a full-width bottom bar area. Hidden
            // entirely on the settings page (no media navigation there).
            if (!showSettings) {
                Box(
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    if (showMediaNav) {
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
                                    visibleDomains = visibleDomains,
                                    onOpenPlayer = openNowPlayingPlayer,
                                    onSelect = { tab -> navigationGuard.navigate { selectedTab = tab } }
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
                    } else {
                        // Single visible module: keep the now-playing bar only,
                        // with no navigation, no empty placeholder, and no handle.
                        AnimatedBottomDock(visible = true) {
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
                                    visibleDomains = visibleDomains,
                                    onOpenPlayer = openNowPlayingPlayer,
                                    onSelect = { tab -> navigationGuard.navigate { selectedTab = tab } }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    }
    }

    musicSettingsError?.let { message -> AlertDialog(onDismissRequest = musicVM::clearSettingsError,
        title = { Text("设置未保存") }, text = { Text(message) },
        confirmButton = { TextButton(onClick = musicVM::clearSettingsError) { Text("知道了") } }) }

    VideoPlayerLayer(
        videoVM = videoVM,
        showVideoPlayer = showVideoPlayer,
        isFullscreen = isFullscreen,
        colorScheme = colorScheme,
        closeVideoPlayback = closeCurrentVideoPlayback,
        closeVideoPlaybackAnyway = closeCurrentVideoPlaybackAnyway,
        onPlayEpisode = onPlayVideo,
        onAutoPlayEpisode = onAutoPlayVideo,
        onPlayFromStart = onPlayVideoFromStart,
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
    visibleDomains: List<SourceDomain>,
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
        visibleDomains = visibleDomains,
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
    val lyricsState by musicVM.lyricsState.collectAsStateWithLifecycle()
    val showLyrics by musicVM.showLyrics.collectAsStateWithLifecycle()
    val lyricsSeekRevision by musicVM.lyricsSeekRevision.collectAsStateWithLifecycle()
    MusicPlayerScreen(
        song = playbackState.currentSong,
        colorScheme = colorScheme,
        isPlaying = playbackState.isPlaying,
        isBuffering = playbackState.isBuffering,
        playbackError = playbackState.errorMessage,
        positionSeconds = playbackState.positionSeconds,
        positionMillisFlow = musicVM.positionMillis,
        durationSeconds = playbackState.durationSeconds,
        bufferedPositionSeconds = playbackState.bufferedPositionSeconds,
        lyricsState = lyricsState,
        showLyrics = showLyrics,
        lyricsSeekRevision = lyricsSeekRevision,
        onToggleLyrics = musicVM::toggleLyricsDisplay,
        onRetryLyrics = musicVM::retryLyrics,
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
        onDownloadSong = musicVM::downloadCurrentSong,
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
    onAutoPlayEpisode: (VideoAutoPlayNextRequest) -> Unit,
    onPlayFromStart: (VideoItem) -> Unit,
    onToggleFullscreen: () -> Unit
) {
    val videoPlaybackState by videoVM.state.collectAsStateWithLifecycle()
    val videoPlaybackError by videoVM.error.collectAsStateWithLifecycle()
    val catalogVideos by videoVM.catalogVideos.collectAsStateWithLifecycle()
    val pipEnabled by videoVM.pipEnabled.collectAsStateWithLifecycle()
    val autoSkipIntro by videoVM.autoSkipIntro.collectAsStateWithLifecycle()
    val autoPlayNextEnabled by videoVM.autoPlayNextEnabled.collectAsStateWithLifecycle()
    val autoPlayNextState by videoVM.autoPlayNextState.collectAsStateWithLifecycle()
    val qualityMode by videoVM.qualityMode.collectAsStateWithLifecycle()

    val activity = LocalContext.current as? MainActivity
    val isInPipMode = activity?.isInVideoPipMode == true
    LifecycleResumeEffect(showVideoPlayer, isInPipMode) {
        videoVM.setAutoPlayNextForeground(showVideoPlayer && !isInPipMode)
        onPauseOrDispose { videoVM.setAutoPlayNextForeground(false) }
    }
    val readyRequest = (autoPlayNextState as? VideoAutoPlayNextState.Ready)?.request
    LaunchedEffect(readyRequest) {
        if (readyRequest != null && videoVM.claimAutoPlayNext(readyRequest)) onAutoPlayEpisode(readyRequest)
    }
    LaunchedEffect(autoPlayNextState, videoPlaybackState.video, videoPlaybackError) {
        // If preparation was cancelled after the old engine closed, leave no empty player behind.
        if (autoPlayNextState == VideoAutoPlayNextState.Dismissed && showVideoPlayer &&
            videoPlaybackState.video == null && videoPlaybackError == null && !videoVM.hasPlayback) {
            closeVideoPlayback()
        }
    }
    val pipBridge = resolveVideoPipBridgeState(
        showVideoPlayer, pipEnabled, videoPlaybackState, videoPlaybackError
    )
    LaunchedEffect(activity, pipBridge) {
        activity?.updateVideoPipState(pipBridge)
    }
    DisposableEffect(activity) {
        onDispose { activity?.updateVideoPipState(VideoPipBridgeState()) }
    }

    // Fullscreen orientation/system-bars control lives in MainScreen's single
    // LaunchedEffect(isFullscreen, showVideoPlayer); no per-layer controller
    // here (duplicate writers caused unstable fullscreen behavior).

    val nextEpisode = remember(videoPlaybackState.video, catalogVideos) {
        videoPlaybackState.video?.let { current -> resolveNextVideoEpisode(current, catalogVideos) }
    }

    // Visible PiP is STARTED (paused, not stopped), including while buffering.
    // ON_STOP means dismissal or background without PiP, not expansion. The
    // close path snapshots and immediately reports Stopped; do not also race
    // a separate Progress request against it. Recreation only needs a sync.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (showVideoPlayer) {
            if (activity?.isChangingConfigurations == true) {
                videoVM.syncNow()
            } else {
                activity?.updateVideoPipState(VideoPipBridgeState())
                closeVideoPlayback()
            }
        }
    }

    BackHandler(enabled = !isInPipMode && (showVideoPlayer || videoPlaybackError != null)) {
        closeVideoPlayback()
    }

    // Use Media3 STATE_ENDED, not rounded seconds or server duration metadata.
    LaunchedEffect(activity, isInPipMode, showVideoPlayer, videoPlaybackState.hasEnded) {
        if (isInPipMode && showVideoPlayer && videoPlaybackState.hasEnded) {
            closeVideoPlayback()
            activity?.dismissVideoPip()
        }
    }

    AnimatedVisibility(
        visible = showVideoPlayer || videoPlaybackError != null,
        enter = NordicMotion.enterSlideUp,
        exit = NordicMotion.exitSlideDown
    ) {
        val preparingRequest = (autoPlayNextState as? VideoAutoPlayNextState.Switching)?.request
        // During preparation keep a dismissible loading player, not an empty/uncancellable overlay.
        val displayState = if (videoPlaybackState.video == null && preparingRequest != null) {
            videoPlaybackState.copy(video = preparingRequest.next, isBuffering = true)
        } else videoPlaybackState
        VideoPlayerScreen(
            state = displayState,
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
            onSetTemporaryPlaybackSpeed = videoVM::setTemporaryPlaybackSpeed,
            onSetPreferredTextTrack = videoVM::setPreferredTextTrack,
            onSetPreferredAudioTrack = videoVM::setPreferredAudioTrack,
            onAttachSubtitleView = videoVM::attachSubtitleView,
            pipEnabled = pipEnabled,
            onTogglePip = videoVM::setPipEnabled,
            autoSkipIntro = autoSkipIntro,
            onToggleAutoSkipIntro = videoVM::setAutoSkipIntro,
            autoPlayNextEnabled = autoPlayNextEnabled,
            onToggleAutoPlayNext = videoVM::setAutoPlayNext,
            autoPlayNextState = autoPlayNextState,
            onAutoPlayNextNow = videoVM::playAutoPlayNextNow,
            onDismissAutoPlayNext = videoVM::dismissAutoPlayNext,
            onCancelPendingAutoPlayNext = videoVM::cancelPendingAutoPlayNext,
            onPanelOpenChanged = videoVM::setAutoPlayNextPanelOpen,
            onSkipIntro = videoVM::skipIntro,
            qualityMode = qualityMode,
            onSetQualityMode = videoVM::setQualityMode,
            isInPipMode = isInPipMode,
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
            onRetryPlayback = videoVM::retryPlayback,
            onRestartFromBeginning = { videoPlaybackState.video?.let(onPlayFromStart) },
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

    // Audiobook keeps playing in the background; on ON_STOP only push an
    // immediate progress snapshot so a process death cannot lose the last
    // <30s before the periodic sync fires.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (audiobookVM.state.value.session != null) {
            audiobookVM.syncNow()
        }
    }

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
