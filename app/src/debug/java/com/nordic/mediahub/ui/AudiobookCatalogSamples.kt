package com.nordic.mediahub.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.nordic.mediahub.R
import com.nordic.mediahub.data.AppPreferences
import com.nordic.mediahub.data.AudiobookBookmark
import com.nordic.mediahub.data.AudiobookChapter
import com.nordic.mediahub.data.AudiobookItemDetail
import com.nordic.mediahub.data.AudiobookItemSummary
import com.nordic.mediahub.data.AudiobookLibrarySummary
import com.nordic.mediahub.data.AudiobookProgress
import com.nordic.mediahub.data.AudiobookAudioTrack
import com.nordic.mediahub.data.AudiobookPlaybackSession
import com.nordic.mediahub.playback.AudiobookPlaybackState
import com.nordic.mediahub.ui.theme.NordicSpacing

/** Deterministic in-memory host. Screens, sheets and dialogs are the production composables. */
@Composable
internal fun AudiobookCatalogSample(
    request: UiSampleRequest,
    onNavigate: (UiSampleScreen) -> Unit,
    onEvent: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val covers = listOf(R.drawable.ui_sample_cover_a, R.drawable.ui_sample_cover_b, R.drawable.ui_sample_cover_c)
        .map { "android.resource://${context.packageName}/$it" }
    val long = request.state == UiSampleState.LongText
    val empty = request.state == UiSampleState.Empty
    val libraryEmpty = request.state == UiSampleState.LibraryEmpty
    val loading = request.state == UiSampleState.Loading
    val refreshing = request.state == UiSampleState.Refreshing
    val cachedError = request.state == UiSampleState.CachedError
    val error = request.state == UiSampleState.Error
    val hasCachedContent = refreshing || cachedError
    val ready = !empty
    var screen by remember {
        mutableStateOf(
            when (request.screen) {
                UiSampleScreen.AudiobookDetail -> UiSampleScreen.AudiobookHome
                UiSampleScreen.AudiobookChapters -> UiSampleScreen.AudiobookPlayer
                UiSampleScreen.AudiobookSpeed -> UiSampleScreen.AudiobookPlayer
                UiSampleScreen.AudiobookSleep -> UiSampleScreen.AudiobookPlayer
                UiSampleScreen.AudiobookBookmarks -> UiSampleScreen.AudiobookPlayer
                else -> request.screen
            }
        )
    }
    var detailPage by remember { mutableStateOf(request.screen == UiSampleScreen.AudiobookDetail) }
    val libraries = remember {
        listOf(AudiobookLibrarySummary("ab-library-1", "中文有声书", "book"), AudiobookLibrarySummary("ab-library-2", "English Audiobooks", "book"))
    }
    val shownLibraries = if (libraryEmpty) emptyList() else libraries
    val progress = AudiobookProgress(currentTimeSeconds = 4211, durationSeconds = 36210, progressFraction = 0.116f, isFinished = false, lastUpdateMillis = 0L)
    val books = remember(request.state) {
        listOf("三体·广播剧", "置身事内", "夜色深处", "漫长的季节", " call me by the voice", "留白的艺术", "夜航西飞", "与自己和解").mapIndexed { index, title ->
            AudiobookItemSummary(
                id = "ab-sample-$index", libraryId = "ab-library-1",
                title = if (long && index == 0) "在漫长的旅途中听见远方的回声 · 全本完整演播版（附作者访谈与后记）" else title,
                author = if (index == 5) "" else "刘慈欣",
                narrator = if (index == 3) "" else "演播人",
                series = "", coverUrl = if (request.state == UiSampleState.NoArtwork) null else covers[index % covers.size], durationSeconds = 36210 + index * 900,
                chapterCount = 12 + index, updatedAtMillis = 0L,
                progress = if (index == 0) progress else null
            )
        }
    }
    val shownBooks = when {
        empty || libraryEmpty -> emptyList()
        loading && !hasCachedContent -> emptyList()
        error && !hasCachedContent -> emptyList()
        else -> books
    }
    val chapters = remember(long) {
        val names = listOf("序章：湖面", "第一章 回声", "第二章 长夜", "第三章 远山", "第四章 空白",
            "第五章 沿途", "第六章 归途", "尾声")
        names.mapIndexed { index, name ->
            val start = index * 1800
            AudiobookChapter(id = index, title = if (long && index == 1) "第一章 一个拥有很长名字的章节标题用来检查两行省略" else name, startSeconds = start, endSeconds = start + 1800)
        }
    }
    val detail = remember(books, chapters, request.state) {
        if (shownBooks.isEmpty()) null else AudiobookItemDetail(
            id = "ab-sample-0", libraryId = "ab-library-1",
            title = if (long) "在漫长的旅途中听见远方的回声 · 全本完整演播版（附作者访谈与后记）" else books.first().title,
            subtitle = if (long) "一档关于声音、记忆与远方的演播合集" else "",
            description = if (long) "这份合集收集了漫长旅途里的每一道风景。从清晨的湖面到深夜的车站，从熟悉的城市到陌生的旷野，声音记录了所有在路上的时刻。\n演播者在录制时尽量保留了原文的节奏与停顿，希望听到的你也能在路上，听见属于自己的回声。\n本简介同时用于检查多行长文本在详情页的展开与收起行为。" else "关于声音、记忆与远方的演播合集。",
            authors = listOf("刘慈欣"), narrators = listOf("演播人"), series = emptyList(),
            coverUrl = books.first().coverUrl, durationSeconds = 36210, chapters = chapters,
            progress = progress
        )
    }
    val session = if (shownBooks.isEmpty() && !hasCachedContent) null else AudiobookPlaybackSession(
        sessionId = "ab-session-sample", libraryItemId = "ab-sample-0",
        displayTitle = if (long) "在漫长的旅途中听见远方的回声 · 全本完整演播版（附作者访谈与后记）" else "三体·广播剧",
        displayAuthor = "刘慈欣 · 演播人", coverUrl = books.firstOrNull()?.coverUrl,
        durationSeconds = 36210, currentTimeSeconds = 4211, startTimeSeconds = 4211,
        chapters = chapters, audioTracks = listOf(AudiobookAudioTrack(0, "part1", "preview://audio/0", 0, 36210))
    )
    val playerState = AudiobookPlaybackState(
        session = session, isPlaying = false,
        isBuffering = loading,
        positionSeconds = 4211, bufferedPositionSeconds = 5810,
        durationSeconds = 36210, playbackSpeed = 1f, chapters = chapters,
        sleepTimerRemainingSeconds = when {
            request.state == UiSampleState.Disabled -> 1234
            screen == UiSampleScreen.AudiobookSleep && request.state == UiSampleState.Refreshing -> 754
            else -> null
        },
        sleepTimerAtChapterEnd = false,
        errorMessage = if (error) "播放暂时不可用，请稍后重试" else null
    )
    val bookmarks = remember(long) {
        when {
            request.state == UiSampleState.Empty && screen == UiSampleScreen.AudiobookBookmarks -> emptyList()
            else -> listOf(
                AudiobookBookmark("bm-1", "ab-sample-0", 4211, "听到这里，第二天继续", 0L),
                AudiobookBookmark("bm-2", "ab-sample-0", 1800, if (long) "这一章的开头有一段很长的书签备注，用来检查两行省略与删除按钮的可达性" else "第一章末尾", 0L),
                AudiobookBookmark("bm-3", "ab-sample-0", 900, "", 0L)
            )
        }
    }
    val running = loading || refreshing
    // Keep LocalAppPreferences explicit so skip-interval labels are deterministic in samples.
    CompositionLocalProvider(LocalAppPreferences provides AppPreferences()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            MediaPageHeader(
                title = if (detailPage) detail?.title ?: "详情" else "有声书",
                subtitle = when {
                    detailPage -> audiobookAuthorLabel(detail?.authors?.joinToString(" / "))
                    error && hasCachedContent -> "刷新失败，先显示本地缓存"
                    refreshing -> "正在刷新，先显示本地缓存"
                    empty -> ""
                    else -> "共 ${shownBooks.size} 本"
                },
                actions = buildList {
                    add(HeaderAction(Icons.Filled.Settings, "打开设置", fixed = true) { onNavigate(UiSampleScreen.Modules) })
                    if (ready) add(HeaderAction(Icons.Filled.Refresh, "刷新有声书", enabled = !running) { onEvent("ab-refresh") })
                },
                colorScheme = colors, showBack = detailPage,
                onBack = { detailPage = false; onEvent("ab-back-home") },
                modifier = Modifier.padding(horizontal = NordicSpacing.content)
            )
            Box(Modifier.weight(1f)) {
                if (detailPage) {
                    if (detail != null) {
                        AudiobookDetailHeader(item = detail, colorScheme = colors, onPlay = { onEvent("ab-play-detail") })
                        AudiobookChapterList(detail.chapters, colors)
                    }
                } else {
                    AudiobookHomeContent(
                        libraries = shownLibraries, selectedLibraryId = shownLibraries.firstOrNull()?.id,
                        loading = loading, standaloneError = if (error && !hasCachedContent) "连接失败: 样例网络错误" else null,
                        books = shownBooks, ready = ready, colors = colors,
                        onSelectLibrary = { onEvent("ab-library:${it.id}") },
                        onOpen = { detailPage = true; onEvent("ab-open:${it.id}") },
                        onPlay = { onEvent("ab-play:${it.id}") }
                    )
                }
            }
        }
    }
    BackHandler(enabled = screen != request.screen || detailPage) {
        if (detailPage) detailPage = false else screen = request.screen
        onEvent("ab-back")
    }
    if (screen == UiSampleScreen.AudiobookPlayer || screen == UiSampleScreen.AudiobookChapters ||
        screen == UiSampleScreen.AudiobookSpeed || screen == UiSampleScreen.AudiobookSleep ||
        screen == UiSampleScreen.AudiobookBookmarks
    ) {
        AudiobookPlayerScreen(
            state = playerState,
            colorScheme = colors,
            externalError = null,
            bookmarks = bookmarks,
            onAddBookmark = { onEvent("ab-bookmark-add") },
            onDeleteBookmark = { id -> onEvent("ab-bookmark-delete:$id") },
            onSetSleepTimer = { minutes, atChapterEnd -> onEvent("ab-sleep:$minutes:$atChapterEnd") },
            onCancelSleepTimer = { onEvent("ab-sleep-cancel") },
            onSeek = { value -> onEvent("ab-seek:$value") },
            onSeekBack = { onEvent("ab-seek-back") },
            onSeekForward = { onEvent("ab-seek-forward") },
            onSeekToPreviousChapter = { onEvent("ab-chapter-prev") },
            onSeekToNextChapter = { onEvent("ab-chapter-next") },
            onSetPlaybackSpeed = { speed -> onEvent("ab-speed:$speed") },
            onPlayPause = { onEvent("ab-play-pause") },
            onClose = { onNavigate(UiSampleScreen.Catalog) },
            onCloseAnyway = { onNavigate(UiSampleScreen.Catalog) }
        )
        if (screen == UiSampleScreen.AudiobookChapters) {
            AudiobookChapterListSheet(
                chapters = chapters.sortedBy { it.startSeconds }, currentPositionSeconds = 4211,
                colorScheme = colors, onSeekTo = { onEvent("ab-chapter-seek:$it") }, onDismiss = { onEvent("ab-chapter-dismiss") }
            )
        }
        if (screen == UiSampleScreen.AudiobookSpeed) {
            AudiobookPlaybackSpeedSheet(
                currentSpeed = playerState.playbackSpeed, colorScheme = colors,
                onSelect = { onEvent("ab-speed-select:$it") }, onDismiss = { onEvent("ab-speed-dismiss") }
            )
        }
        if (screen == UiSampleScreen.AudiobookSleep) {
            AudiobookSleepTimerSheet(
                sleepTimerRemainingSeconds = playerState.sleepTimerRemainingSeconds,
                sleepTimerAtChapterEnd = playerState.sleepTimerAtChapterEnd, colorScheme = colors,
                onSet = { minutes, atChapterEnd -> onEvent("ab-sleep-set:$minutes:$atChapterEnd") },
                onCancel = { onEvent("ab-sleep-sheet-cancel") }, onDismiss = { onEvent("ab-sleep-dismiss") }
            )
        }
        if (screen == UiSampleScreen.AudiobookBookmarks) {
            AudiobookBookmarkSheet(
                bookmarks = bookmarks, colorScheme = colors, currentPositionSeconds = 4211,
                onAddBookmark = { onEvent("ab-bookmark-add") },
                onJumpTo = { onEvent("ab-bookmark-jump:$it") },
                onDelete = { onEvent("ab-bookmark-delete:$it") },
                onDismiss = { onEvent("ab-bookmark-dismiss") }
            )
        }
    }
}

@Composable
private fun AudiobookHomeContent(
    libraries: List<AudiobookLibrarySummary>,
    selectedLibraryId: String?,
    loading: Boolean,
    standaloneError: String?,
    books: List<AudiobookItemSummary>,
    ready: Boolean,
    colors: ColorScheme,
    onSelectLibrary: (AudiobookLibrarySummary) -> Unit,
    onOpen: (AudiobookItemSummary) -> Unit,
    onPlay: (AudiobookItemSummary) -> Unit
) {
    // Mirrors the production AudiobookScreen body (LibrarySelector + state cards + summary list).
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(NordicSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)
    ) {
        if (libraries.isNotEmpty()) {
            item { AudiobookLibrarySelector(libraries, selectedLibraryId, colors, onSelect = { libraryId ->
                // Library selector rows report the library id; resolve back to the summary for events.
                onSelectLibrary(libraries.first { it.id == libraryId })
            }) }
        }
        when {
            standaloneError != null -> item {
                MediaStateCard("AudiobookShelf 错误", standaloneError, hint = "检查配置或点击刷新重试", tone = MediaStateTone.Error)
            }
            loading && books.isEmpty() -> item { MediaLoadingCard("正在同步 AudiobookShelf", "加载书库、封面和续播进度...") }
            !ready -> item {
                MediaStateCard("先接入你的有声书书库", "填入 AudiobookShelf 地址、用户名和密码后，这里会显示真实书目、章节和续播进度。", hint = "前往配置 tab 开始连接")
            }
            libraries.isEmpty() -> item {
                MediaStateCard("没有可用书库", "已连接 AudiobookShelf，但当前账号下没有可访问的 audiobook library。", hint = "检查服务器权限或书库类型")
            }
            books.isEmpty() -> item {
                MediaStateCard("这个书库还没有内容", "已连接 AudiobookShelf，但当前书库里没有可展示的有声书条目。", hint = "切换其他书库或回到服务端检查扫描结果")
            }
            else -> items(books, key = { it.id }, contentType = { "audiobook-summary-card" }) { item ->
                AudiobookSummaryCard(item, colors, onOpen = { onOpen(item) }, onPlay = { onPlay(item) })
            }
        }
    }
}

@Composable
private fun AudiobookChapterList(chapters: List<AudiobookChapter>, colors: ColorScheme) {
    val currentChapter = resolveCurrentAudiobookChapter(
        sortAudiobookDetailChapters(chapters),
        4211
    )
    LazyColumn(Modifier.fillMaxSize().padding(top = NordicSpacing.lg)) {
        item { Text("章节", style = MaterialTheme.typography.titleMedium, color = colors.onSurface, modifier = Modifier.semantics { heading() }) }
        items(chapters, key = { it.id }, contentType = { "audiobook-chapter-row" }) { chapter ->
            AudiobookChapterRow(chapter, colors, isCurrent = chapter.id == currentChapter?.id)
        }
    }
}
