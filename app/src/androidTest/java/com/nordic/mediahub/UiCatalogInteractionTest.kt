package com.nordic.mediahub

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.unit.dp
import android.app.ActivityManager
import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiCatalogInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<UiCatalogActivity>()

    @After fun restoreSystemFont() { UiTestDevice.setFontScale(1f) }

    private fun show(screen: String, state: String = "normal", dark: Boolean = false, font: Float = 1f) {
        UiTestDevice.setFontScale(font)
        compose.runOnUiThread { compose.activity.showSample(screen, state, dark, font) }
        compose.waitForIdle()
    }

    @Test fun moduleSwitchesUpdateImmediatelyAndRejectAllHidden() {
        show("modules")
        compose.onNodeWithText("音乐").assertIsOn().performClick().assertIsOff()
        compose.onNodeWithText("有声书").performClick().assertIsOff()
        compose.onNodeWithText("视频").performClick().assertIsOn()
        compose.onNodeWithText("至少保留一个媒体模块").assertIsDisplayed()
        compose.onNodeWithText("音乐").performClick().assertIsOn()
    }

    @Test fun songFilterAndClearUseTheProductionPage() {
        show("songs")
        compose.onNode(hasSetTextAction()).performTextInput("九万字")
        compose.onNodeWithText("深空尽头", substring = false).assertDoesNotExist()
        compose.onNodeWithContentDescription("清除歌曲筛选").performClick()
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.onNode(hasVerticalLazyScrollAction()).performScrollToNode(hasText("深空尽头", substring = false))
        compose.onNodeWithText("深空尽头", substring = false).assertIsDisplayed()
    }

    @Test fun playerTransportAndSpeedSelectionProduceCallbacks() {
        show("player")
        val play = compose.onNodeWithContentDescription("播放", substring = false)
        if (compose.onAllNodes(hasPlayerControlsScrollAction()).fetchSemanticsNodes().isNotEmpty()) play.performScrollTo()
        play.assertIsDisplayed().assertIsFullyVisible().assertMinimumTouchTarget().performTouchInput { click() }
        compose.runOnIdle { assertTrue(compose.activity.recordedEvents.contains("player-play")) }
        show("speed")
        compose.onNodeWithText("1.5x").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(compose.activity.recordedEvents.contains("speed:1.5")) }
    }

    @Test fun emptyPlayerStartsAtZeroWithDisabledTransport() {
        show("player", state = "empty")
        val play = compose.onNodeWithContentDescription("播放", substring = false)
        if (compose.onAllNodes(hasPlayerControlsScrollAction()).fetchSemanticsNodes().isNotEmpty()) play.performScrollTo()
        play.assertIsNotEnabled().assertIsDisplayed().assertIsFullyVisible()
        compose.onNodeWithText("0:00", substring = false).assertIsDisplayed()
        compose.onNodeWithText("1:16", substring = false).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    private fun performPlayerAction(label: String) {
        compose.onNodeWithTag("player-primary-display").performCustomAccessibilityActionWithLabel(label)
    }

    @Test fun playerArtworkExposesLyricsToggleAsAccessibilityAction() {
        show("player")
        performPlayerAction("显示歌词")
        compose.runOnIdle { assertTrue(compose.activity.recordedEvents.contains("lyrics")) }
        show("lyrics")
        performPlayerAction("显示封面")
        compose.runOnIdle { assertTrue(compose.activity.recordedEvents.contains("lyrics")) }
    }

    @Test fun playerArtworkExposesSeekAsAccessibilityActions() {
        show("player")
        performPlayerAction("前进 10 秒")
        compose.runOnIdle {
            assertTrue(
                compose.activity.recordedEvents.toString(),
                compose.activity.recordedEvents.any { it.startsWith("seek:") }
            )
        }
    }

    @Test fun emptyPlayerExposesNoCustomActions() {
        show("player", state = "empty")
        val actionFailed = try {
            performPlayerAction("显示歌词")
            false
        } catch (error: Exception) {
            true
        }
        assertTrue("Empty player must not expose playback custom actions", actionFailed)
    }

    @Test fun serverFormEditsAndSavesWithoutPersistentSideEffects() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = File(context.applicationInfo.dataDir, "shared_prefs/secret_prefs.xml")
        val before = preferences.takeIf(File::isFile)?.readBytes()
        show("server")
        val name = compose.onAllNodes(hasSetTextAction())[0]
        name.performTextClearance()
        name.performTextInput("测试音乐库")
        compose.onNode(hasVerticalLazyScrollAction()).performScrollToNode(hasText("保存", substring = false))
        compose.onNodeWithText("保存", substring = false).assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(compose.activity.recordedEvents.contains("save-preview")) }
        assertArrayEquals(before, preferences.takeIf(File::isFile)?.readBytes())
    }

    @Test fun catalogPlayerDoesNotStartAPlaybackServiceOrChangePreferences() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = File(context.applicationInfo.dataDir, "shared_prefs/secret_prefs.xml")
        val before = preferences.takeIf(File::isFile)?.readBytes()
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        fun services() = manager.getRunningServices(100).map { it.service.className }.toSet()
        val beforeServices = services()
        show("player")
        compose.onNodeWithContentDescription("音乐操作").performClick()
        compose.onNodeWithText("下载当前歌曲").performClick()
        compose.runOnIdle { assertTrue(compose.activity.recordedEvents.contains("download-preview")) }
        for (screen in listOf("home", "playlist_create", "equalizer", "music_actions")) show(screen)
        assertArrayEquals(before, preferences.takeIf(File::isFile)?.readBytes())
        assertEquals(beforeServices, services())
    }

    @Test fun queueTitlesHaveSpaceAndReorderHasAnAccessibleMenu() {
        show("queue", font = 2f)
        val title = compose.onAllNodesWithText("九万字", useUnmergedTree = true).onFirst().fetchSemanticsNode().boundsInRoot
        assertTrue(title.width >= 100f)
        compose.onAllNodesWithTag("queue-artwork", useUnmergedTree = true).onFirst()
            .assertWidthIsEqualTo(42.dp).assertHeightIsEqualTo(42.dp)
        compose.onNodeWithContentDescription("队列操作：九万字").performClick()
        compose.onNodeWithText("下移").performClick()
        compose.runOnIdle { assertTrue(compose.activity.recordedEvents.contains("queue-move:0:1")) }
    }

    @Test fun draggingQueueRowSubmitsTheLatestDropPosition() {
        show("queue")
        val handle = compose.onAllNodesWithContentDescription("拖动调整顺序", useUnmergedTree = true).onFirst()
        val distance = with(compose.density) { 80.dp.toPx() }
        handle.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(650)
        handle.performTouchInput { moveBy(Offset(0f, distance)); up() }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(compose.activity.recordedEvents.toString(), compose.activity.recordedEvents.contains("queue-move:0:1")) }
    }

    @Test fun additionalServerFieldsStayReachableAtLargeFont() {
        show("server_webdav", font = 2f)
        compose.onNode(hasVerticalLazyScrollAction()).performScrollToNode(hasText("起始目录"))
        compose.onNodeWithText("起始目录").assertIsDisplayed()
        compose.onNode(hasVerticalLazyScrollAction()).performScrollToNode(hasText("保存", substring = false))
        compose.onNodeWithText("保存", substring = false).assertIsDisplayed()
        show("server_emby", font = 2f)
        compose.onNode(hasVerticalLazyScrollAction()).performScrollToNode(hasText("API Key（可选）"))
        compose.onNodeWithText("API Key（可选）").assertIsDisplayed()
        compose.onNode(hasVerticalLazyScrollAction()).performScrollToNode(hasText("保存", substring = false))
        compose.onNodeWithText("保存", substring = false).assertIsDisplayed()
    }

    @Test fun nowPlayingBarGrowsForLargeTextInsteadOfClippingMetadata() {
        show("songs")
        val normalHeight = compose.onNodeWithTag("now-playing-bar").fetchSemanticsNode().boundsInRoot.height
        show("songs", font = 2f)
        val barNode = compose.onNodeWithTag("now-playing-bar").fetchSemanticsNode()
        val texts = listOf("九万字", "张靓颖").map { text ->
            text to compose.onNode(
                hasText(text, substring = false) and hasAnyAncestor(hasTestTag("now-playing-bar")),
                useUnmergedTree = true
            ).assertIsDisplayed().assertTextHasNoVerticalOverflow().fetchSemanticsNode()
        }
        // Compare parent/child geometry in one UI frame, not across asynchronous window inset updates.
        compose.runOnIdle {
            val bar = barNode.boundsInRoot
            assertTrue("The bar must grow with its text instead of keeping the compact height", bar.height > normalHeight)
            texts.forEach { (text, node) ->
                val bounds = node.boundsInRoot
                assertTrue("$text $bounds must stay within $bar", bounds.top >= bar.top && bounds.bottom <= bar.bottom)
            }
        }
    }

    @Test fun dockShowsErrorAndBufferingStatusWithButtonRole() {
        show("songs", state = "error")
        compose.onNodeWithText("连接暂时不可用", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("播放", substring = false)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        show("songs", state = "loading")
        compose.onNodeWithText("正在缓冲", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("播放", substring = false).assertIsDisplayed().assertMinimumTouchTarget()
    }

    @Test fun sampleControlsExposeRolesSelectionAndMinimumTouchTargets() {
        for (font in listOf(1f, 2f)) {
            show("songs", font = font)
            compose.onNodeWithContentDescription("打开设置").assertMinimumTouchTarget()
            compose.onNodeWithContentDescription("播放", substring = false).assertMinimumTouchTarget()
            compose.onNodeWithText("歌曲", substring = false).assertIsSelected().assertMinimumTouchTarget()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            show("queue", font = font)
            compose.onNodeWithText("清空后续", substring = false).assertMinimumTouchTarget()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            show("modules", font = font)
            compose.onNodeWithText("音乐", substring = false).assertIsOn().assertMinimumTouchTarget()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            show("speed", font = font)
            compose.onNode(hasText("1.0x", substring = false) and isSelectable()).assertIsSelected()
                .assertMinimumTouchTarget()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        }
    }

    @Test fun busyFormAndDisabledSettingsDoNotExposeEnabledActions() {
        show("server", state = "disabled")
        compose.onNodeWithText("显示名称", substring = false).assertIsNotEnabled()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.onNode(hasVerticalLazyScrollAction()).performScrollToNode(hasText("正在保存", substring = false))
        compose.onNodeWithText("正在保存", substring = false).assertIsNotEnabled().assertMinimumTouchTarget()
        compose.onNodeWithText("测试连接", substring = false).assertIsNotEnabled().assertMinimumTouchTarget()
        show("settings_rows", state = "disabled")
        compose.onNodeWithText("清理缓存", substring = false).performScrollTo().assertIsNotEnabled()
    }

    @Test fun serverSaveStaysReachableAboveTheKeyboardAtLargeFont() = UiTestDevice.withSoftwareKeyboard {
        show("server", font = 2f)
        val field = compose.onAllNodes(hasSetTextAction()).onFirst()
        field.performClick().assertIsFocused()
        val window = compose.activity.window
        compose.runOnUiThread { WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.ime()) }
        // Both showing and hiding animate; finish cleanup before another Activity starts measuring.
        fun awaitKeyboardLayout(visible: Boolean) {
            var lastBottom = -1
            var lastViewportHeight = -1f
            var stableSince = android.os.SystemClock.uptimeMillis()
            compose.waitUntil(10_000) {
                val insets = ViewCompat.getRootWindowInsets(window.decorView)
                val bottom = insets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
                val viewportHeight = compose.onNode(hasVerticalLazyScrollAction()).fetchSemanticsNode().boundsInWindow.height
                val now = android.os.SystemClock.uptimeMillis()
                val expectedBottom = if (visible) bottom > 0 else bottom == 0
                if (insets?.isVisible(WindowInsetsCompat.Type.ime()) != visible || !expectedBottom ||
                    bottom != lastBottom || viewportHeight != lastViewportHeight) {
                    lastBottom = bottom
                    lastViewportHeight = viewportHeight
                    stableSince = now
                    false
                } else now - stableSince >= 500
            }
        }
        try {
            awaitKeyboardLayout(visible = true)
            compose.onNode(hasVerticalLazyScrollAction()).performScrollToNode(hasText("保存", substring = false))
            val save = compose.onNodeWithText("保存", substring = false).assertIsDisplayed().assertIsFullyVisible().assertMinimumTouchTarget()
            val imeBottom = ViewCompat.getRootWindowInsets(window.decorView)!!.getInsets(WindowInsetsCompat.Type.ime()).bottom
            assertTrue("Save must be above the IME", save.fetchSemanticsNode().boundsInWindow.bottom <= window.decorView.height - imeBottom + 1f)
            save.performTouchInput { click() }
            compose.runOnIdle { assertTrue(compose.activity.recordedEvents.contains("save-preview")) }
        } finally {
            compose.runOnUiThread { WindowCompat.getInsetsController(window, window.decorView).hide(WindowInsetsCompat.Type.ime()) }
            awaitKeyboardLayout(visible = false)
        }
    }

    @Test fun statusIconPixelGuardRejectsBlankAndOppositeThemeFrames() {
        val bitmap = android.graphics.Bitmap.createBitmap(300, 60, android.graphics.Bitmap.Config.ARGB_8888)
        try {
            for (dark in listOf(false, true)) {
                bitmap.eraseColor(if (dark) android.graphics.Color.BLACK else android.graphics.Color.LTGRAY)
                assertFalse(UiTestDevice.hasExpectedSystemBarInk(bitmap, dark, 60))
                for (y in 20 until 40) for (x in 35 until 55) {
                    bitmap.setPixel(x, y, if (dark) android.graphics.Color.WHITE else android.graphics.Color.BLACK)
                }
                assertTrue(UiTestDevice.hasExpectedSystemBarInk(bitmap, dark, 60))
                assertFalse(UiTestDevice.hasExpectedSystemBarInk(bitmap, !dark, 60))
            }
        } finally {
            bitmap.recycle()
        }
    }

    @Test fun themeAndFontChangesKeepSystemBarsInSyncAfterClosingSheets() {
        for (font in listOf(1f, 2f)) for (dark in listOf(false, true)) {
            show("speed", dark = !dark, font = font)
            show("songs", dark = dark, font = font)
            compose.onNodeWithContentDescription("打开设置").assertIsDisplayed()
            val controller = WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView)
            compose.waitUntil(5_000) { controller.isAppearanceLightStatusBars == !dark && controller.isAppearanceLightNavigationBars == !dark }
        }
    }

    @Test fun playerControlsStayReachableByScrollingAtLargeFont() {
        for (screen in listOf("player", "lyrics")) {
            show(screen, font = 2f)
            val controlsScroll = hasPlayerControlsScrollAction()
            val scrollNodes = compose.onAllNodes(controlsScroll).fetchSemanticsNodes()
            if (compose.activity.resources.configuration.screenHeightDp < 600) {
                assertEquals("Short screens must retain a scrollable controls path", 1, scrollNodes.size)
            }
            val play = compose.onNodeWithContentDescription("播放", substring = false)
            if (scrollNodes.isNotEmpty()) {
                compose.onNode(controlsScroll).performTouchInput { swipeUp() }
                play.performScrollTo()
            }
            play.assertIsDisplayed().assertIsFullyVisible().assertMinimumTouchTarget().performTouchInput { click() }
            compose.runOnIdle { assertTrue(compose.activity.recordedEvents.contains("player-play")) }
            compose.waitForIdle()
            UiTestDevice.captureInteractionImage("$screen-controls-large.png")
        }
    }

    @Test fun longSettingsValueStacksBelowTitleAtLargeFont() {
        show("settings_rows", state = "long", font = 2f)
        val title = compose.onNodeWithText("默认打开的媒体页面", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val value = compose.onNodeWithText("上次访问的页面", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Long values should not squeeze the title into a narrow column", value.top >= title.bottom - 1f)
    }

    @Test fun videoHomeCardsOpenDetailThroughProductionContent() {
        show("video_home")
        val card = compose.onNode(hasText("北境回声", substring = false) and hasClickAction())
        card.assertIsDisplayed().assertMinimumTouchTarget().performTouchInput { click() }
        compose.runOnIdle { assertTrue(compose.activity.recordedEvents.contains("video-open:video-movie-0")) }
    }

    @Test fun videoSearchShowsNoMatchStateAndKeepsClearPath() {
        show("video_search", state = "empty")
        compose.onNodeWithText("没有匹配的视频", substring = false).assertIsDisplayed()
        compose.onNodeWithContentDescription("清除视频搜索关键词").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(compose.activity.recordedEvents.contains("video-search:")) }
    }

    @Test fun videoSeriesHighlightsResolvedCurrentEpisode() {
        show("video_series")
        val current = compose.onNode(hasText("回声", substring = false) and isSelected())
        current.performScrollTo().assertIsDisplayed().assertIsSelected()
    }

    @Test fun videoDetailActionsAndEpisodesRemainReachableAtLargeFont() {
        show("video_detail", font = 2f)
        val play = compose.onNodeWithText("继续从", substring = true)
        play.performScrollTo().assertIsDisplayed().assertIsFullyVisible().assertMinimumTouchTarget().performTouchInput { click() }
        compose.runOnIdle { assertTrue(compose.activity.recordedEvents.contains("video-play:video-movie-0")) }

        show("video_series", font = 2f)
        val episode = compose.onNode(hasText("回声", substring = false) and hasClickAction())
        episode.performScrollTo().assertIsDisplayed().assertIsFullyVisible().assertMinimumTouchTarget()
            .performTouchInput { click() }
        compose.runOnIdle { assertTrue(compose.activity.recordedEvents.contains("video-play-episode:video-ep-1")) }
        compose.onNodeWithText("归途", substring = false).performScrollTo().assertIsNotEnabled()
    }

    @Test fun videoSeriesUnwatchedEmptyUsesCompactStateCard() {
        show("video_series", state = "empty")
        compose.onNodeWithText("未看", substring = false).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("未看", substring = false).performClick()
        val card = compose.onNodeWithText("没有未看的分集", substring = false)
        card.performScrollTo().assertIsDisplayed().assertIsFullyVisible()
        compose.onNodeWithText("本季已没有尚未播放的分集。", substring = false).assertIsDisplayed()
    }

    @Test fun settingsHomeShowsSectionsAndProducesNavigationCallbacks() {
        show("settings_home")
        compose.onNodeWithText("设置", substring = false).assertIsDisplayed()
        compose.onNodeWithText("连接", substring = false).assertIsDisplayed()
        compose.onNodeWithText("媒体服务器", substring = false).performScrollTo().performClick()
        compose.runOnIdle { assertTrue(compose.activity.recordedEvents.contains("settings:SERVERS")) }
    }

    @Test fun settingsServersExposeRadioRolesAndEmptyState() {
        show("settings_servers", state = "empty")
        compose.onNode(hasVerticalLazyScrollAction()).performScrollToNode(hasText("尚未添加音乐服务器", substring = false))
        compose.onNodeWithText("尚未添加音乐服务器", substring = false).assertIsDisplayed()
        compose.onNode(hasVerticalLazyScrollAction()).performScrollToNode(hasText("尚未添加视频服务器", substring = false))
    }

    @Test fun settingsPrefsAndDataPagesStayInMemoryOnly() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = File(context.applicationInfo.dataDir, "shared_prefs/secret_prefs.xml")
        val before = preferences.takeIf(File::isFile)?.readBytes()
        show("settings_prefs")
        compose.onNodeWithText("主题", substring = false).assertIsDisplayed().performClick()
        compose.onNodeWithText("浅色", substring = false).performClick()
        compose.runOnIdle { assertTrue(compose.activity.recordedEvents.contains("prefs-update")) }
        show("settings_data")
        compose.onNodeWithText("恢复偏好默认值", substring = false).performScrollTo().performTouchInput { click() }
        compose.runOnIdle { assertTrue(compose.activity.recordedEvents.contains("settings:reset-prefs")) }
        assertArrayEquals(before, preferences.takeIf(File::isFile)?.readBytes())
    }

    @Test fun audiobookSleepTimerDeduplicatesPresetEntry() {
        show("ab_sleep")
        compose.onNodeWithText("使用预选:45 分钟", substring = false).assertExists()
        compose.onNodeWithText("30 分钟后停止", substring = false).assertExists()
        compose.onAllNodesWithText("45 分钟后停止", substring = false).assertCountEquals(0)
        compose.onNodeWithText("关闭", substring = false).assertExists()
        compose.onNodeWithText("本章结束", substring = false).assertExists()
    }
}
