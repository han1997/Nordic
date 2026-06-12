package com.nordic.mediahub.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nordic.mediahub.NavidromeConfigCard
import com.nordic.mediahub.api.NavidromeAlbum
import com.nordic.mediahub.api.NavidromeArtist
import com.nordic.mediahub.api.NavidromeSong
import com.nordic.mediahub.data.ConfigRepository
import com.nordic.mediahub.data.NavidromeConfig
import com.nordic.mediahub.data.NavidromeMusicCacheRepository
import com.nordic.mediahub.data.NavidromeRepository
import kotlinx.coroutines.launch

@Composable
fun MusicScreenV2(
    isDark: Boolean,
    onThemeToggle: (Boolean) -> Unit,
    onSongSelected: (NavidromeSong) -> Unit = {}
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val repository = remember { ConfigRepository(context) }
    val cacheRepository = remember { NavidromeMusicCacheRepository(context) }
    val savedConfig by repository.navidromeConfig.collectAsStateWithLifecycle(NavidromeConfig())
    var config by remember { mutableStateOf(NavidromeConfig()) }
    var showConfig by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }
    var albums by remember { mutableStateOf(emptyList<NavidromeAlbum>()) }
    var songs by remember { mutableStateOf(emptyList<NavidromeSong>()) }
    var artists by remember { mutableStateOf(emptyList<NavidromeArtist>()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var cacheUpdatedAtMillis by remember { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun applyCachedMusicData(targetConfig: NavidromeConfig): Boolean {
        val cached = cacheRepository.load(targetConfig)
        if (cached == null) {
            albums = emptyList()
            songs = emptyList()
            artists = emptyList()
            cacheUpdatedAtMillis = null
            return false
        }

        albums = cached.albums
        songs = cached.songs
        artists = cached.artists
        cacheUpdatedAtMillis = cached.updatedAtMillis
        errorMsg = null
        return true
    }

    suspend fun refreshMusicData(targetConfig: NavidromeConfig) {
        if (!targetConfig.isReadyForMusicSync() || isLoading) return

        isLoading = true
        errorMsg = null
        try {
            val repo = NavidromeRepository(targetConfig)
            val freshAlbums = repo.getRecentAlbums()
            val freshSongs = repo.getRecentSongs()
            val freshArtists = repo.getArtists()
            val freshCache = cacheRepository.buildCache(
                config = targetConfig,
                albums = freshAlbums,
                songs = freshSongs,
                artists = freshArtists
            )

            albums = freshAlbums
            songs = freshSongs
            artists = freshArtists
            cacheUpdatedAtMillis = freshCache.updatedAtMillis
            cacheRepository.save(targetConfig, freshCache)
        } catch (e: Exception) {
            val hasCachedContent = albums.isNotEmpty() || songs.isNotEmpty() || artists.isNotEmpty()
            errorMsg = if (hasCachedContent) {
                "正在显示上次缓存：${e.message}"
            } else {
                "连接失败: ${e.message}"
            }
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(savedConfig) {
        config = savedConfig
        if (savedConfig.isReadyForMusicSync()) {
            applyCachedMusicData(savedConfig)
            refreshMusicData(savedConfig)
        } else {
            albums = emptyList()
            songs = emptyList()
            artists = emptyList()
            cacheUpdatedAtMillis = null
            errorMsg = null
        }
    }

    val hasContent = albums.isNotEmpty() || songs.isNotEmpty() || artists.isNotEmpty()
    val cacheAgeLabel = formatCacheAge(cacheUpdatedAtMillis)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("音乐库", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = colorScheme.onBackground)
                    Text(
                        when {
                            isLoading && hasContent -> "正在刷新，先显示本地缓存"
                            cacheAgeLabel != null -> "本地缓存，$cacheAgeLabel"
                            hasContent -> "封面优先，先从刚到的新专辑开始"
                            else -> "连接 Navidrome 后，这里会自动同步你的内容"
                        },
                        fontSize = 14.sp,
                        color = colorScheme.onSurface.copy(alpha = 0.62f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (config.isReadyForMusicSync()) {
                        AnimatedIconButton(if (isLoading) "…" else "↻") {
                            if (!isLoading) {
                                scope.launch { refreshMusicData(config) }
                            }
                        }
                    }
                    AnimatedIconButton(if (isDark) "☀" else "☾") { onThemeToggle(!isDark) }
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
                NavidromeConfigCard(
                    config = config,
                    colorScheme = colorScheme,
                    onConfigChange = { config = it },
                    onSave = {
                        scope.launch {
                            val nextConfig = config
                            repository.saveNavidromeConfig(nextConfig)
                            applyCachedMusicData(nextConfig)
                            refreshMusicData(nextConfig)
                        }
                    }
                )
            }
        }
        item {
            MusicSegmentedTabs(
                selectedTab = selectedTab,
                colorScheme = colorScheme,
                onTabSelected = { selectedTab = it }
            )
        }

        if (errorMsg != null) {
            item {
                Surface(
                    color = colorScheme.errorContainer,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            if (hasContent) "刷新失败" else "连接失败",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onErrorContainer
                        )
                        Text(errorMsg!!, fontSize = 13.sp, color = colorScheme.onErrorContainer.copy(alpha = 0.82f))
                    }
                }
            }
        }

        if (isLoading && !hasContent) {
            item {
                Surface(
                    color = colorScheme.surfaceVariant.copy(alpha = 0.76f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 34.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("正在同步 Navidrome 内容...", fontSize = 14.sp, color = colorScheme.onSurface.copy(alpha = 0.62f))
                    }
                }
            }
        }

        if (!isLoading && errorMsg == null && !hasContent) {
            item {
                Surface(
                    color = colorScheme.surfaceVariant.copy(alpha = 0.72f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("先接入你的音乐库", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = colorScheme.onSurface)
                        Text(
                            "填入 Navidrome 地址、用户名和密码后，最近添加的专辑和歌曲会直接出现在这里。",
                            fontSize = 14.sp,
                            color = colorScheme.onSurface.copy(alpha = 0.64f),
                            lineHeight = 20.sp
                        )
                        Text(
                            "点右上角设置开始连接",
                            fontSize = 13.sp,
                            color = colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        if (albums.isNotEmpty()) {
            item {
                MusicHeroBanner(album = albums.first(), colorScheme = colorScheme)
            }
            item {
                MusicSectionHeader(
                    title = "最近添加",
                    subtitle = "新同步到曲库的专辑，先看封面再决定播什么",
                    colorScheme = colorScheme
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(albums.take(10)) { album ->
                        CompactAlbumShelfCard(album = album, colorScheme = colorScheme)
                    }
                }
            }
        }

        if (songs.isNotEmpty()) {
            item {
                MusicSectionHeader(
                    title = "最近播放",
                    subtitle = "从上次停下的位置继续",
                    colorScheme = colorScheme
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(songs.take(10)) { song ->
                        SongShelfCard(
                            song = song,
                            colorScheme = colorScheme,
                            onClick = { onSongSelected(song) }
                        )
                    }
                }
            }
        }

        if (artists.isNotEmpty()) {
            item {
                MusicSectionHeader(
                    title = "常听歌手",
                    subtitle = "从熟悉的声音继续展开",
                    colorScheme = colorScheme
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(artists.take(10)) { artist ->
                        ArtistShelfCard(artist = artist, colorScheme = colorScheme)
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicSegmentedTabs(
    selectedTab: Int,
    colorScheme: ColorScheme,
    onTabSelected: (Int) -> Unit
) {
    val tabs = listOf("发现", "歌单", "搜索")

    Surface(
        color = colorScheme.surfaceVariant.copy(alpha = 0.56f),
        contentColor = colorScheme.onSurface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.06f)),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabs.forEachIndexed { index, label ->
                val selected = selectedTab == index
                val tabColor by animateColorAsState(
                    targetValue = if (selected) colorScheme.surface.copy(alpha = 0.96f) else Color.Transparent,
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                )
                val textColor by animateColorAsState(
                    targetValue = if (selected) colorScheme.primary else colorScheme.onSurface.copy(alpha = 0.62f),
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                )

                Surface(
                    color = tabColor,
                    contentColor = textColor,
                    shape = RoundedCornerShape(14.dp),
                    tonalElevation = if (selected) 2.dp else 0.dp,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onTabSelected(index) }
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            color = textColor,
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

private fun NavidromeConfig.isReadyForMusicSync(): Boolean {
    return serverUrl.isNotBlank() && username.isNotBlank()
}

private fun formatCacheAge(updatedAtMillis: Long?): String? {
    if (updatedAtMillis == null || updatedAtMillis <= 0L) return null

    val elapsedMillis = (System.currentTimeMillis() - updatedAtMillis).coerceAtLeast(0L)
    val elapsedMinutes = elapsedMillis / 60_000L
    val elapsedHours = elapsedMillis / 3_600_000L
    val elapsedDays = elapsedMillis / 86_400_000L

    return when {
        elapsedMinutes < 1L -> "刚刚更新"
        elapsedMinutes < 60L -> "${elapsedMinutes} 分钟前更新"
        elapsedHours < 24L -> "${elapsedHours} 小时前更新"
        else -> "${elapsedDays} 天前更新"
    }
}
