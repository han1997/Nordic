package com.nordic.mediahub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.nordic.mediahub.api.*
import com.nordic.mediahub.data.*
import com.nordic.mediahub.playback.MusicPlaybackEngine
import com.nordic.mediahub.ui.AnimatedIconButton
import com.nordic.mediahub.ui.*
import com.nordic.mediahub.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.graphics.Color as AndroidColor

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    val context = LocalContext.current
    val playbackEngine = remember { MusicPlaybackEngine(context) }
    val configRepository = remember { ConfigRepository(context) }
    val navidromeConfig by configRepository.navidromeConfig.collectAsStateWithLifecycle(NavidromeConfig())
    DisposableEffect(playbackEngine) {
        onDispose { playbackEngine.release() }
    }
    val playbackState by playbackEngine.state.collectAsStateWithLifecycle()
    val currentSong = playbackState.currentSong
    val isPlaying = playbackState.isPlaying
    var lyrics by remember { mutableStateOf<MusicLyrics?>(null) }
    var isLyricsLoading by remember { mutableStateOf(false) }
    var lyricsError by remember { mutableStateOf<String?>(null) }
    val colorScheme = MaterialTheme.colorScheme
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

        if (navidromeConfig.serverUrl.isBlank() || navidromeConfig.username.isBlank()) {
            isLyricsLoading = false
            lyricsError = "未配置 Navidrome"
            return@LaunchedEffect
        }

        isLyricsLoading = true
        val loadedLyrics = runCatching {
            NavidromeRepository(navidromeConfig).getLyrics(song)
        }.getOrNull()
        lyrics = loadedLyrics
        lyricsError = if (loadedLyrics == null) "暂无歌词" else null
        isLyricsLoading = false
    }

    Scaffold(
        containerColor = colorScheme.background,
        bottomBar = {
            if (!showPlayer) {
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
                        onSeek = playbackEngine::seekTo,
                        onPlayPause = onPlayPause,
                        onClose = { showPlayer = false },
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
                                    onSongSelected = { song ->
                                        playbackEngine.play(song)
                                        showPlayer = true
                                    }
                                )
                                1 -> AudiobookScreen(colorScheme, isDark, onThemeToggle)
                                2 -> VideoScreen(colorScheme, isDark, onThemeToggle)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PolishedPlaybackDock(
    selected: Int,
    colorScheme: ColorScheme,
    currentSong: NavidromeSong?,
    isPlaying: Boolean,
    playbackStatus: String? = null,
    onOpenPlayer: () -> Unit,
    onPlayPause: () -> Unit,
    onSelect: (Int) -> Unit
) {
    Surface(
        color = colorScheme.surface.copy(alpha = 0.94f),
        contentColor = colorScheme.onSurface,
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 6.dp,
        shadowElevation = 12.dp,
        border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.08f)),
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
    ) {
        Column(
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            PolishedNowPlayingBar(
                song = currentSong,
                colorScheme = colorScheme,
                isPlaying = isPlaying,
                playbackStatus = playbackStatus,
                onOpenPlayer = onOpenPlayer,
                onPlayPause = onPlayPause
            )
            Box(
                Modifier
                    .padding(horizontal = 18.dp, vertical = 2.dp)
                    .height(1.dp)
                    .fillMaxWidth()
                    .background(colorScheme.onSurface.copy(alpha = 0.07f))
            )
            PolishedBottomNav(selected, colorScheme, onSelect)
        }
    }
}

@Composable
fun PolishedBottomNav(selected: Int, colorScheme: ColorScheme, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(58.dp)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        PolishedNavItem("♪", "音乐", selected == 0, colorScheme, Modifier.weight(1f)) { onSelect(0) }
        PolishedNavItem("▤", "有声书", selected == 1, colorScheme, Modifier.weight(1f)) { onSelect(1) }
        PolishedNavItem("▶", "视频", selected == 2, colorScheme, Modifier.weight(1f)) { onSelect(2) }
    }
}

@Composable
fun PolishedNavItem(
    icon: String,
    label: String,
    selected: Boolean,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)
    )
    val itemColor by animateColorAsState(
        targetValue = if (selected) colorScheme.primary.copy(alpha = 0.13f) else Color.Transparent,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) colorScheme.primary else colorScheme.onSurface.copy(alpha = 0.58f),
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .scale(scale)
            .clip(RoundedCornerShape(18.dp))
            .background(itemColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(icon, fontSize = 19.sp, color = contentColor, fontWeight = FontWeight.SemiBold)
            Text(
                label,
                fontSize = 11.sp,
                color = contentColor,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
        }
    }
}

@Composable
fun PolishedNowPlayingBar(
    song: NavidromeSong?,
    colorScheme: ColorScheme,
    isPlaying: Boolean,
    playbackStatus: String? = null,
    onOpenPlayer: () -> Unit,
    onPlayPause: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(66.dp)
            .clickable(onClick = onOpenPlayer)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            colorScheme.primary.copy(alpha = 0.28f),
                            colorScheme.secondary.copy(alpha = 0.18f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            if (song?.coverArt != null) {
                AsyncImage(
                    model = song.coverArt,
                    contentDescription = song.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            } else {
            Text("♪", fontSize = 22.sp, color = colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        }
        Spacer(Modifier.width(14.dp))
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            if (song == null) {
            Text(
                "播放队列",
                fontSize = 15.sp,
                color = colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            } else {
                Text(
                    song.title,
                    fontSize = 15.sp,
                    color = colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (song == null) {
            Text(
                "等待播放",
                fontSize = 12.sp,
                color = colorScheme.onSurface.copy(alpha = 0.56f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            } else {
                Text(
                    playbackStatus ?: song.artist ?: song.album ?: "Unknown artist",
                    fontSize = 12.sp,
                    color = if (playbackStatus == null) {
                        colorScheme.onSurface.copy(alpha = 0.56f)
                    } else {
                        colorScheme.primary.copy(alpha = 0.78f)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Surface(
            color = colorScheme.primary,
            contentColor = colorScheme.onPrimary,
            shape = RoundedCornerShape(999.dp),
            shadowElevation = 2.dp,
            modifier = Modifier.clickable(onClick = onPlayPause)
        ) {
            Box(
                modifier = Modifier.size(38.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isPlaying) {
                    Text("Ⅱ", fontSize = 16.sp, color = colorScheme.onPrimary)
                } else {
                Text("▶", fontSize = 16.sp, color = colorScheme.onPrimary)
            }
        }
    }
}
}


@Composable
fun BottomNav(selected: Int, colorScheme: ColorScheme, onSelect: (Int) -> Unit) {
    Surface(color = colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            NavItem("🎵", "音乐", selected == 0, colorScheme) { onSelect(0) }
            NavItem("📚", "有声书", selected == 1, colorScheme) { onSelect(1) }
            NavItem("📺", "视频", selected == 2, colorScheme) { onSelect(2) }
        }
    }
}

@Composable
fun NavItem(icon: String, label: String, selected: Boolean, colorScheme: ColorScheme, onClick: () -> Unit) {
    Column(
        Modifier.clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(icon, fontSize = 24.sp)
        Text(
            label,
            fontSize = 11.sp,
            color = if (selected) colorScheme.primary else colorScheme.onSurface.copy(0.6f),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
fun NowPlayingBar(colorScheme: ColorScheme) {
    Surface(color = colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                    .background(Brush.linearGradient(listOf(colorScheme.primary.copy(0.4f), colorScheme.secondary.copy(0.4f))))
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("播放队列", fontSize = 15.sp, color = colorScheme.onSurface, fontWeight = FontWeight.Medium)
                Text("等待播放", fontSize = 13.sp, color = colorScheme.onSurface.copy(0.6f))
            }
            Text("⏸", fontSize = 28.sp, color = colorScheme.primary)
        }
    }
}

@Composable
fun MusicScreen(colorScheme: ColorScheme, isDark: Boolean, onThemeToggle: (Boolean) -> Unit) {
    val context = LocalContext.current
    val repository = remember { ConfigRepository(context) }
    val savedConfig by repository.navidromeConfig.collectAsStateWithLifecycle(NavidromeConfig())
    var config by remember { mutableStateOf(NavidromeConfig()) }
    var showConfig by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }
    var albums by remember { mutableStateOf<List<com.nordic.mediahub.api.NavidromeAlbum>>(emptyList()) }
    var songs by remember { mutableStateOf<List<com.nordic.mediahub.api.NavidromeSong>>(emptyList()) }
    var artists by remember { mutableStateOf<List<com.nordic.mediahub.api.NavidromeArtist>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun loadMusicData() {
        isLoading = true
        errorMsg = null
        try {
            val repo = NavidromeRepository(config)
            albums = repo.getRecentAlbums()
            songs = repo.getRecentSongs()
            artists = repo.getArtists()
        } catch (e: Exception) {
            errorMsg = "连接失败: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(savedConfig) {
        config = savedConfig
        if (savedConfig.serverUrl.isNotEmpty() && savedConfig.username.isNotEmpty()) {
            loadMusicData()
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("音乐库", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = colorScheme.onBackground)
                Row {
                    AnimatedIconButton(if (isDark) "☀" else "🌙") { onThemeToggle(!isDark) }
                    AnimatedIconButton("⚙") { showConfig = !showConfig }
                }
            }
        }
        item {
            AnimatedVisibility(
                visible = showConfig,
                enter = fadeIn(tween(300, easing = FastOutSlowInEasing)) + expandVertically(),
                exit = fadeOut(tween(200)) + shrinkVertically()
            ) {
                NavidromeConfigCard(config, colorScheme,
                    onConfigChange = { config = it },
                    onSave = {
                        scope.launch {
                            repository.saveNavidromeConfig(config)
                            loadMusicData()
                        }
                    }
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("发现", "歌单", "搜索").forEachIndexed { index, label ->
                    Surface(
                        color = if (selectedTab == index) colorScheme.primary else colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).clickable { selectedTab = index }
                    ) {
                        Text(
                            label,
                            modifier = Modifier.padding(12.dp),
                            color = if (selectedTab == index) colorScheme.onPrimary else colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
        if (errorMsg != null) {
            item {
                Surface(color = colorScheme.errorContainer, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(errorMsg!!, modifier = Modifier.padding(12.dp), color = colorScheme.onErrorContainer, fontSize = 13.sp)
                }
            }
        }
        if (isLoading) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("加载中...", fontSize = 14.sp, color = colorScheme.onSurface.copy(0.6f))
                }
            }
        }
        if (!isLoading && errorMsg == null && albums.isEmpty()) {
            item {
                Text("请配置服务器以查看内容", fontSize = 14.sp, color = colorScheme.onSurface.copy(0.6f), modifier = Modifier.fillMaxWidth().padding(32.dp))
            }
        }
        item {
            Text("最近添加", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = colorScheme.onBackground)
        }
        items(albums.take(5)) { album ->
            AlbumCard(album, colorScheme)
        }
        item {
            Text("最近播放", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = colorScheme.onBackground)
        }
        items(songs.take(5)) { song ->
            SongCard(song, colorScheme)
        }
        item {
            Text("歌手", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = colorScheme.onBackground)
        }
        items(artists.take(5)) { artist ->
            ArtistCard(artist, colorScheme)
        }
        item {
            Text("专辑", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = colorScheme.onBackground)
        }
        items(albums.take(5)) { album ->
            AlbumCard(album, colorScheme)
        }
    }
}

@Composable
fun AudiobookScreen(colorScheme: ColorScheme, isDark: Boolean, onThemeToggle: (Boolean) -> Unit) {
    val context = LocalContext.current
    val repository = remember { ConfigRepository(context) }
    val savedConfig by repository.audiobookConfig.collectAsStateWithLifecycle(AudiobookShelfConfig())
    var config by remember { mutableStateOf(AudiobookShelfConfig()) }
    var showConfig by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(savedConfig) { config = savedConfig }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("有声书", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = colorScheme.onBackground)
                Row {
                    AnimatedIconButton(if (isDark) "☀" else "🌙") { onThemeToggle(!isDark) }
                    AnimatedIconButton("⚙") { showConfig = !showConfig }
                }
            }
        }
        item {
            AnimatedVisibility(
                visible = showConfig,
                enter = fadeIn(tween(300, easing = FastOutSlowInEasing)) + expandVertically(),
                exit = fadeOut(tween(200)) + shrinkVertically()
            ) {
                AudiobookConfigCard(config, colorScheme,
                    onConfigChange = { config = it },
                    onSave = { scope.launch { repository.saveAudiobookConfig(config) } }
                )
            }
        }
        itemsIndexed(listOf("书名 1", "书名 2", "书名 3")) { index, title ->
            var visible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(index * 50L)
                visible = true
            }
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(400)) + slideInVertically { it / 2 }
            ) {
                AudiobookCard(title, "作者", "12章", colorScheme)
            }
        }
    }
}

@Composable
fun VideoScreen(colorScheme: ColorScheme, isDark: Boolean, onThemeToggle: (Boolean) -> Unit) {
    val context = LocalContext.current
    val repository = remember { ConfigRepository(context) }
    val savedConfig by repository.videoConfig.collectAsStateWithLifecycle(VideoServerConfig())
    var config by remember { mutableStateOf(VideoServerConfig()) }
    var showConfig by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(savedConfig) { config = savedConfig }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("视频", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = colorScheme.onBackground)
                Row {
                    AnimatedIconButton(if (isDark) "☀" else "🌙") { onThemeToggle(!isDark) }
                    AnimatedIconButton("⚙") { showConfig = !showConfig }
                }
            }
        }
        item {
            AnimatedVisibility(
                visible = showConfig,
                enter = fadeIn(tween(300, easing = FastOutSlowInEasing)) + expandVertically(),
                exit = fadeOut(tween(200)) + shrinkVertically()
            ) {
                VideoConfigCard(config, colorScheme,
                    onConfigChange = { config = it },
                    onSave = { scope.launch { repository.saveVideoConfig(config) } }
                )
            }
        }
        itemsIndexed(listOf("视频 1", "视频 2", "视频 3", "视频 4")) { index, title ->
            var visible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(index * 50L)
                visible = true
            }
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(400)) + slideInVertically { it / 2 }
            ) {
                VideoCard(title, "2小时3${index}分", colorScheme)
            }
        }
    }
}

@Composable
fun TrackCard(title: String, artist: String, duration: String, colorScheme: ColorScheme) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)
    )

    Surface(
        color = colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().scale(scale).clickable(interactionSource, null) {}
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                    .background(Brush.linearGradient(listOf(colorScheme.primary.copy(0.3f), colorScheme.secondary.copy(0.3f))))
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, color = colorScheme.onSurface, fontWeight = FontWeight.Medium)
                Text(artist, fontSize = 13.sp, color = colorScheme.onSurface.copy(0.6f))
            }
            Text(duration, fontSize = 13.sp, color = colorScheme.onSurface.copy(0.6f))
        }
    }
}

@Composable
fun AudiobookCard(title: String, author: String, chapters: String, colorScheme: ColorScheme) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)
    )

    Surface(
        color = colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().scale(scale).clickable(interactionSource, null) {}
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(8.dp))
                    .background(Brush.linearGradient(listOf(colorScheme.primary.copy(0.3f), colorScheme.secondary.copy(0.3f))))
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, color = colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Text(author, fontSize = 13.sp, color = colorScheme.onSurface.copy(0.6f))
                Text(chapters, fontSize = 13.sp, color = colorScheme.onSurface.copy(0.6f))
            }
        }
    }
}

@Composable
fun VideoCard(title: String, duration: String, colorScheme: ColorScheme) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)
    )

    Surface(
        color = colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().scale(scale).clickable(interactionSource, null) {}
    ) {
        Column {
            Box(
                Modifier.fillMaxWidth().height(180.dp)
                    .background(Brush.linearGradient(listOf(colorScheme.secondary.copy(0.3f), colorScheme.primary.copy(0.3f))))
            )
            Column(Modifier.padding(14.dp)) {
                Text(title, fontSize = 15.sp, color = colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Text(duration, fontSize = 13.sp, color = colorScheme.onSurface.copy(0.6f))
            }
        }
    }
}

@Composable
fun NavidromeConfigCard(config: NavidromeConfig, colorScheme: ColorScheme, onConfigChange: (NavidromeConfig) -> Unit, onSave: () -> Unit) {
    Surface(color = colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Navidrome 服务器", fontSize = 15.sp, color = colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            ConfigTextField("服务器地址", config.serverUrl, "https://music.example.com", colorScheme) {
                onConfigChange(config.copy(serverUrl = it))
            }
            ConfigTextField("用户名", config.username, "username", colorScheme) {
                onConfigChange(config.copy(username = it))
            }
            ConfigTextField("密码", config.password, "password", colorScheme, true) {
                onConfigChange(config.copy(password = it))
            }
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Text("保存配置")
            }
        }
    }
}

@Composable
fun AudiobookConfigCard(config: AudiobookShelfConfig, colorScheme: ColorScheme, onConfigChange: (AudiobookShelfConfig) -> Unit, onSave: () -> Unit) {
    Surface(color = colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("AudiobookShelf 服务器", fontSize = 15.sp, color = colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            ConfigTextField("服务器地址", config.serverUrl, "https://audiobook.example.com", colorScheme) {
                onConfigChange(config.copy(serverUrl = it))
            }
            ConfigTextField("用户名", config.username, "username", colorScheme) {
                onConfigChange(config.copy(username = it))
            }
            ConfigTextField("密码", config.password, "password", colorScheme, true) {
                onConfigChange(config.copy(password = it))
            }
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Text("保存配置")
            }
        }
    }
}

@Composable
fun VideoConfigCard(config: VideoServerConfig, colorScheme: ColorScheme, onConfigChange: (VideoServerConfig) -> Unit, onSave: () -> Unit) {
    Surface(color = colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("视频服务器", fontSize = 15.sp, color = colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VideoServerType.values().forEach { type ->
                    val selected = config.type == type
                    val scale by animateFloatAsState(
                        targetValue = if (selected) 1.01f else 1f,
                        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)
                    )
                    Surface(
                        color = if (selected) colorScheme.primary else colorScheme.surface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).scale(scale).clickable { onConfigChange(config.copy(type = type)) }
                    ) {
                        Text(
                            type.name,
                            modifier = Modifier.padding(10.dp),
                            color = if (selected) colorScheme.onPrimary else colorScheme.onSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            AnimatedContent(
                targetState = config.type,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) }
            ) { type ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ConfigTextField("服务器地址", config.serverUrl, "https://video.example.com", colorScheme) {
                        onConfigChange(config.copy(serverUrl = it))
                    }
                    when (type) {
                        VideoServerType.EMBY, VideoServerType.PLEX -> {
                            ConfigTextField("用户名", config.username, "username", colorScheme) {
                                onConfigChange(config.copy(username = it))
                            }
                            ConfigTextField("密码", config.password, "password", colorScheme, true) {
                                onConfigChange(config.copy(password = it))
                            }
                            if (type == VideoServerType.EMBY) {
                                ConfigTextField("API Key (可选)", config.apiKey, "api key", colorScheme) {
                                    onConfigChange(config.copy(apiKey = it))
                                }
                            }
                        }
                        VideoServerType.WEBDAV -> {
                            ConfigTextField("用户名", config.username, "username", colorScheme) {
                                onConfigChange(config.copy(username = it))
                            }
                            ConfigTextField("密码", config.password, "password", colorScheme, true) {
                                onConfigChange(config.copy(password = it))
                            }
                        }
                    }
                    Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                        Text("保存配置")
                    }
                }
            }
        }
    }
}

@Composable
fun ConfigTextField(
    label: String,
    value: String,
    placeholder: String,
    colorScheme: ColorScheme,
    isPassword: Boolean = false,
    onValueChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 12.sp, color = colorScheme.onSurface.copy(0.7f))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = colorScheme.onSurface.copy(0.4f), fontSize = 14.sp) },
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colorScheme.primary,
                unfocusedBorderColor = colorScheme.onSurface.copy(0.2f),
                focusedTextColor = colorScheme.onSurface,
                unfocusedTextColor = colorScheme.onSurface
            ),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            singleLine = true
        )
    }
}
