package com.nordic.mediahub.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nordic.mediahub.data.MusicLyrics
import com.nordic.mediahub.data.MusicLyricsLine
import com.nordic.mediahub.data.displayCues
import com.nordic.mediahub.data.resolveActiveMusicLyricIndex
import com.nordic.mediahub.playback.MusicLyricsUiState
import com.nordic.mediahub.ui.theme.NordicAlpha
import com.nordic.mediahub.ui.theme.NordicControlSizes
import com.nordic.mediahub.ui.theme.NordicMotion
import com.nordic.mediahub.ui.theme.NordicShapes
import com.nordic.mediahub.ui.theme.NordicSpacing
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.roundToInt

// Dedicated display-surface sizes; keep the existing typography exception explicit.
private val LYRIC_LINE_FONT_SIZE = 18.sp
private val LYRIC_LINE_FONT_SIZE_COMPACT = 16.sp
private val LYRIC_LINE_LINE_HEIGHT = 24.sp
private val LYRIC_LINE_LINE_HEIGHT_COMPACT = 20.sp

internal fun lyricsStateForSong(songId: String?, state: MusicLyricsUiState): MusicLyricsUiState = when {
    songId == null -> MusicLyricsUiState.Idle
    state.songId != songId -> MusicLyricsUiState.Loading(songId)
    else -> state
}

internal fun resolveLyricsModeLabel(lyrics: MusicLyrics?): String? {
    val cues = lyrics?.displayCues().orEmpty()
    if (cues.isEmpty()) return null
    return if (lyrics?.synced == true && cues.any { it.startMillis != null }) "同步歌词" else "普通歌词"
}

@Composable
internal fun MusicLyricsDisplay(
    state: MusicLyricsUiState,
    isPlaying: Boolean,
    positionMillisFlow: StateFlow<Long>,
    seekRevision: Long,
    colorScheme: ColorScheme,
    compact: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.clip(NordicShapes.xl)
            .background(Brush.linearGradient(listOf(
                colorScheme.surfaceVariant.copy(alpha = 0.50f),
                colorScheme.primary.copy(alpha = 0.10f),
                colorScheme.secondary.copy(alpha = 0.06f),
                colorScheme.surfaceVariant.copy(alpha = 0.42f)
            )))
            .padding(horizontal = NordicSpacing.xl, vertical = if (compact) NordicSpacing.lg else NordicSpacing.xl),
        contentAlignment = Alignment.Center
    ) {
        when (state) {
            is MusicLyricsUiState.Content -> key(state.requestId) {
                val cues = remember(state.lyrics) { state.lyrics.displayCues() }
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        resolveLyricsModeLabel(state.lyrics).orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.primary.copy(alpha = NordicAlpha.medium)
                    )
                    Spacer(Modifier.height(NordicSpacing.sm))
                    if (state.lyrics.synced && cues.any { it.startMillis != null }) {
                        SyncedLyricsList(cues, isPlaying, positionMillisFlow, seekRevision, colorScheme, compact, Modifier.weight(1f))
                    } else {
                        LyricList(cues, null, rememberLazyListState(), colorScheme, compact, Modifier.weight(1f))
                    }
                }
            }
            else -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)
            ) {
                Text(
                    when (state) {
                        is MusicLyricsUiState.Loading -> "正在加载歌词"
                        is MusicLyricsUiState.Error -> state.message
                        else -> "暂无歌词"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = colorScheme.onSurface.copy(alpha = NordicAlpha.subtle),
                    textAlign = TextAlign.Center
                )
                if (state is MusicLyricsUiState.Error && state.canRetry) {
                    TextButton(onClick = onRetry, colors = ButtonDefaults.textButtonColors(contentColor = colorScheme.primary)) {
                        Text("重试")
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncedLyricsList(
    cues: List<MusicLyricsLine>,
    isPlaying: Boolean,
    positionMillisFlow: StateFlow<Long>,
    seekRevision: Long,
    colorScheme: ColorScheme,
    compact: Boolean,
    modifier: Modifier
) {
    val positionMillis by positionMillisFlow.collectAsStateWithLifecycle()
    val activeIndex by remember(cues) { derivedStateOf { resolveActiveMusicLyricIndex(cues, positionMillis) } }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val controller = remember { MusicLyricsFollowController(scope, isPlaying) }
    val follow by controller.state.collectAsStateWithLifecycle()
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    var autoScrolling by remember { mutableStateOf(false) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var observedSeek by remember { mutableLongStateOf(seekRevision) }
    var alignedSeek by remember { mutableLongStateOf(seekRevision) }
    var alignedIndex by remember { mutableStateOf<Int?>(null) }
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val actionStyle = MaterialTheme.typography.labelLarge
    val textMeasurer = rememberTextMeasurer()
    val actionPadding = ButtonDefaults.TextButtonContentPadding
    val horizontalActionPadding = with(density) {
        (actionPadding.calculateLeftPadding(direction) + actionPadding.calculateRightPadding(direction)).roundToPx()
    }
    val actionTextHeight = remember(textMeasurer, actionStyle, viewport.width, density, direction) {
        val width = if (viewport.width > 0) (viewport.width - horizontalActionPadding).coerceAtLeast(1) else Constraints.Infinity
        textMeasurer.measure("回到当前", style = actionStyle, constraints = Constraints(maxWidth = width)).size.height
    }
    val actionHeight = maxOf(
        NordicControlSizes.touchTarget,
        with(density) { actionTextHeight.toDp() } + actionPadding.calculateTopPadding() + actionPadding.calculateBottomPadding()
    )
    val paddingPixels = (viewport.height / 2f).roundToInt()
    val padding = with(density) { paddingPixels.toDp() }
    val target = activeIndex ?: 0
    val targetHeight by remember(target, listState) {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == target }?.size ?: 0 }
    }

    DisposableEffect(controller) { onDispose { controller.close() } }
    LaunchedEffect(isPlaying) { controller.setPlaying(isPlaying) }
    LaunchedEffect(seekRevision) {
        if (observedSeek != seekRevision) {
            observedSeek = seekRevision
            controller.returnToCurrent()
        }
    }
    LaunchedEffect(listState, controller) {
        snapshotFlow { Triple(dragged, listState.isScrollInProgress, autoScrolling) }
            .collect { (dragging, scrolling, automatic) ->
                when (lyricScrollActivity(dragging, scrolling, automatic)) {
                    LyricScrollActivity.User -> controller.onUserScrollStarted()
                    LyricScrollActivity.Idle -> controller.onUserScrollStopped()
                    LyricScrollActivity.Automatic -> Unit
                }
            }
    }
    LaunchedEffect(follow, target, targetHeight, viewport, seekRevision, density.fontScale) {
        if (!follow.following || cues.isEmpty() || viewport.height <= 0) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo }.first {
            it.totalItemsCount == cues.size && it.beforeContentPadding == paddingPixels && it.viewportSize.height > 0
        }
        autoScrolling = true
        try {
            val animate = alignedIndex != null && alignedSeek == seekRevision && target >= requireNotNull(alignedIndex)
            alignLyricItem(listState, target, animate)
            alignedIndex = target
            alignedSeek = seekRevision
        } finally {
            autoScrolling = false
        }
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        LyricList(
            cues, activeIndex, listState, colorScheme, compact,
            modifier = Modifier.weight(1f),
            listModifier = Modifier.onSizeChanged { viewport = it },
            padding = PaddingValues(vertical = padding)
        )
        // Reserve the slot so entering manual mode never changes the measured viewport.
        Box(Modifier.fillMaxWidth().height(actionHeight), contentAlignment = Alignment.Center) {
            if (!follow.following) {
                TextButton(
                    onClick = controller::returnToCurrent,
                    colors = ButtonDefaults.textButtonColors(contentColor = colorScheme.primary)
                ) { Text("回到当前", style = actionStyle) }
            }
        }
    }
}

private suspend fun alignLyricItem(state: LazyListState, index: Int, animate: Boolean) {
    val alreadyVisible = state.layoutInfo.visibleItemsInfo.any { it.index == index }
    if (!alreadyVisible) state.scrollToItem(index)
    val item = snapshotFlow { state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } }.first { it != null }
        ?: return
    val layout = state.layoutInfo
    val distance = lyricAlignmentDelta(item.offset, item.size, layout.viewportStartOffset, layout.viewportEndOffset)
    if (abs(distance) < 1f) return
    if (animate && alreadyVisible) {
        state.animateScrollBy(distance, tween(NordicMotion.durationShort, easing = NordicMotion.easingStandard))
    } else {
        state.scrollBy(distance)
    }
}

@Composable
private fun LyricList(
    cues: List<MusicLyricsLine>,
    activeIndex: Int?,
    state: LazyListState,
    colorScheme: ColorScheme,
    compact: Boolean,
    modifier: Modifier,
    listModifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(0.dp)
) {
    Box(modifier) {
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize().then(listModifier),
            contentPadding = padding,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) NordicSpacing.sm else NordicSpacing.md)
        ) {
            itemsIndexed(cues, key = { index, _ -> index }, contentType = { _, _ -> "lyric-cue" }) { index, line ->
                val active = index == activeIndex
                val color by animateColorAsState(
                    if (active) colorScheme.onSurface else colorScheme.onSurface.copy(alpha = NordicAlpha.subtle),
                    tween(NordicMotion.durationShort, easing = NordicMotion.easingStandard), label = "lyric-color"
                )
                Text(
                    text = line.text,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = NordicSpacing.sm).semantics { selected = active },
                    fontSize = if (compact) LYRIC_LINE_FONT_SIZE_COMPACT else LYRIC_LINE_FONT_SIZE,
                    lineHeight = if (compact) LYRIC_LINE_LINE_HEIGHT_COMPACT else LYRIC_LINE_LINE_HEIGHT,
                    color = color,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }
        MusicScrollbar(state, Modifier.align(Alignment.CenterEnd).padding(end = NordicSpacing.xs))
    }
}
