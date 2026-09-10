package com.nordic.mediahub.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nordic.mediahub.data.AudiobookBookmark
import com.nordic.mediahub.data.AudiobookChapter
import com.nordic.mediahub.playback.resolvePlaybackSpeedLabel
import com.nordic.mediahub.playback.AudiobookPlaybackState
import com.nordic.mediahub.ui.theme.NordicAlpha
import com.nordic.mediahub.ui.theme.NordicMotion
import com.nordic.mediahub.ui.theme.NordicShapes
import com.nordic.mediahub.ui.theme.NordicSpacing
import kotlinx.coroutines.launch

private const val AUDIOBOOK_SWIPE_DISMISS_THRESHOLD_RATIO = 0.25f
private const val AUDIOBOOK_SWIPE_DISMISS_MAX_SCALE_DOWN = 0.04f
private const val AUDIOBOOK_SWIPE_DISMISS_MAX_ALPHA_DECAY = 0.6f

/** Speed rates offered by the audiobook playback-speed sheet (audiobookshelf-style). */
internal val AUDIOBOOK_PLAYBACK_SPEED_OPTIONS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AudiobookPlayerScreen(
    state: AudiobookPlaybackState,
    colorScheme: ColorScheme,
    externalError: String? = null,
    bookmarks: List<AudiobookBookmark> = emptyList(),
    onAddBookmark: () -> Unit = {},
    onDeleteBookmark: (String) -> Unit = {},
    onSetSleepTimer: (Int, Boolean) -> Unit = { _, _ -> },
    onCancelSleepTimer: () -> Unit = {},
    onSeek: (Int) -> Unit,
    onSeekBack: () -> Unit = {},
    onSeekForward: () -> Unit = {},
    onSeekToPreviousChapter: () -> Unit = {},
    onSeekToNextChapter: () -> Unit = {},
    onSetPlaybackSpeed: (Float) -> Unit = {},
    onPlayPause: () -> Unit,
    onClose: () -> Unit,
    onCloseAnyway: () -> Unit = {}
) {
    val session = state.session
    val duration = state.durationSeconds.coerceAtLeast(0)
    val timeline = resolvePlayerTimeline(state.positionSeconds, duration)
    val currentOnClose by rememberUpdatedState(onClose)
    var scrubPosition by remember(session?.sessionId) { mutableStateOf<Float?>(null) }
    var showBookmarks by remember(session?.sessionId) { mutableStateOf(false) }
    var showSleepTimer by remember(session?.sessionId) { mutableStateOf(false) }
    var showChapterList by remember(session?.sessionId) { mutableStateOf(false) }
    var showSpeedSheet by remember(session?.sessionId) { mutableStateOf(false) }
    val visiblePosition = (scrubPosition ?: state.positionSeconds.toFloat()).coerceIn(0f, timeline.sliderMaxSeconds.toFloat())
    val errorMessage = (externalError ?: state.errorMessage)?.takeIf { it.isNotBlank() }
    val chapterNavigationEnabled = session != null && state.chapters.isNotEmpty()
    val playbackControlsEnabled = session != null
    val sortedChapters = remember(state.chapters) {
        state.chapters.sortedBy { chapter -> chapter.startSeconds }
    }
    val currentChapter = resolveCurrentAudiobookChapterFromSorted(
        sortedChapters = sortedChapters,
        positionSeconds = visiblePosition.toInt()
    )
    val statusText = when {
        errorMessage != null -> errorMessage
        session == null -> "从书库选择一本有声书"
        state.isBuffering -> "正在缓冲"
        state.isPlaying -> "正在播放"
        else -> "已暂停"
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        colorScheme.primary.copy(alpha = 0.16f),
                        colorScheme.secondary.copy(alpha = 0.06f),
                        colorScheme.background,
                        colorScheme.background
                    )
                )
            )
    ) {
        val compact = maxHeight < 740.dp
        val sidePadding = if (compact) NordicSpacing.lg else NordicSpacing.xl
        val topPadding = if (compact) NordicSpacing.sm else NordicSpacing.md
        val bottomPadding = if (compact) NordicSpacing.md else NordicSpacing.lg
        val sectionGap = NordicSpacing.md
        val screenHeightPx = with(LocalDensity.current) { maxHeight.toPx() }
        val swipeThresholdPx = screenHeightPx * AUDIOBOOK_SWIPE_DISMISS_THRESHOLD_RATIO

        val dismissScope = rememberCoroutineScope()
        val dragYState = remember { mutableFloatStateOf(0f) }
        val animatedDismiss = remember { Animatable(0f) }
        var isDismissing by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(screenHeightPx, swipeThresholdPx) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            dragYState.floatValue = 0f
                            isDismissing = false
                            dismissScope.launch { animatedDismiss.snapTo(0f) }
                        },
                        onDragEnd = {
                            val accumulated = dragYState.floatValue
                            dragYState.floatValue = 0f
                            if (accumulated >= swipeThresholdPx) {
                                isDismissing = true
                                dismissScope.launch {
                                    animatedDismiss.snapTo(accumulated)
                                    animatedDismiss.animateTo(
                                        targetValue = screenHeightPx,
                                        animationSpec = tween(
                                            NordicMotion.durationShort,
                                            easing = NordicMotion.easingStandard
                                        )
                                    )
                                    currentOnClose()
                                }
                            } else if (accumulated > 0f) {
                                dismissScope.launch {
                                    animatedDismiss.snapTo(accumulated)
                                    animatedDismiss.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            dragYState.floatValue = 0f
                            isDismissing = false
                            dismissScope.launch { animatedDismiss.snapTo(0f) }
                        }
                    ) { change, dragAmountY ->
                        change.consume()
                        val next = (dragYState.floatValue + dragAmountY).coerceAtLeast(0f)
                        dragYState.floatValue = next
                    }
                }
                .graphicsLayer {
                    val live = if (isDismissing) animatedDismiss.value else dragYState.floatValue
                    val progress = (live / screenHeightPx).coerceIn(0f, 1f)
                    translationY = live
                    val scale = 1f - AUDIOBOOK_SWIPE_DISMISS_MAX_SCALE_DOWN * progress
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f - AUDIOBOOK_SWIPE_DISMISS_MAX_ALPHA_DECAY * progress
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(
                        start = sidePadding,
                        top = topPadding,
                        end = sidePadding,
                        bottom = bottomPadding
                    ),
                verticalArrangement = Arrangement.spacedBy(sectionGap)
            ) {
                MediaPlayerTopBar("有声书", colorScheme, onClose, resolvePlaybackSpeedLabel(state.playbackSpeed),
                    onSpeed = { showSpeedSheet = true }, speedEnabled = playbackControlsEnabled)
                MediaAudioPlayerBody(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    artwork = { displayModifier ->
                        AudiobookPrimaryDisplay(session?.displayTitle ?: "有声书播放", session?.coverUrl,
                            colorScheme, displayModifier)
                    },
                    controls = {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(NordicSpacing.xs)) {
                            Text(session?.displayTitle ?: "等待播放", style = MaterialTheme.typography.headlineMedium,
                                color = colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            session?.displayAuthor?.takeIf { it.isNotBlank() }?.let { author ->
                                Text(author, style = MaterialTheme.typography.bodyMedium,
                                    color = colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            currentChapter?.title?.takeIf { it.isNotBlank() }?.let { chapter ->
                                Text("当前章节：$chapter", style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Text(statusText, style = MaterialTheme.typography.bodySmall,
                            color = if (errorMessage != null) colorScheme.error else colorScheme.onSurfaceVariant,
                            maxLines = 3, overflow = TextOverflow.Ellipsis)
                        if (errorMessage != null) {
                            MediaPlayerTool("仍要关闭", "停止并关闭有声书", colorScheme, onCloseAnyway,
                                icon = Icons.Filled.Close, destructive = true)
                        }
                        MediaPlayerTimeline(visiblePosition, duration, colorScheme, playbackControlsEnabled,
                            onScrub = { scrubPosition = it },
                            onScrubFinished = {
                                val target = scrubPosition ?: visiblePosition
                                onSeek(target.toInt())
                                scrubPosition = null
                            },
                            onScrubCancelled = { scrubPosition = null })
                        MediaTransportRow(
                            leading = MediaPlayerAction(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "上一章节",
                                onSeekToPreviousChapter, chapterNavigationEnabled),
                            previous = MediaPlayerAction(Icons.Filled.Replay, "后退 ${LocalAppPreferences.current.audiobookSkipBack} 秒", onSeekBack, playbackControlsEnabled),
                            play = MediaPlayerAction(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                if (state.isPlaying) "暂停" else "播放", onPlayPause, playbackControlsEnabled),
                            next = MediaPlayerAction(Icons.Filled.FastForward, "前进 ${LocalAppPreferences.current.audiobookSkipForward} 秒", onSeekForward, playbackControlsEnabled),
                            trailing = MediaPlayerAction(Icons.AutoMirrored.Filled.KeyboardArrowRight, "下一章节",
                                onSeekToNextChapter, chapterNavigationEnabled),
                            colors = colorScheme
                        )
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NordicSpacing.sm),
                            verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)) {
                            MediaPlayerTool("章节", "章节列表", colorScheme, { showChapterList = true },
                                icon = Icons.AutoMirrored.Filled.MenuBook, enabled = chapterNavigationEnabled)
                            MediaPlayerTool("书签", "书签", colorScheme, { showBookmarks = true },
                                icon = Icons.Filled.BookmarkBorder, enabled = playbackControlsEnabled)
                            val timerActive = isAudiobookSleepTimerActive(state.sleepTimerRemainingSeconds, state.sleepTimerAtChapterEnd)
                            MediaPlayerTool(if (timerActive) "定时中" else "定时",
                                if (timerActive) sleepTimerRemainingLabel(state.sleepTimerRemainingSeconds, state.sleepTimerAtChapterEnd) else "睡眠定时器",
                                colorScheme, { showSleepTimer = true }, icon = Icons.Filled.Bedtime,
                                enabled = playbackControlsEnabled, active = timerActive)
                        }
                    }
                )
            }
        }
    }

    if (showBookmarks) {
        AudiobookBookmarkSheet(
            bookmarks = bookmarks,
            colorScheme = colorScheme,
            currentPositionSeconds = state.positionSeconds,
            onAddBookmark = onAddBookmark,
            onJumpTo = { position ->
                showBookmarks = false
                onSeek(position)
            },
            onDelete = { bookmarkId ->
                onDeleteBookmark(bookmarkId)
            },
            onDismiss = { showBookmarks = false }
        )
    }

    if (showSleepTimer) {
        AudiobookSleepTimerSheet(
            sleepTimerRemainingSeconds = state.sleepTimerRemainingSeconds,
            sleepTimerAtChapterEnd = state.sleepTimerAtChapterEnd,
            colorScheme = colorScheme,
            onSet = { minutes, atChapterEnd ->
                onSetSleepTimer(minutes, atChapterEnd)
                showSleepTimer = false
            },
            onCancel = {
                onCancelSleepTimer()
                showSleepTimer = false
            },
            onDismiss = { showSleepTimer = false }
        )
    }

    if (showChapterList) {
        AudiobookChapterListSheet(
            chapters = sortedChapters,
            currentPositionSeconds = state.positionSeconds,
            colorScheme = colorScheme,
            onSeekTo = { positionSeconds ->
                showChapterList = false
                onSeek(positionSeconds)
            },
            onDismiss = { showChapterList = false }
        )
    }

    if (showSpeedSheet) {
        AudiobookPlaybackSpeedSheet(
            currentSpeed = state.playbackSpeed,
            colorScheme = colorScheme,
            onSelect = { speed ->
                onSetPlaybackSpeed(speed)
                showSpeedSheet = false
            },
            onDismiss = { showSpeedSheet = false }
        )
    }
}

@Composable
private fun AudiobookChapterListSheet(
    chapters: List<AudiobookChapter>,
    currentPositionSeconds: Int,
    colorScheme: ColorScheme,
    onSeekTo: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val currentIndex = chapters.indexOfLast { it.startSeconds <= currentPositionSeconds }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentIndex.coerceAtLeast(0))
    MediaPlayerSheet("章节", colorScheme, onDismiss, "共 ${chapters.size} 章 · 点击跳转", skipPartiallyExpanded = false) {
        if (chapters.isEmpty()) MediaStateCard("暂无章节", "这本有声书未提供章节信息。", density = MediaStateDensity.Compact)
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false), state = listState) {
            itemsIndexed(chapters, key = { index, chapter -> "${chapter.startSeconds}:$index" },
                contentType = { _, _ -> "audiobook-chapter-row" }) { index, chapter ->
                MediaPlayerChoiceRow(
                    title = "${index + 1}. ${chapter.title}", selected = index == currentIndex, colors = colorScheme,
                    subtitle = "${formatDuration(chapter.startSeconds)} – ${formatDuration(chapter.endSeconds)}",
                    onClick = { onSeekTo(chapter.startSeconds) }
                )
            }
        }
    }
}

@Composable
private fun AudiobookPlaybackSpeedSheet(currentSpeed: Float, colorScheme: ColorScheme, onSelect: (Float) -> Unit, onDismiss: () -> Unit) {
    MediaPlaybackSpeedSheet(AUDIOBOOK_PLAYBACK_SPEED_OPTIONS, currentSpeed, colorScheme, onSelect, onDismiss)
}

@Composable
private fun AudiobookSleepTimerSheet(
    sleepTimerRemainingSeconds: Int?,
    sleepTimerAtChapterEnd: Boolean,
    colorScheme: ColorScheme,
    onSet: (Int, Boolean) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    val preselectedMinutes = LocalAppPreferences.current.audiobookSleepMinutes
    val active = isAudiobookSleepTimerActive(sleepTimerRemainingSeconds, sleepTimerAtChapterEnd)
    MediaPlayerSheet("睡眠定时器", colorScheme, onDismiss,
        sleepTimerRemainingLabel(sleepTimerRemainingSeconds, sleepTimerAtChapterEnd), skipPartiallyExpanded = false) {
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
            item { MediaPlayerChoiceRow("使用预选：" + LocalAppPreferences.current.audiobookSleepMinutes + " 分钟", null, colorScheme,
                onClick = { onSet(preselectedMinutes, false) }) }
            item { MediaPlayerChoiceRow("关闭", !active, colorScheme, onCancel) }
            items(listOf(10, 20, 30, 45, 60), key = { it }) { minutes ->
                // Remaining time cannot tell us which preset was originally chosen.
                MediaPlayerChoiceRow("$minutes 分钟后停止", null, colorScheme, onClick = { onSet(minutes, false) })
            }
            item { MediaPlayerChoiceRow("本章结束", active && sleepTimerAtChapterEnd, colorScheme,
                onClick = { onSet(0, true) }) }
        }
    }
}

internal fun isAudiobookSleepTimerActive(remainingSeconds: Int?, atChapterEnd: Boolean): Boolean =
    if (remainingSeconds != null && remainingSeconds <= 0) false else atChapterEnd || remainingSeconds != null

internal fun sleepTimerRemainingLabel(
    sleepTimerRemainingSeconds: Int?,
    atChapterEnd: Boolean
): String {
    val remaining = sleepTimerRemainingSeconds
    if (remaining != null && remaining <= 0) return "已停止"
    if (atChapterEnd) return "将在当前章节结束时停止"
    val countdown = remaining ?: return "未开启"
    val minutes = countdown / 60
    val seconds = countdown % 60
    return if (minutes > 0) {
        "${minutes}分${seconds}秒后停止"
    } else {
        "${seconds}秒后停止"
    }
}

@Composable
private fun AudiobookBookmarkSheet(
    bookmarks: List<AudiobookBookmark>,
    colorScheme: ColorScheme,
    currentPositionSeconds: Int,
    onAddBookmark: () -> Unit,
    onJumpTo: (Int) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    MediaPlayerSheet("书签", colorScheme, onDismiss, "${bookmarks.size} 个书签", skipPartiallyExpanded = false) {
        PrimaryActionButton("在 ${formatDuration(currentPositionSeconds)} 添加书签", colorScheme,
            onClick = onAddBookmark, icon = Icons.Filled.Bookmark)
        if (bookmarks.isEmpty()) {
            MediaStateCard("暂无书签", "使用上方按钮标记当前进度，稍后可从这里跳回。", density = MediaStateDensity.Compact)
        } else LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)) {
            itemsIndexed(bookmarks, key = { index, bookmark -> "${bookmark.id}:$index" },
                contentType = { _, _ -> "audiobook-bookmark-row" }) { _, bookmark ->
                AudiobookBookmarkRow(bookmark, colorScheme,
                    kotlin.math.abs(currentPositionSeconds.toLong() - bookmark.positionSeconds.toLong()) <= 2L,
                    onClick = { onJumpTo(bookmark.positionSeconds) }, onDelete = { onDelete(bookmark.id) })
            }
        }
    }
}

@Composable
private fun AudiobookBookmarkRow(
    bookmark: AudiobookBookmark,
    colorScheme: ColorScheme,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(color = if (isCurrent) colorScheme.primaryContainer else colorScheme.surfaceVariant.copy(alpha = 0.42f),
        shape = NordicShapes.md, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().semantics { selected = isCurrent }
            .clickable(role = Role.Button, onClickLabel = "跳转到书签", onClick = onClick)
            .padding(NordicSpacing.sm), horizontalArrangement = Arrangement.spacedBy(NordicSpacing.sm),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NordicSpacing.xs)) {
                Text(bookmark.label.ifBlank { "书签" }, style = MaterialTheme.typography.bodyMedium,
                    color = if (isCurrent) colorScheme.onPrimaryContainer else colorScheme.onSurface,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(formatDuration(bookmark.positionSeconds), style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = if (isCurrent) colorScheme.onPrimaryContainer.copy(alpha = 0.78f) else colorScheme.onSurfaceVariant)
            }
            MediaPlayerIconAction(MediaPlayerAction(Icons.Filled.Delete, "删除书签", onDelete), colorScheme, destructive = true)
        }
    }
}

@Composable
private fun AudiobookPrimaryDisplay(title: String, coverUrl: String?, colorScheme: ColorScheme, modifier: Modifier = Modifier) {
    MediaPlayerArtwork(title, coverUrl, Icons.AutoMirrored.Filled.MenuBook, colorScheme, modifier)
}

internal fun resolveCurrentAudiobookChapter(
    chapters: List<AudiobookChapter>,
    positionSeconds: Int
): AudiobookChapter? {
    val sortedChapters = chapters.sortedBy { chapter -> chapter.startSeconds }
    return resolveCurrentAudiobookChapterFromSorted(sortedChapters, positionSeconds)
}

internal fun resolveCurrentAudiobookChapterFromSorted(
    sortedChapters: List<AudiobookChapter>,
    positionSeconds: Int
): AudiobookChapter? {
    val safePosition = positionSeconds.coerceAtLeast(0)
    return sortedChapters.lastOrNull { chapter -> chapter.startSeconds <= safePosition }
}
