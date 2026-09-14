package com.nordic.mediahub

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real application host and encrypted preference flow, on the dedicated empty emulator only. */
@RunWith(AndroidJUnit4::class)
class MainSettingsUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun settingsGearAndModuleChangesWorkThroughTheRealRoot() {
        UiTestDevice.requireDedicatedEmulator()
        compose.waitUntil(20_000) { compose.onAllNodesWithContentDescription("打开设置").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("打开设置").performClick()
        compose.onNode(hasVerticalLazyScrollAction()).performScrollToNode(hasText("模块显示", substring = false))
        compose.onNodeWithText("模块显示", substring = false).performClick()
        val video = compose.onNodeWithText("视频", substring = false)
        video.assertIsOn().performClick()
        try {
            compose.waitUntil(10_000) {
                video.fetchSemanticsNode().config[SemanticsProperties.ToggleableState] == ToggleableState.Off
            }
            video.assertIsOff()
        } finally {
            if (video.fetchSemanticsNode().config[SemanticsProperties.ToggleableState] == ToggleableState.Off) video.performClick()
            compose.waitUntil(10_000) {
                video.fetchSemanticsNode().config[SemanticsProperties.ToggleableState] == ToggleableState.On
            }
        }
        video.assertIsOn()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("模块显示", substring = false).fetchSemanticsNodes().isNotEmpty() }
        if (compose.onAllNodesWithText("选择首页显示的媒体内容").fetchSemanticsNodes().isEmpty()) {
            compose.onNode(hasVerticalLazyScrollAction()).performScrollToNode(hasText("模块显示", substring = false))
            compose.onNodeWithText("模块显示", substring = false).performClick()
        }
        compose.onNodeWithText("视频", substring = false).assertIsOn()
    }
}
