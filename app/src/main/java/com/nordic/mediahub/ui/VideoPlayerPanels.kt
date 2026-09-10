package com.nordic.mediahub.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nordic.mediahub.data.VideoItem
import com.nordic.mediahub.playback.VideoPlaybackState
import com.nordic.mediahub.playback.resolvePlaybackSpeedLabel
import com.nordic.mediahub.ui.theme.NordicAlpha
import com.nordic.mediahub.ui.theme.NordicShapes
import com.nordic.mediahub.ui.theme.NordicSpacing
import com.nordic.mediahub.ui.theme.NordicTheme
import kotlin.math.abs

/** In-window modal: keeps the app shell's fullscreen/system-bar owner unchanged. */
@Composable
internal fun VideoPlayerPanelHost(
    panel: VideoPlayerPanel,
    state: VideoPlaybackState,
    episodes: List<VideoItem>,
    nextEpisode: VideoItem?,
    isFullscreen: Boolean,
    pipEnabled: Boolean,
    autoSkipIntro: Boolean,
    qualityMode: com.nordic.mediahub.data.VideoQualityMode,
    onPanelChange: (VideoPlayerPanel) -> Unit,
    onDismiss: () -> Unit,
    onSetPlaybackSpeed: (Float) -> Unit,
    onCycleAspectRatio: () -> Unit,
    onSetPreferredTextTrack: (com.nordic.mediahub.data.VideoStreamInfo?) -> Unit,
    onSetPreferredAudioTrack: (com.nordic.mediahub.data.VideoStreamInfo?) -> Unit,
    onTogglePip: (Boolean) -> Unit,
    onToggleAutoSkipIntro: (Boolean) -> Unit,
    onSetQualityMode: (com.nordic.mediahub.data.VideoQualityMode) -> Unit,
    onSeekTo: (Int) -> Unit,
    onPlayEpisode: (VideoItem) -> Unit,
    onPlayNextEpisode: () -> Unit
) {
    val video = state.video ?: return
    BackHandler(onBack = onDismiss)
    NordicTheme(darkTheme = true) {
        val colors = MaterialTheme.colorScheme
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.48f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = "关闭${panel.title}",
                        onClick = onDismiss
                    )
            )
            BoxWithConstraints(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(NordicSpacing.sm)
            ) {
                val sidePanel = useVideoPlayerSidePanel(isFullscreen, maxWidth, maxHeight)
                Surface(
                    color = colors.surface.copy(alpha = 1f),
                    contentColor = colors.onSurface,
                    shape = NordicShapes.xl,
                    modifier = Modifier
                        .align(if (sidePanel) Alignment.CenterEnd else Alignment.BottomCenter)
                        .width(if (sidePanel) (maxWidth * 0.45f).coerceIn(300.dp, 400.dp) else maxWidth)
                        .fillMaxHeight(if (sidePanel) 1f else 0.76f)
                        .semantics { paneTitle = panel.title }
                        // Blank space belongs to this panel, not to the dismissal scrim behind it.
                        .pointerInput(Unit) { detectTapGestures(onTap = {}) }
                ) {
                    Column(Modifier.fillMaxSize().padding(NordicSpacing.lg)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    panel.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.semantics { heading() }
                                )
                                if (panel == VideoPlayerPanel.Episodes) {
                                    Text(
                                        video.seriesName?.takeIf { it.isNotBlank() } ?: video.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurface.copy(alpha = NordicAlpha.medium),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Filled.Close, contentDescription = "关闭${panel.title}")
                            }
                        }
                        when (panel) {
                            VideoPlayerPanel.Settings -> Column(
                                Modifier.weight(1f).verticalScroll(rememberScrollState())
                            ) {
                                if (state.introRange != null) {
                                    VideoPlayerSettingToggleRow(
                                        icon = Icons.Filled.SkipNext,
                                        title = "自动跳过片头",
                                        description = "播放进入片头区间时自动跳过一次",
                                        checked = autoSkipIntro,
                                        colors = colors,
                                        onCheckedChange = onToggleAutoSkipIntro
                                    )
                                }
                                if (video.chapters.isNotEmpty()) {
                                    VideoPlayerSettingRow(Icons.Filled.VideoLibrary, "章节",
                                        resolveVideoChaptersSummary(video, state.positionSeconds), colors) {
                                        onPanelChange(VideoPlayerPanel.Chapters)
                                    }
                                }
                                VideoPlayerSettingRow(Icons.Filled.Speed, "播放速度",
                                    resolvePlaybackSpeedLabel(state.playbackSpeed), colors) {
                                    onPanelChange(VideoPlayerPanel.Speed)
                                }
                                VideoPlayerSettingRow(Icons.Filled.Subtitles, "字幕与音轨",
                                    resolveVideoTracksSummary(state), colors) {
                                    onPanelChange(VideoPlayerPanel.Tracks)
                                }
                                VideoPlayerSettingRow(Icons.Filled.AspectRatio, "画面比例",
                                    videoPlayerAspectRatioLabel(state.aspectRatioMode), colors,
                                    onClick = onCycleAspectRatio)
                                if (video.sourceType == com.nordic.mediahub.data.VideoServerType.EMBY && !video.streamUrl.isNullOrBlank()) {
                                    VideoPlayerSettingRow(Icons.Filled.HighQuality, "清晰度",
                                        qualityMode.label, colors) {
                                        onPanelChange(VideoPlayerPanel.Quality)
                                    }
                                }
                                if (episodes.isNotEmpty()) {
                                    VideoPlayerSettingRow(Icons.AutoMirrored.Filled.PlaylistPlay, "选集",
                                        "已载入 ${episodes.size} 集", colors) {
                                        onPanelChange(VideoPlayerPanel.Episodes)
                                    }
                                }
                                if (!nextEpisode?.streamUrl.isNullOrBlank()) {
                                    VideoPlayerSettingRow(Icons.Filled.SkipNext, "下一集",
                                        nextEpisode?.title.orEmpty(), colors, onClick = onPlayNextEpisode)
                                }
                                VideoPlayerSettingRow(Icons.Filled.Info, "影片信息", "简介与播放进度", colors) {
                                    onPanelChange(VideoPlayerPanel.Info)
                                }
                                VideoPlayerSettingToggleRow(
                                    icon = Icons.Filled.PictureInPictureAlt,
                                    title = "画中画",
                                    description = "离开应用时以小窗继续播放",
                                    checked = pipEnabled,
                                    colors = colors,
                                    onCheckedChange = onTogglePip
                                )
                                Text(
                                    "双击左侧后退 ${LocalAppPreferences.current.videoSkipBack} 秒，右侧前进 ${LocalAppPreferences.current.videoSkipForward} 秒；长按临时 2 倍速。左右侧滑动分别调整亮度和音量。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurface.copy(alpha = NordicAlpha.medium),
                                    modifier = Modifier.padding(top = NordicSpacing.lg)
                                )
                            }
                            VideoPlayerPanel.Speed -> LazyColumn(Modifier.weight(1f)) {
                                items(VIDEO_PLAYBACK_SPEED_OPTIONS, key = { it }) { speed ->
                                    MediaPlayerChoiceRow(
                                        title = resolvePlaybackSpeedLabel(speed),
                                        selected = abs(speed - state.playbackSpeed) < 0.001f,
                                        colors = colors,
                                        onClick = { onSetPlaybackSpeed(speed) }
                                    )
                                }
                            }
                            VideoPlayerPanel.Tracks -> VideoPlayerTracksContent(
                                state, colors, onSetPreferredTextTrack, onSetPreferredAudioTrack,
                                Modifier.weight(1f)
                            )
                            VideoPlayerPanel.Info -> VideoPlayerInfoContent(
                                video, state.positionSeconds,
                                maxOf(state.durationSeconds, video.durationSeconds), colors,
                                Modifier.weight(1f)
                            )
                            VideoPlayerPanel.Episodes -> VideoPlayerEpisodesContent(
                                video, episodes, colors, onPlayEpisode, Modifier.weight(1f)
                            )
                            VideoPlayerPanel.Chapters -> VideoPlayerChaptersContent(
                                video, state.positionSeconds, colors, onSeekTo, Modifier.weight(1f)
                            )
                            VideoPlayerPanel.Quality -> VideoPlayerQualityContent(
                                qualityMode, colors, onSetQualityMode, Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoPlayerSettingRow(
    icon: ImageVector,
    title: String,
    value: String,
    colors: ColorScheme,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = NordicSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(NordicSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = colors.onSurface, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NordicSpacing.xs)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.bodySmall,
                color = colors.onSurface.copy(alpha = NordicAlpha.medium),
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = colors.onSurface.copy(alpha = NordicAlpha.subtle))
    }
}

@Composable
private fun VideoPlayerSettingToggleRow(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    colors: ColorScheme,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = NordicSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(NordicSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = colors.onSurface, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NordicSpacing.xs)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(description, style = MaterialTheme.typography.bodySmall,
                color = colors.onSurface.copy(alpha = NordicAlpha.medium),
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun VideoPlayerInfoContent(
    video: VideoItem,
    positionSeconds: Int,
    durationSeconds: Int,
    colors: ColorScheme,
    modifier: Modifier
) {
    val chips = remember(video) { videoPlayerInfoChips(video) }
    val rows = videoPlayerInfoRows(video, positionSeconds, durationSeconds)
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(top = NordicSpacing.md),
        verticalArrangement = Arrangement.spacedBy(NordicSpacing.md)
    ) {
        Text(video.title, style = MaterialTheme.typography.titleMedium)
        if (chips.isNotEmpty()) Text(chips.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall, color = colors.primary)
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NordicSpacing.md)) {
                Text(row.label, style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurface.copy(alpha = NordicAlpha.medium))
                Text(row.value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            }
        }
        Text("简介", style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = NordicSpacing.sm))
        Text(video.overview.ifBlank { "暂无简介" }, style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface.copy(alpha = NordicAlpha.medium))
    }
}

@Composable
private fun VideoPlayerEpisodesContent(
    current: VideoItem,
    episodes: List<VideoItem>,
    colors: ColorScheme,
    onSelect: (VideoItem) -> Unit,
    modifier: Modifier
) {
    val seasons = remember(episodes) { videoPlayerSeasons(episodes) }
    var selectedSeason by remember(current.id) { mutableStateOf(current.seasonNumber) }
    val seasonEpisodes = remember(episodes, selectedSeason) {
        episodes.filter { it.seasonNumber == selectedSeason }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(current.id, selectedSeason, seasonEpisodes) {
        if (seasonEpisodes.isNotEmpty()) {
            listState.scrollToItem(videoPlayerEpisodeStartIndex(seasonEpisodes, current.id))
        }
    }
    Column(modifier) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(NordicSpacing.sm)) {
            items(seasons, key = { it?.toString() ?: "unseasoned" }) { season ->
                FilterChip(
                    selected = selectedSeason == season,
                    onClick = { selectedSeason = season },
                    label = { Text(videoPlayerSeasonLabel(season)) }
                )
            }
        }
        Text(
            if (episodes.size <= 1) "暂无其他已载入剧集" else "已载入 ${episodes.size} 集 · 点击切换播放",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurface.copy(alpha = NordicAlpha.medium),
            modifier = Modifier.padding(vertical = NordicSpacing.sm)
        )
        LazyColumn(
            modifier = Modifier.weight(1f), state = listState,
            verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)
        ) {
            items(seasonEpisodes, key = { it.id }, contentType = { "player-episode" }) { episode ->
                VideoEpisodeRow(
                    episode = episode, colorScheme = colors,
                    isCurrent = episode.id == current.id, compact = true,
                    onClick = { onSelect(episode) }
                )
            }
        }
    }
}

/** One-line summary for the Settings entry row. */
internal fun resolveVideoTracksSummary(state: VideoPlaybackState): String {
    val subtitle = state.selectedSubtitleStream
    val audio = state.selectedAudioStream
    val subtitleLabel = subtitle?.let { it.displayTitle ?: it.language } ?: "关闭"
    val audioLabel = audio?.let { it.displayTitle ?: it.language }
        ?: state.availableAudioStreams.firstOrNull()?.let { it.displayTitle ?: it.language }
        ?: "默认"
    return "字幕 $subtitleLabel · 音轨 $audioLabel"
}

/** Settings entry-row summary: current chapter name, or the total count. */
internal fun resolveVideoChaptersSummary(video: VideoItem, positionSeconds: Int): String {
    val chapters = video.chapters
    if (chapters.isEmpty()) return "无章节"
    val currentIndex = videoChapterIndexForPosition(chapters, positionSeconds)
    return currentIndex?.let { index ->
        val chapter = chapters[index]
        "${index + 1}/${chapters.size} · ${chapter.name}"
    } ?: "${chapters.size} 章"
}

/** Index of the chapter covering [positionSeconds], or null before the first chapter. */
internal fun videoChapterIndexForPosition(
    chapters: List<com.nordic.mediahub.data.VideoChapterInfo>,
    positionSeconds: Int
): Int? {
    if (chapters.isEmpty()) return null
    var result: Int? = null
    chapters.forEachIndexed { index, chapter ->
        if (positionSeconds >= chapter.startSeconds) result = index
    }
    return result
}

@Composable
private fun VideoPlayerChaptersContent(
    video: VideoItem,
    positionSeconds: Int,
    colors: ColorScheme,
    onSeekTo: (Int) -> Unit,
    modifier: Modifier
) {
    val chapters = video.chapters
    val listState = rememberLazyListState()
    val currentIndex = videoChapterIndexForPosition(chapters, positionSeconds)
    LaunchedEffect(video.id, currentIndex) {
        val target = currentIndex ?: return@LaunchedEffect
        // Bring the active chapter into view without forcing a jump when it is
        // already visible (same contract as the horizontal segment lists).
        val visible = listState.layoutInfo.visibleItemsInfo.any { it.index == target }
        if (!visible) listState.scrollToItem(target)
    }
    Column(modifier) {
        Text(
            "点击章节跳转到起点",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurface.copy(alpha = NordicAlpha.medium),
            modifier = Modifier.padding(vertical = NordicSpacing.sm)
        )
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)
        ) {
            items(chapters, key = { "chapter-${it.startSeconds}-${it.name}" }, contentType = { "player-chapter" }) { chapter ->
                val index = chapters.indexOf(chapter)
                MediaPlayerChoiceRow(
                    title = chapter.name,
                    subtitle = formatDuration(chapter.startSeconds),
                    selected = index == currentIndex,
                    colors = colors,
                    onClick = { onSeekTo(chapter.startSeconds) }
                )
            }
        }
    }
}

@Composable
private fun VideoPlayerQualityContent(
    qualityMode: com.nordic.mediahub.data.VideoQualityMode,
    colors: ColorScheme,
    onSetQualityMode: (com.nordic.mediahub.data.VideoQualityMode) -> Unit,
    modifier: Modifier
) {
    Column(modifier.verticalScroll(rememberScrollState())) {
        Text(
            "切换清晰度后重新播放生效，自动保留播放进度；限码率档位由服务器转码播放。",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurface.copy(alpha = NordicAlpha.medium),
            modifier = Modifier.padding(vertical = NordicSpacing.sm)
        )
        Column(verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)) {
            com.nordic.mediahub.data.VideoQualityMode.entries.forEach { mode ->
                MediaPlayerChoiceRow(
                    title = mode.label,
                    subtitle = when (mode) {
                        com.nordic.mediahub.data.VideoQualityMode.AUTO -> "直连优先，按服务器能力播放"
                        com.nordic.mediahub.data.VideoQualityMode.ORIGINAL -> "始终播放原始码率"
                        else -> "转码到 ${mode.label}（H.264 / AAC）"
                    },
                    selected = mode == qualityMode,
                    colors = colors,
                    onClick = { onSetQualityMode(mode) }
                )
            }
        }
    }
}

@Composable
private fun VideoPlayerTracksContent(
    state: VideoPlaybackState,
    colors: ColorScheme,
    onSetPreferredTextTrack: (com.nordic.mediahub.data.VideoStreamInfo?) -> Unit,
    onSetPreferredAudioTrack: (com.nordic.mediahub.data.VideoStreamInfo?) -> Unit,
    modifier: Modifier
) {
    LazyColumn(
        modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)
    ) {
        item {
            Text(
                "字幕",
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface.copy(alpha = NordicAlpha.medium),
                modifier = Modifier.padding(top = NordicSpacing.sm)
            )
        }
        item {
            MediaPlayerChoiceRow(
                title = "关闭字幕",
                selected = state.selectedSubtitleStream == null,
                colors = colors,
                onClick = { onSetPreferredTextTrack(null) }
            )
        }
        if (state.availableSubtitleStreams.isEmpty()) {
            item {
                Text(
                    "这个视频没有可用的字幕轨道",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurface.copy(alpha = NordicAlpha.medium)
                )
            }
        } else {
            items(state.availableSubtitleStreams, key = { "sub-${it.index}" }) { stream ->
                MediaPlayerChoiceRow(
                    title = stream.displayTitle ?: stream.language ?: "字幕 ${stream.index}",
                    selected = state.selectedSubtitleStream?.index == stream.index,
                    colors = colors,
                    onClick = { onSetPreferredTextTrack(stream) }
                )
            }
        }
        item {
            Text(
                "音轨",
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface.copy(alpha = NordicAlpha.medium),
                modifier = Modifier.padding(top = NordicSpacing.sm)
            )
        }
        if (state.availableAudioStreams.isEmpty()) {
            item {
                Text(
                    "没有检测到其他音轨",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurface.copy(alpha = NordicAlpha.medium)
                )
            }
        } else {
            items(state.availableAudioStreams, key = { "audio-${it.index}" }) { stream ->
                MediaPlayerChoiceRow(
                    title = stream.displayTitle ?: stream.language ?: "音轨 ${stream.index}",
                    selected = state.selectedAudioStream?.index == stream.index ||
                        (state.selectedAudioStream == null && stream == state.availableAudioStreams.firstOrNull()),
                    colors = colors,
                    onClick = { onSetPreferredAudioTrack(stream) }
                )
            }
        }
    }
}
