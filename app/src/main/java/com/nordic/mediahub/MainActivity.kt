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
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nordic.mediahub.data.ConfigRepository
import com.nordic.mediahub.data.AudiobookShelfConfig
import com.nordic.mediahub.data.AudiobookShelfRepository
import com.nordic.mediahub.data.MusicLyrics
import com.nordic.mediahub.data.NavidromeConfig
import com.nordic.mediahub.data.NavidromeRepository
import com.nordic.mediahub.data.isReadyForAudiobookSync
import com.nordic.mediahub.data.isReadyForMusicSync
import com.nordic.mediahub.playback.AudiobookPlaybackEngine
import com.nordic.mediahub.playback.MusicPlaybackEngine
import com.nordic.mediahub.ui.*
import com.nordic.mediahub.ui.theme.*
import coil.Coil
import coil.ImageLoader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import android.graphics.Color as AndroidColor

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
    var selectedTab by remember { mutableStateOf(0) }
    var showPlayer by remember { mutableStateOf(false) }
    var showAudiobookPlayer by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var audiobookPlaybackError by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val playbackEngine = remember { MusicPlaybackEngine(context) }
    val audiobookPlaybackEngine = remember { AudiobookPlaybackEngine(context) }
    val configRepository = remember { ConfigRepository(context) }
    val navidromeConfig by configRepository.navidromeConfig.collectAsStateWithLifecycle(NavidromeConfig())
    val audiobookConfig by configRepository.audiobookConfig.collectAsStateWithLifecycle(AudiobookShelfConfig())
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
    DisposableEffect(playbackEngine) {
        onDispose { playbackEngine.release() }
    }
    DisposableEffect(audiobookPlaybackEngine) {
        onDispose { audiobookPlaybackEngine.release() }
    }
    val playbackState by playbackEngine.state.collectAsStateWithLifecycle()
    val audiobookPlaybackState by audiobookPlaybackEngine.state.collectAsStateWithLifecycle()
    val currentSong = playbackState.currentSong
    val isPlaying = playbackState.isPlaying
    var lyrics by remember { mutableStateOf<MusicLyrics?>(null) }
    var isLyricsLoading by remember { mutableStateOf(false) }
    var lyricsError by remember { mutableStateOf<String?>(null) }
    val colorScheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val onPlayPause = {
        if (currentSong == null) {
            showPlayer = true
        } else {
            playbackEngine.togglePlayPause()
        }
    }
    val playbackStatus = when {
        playbackState.errorMessage != null -> playbackState.errorMessage
        playbackState.isBuffering -> "正在缓冲"
        else -> null
    }

    fun closeAudiobookPlayback() {
        val session = audiobookPlaybackEngine.state.value.session
        val positionSeconds = audiobookPlaybackEngine.state.value.positionSeconds
        val repo = audiobookRepository
        showAudiobookPlayer = false
        audiobookPlaybackError = null
        audiobookPlaybackEngine.stop()

        if (session != null && repo != null) {
            scope.launch {
                runCatching {
                    repo.closeSession(session, positionSeconds)
                }
            }
        }
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

    LaunchedEffect(
        audiobookPlaybackState.session?.sessionId,
        audiobookRepository
    ) {
        val initialSession = audiobookPlaybackState.session ?: return@LaunchedEffect
        val repo = audiobookRepository ?: return@LaunchedEffect
        var lastSyncedPosition = audiobookPlaybackState.positionSeconds

        while (true) {
            delay(30_000)
            val currentState = audiobookPlaybackEngine.state.value
            val currentSession = currentState.session ?: return@LaunchedEffect
            if (currentSession.sessionId != initialSession.sessionId) {
                return@LaunchedEffect
            }

            val currentPosition = currentState.positionSeconds
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

    Scaffold(
        containerColor = colorScheme.background,
        bottomBar = {
            if (!showPlayer && !showAudiobookPlayer) {
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
        Box(Modifier.fillMaxSize()) {
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
                        onSeek = playbackEngine::seekTo,
                        onPlayPause = onPlayPause,
                        onClose = { showPlayer = false },
                        onSeekToNext = playbackEngine::seekToNext,
                        onSeekToPrevious = playbackEngine::seekToPrevious,
                        onToggleRepeat = playbackEngine::toggleRepeatMode,
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
                                    onSongSelected = { songs, index ->
                                        closeAudiobookPlayback()
                                        playbackEngine.playQueue(songs, index)
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
                                        scope.launch {
                                            audiobookPlaybackError = null
                                            runCatching {
                                                audiobookRepository?.startPlayback(item.id)
                                                    ?: error("未配置 AudiobookShelf")
                                            }.onSuccess { session ->
                                                audiobookPlaybackEngine.play(session)
                                                showAudiobookPlayer = true
                                                showPlayer = false
                                            }.onFailure { error ->
                                                audiobookPlaybackError = error.message ?: "启动有声书播放失败"
                                            }
                                        }
                                    }
                                )
                                2 -> VideoScreen(colorScheme, isDark, onThemeToggle)
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
            onSeek = audiobookPlaybackEngine::seekTo,
            onPlayPause = audiobookPlaybackEngine::togglePlayPause,
            onClose = { closeAudiobookPlayback() }
        )
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
            onDismiss = { showQueueSheet = false }
        )
    }
}
