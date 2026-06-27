package com.nordic.mediahub.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.nordic.mediahub.data.ConfigRepository
import com.nordic.mediahub.data.EmbyRepository
import com.nordic.mediahub.data.VideoEpisode
import com.nordic.mediahub.data.VideoEpisodeQueue
import com.nordic.mediahub.data.VideoItem
import com.nordic.mediahub.data.VideoPlaybackInfo
import com.nordic.mediahub.data.VideoProgress
import com.nordic.mediahub.data.VideoSeason
import com.nordic.mediahub.data.VideoServerConfig
import com.nordic.mediahub.data.isReadyForVideoSync
import com.nordic.mediahub.data.toPlaybackVideoItem
import kotlinx.coroutines.launch

@Composable
fun VideoDetailScreen(
    videoItem: VideoItem,
    colorScheme: ColorScheme,
    onPlay: (VideoPlaybackInfo) -> Unit,
    onPlayEpisode: (VideoPlaybackInfo, VideoEpisodeQueue) -> Unit = { playbackInfo, _ -> onPlay(playbackInfo) },
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configRepository = remember { ConfigRepository(context) }
    val savedConfig by configRepository.videoConfig.collectAsStateWithLifecycle(VideoServerConfig())
    val embyRepository = remember(savedConfig) {
        if (savedConfig.isReadyForVideoSync()) EmbyRepository(savedConfig) else null
    }
    val scope = rememberCoroutineScope()
    var isPlayable by remember { mutableStateOf(true) }
    var isLoadingPlayback by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var seasons by remember { mutableStateOf(emptyList<VideoSeason>()) }
    var episodes by remember { mutableStateOf(emptyList<VideoEpisode>()) }
    var selectedSeasonId by remember { mutableStateOf<String?>(null) }
    var seasonsError by remember { mutableStateOf<String?>(null) }
    var episodesError by remember { mutableStateOf<String?>(null) }
    var overviewExpanded by remember { mutableStateOf(false) }
    var itemState by remember(videoItem.id) { mutableStateOf(videoItem) }

    val isSeries = itemState.type.equals("Series", ignoreCase = true)

    LaunchedEffect(videoItem.id, embyRepository) {
        if (isSeries && embyRepository != null) {
            try {
                seasons = embyRepository.getSeasons(videoItem.id)
                if (seasons.isNotEmpty()) {
                    selectedSeasonId = seasons.first().id
                }
            } catch (e: Exception) {
                seasonsError = e.message ?: "获取季列表失败"
            }
        }
    }

    LaunchedEffect(selectedSeasonId, embyRepository) {
        if (selectedSeasonId != null && embyRepository != null) {
            episodesError = null
            try {
                episodes = embyRepository.getEpisodes(selectedSeasonId!!)
            } catch (e: Exception) {
                episodes = emptyList()
                episodesError = e.message ?: "获取集列表失败"
            }
        } else {
            episodes = emptyList()
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item {
            VideoDetailThumbnail(
                imageUrl = itemState.imageUrl,
                title = itemState.title,
                colorScheme = colorScheme,
                onBack = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            )
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    itemState.title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                VideoDetailMetaChips(
                    videoItem = itemState,
                    colorScheme = colorScheme
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VideoDetailActionChip(
                        text = if (itemState.isFavorite) "Favorited" else "Favorite",
                        selected = itemState.isFavorite,
                        colorScheme = colorScheme,
                        onClick = {
                            val repo = embyRepository ?: return@VideoDetailActionChip
                            val nextFavorite = !itemState.isFavorite
                            scope.launch {
                                runCatching { repo.setItemFavorite(itemState.id, nextFavorite) }
                                    .onSuccess { itemState = itemState.copy(isFavorite = nextFavorite) }
                                    .onFailure { error ->
                                        playbackError = error.message ?: "Update favorite failed"
                                    }
                            }
                        }
                    )
                    VideoDetailActionChip(
                        text = if (itemState.progress?.isPlayed == true) "Watched" else "Unwatched",
                        selected = itemState.progress?.isPlayed == true,
                        colorScheme = colorScheme,
                        onClick = {
                            val repo = embyRepository ?: return@VideoDetailActionChip
                            val nextPlayed = itemState.progress?.isPlayed != true
                            scope.launch {
                                runCatching { repo.setItemPlayed(itemState.id, nextPlayed) }
                                    .onSuccess { itemState = itemState.withPlayed(nextPlayed) }
                                    .onFailure { error ->
                                        playbackError = error.message ?: "Update watched state failed"
                                    }
                            }
                        }
                    )
                }

                if (itemState.overview.isNotBlank()) {
                    Text(
                        itemState.overview,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = colorScheme.onSurface.copy(alpha = 0.72f),
                        maxLines = if (overviewExpanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (itemState.overview.length > 120 || itemState.overview.count { it == '\n' } >= 3) {
                        Text(
                            if (overviewExpanded) "收起" else "展开",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.primary,
                            modifier = Modifier.clickable { overviewExpanded = !overviewExpanded }
                        )
                    }
                }

                playbackError?.let { error ->
                    Text(
                        error,
                        fontSize = 13.sp,
                        color = colorScheme.error
                    )
                }

                Surface(
                    color = colorScheme.primary,
                    contentColor = colorScheme.onPrimary,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (isLoadingPlayback || !isPlayable) return@Surface
                        val repo = embyRepository ?: return@Surface
                        isLoadingPlayback = true
                        playbackError = null
                        scope.launch {
                            runCatching {
                                repo.getPlaybackInfo(itemState)
                            }.onSuccess { info ->
                                onPlay(info)
                            }.onFailure { error ->
                                playbackError = error.message ?: "启动 Emby 播放失败"
                            }
                            isLoadingPlayback = false
                        }
                    },
                    enabled = !isLoadingPlayback && isPlayable
                ) {
                    Text(
                        if (isLoadingPlayback) "加载中..." else "播放",
                        modifier = Modifier.padding(vertical = 14.dp, horizontal = 20.dp),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (isSeries) {
            if (seasonsError != null) {
                item {
                    Text(
                        seasonsError.orEmpty(),
                        fontSize = 13.sp,
                        color = colorScheme.error,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
                    )
                }
            }

            if (seasons.isNotEmpty()) {
                item {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(seasons, key = { it.id }) { season ->
                            val selected = season.id == selectedSeasonId
                            Surface(
                                color = if (selected) colorScheme.primary.copy(alpha = 0.16f) else colorScheme.surfaceVariant.copy(alpha = 0.56f),
                                contentColor = if (selected) colorScheme.primary else colorScheme.onSurface,
                                shape = RoundedCornerShape(18.dp),
                                border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.06f)),
                                modifier = Modifier.clickable { selectedSeasonId = season.id }
                            ) {
                                Text(
                                    season.name,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    fontSize = 13.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            if (episodesError != null) {
                item {
                    Text(
                        episodesError.orEmpty(),
                        fontSize = 13.sp,
                        color = colorScheme.error,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
                    )
                }
            }

            if (episodes.isNotEmpty()) {
                itemsIndexed(
                    items = episodes,
                    key = { _, episode -> episode.id },
                    contentType = { _, _ -> "episode-card" }
                ) { episodeIndex, episode ->
                    VideoEpisodeCard(
                        episode = episode,
                        colorScheme = colorScheme,
                        onClick = {
                            val repo = embyRepository ?: return@VideoEpisodeCard
                            val episodeVideoItem = episode.toPlaybackVideoItem(videoItem.libraryId)
                            val episodeQueue = VideoEpisodeQueue(
                                libraryId = videoItem.libraryId,
                                episodes = episodes,
                                currentIndex = episodeIndex
                            )
                            scope.launch {
                                isLoadingPlayback = true
                                playbackError = null
                                runCatching {
                                    repo.getPlaybackInfo(episodeVideoItem)
                                }.onSuccess { info ->
                                    onPlayEpisode(info, episodeQueue)
                                }.onFailure { error ->
                                    playbackError = error.message ?: "启动 Emby 播放失败"
                                }
                                isLoadingPlayback = false
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoDetailActionChip(
    text: String,
    selected: Boolean,
    colorScheme: ColorScheme,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) colorScheme.primary.copy(alpha = 0.16f) else colorScheme.surfaceVariant.copy(alpha = 0.56f),
        contentColor = if (selected) colorScheme.primary else colorScheme.onSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.06f)),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun VideoDetailThumbnail(
    imageUrl: String?,
    title: String,
    colorScheme: ColorScheme,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var imageFailed by remember(imageUrl) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        colorScheme.primary.copy(alpha = 0.22f),
                        colorScheme.secondary.copy(alpha = 0.14f),
                        colorScheme.surfaceVariant.copy(alpha = 0.82f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (imageUrl != null && !imageFailed) {
            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = { imageFailed = true }
            )
        } else {
            Text(
                "VIDEO",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface.copy(alpha = 0.48f)
            )
        }

        Surface(
            color = Color.White.copy(alpha = 0.18f),
            contentColor = Color.White,
            shape = RoundedCornerShape(999.dp),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(14.dp),
            onClick = onBack
        ) {
            Text(
                "<",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VideoDetailMetaChips(
    videoItem: VideoItem,
    colorScheme: ColorScheme
) {
    val chips = buildList {
        videoItem.type.takeIf { it.isNotBlank() }?.let { add(it) }
        videoItem.year?.let { add(it.toString()) }
        if (videoItem.durationSeconds > 0) add(formatVideoDuration(videoItem.durationSeconds))
    }
    if (chips.isEmpty()) return

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        chips.forEach { chip ->
            Surface(
                color = colorScheme.surfaceVariant.copy(alpha = 0.56f),
                contentColor = colorScheme.onSurface,
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    chip,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun VideoEpisodeCard(
    episode: VideoEpisode,
    colorScheme: ColorScheme,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interactionSource)

    Surface(
        color = colorScheme.surfaceVariant.copy(alpha = 0.42f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.045f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 6.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (episode.imageUrl != null) {
                var imageFailed by remember(episode.imageUrl) { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .aspectRatio(16f / 9f)
                        .weight(0.38f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    colorScheme.primary.copy(alpha = 0.16f),
                                    colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (!imageFailed) {
                        AsyncImage(
                            model = episode.imageUrl,
                            contentDescription = episode.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            onError = { imageFailed = true }
                        )
                    } else {
                        Text(
                            "E${episode.episodeNumber}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurface.copy(alpha = 0.42f)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    episode.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    buildString {
                        append("S${episode.seasonNumber}E${episode.episodeNumber}")
                        if (episode.durationSeconds > 0) {
                            append("  ")
                            append(formatVideoDuration(episode.durationSeconds))
                        }
                    },
                    fontSize = 12.sp,
                    color = colorScheme.onSurface.copy(alpha = 0.56f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val progress = episode.progress
                progress?.toEpisodeProgressLabel()?.let { progressLabel ->
                    Text(
                        progressLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (progress.isPlayed) {
                            colorScheme.primary
                        } else {
                            colorScheme.onSurface.copy(alpha = 0.62f)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (episode.overview.isNotBlank()) {
                    Text(
                        episode.overview,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = colorScheme.onSurface.copy(alpha = 0.52f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun VideoProgress.toEpisodeProgressLabel(): String? {
    return when {
        isPlayed -> "已看完"
        currentTimeSeconds > 0 -> {
            val progressText = playedPercentage
                .takeIf { it > 0f }
                ?.let { " ${(it).toInt()}%" }
                .orEmpty()
            "继续观看 ${formatVideoDuration(currentTimeSeconds)}$progressText"
        }
        else -> null
    }
}

private fun VideoItem.withPlayed(played: Boolean): VideoItem {
    val nextProgress = (progress ?: VideoProgress()).copy(
        currentTimeSeconds = if (played) durationSeconds else 0,
        playedPercentage = if (played) 100f else 0f,
        isPlayed = played
    )
    return copy(progress = nextProgress)
}
