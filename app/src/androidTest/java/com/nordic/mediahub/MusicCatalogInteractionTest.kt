package com.nordic.mediahub

import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inspector.WindowInspector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import android.accessibilityservice.AccessibilityService
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Deterministic content and real gestures; no server, download, audio effect or preference writes. */
@RunWith(AndroidJUnit4::class)
class MusicCatalogInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<UiCatalogActivity>()
    @After fun restoreSystemFont() { UiTestDevice.setFontScale(1f) }

    private fun show(screen: String, state: String = "normal", font: Float = 2f, dark: Boolean = false) {
        UiTestDevice.setFontScale(font)
        // An in-memory page may have navigated without changing the original request key.
        compose.runOnUiThread { compose.activity.showSample("catalog") }
        compose.waitForIdle()
        compose.runOnUiThread { compose.activity.showSample(screen, state, dark, font) }
        compose.waitForIdle()
        UiTestDevice.waitForStableWindow()
    }

    private fun event(value: String) = compose.runOnIdle {
        assertTrue(compose.activity.recordedEvents.toString(), value in compose.activity.recordedEvents)
    }
    private fun noMutation() = compose.runOnIdle {
        assertFalse(compose.activity.recordedEvents.any { it.startsWith("playlist-submit:") || it == "playlist-delete" })
    }
    private fun clickLabel(label: String) = SemanticsMatcher("click label $label") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == label
    }
    private fun scrollTo(matcher: SemanticsMatcher) {
        compose.onNode(hasVerticalLazyScrollAction()).performScrollToNode(matcher)
    }
    private fun tap(node: SemanticsNodeInteraction, scroll: Boolean = false) {
        if (scroll) node.performScrollTo()
        node.assertIsDisplayed().assertIsFullyVisible().assertMinimumTouchTarget()
            .performTouchInput { click() }
        compose.waitForIdle()
    }
    private fun tapRow(title: String, label: String) {
        val matcher = hasText(title, substring = false) and clickLabel(label)
        scrollTo(matcher)
        tap(compose.onNode(matcher), scroll = true)
    }
    private fun tapPageAction(label: String) {
        val matcher = hasText(label, substring = false) and hasClickAction()
        scrollTo(matcher)
        tap(compose.onNode(matcher), scroll = true)
    }

    private fun revealShelfTitle(title: SemanticsNodeInteraction) {
        // performScrollTo handles the nearest LazyRow. Its enclosing vertical list must
        // also reveal the title when a shelf is taller than the short-screen viewport.
        title.performScrollTo()
        val page = compose.onNode(hasVerticalLazyScrollAction())
        repeat(4) {
            val textNode = title.fetchSemanticsNode()
            val pageNode = page.fetchSemanticsNode()
            val delta = compose.runOnIdle {
                val viewport = pageNode.boundsInRoot
                val top = textNode.positionInRoot.y
                val bottom = top + textNode.size.height
                when {
                    bottom > viewport.bottom -> bottom - viewport.bottom
                    top < viewport.top -> top - viewport.top
                    else -> 0f
                }
            }
            if (kotlin.math.abs(delta) <= 1f) return
            page.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, delta) }
            compose.waitForIdle()
        }
    }

    @Test fun discoverySectionActionsKeepTheirNavigationAndAccessibleNames() {
        for ((section, destination) in listOf("最近专辑" to "albums", "最近添加" to "songs", "曲库歌手" to "artists")) {
            show("home")
            val matcher = hasContentDescription("查看全部$section")
            scrollTo(matcher)
            compose.onNode(matcher).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            tap(compose.onNode(matcher), scroll = true)
            event("navigate:$destination")
        }
    }

    @Test fun discoveryShelfClickUsesTheOriginalQueueIndex() {
        show("home")
        val section = hasTestTag("music-home-songs")
        scrollTo(section)
        val card = compose.onNode(hasText("深空尽头", substring = false) and hasClickAction() and hasAnyAncestor(section))
        card.assertMinimumTouchTarget()
        // A shelf can be taller than a short viewport. Tap its fully visible title, not a clipped card centre.
        val title = compose.onNode(hasText("深空尽头", substring = false) and hasAnyAncestor(section), useUnmergedTree = true)
        revealShelfTitle(title)
        title.assertIsFullyVisible().assertTextHasNoVerticalOverflow().performTouchInput { click() }
        event("song:sample-1:1:false")
        UiTestDevice.captureInteractionImage("music-home-shelf-large.png")
    }

    @Test fun albumSortingAndDetailPlayUseProductionCallbacks() {
        show("albums")
        val sort = compose.onNode(hasText("名称", substring = false) and isSelectable())
        tap(sort, scroll = true)
        sort.assertIsSelected().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        event("album-sort:Name")
        tapRow("沿途的风", "打开专辑")
        event("navigate:album")
        tapPageAction("播放全部")
        event("play-album")
    }

    @Test fun artistListAndDetailKeepAlbumNavigationAndPlayAll() {
        show("artists")
        tapRow("张靓颖", "查看歌手")
        event("navigate:artist")
        tapPageAction("播放全部")
        event("play-artist")
        tapRow("沿途的风", "打开专辑")
        event("navigate:album")
    }

    @Test fun searchSongsKeepTheirResultQueueAndClearReturnsToSuggestions() {
        show("search")
        tapRow("深空尽头", "播放歌曲")
        event("song:sample-1:1:false")
        UiTestDevice.captureInteractionImage("music-search-song-large.png")
        scrollTo(hasSetTextAction())
        tap(compose.onNodeWithContentDescription("清除搜索关键词"), scroll = true)
        event("search-clear")
        scrollTo(hasText("搜索建议", substring = false))
        compose.onNodeWithText("搜索建议", substring = false).assertIsDisplayed()
    }

    @Test fun searchArtistsAndAlbumShelvesKeepTheirDetailCallbacks() {
        show("search")
        tapRow("张靓颖", "查看歌手")
        event("navigate:artist")
        show("search")
        val card = hasText("深空尽头", substring = false) and hasClickAction() and !clickLabel("播放歌曲")
        scrollTo(card)
        compose.onNode(card).assertMinimumTouchTarget()
        val title = compose.onNode(hasText("深空尽头", substring = false) and
            hasAnyAncestor(hasClickAction() and !clickLabel("播放歌曲")), useUnmergedTree = true)
        revealShelfTitle(title)
        title.assertIsFullyVisible().performTouchInput { click() }
        event("navigate:album")
    }

    @Test fun searchErrorRetryUsesTheSameQueryChangeCallback() {
        show("search", state = "error")
        tapPageAction("重试搜索")
        event("search:沿途")
        compose.onNodeWithText("搜索失败", substring = false).assertDoesNotExist()
        tapRow("张靓颖", "查看歌手")
        event("navigate:artist")
    }

    @Test fun cachedRefreshesAndFailuresKeepLibraryRowsInsteadOfFalseEmptyCards() {
        for (state in listOf("refreshing", "cached_error")) {
            for ((screen, title, label) in listOf(Triple("albums", "沿途的风", "打开专辑"),
                Triple("artists", "张靓颖", "查看歌手"), Triple("playlists", "夜航电台", "打开歌单"))) {
                show(screen, state)
                val row = hasText(title, substring = false) and clickLabel(label)
                scrollTo(row)
                compose.onNode(row).assertIsDisplayed()
                compose.onNodeWithText("加载失败", substring = false).assertDoesNotExist()
                compose.onNodeWithText("先接入你的音乐库", substring = false).assertDoesNotExist()
            }
        }
    }

    @Test fun failedPagesHaveScrollableRetryAndDoNotReportSuccessfulEmptyContent() {
        for (screen in listOf("home", "albums", "artists", "artist", "playlists", "playlist")) {
            println("Retry scenario: $screen")
            show(screen, state = "error")
            tapPageAction("重试")
            event("retry:$screen")
            compose.onNodeWithText("加载失败", substring = false).assertDoesNotExist()
        }
    }

    @Test fun knownEmptyCollectionsDisablePlayAndConfiguredEmptyIsNotSetup() {
        for (screen in listOf("artist", "playlist")) {
            show(screen, state = "empty")
            scrollTo(hasText("播放全部", substring = false))
            val play = compose.onNodeWithText("播放全部", substring = false)
            play.performScrollTo().assertIsFullyVisible().assertMinimumTouchTarget().assertIsNotEnabled()
            if (screen == "playlist") compose.onNodeWithText("35:34", substring = false).assertDoesNotExist()
            play.performTouchInput { click() }
            compose.runOnIdle { assertFalse(compose.activity.recordedEvents.any { it.startsWith("play-") }) }
        }
        show("home", state = "library_empty")
        compose.onNodeWithText("音乐库暂无内容", substring = false).assertIsDisplayed()
        compose.onNodeWithText("先接入你的音乐库", substring = false).assertDoesNotExist()
    }

    @Test fun playlistNavigationManagementAndRenameKeepTheirCallbacks() {
        show("playlists")
        tapRow("沿途听见", "打开歌单")
        event("navigate:playlist")
        tapPageAction("播放全部")
        event("play-playlist")
        scrollTo(hasText("删除歌单", substring = false) and hasClickAction())
        val rename = compose.onNodeWithText("重命名", substring = false)
        val delete = compose.onNodeWithText("删除歌单", substring = false)
        delete.performScrollTo().assertIsFullyVisible().assertMinimumTouchTarget()
        rename.assertIsFullyVisible().assertMinimumTouchTarget()
        UiTestDevice.captureInteractionImage("music-playlist-actions-large.png")
        tap(rename)
        val name = compose.onNode(hasSetTextAction())
        name.performTextReplacement("新的沿途歌单")
        tap(compose.onNodeWithText("保存", substring = false))
        event("playlist-submit:新的沿途歌单")
    }

    @Test fun blankPlaylistNameAndImeDoneShareTheSameSubmissionGuard() {
        show("playlist_create", font = 1f)
        val field = compose.onNode(hasSetTextAction())
        compose.onNodeWithText("创建", substring = false).assertIsNotEnabled().assertMinimumTouchTarget()
        field.performTextInput("   ")
        field.performImeAction()
        noMutation()
        field.performTextReplacement("  清晨电台  ")
        field.performImeAction()
        event("playlist-submit:清晨电台")
        compose.runOnIdle { assertEquals(1, compose.activity.recordedEvents.count { it.startsWith("playlist-submit:") }) }
    }

    @Test fun longDeleteConfirmationCanScrollAndCancelNeverDeletes() {
        show("playlist_delete", state = "long")
        val text = compose.onNode(hasText("确定删除", substring = true), useUnmergedTree = true)
        text.performScrollTo().assertTextHasNoVerticalOverflow()
        tap(compose.onNodeWithText("取消", substring = false))
        event("playlist-dismiss")
        noMutation()
        show("playlist_delete")
        compose.onNode(isDialog()).performTouchInput { click(Offset(4f, centerY)) }
        event("playlist-dismiss")
        noMutation()
        show("playlist_delete", state = "long")
        tap(compose.onNodeWithText("删除", substring = false))
        event("playlist-delete")
    }

    @Test fun submittingPlaylistDialogsDisableEditingDismissalAndMutations() {
        for (screen in listOf("playlist_create", "playlist_rename", "playlist_delete")) {
            show(screen, state = "loading")
            compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
            compose.onNodeWithText("处理中", substring = false).assertIsNotEnabled().assertMinimumTouchTarget()
            compose.onNodeWithText("取消", substring = false).assertIsNotEnabled().assertMinimumTouchTarget()
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            assertTrue(instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK))
            instrumentation.waitForIdleSync()
            compose.waitForIdle()
            compose.onNodeWithText("处理中", substring = false).assertExists()
            compose.onNode(isDialog()).performTouchInput { click(Offset(4f, centerY)) }
            compose.onNodeWithText("处理中", substring = false).assertExists()
            noMutation()
        }
    }

    @Test fun playlistErrorsRemainReadableAndRetainTheDraftForRetry() {
        show("playlist_rename", state = "error")
        val error = compose.onNode(hasText("暂时无法同步", substring = true), useUnmergedTree = true)
        error.performScrollTo().assertTextHasNoVerticalOverflow()
        compose.onNode(hasSetTextAction()).assertTextContains("沿途听见")
        tap(compose.onNodeWithText("保存", substring = false))
        event("playlist-submit:沿途听见")
    }

    @Test fun playlistSaveIsFullyAboveTheRealKeyboardAtLargeFont() = UiTestDevice.withSoftwareKeyboard {
        show("playlist_rename")
        val field = compose.onNode(hasSetTextAction())
        field.performClick().performTextReplacement("键盘里的歌单")
        val window = dialogWindow()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val originalFlags = automation.serviceInfo.flags
        automation.serviceInfo = automation.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
        fun keyboardBounds(): Rect? = automation.windows.firstOrNull {
            it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD
        }?.let { info -> Rect().also(info::getBoundsInScreen) }
        fun awaitKeyboard(visible: Boolean) {
            var previous: Rect? = null
            var previousHeight = -1
            var stableSince = SystemClock.uptimeMillis()
            compose.waitUntil(10_000) {
                val bounds = keyboardBounds()
                val height = window.decorView.height
                val now = SystemClock.uptimeMillis()
                if ((bounds != null) != visible || bounds != previous || height != previousHeight) {
                    previous = bounds; previousHeight = height; stableSince = now; false
                } else now - stableSince >= 500
            }
        }
        try {
            compose.runOnUiThread { WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.ime()) }
            awaitKeyboard(true)
            field.performScrollTo().assertIsFullyVisible().assertMinimumTouchTarget()
            val save = compose.onNodeWithText("保存", substring = false)
                .assertIsFullyVisible().assertMinimumTouchTarget()
            val origin = IntArray(2)
            compose.runOnUiThread { window.decorView.getLocationOnScreen(origin) }
            UiTestDevice.captureInteractionImage("music-playlist-ime-large.png")
            assertTrue("Save must be above IME: origin=${origin[1]}, button=${save.fetchSemanticsNode().boundsInWindow}, ime=${keyboardBounds()}",
                origin[1] + save.fetchSemanticsNode().boundsInWindow.bottom <= keyboardBounds()!!.top + 1f)
            assertTrue("The input must also remain above IME",
                origin[1] + field.fetchSemanticsNode().boundsInWindow.bottom <= keyboardBounds()!!.top + 1f)
            tap(save)
            event("playlist-submit:键盘里的歌单")
        } finally {
            try {
                compose.runOnUiThread { WindowCompat.getInsetsController(window, window.decorView).hide(WindowInsetsCompat.Type.ime()) }
                awaitKeyboard(false)
            } finally {
                automation.serviceInfo = automation.serviceInfo.apply { flags = originalFlags }
            }
        }
    }

    @Test fun equalizerPresetsAndTheLastBandAreReachableAndExposeTheirState() {
        show("equalizer")
        val preset = compose.onNode(hasText("古典", substring = false) and isSelectable())
        tap(preset, scroll = true)
        preset.assertIsSelected().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        event("eq-preset:1")
        val last = hasContentDescription("调整 14.0 kHz 频段")
        scrollTo(last)
        val slider = compose.onNode(last)
        slider.performScrollTo()
        UiTestDevice.captureInteractionImage("music-equalizer-before-gesture.png")
        slider.assertIsFullyVisible().assertMinimumTouchTarget()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .performTouchInput { click(Offset(width * 0.75f, centerY)) }
        event("eq-band:4")
        UiTestDevice.captureInteractionImage("music-equalizer-band-large.png")
    }

    @Test fun downloadsCancelRetryAndDisabledStatesKeepTheirOriginalCallbacks() {
        for (state in listOf("normal", "error")) {
            show("music_actions", state)
            tap(compose.onNodeWithText("下载当前歌曲", substring = false), scroll = true)
            event("download-preview")
        }
        show("music_actions", state = "loading")
        tap(compose.onNodeWithText("取消下载", substring = false), scroll = true)
        event("cancel-download-preview")
        for (state in listOf("empty", "disabled")) {
            show("music_actions", state)
            val download = compose.onNodeWithText("下载当前歌曲", substring = false)
            download.performScrollTo().assertIsNotEnabled().assertMinimumTouchTarget()
            compose.runOnIdle { assertFalse(compose.activity.recordedEvents.any { it.contains("download-preview") }) }
        }
    }

    @Test fun favoriteActionUsesTheExistingPlayerEntryAndCallback() {
        show("player")
        val favorite = compose.onNodeWithContentDescription("收藏", substring = false)
        if (compose.onAllNodes(hasPlayerControlsScrollAction()).fetchSemanticsNodes().isNotEmpty()) favorite.performScrollTo()
        tap(favorite)
        event("favorite")
        compose.onNodeWithContentDescription("取消收藏", substring = false).assertExists()
    }

    private fun dialogWindow(): Window = compose.runOnUiThread {
        fun find(view: View): Window? {
            if (view is DialogWindowProvider) return view.window
            if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
            return null
        }
        if (Build.VERSION.SDK_INT >= 29) {
            WindowInspector.getGlobalWindowViews().mapNotNull(::find).single()
        } else error("Only the dedicated API 34 emulator is supported")
    }
}
