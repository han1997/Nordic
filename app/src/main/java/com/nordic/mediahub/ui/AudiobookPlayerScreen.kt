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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay30
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@OptIn(ExperimentalMaterial3Api::class)
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
    onCyclePlaybackSpeed: () -> Unit = {},
    onSetPlaybackSpeed: (Float) -> Unit = {},
    onPlayPause: () -> Unit,
    onClose: () -> Unit,
    onCloseAnyway: () -> Unit = {}
) {
    val session = state.session
    val duration = state.durationSeconds.coerceAtLeast(1)
    var scrubPosition by remember(session?.sessionId) { mutableStateOf<Float?>(null) }
    var showBookmarks by remember(session?.sessionId) { mutableStateOf(false) }
    var showSleepTimer by remember(session?.sessionId) { mutableStateOf(false) }
    var showChapterList by remember(session?.sessionId) { mutableStateOf(false) }
    var showSpeedSheet by remember(session?.sessionId) { mutableStateOf(false) }
    val visiblePosition = scrubPosition ?: state.positionSeconds.toFloat()
    val errorMessage = externalError ?: state.errorMessage
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
        val statusTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val topPadding = statusTopPadding + if (compact) NordicSpacing.sm else NordicSpacing.md
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
                .pointerInput(Unit) {
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
                                    onClose()
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
                    .navigationBarsPadding()
                    .padding(
                        start = sidePadding,
                        top = topPadding,
                        end = sidePadding,
                        bottom = bottomPadding
                    ),
                verticalArrangement = Arrangement.spacedBy(sectionGap)
            ) {
            AudiobookPlayerTopBar(colorScheme = colorScheme, onClose = onClose)
            AudiobookPrimaryDisplay(
                title = session?.displayTitle ?: "有声书播放",
                coverUrl = session?.coverUrl,
                colorScheme = colorScheme,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)
            ) {
                Text(
                    session?.displayTitle ?: "等待播放",
                    fontSize = if (compact) 22.sp else 25.sp,
                    lineHeight = if (compact) 26.sp else 30.sp,
                    color = colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    session?.displayAuthor?.takeIf { it.isNotBlank() } ?: statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (errorMessage == null) {
                        colorScheme.onSurface.copy(alpha = NordicAlpha.medium)
                    } else {
                        colorScheme.error
                    },
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (errorMessage != null) {
                    Surface(
                        color = colorScheme.error.copy(alpha = 0.14f),
                        contentColor = colorScheme.error,
                        shape = NordicShapes.full,
                        border = BorderStroke(1.dp, colorScheme.error.copy(alpha = 0.24f)),
                        modifier = Modifier.clickable(onClick = onCloseAnyway)
                    ) {
                        Text(
                            "仍要关闭",
                            modifier = Modifier.padding(horizontal = NordicSpacing.lg, vertical = NordicSpacing.sm),
                            color = colorScheme.error,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(NordicSpacing.sm)) {
                    MetaChip(formatDuration(duration), colorScheme)
                    MetaChip(
                        text = formatPlaybackSpeed(state.playbackSpeed),
                        colorScheme = colorScheme,
                        enabled = playbackControlsEnabled,
                        onClick = { showSpeedSheet = true }
                    )
                    if (currentChapter != null) {
                        MetaChip(
                            text = currentChapter.title,
                            colorScheme = colorScheme,
                            enabled = chapterNavigationEnabled,
                            onClick = { showChapterList = true }
                        )
                    } else {
                        MetaChip(
                            text = "章节",
                            colorScheme = colorScheme,
                            enabled = chapterNavigationEnabled,
                            onClick = { showChapterList = true }
                        )
                    }
                }
            }
            Surface(
                color = colorScheme.surfaceVariant.copy(alpha = 0.5f),
                contentColor = colorScheme.onSurface,
                shape = NordicShapes.xl,
                border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.05f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(
                        start = NordicSpacing.md,
                        top = if (compact) NordicSpacing.sm else NordicSpacing.md,
                        end = NordicSpacing.md,
                        bottom = NordicSpacing.md
                    ),
                    verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)
                ) {
                    PlayerThinSlider(
                        position = visiblePosition.coerceIn(0f, duration.toFloat()),
                        duration = duration,
                        colorScheme = colorScheme,
                        enabled = playbackControlsEnabled,
                        onPositionChange = { scrubPosition = it },
                        onPositionChangeFinished = {
                            val target = scrubPosition ?: visiblePosition
                            onSeek(target.toInt())
                            scrubPosition = null
                        },
                        onPositionChangeCanceled = { scrubPosition = null }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            formatDuration(visiblePosition.toInt()),
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurface.copy(alpha = NordicAlpha.subtle)
                        )
                        Text(
                            formatDuration(duration),
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurface.copy(alpha = NordicAlpha.subtle)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AudiobookControlButton(
                            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "上一章节",
                            colorScheme = colorScheme,
                            compact = compact,
                            enabled = chapterNavigationEnabled,
                            onClick = onSeekToPreviousChapter
                        )
                        Spacer(Modifier.size(NordicSpacing.sm))
                        AudiobookControlButton(
                            icon = Icons.Filled.Replay30,
                            contentDescription = "后退 30 秒",
                            colorScheme = colorScheme,
                            compact = compact,
                            enabled = playbackControlsEnabled,
                            onClick = onSeekBack
                        )
                        Spacer(Modifier.size(NordicSpacing.sm))
                        AudiobookPlayButton(
                            icon = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (state.isPlaying) "暂停" else "播放",
                            colorScheme = colorScheme,
                            compact = compact,
                            enabled = playbackControlsEnabled,
                            onClick = onPlayPause
                        )
                        Spacer(Modifier.size(NordicSpacing.sm))
                        AudiobookControlButton(
                            icon = Icons.Filled.Forward30,
                            contentDescription = "前进 30 秒",
                            colorScheme = colorScheme,
                            compact = compact,
                            enabled = playbackControlsEnabled,
                            onClick = onSeekForward
                        )
Spacer(Modifier.size(NordicSpacing.sm))
                        AudiobookControlButton(
                            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "下一章节",
                            colorScheme = colorScheme,
                            compact = compact,
                            enabled = chapterNavigationEnabled,
                            onClick = onSeekToNextChapter
                        )
                        Spacer(Modifier.size(NordicSpacing.sm))
                        AudiobookControlButton(
                            icon = Icons.Filled.BookmarkBorder,
                            contentDescription = "书签",
                            colorScheme = colorScheme,
                            compact = compact,
                            enabled = playbackControlsEnabled,
                            onClick = { showBookmarks = true }
                        )
                        Spacer(Modifier.size(NordicSpacing.sm))
                        AudiobookControlButton(
                            icon = Icons.Filled.Bedtime,
                            contentDescription = "睡眠定时器",
                            colorScheme = colorScheme,
                            compact = compact,
                            enabled = playbackControlsEnabled,
                            onClick = { showSleepTimer = true }
                        )
                    }
                }
            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudiobookChapterListSheet(
    chapters: List<AudiobookChapter>,
    currentPositionSeconds: Int,
    colorScheme: ColorScheme,
    onSeekTo: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val currentChapterIndex = chapters.indexOfLast { chapter ->
        chapter.startSeconds <= currentPositionSeconds
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colorScheme.surface,
        shape = NordicShapes.xl,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = NordicSpacing.lg)
                .padding(bottom = NordicSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(NordicSpacing.xs)) {
                    Text(
                        "章节",
                        style = MaterialTheme.typography.titleMedium,
                        color = colorScheme.onSurface
                    )
                    Text(
                        "共 ${chapters.size} 章 · 点击跳转",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurface.copy(alpha = NordicAlpha.subtle)
                    )
                }
                AudiobookControlButton(
                    icon = Icons.Filled.Close,
                    contentDescription = "关闭章节列表",
                    colorScheme = colorScheme,
                    compact = false,
                    enabled = true,
                    onClick = onDismiss
                )
            }

            LazyColumn(
                modifier = Modifier.heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(NordicSpacing.xs)
            ) {
                itemsIndexed(
                    items = chapters,
                    key = { index, chapter -> "${chapter.startSeconds}:$index" },
                    contentType = { _, _ -> "audiobook-chapter-row" }
                ) { index, chapter ->
                    val isCurrent = index == currentChapterIndex
                    Surface(
                        color = if (isCurrent) {
                            colorScheme.primary.copy(alpha = 0.14f)
                        } else {
                            colorScheme.surfaceVariant.copy(alpha = 0.42f)
                        },
                        shape = NordicShapes.md,
                        border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.045f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSeekTo(chapter.startSeconds) }
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = NordicSpacing.md,
                                vertical = NordicSpacing.sm
                            ),
                            horizontalArrangement = Arrangement.spacedBy(NordicSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (isCurrent) {
                                    colorScheme.primary
                                } else {
                                    colorScheme.onSurface.copy(alpha = NordicAlpha.subtle)
                                },
                                maxLines = 1
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(NordicSpacing.xs)
                            ) {
                                Text(
                                    text = chapter.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (isCurrent) colorScheme.primary else colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = formatDuration(chapter.startSeconds),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurface.copy(alpha = NordicAlpha.subtle),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Playback-speed panel (audiobookshelf-style): a selectable list of rates
 * replacing the single tap-to-cycle chip interaction.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudiobookPlaybackSpeedSheet(
    currentSpeed: Float,
    colorScheme: ColorScheme,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colorScheme.surface,
        shape = NordicShapes.xl,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = NordicSpacing.lg)
                .padding(bottom = NordicSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)
        ) {
            Text(
                "播放速度",
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.onSurface,
                modifier = Modifier.padding(bottom = NordicSpacing.xs)
            )
            AUDIOBOOK_PLAYBACK_SPEED_OPTIONS.forEach { speed ->
                val selected = kotlin.math.abs(speed - currentSpeed) < 0.001f
                Surface(
                    color = if (selected) {
                        colorScheme.primary.copy(alpha = 0.16f)
                    } else {
                        colorScheme.surfaceVariant.copy(alpha = 0.42f)
                    },
                    contentColor = colorScheme.onSurface,
                    shape = NordicShapes.md,
                    border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.045f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(speed) }
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = NordicSpacing.md, vertical = NordicSpacing.md)
                    ) {
                        Text(
                            text = formatPlaybackSpeed(speed),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (selected) colorScheme.primary else colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudiobookSleepTimerSheet(
    sleepTimerRemainingSeconds: Int?,
    sleepTimerAtChapterEnd: Boolean,
    colorScheme: ColorScheme,
    onSet: (Int, Boolean) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        "关闭" to (null to null),
        "10 分钟" to (10 to false),
        "20 分钟" to (20 to false),
        "30 分钟" to (30 to false),
        "45 分钟" to (45 to false),
        "60 分钟" to (60 to false),
        "本章结束" to (null to true)
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colorScheme.surface,
        shape = NordicShapes.xl,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = NordicSpacing.lg)
                .padding(bottom = NordicSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(NordicSpacing.xs)) {
                    Text(
                        "睡眠定时器",
                        style = MaterialTheme.typography.titleMedium,
                        color = colorScheme.onSurface
                    )
                    Text(
                        sleepTimerRemainingLabel(
                            sleepTimerRemainingSeconds = sleepTimerRemainingSeconds,
                            atChapterEnd = sleepTimerAtChapterEnd
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurface.copy(alpha = NordicAlpha.subtle)
                    )
                }
                AudiobookControlButton(
                    icon = Icons.Filled.Close,
                    contentDescription = "关闭",
                    colorScheme = colorScheme,
                    compact = false,
                    enabled = true,
                    onClick = onDismiss
                )
            }

            options.forEach { (label, value) ->
                val (minutes, atChapterEnd) = value
                val selected = if (minutes == null && atChapterEnd == null) {
                    sleepTimerRemainingSeconds == null && !sleepTimerAtChapterEnd
                } else if (atChapterEnd == true) {
                    sleepTimerAtChapterEnd
                } else {
                    // Highlight the minute option whose countdown is still running
                    // (remaining rounded up to whole minutes matches the option).
                    val minutesOfRemaining = minutes?.let { m ->
                        val remaining = sleepTimerRemainingSeconds
                        if (!sleepTimerAtChapterEnd && remaining != null && remaining > 0) {
                            kotlin.math.ceil(remaining / 60.0).toInt() == m
                        } else {
                            false
                        }
                    } ?: false
                    minutesOfRemaining
                }
                Surface(
                    color = if (selected) {
                        colorScheme.primary.copy(alpha = 0.16f)
                    } else {
                        colorScheme.surfaceVariant.copy(alpha = 0.42f)
                    },
                    contentColor = colorScheme.onSurface,
                    shape = NordicShapes.md,
                    border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.045f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (minutes == null && atChapterEnd == null) {
                                onCancel()
                            } else {
                                onSet(minutes ?: 0, atChapterEnd == true)
                            }
                        }
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = NordicSpacing.md, vertical = NordicSpacing.md)
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (selected) colorScheme.primary else colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

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

@OptIn(ExperimentalMaterial3Api::class)
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colorScheme.surface,
        shape = NordicShapes.xl,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = NordicSpacing.lg)
                .padding(bottom = NordicSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(NordicSpacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(NordicSpacing.xs)) {
                    Text(
                        "书签",
                        style = MaterialTheme.typography.titleMedium,
                        color = colorScheme.onSurface
                    )
                    Text(
                        "${bookmarks.size} 个书签",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurface.copy(alpha = NordicAlpha.subtle)
                    )
                }
                AudiobookControlButton(
                    icon = Icons.Filled.Close,
                    contentDescription = "关闭书签",
                    colorScheme = colorScheme,
                    compact = false,
                    enabled = true,
                    onClick = onDismiss
                )
            }

            Surface(
                color = colorScheme.primary.copy(alpha = 0.16f),
                contentColor = colorScheme.primary,
                shape = NordicShapes.full,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onAddBookmark)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = NordicSpacing.lg, vertical = NordicSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(NordicSpacing.sm, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bookmark,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "在当前进度添加书签",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
            ) {
                if (bookmarks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = NordicSpacing.xxl),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.BookmarkBorder,
                                contentDescription = null,
                                tint = colorScheme.primary.copy(alpha = NordicAlpha.medium),
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                "暂无书签，点击播放器中的书签按钮在当前进度添加",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.onSurface.copy(alpha = NordicAlpha.subtle),
                                textAlign = TextAlign.Center,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)
                    ) {
                        itemsIndexed(
                            items = bookmarks,
                            key = { index, bookmark -> "${bookmark.id}:$index" },
                            contentType = { _, _ -> "audiobook-bookmark-row" }
                        ) { _, bookmark ->
                            val isAtBookmark =
                                kotlin.math.abs(currentPositionSeconds - bookmark.positionSeconds) <= 2
                            AudiobookBookmarkRow(
                                bookmark = bookmark,
                                colorScheme = colorScheme,
                                isCurrent = isAtBookmark,
                                onClick = { onJumpTo(bookmark.positionSeconds) },
                                onDelete = { onDelete(bookmark.id) }
                            )
                        }
                    }
                }
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
    Surface(
        color = if (isCurrent) {
            colorScheme.primary.copy(alpha = 0.14f)
        } else {
            colorScheme.surfaceVariant.copy(alpha = 0.42f)
        },
        shape = NordicShapes.md,
        border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.045f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = NordicSpacing.md, vertical = NordicSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Bookmark,
                contentDescription = null,
                tint = if (isCurrent) colorScheme.primary else colorScheme.onSurface.copy(alpha = NordicAlpha.subtle),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.size(NordicSpacing.md))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(NordicSpacing.xs)
            ) {
                Text(
                    bookmark.label.takeIf { it.isNotBlank() } ?: "书签",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    formatDuration(bookmark.positionSeconds),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Normal,
                    color = colorScheme.onSurface.copy(alpha = NordicAlpha.subtle),
                    maxLines = 1
                )
            }
            Spacer(Modifier.size(NordicSpacing.sm))
            Surface(
                color = colorScheme.error.copy(alpha = 0.14f),
                contentColor = colorScheme.error,
                shape = NordicShapes.full,
                modifier = Modifier
                    .size(34.dp)
                    .clickable(onClick = onDelete)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "删除书签",
                        tint = colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AudiobookPrimaryDisplay(
    title: String,
    coverUrl: String?,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val side = minOf(maxWidth, maxHeight)

        Surface(
            color = colorScheme.surfaceVariant.copy(alpha = 0.48f),
            shape = NordicShapes.xl,
            shadowElevation = 10.dp,
            border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.06f)),
            modifier = Modifier.size(side)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
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
                if (coverUrl != null) {
                    AuthedAsyncImage(
                        url = coverUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.44f)
                            .aspectRatio(1f)
                            .clip(NordicShapes.xl)
                            .background(colorScheme.surface.copy(alpha = 0.62f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            tint = colorScheme.primary.copy(alpha = NordicAlpha.medium),
                            modifier = Modifier.size(42.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AudiobookPlayerTopBar(
    colorScheme: ColorScheme,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = colorScheme.surfaceVariant.copy(alpha = 0.58f),
            contentColor = colorScheme.onSurface,
            shape = NordicShapes.md,
            border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.05f)),
            modifier = Modifier.size(42.dp).clickable(onClick = onClose)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = "关闭播放器",
                    tint = colorScheme.onSurface.copy(alpha = NordicAlpha.medium),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Text(
            "有声书播放",
            style = MaterialTheme.typography.titleSmall,
            color = colorScheme.onSurface
        )
        Spacer(Modifier.size(42.dp))
    }
}

@Composable
private fun AudiobookControlButton(
    icon: ImageVector,
    contentDescription: String,
    colorScheme: ColorScheme,
    compact: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val foreground = if (enabled) {
        colorScheme.primary
    } else {
        colorScheme.onSurface.copy(alpha = NordicAlpha.faint)
    }

    Surface(
        color = if (enabled) colorScheme.primary.copy(alpha = 0.16f) else colorScheme.surface.copy(alpha = 0.30f),
        contentColor = foreground,
        shape = NordicShapes.full,
        modifier = Modifier
            .size(if (compact) 42.dp else 46.dp)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = foreground,
                modifier = Modifier.size(if (compact) 22.dp else 24.dp)
            )
        }
    }
}

@Composable
private fun AudiobookPlayButton(
    icon: ImageVector,
    contentDescription: String,
    colorScheme: ColorScheme,
    compact: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (enabled) colorScheme.primary else colorScheme.primary.copy(alpha = 0.32f),
        contentColor = colorScheme.onPrimary,
        shape = NordicShapes.full,
        shadowElevation = if (enabled) 4.dp else 0.dp,
        modifier = Modifier
            .size(if (compact) 58.dp else 62.dp)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = colorScheme.onPrimary,
                modifier = Modifier.size(if (compact) 30.dp else 32.dp)
            )
        }
    }
}

private fun formatPlaybackSpeed(speed: Float): String {
    val safeSpeed = speed.takeIf { it.isFinite() && it > 0f } ?: 1f
    val rounded = kotlin.math.round(safeSpeed * 100f) / 100f
    return when (rounded) {
        1f -> "1x"
        1.5f -> "1.5x"
        2f -> "2x"
        else -> "${rounded}x"
    }
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
