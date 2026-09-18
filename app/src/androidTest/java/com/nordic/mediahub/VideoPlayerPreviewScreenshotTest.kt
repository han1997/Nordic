package com.nordic.mediahub

import android.content.Intent
import android.os.SystemClock
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Captures the real [com.nordic.mediahub.ui.VideoPlayerScreen] panels through the
 * debug-only offline preview activity. Never touches a playback engine or a server.
 */
@RunWith(AndroidJUnit4::class)
class VideoPlayerPreviewScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<VideoPlayerPreviewActivity>()

    private fun capture(name: String, manifest: JSONArray, panel: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val output = File(instrumentation.targetContext.getExternalFilesDir(null), "ui-player-preview/$PHASE")
        check(output.mkdirs() || output.isDirectory)
        val bitmap = UiTestDevice.captureStableWindow()
        File(output, "$name.png").outputStream().use { check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
        manifest.put(JSONObject().put("file", "$name.png").put("panel", panel))
        bitmap.recycle()
    }

    /** Panel dismissal closes the whole panel stack; the settings panel must be reopened each time. */
    private fun openSettingsPanel() {
        compose.onNodeWithContentDescription("更多播放设置").performClick()
        compose.waitForIdle()
        UiTestDevice.waitForStableWindow()
    }

    private fun openSubPanel(rowTitle: String) {
        openSettingsPanel()
        clickRow(rowTitle)
    }

    private fun clickRow(rowTitle: String) {
        compose.onNode(hasText(rowTitle, substring = false)).assertIsDisplayed().performClick()
        compose.waitForIdle()
        UiTestDevice.waitForStableWindow()
    }

    /** Off-screen settings rows need one in-panel swipe before a real click. */
    private fun scrollSettingsPanelUp() {
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .performTouchInput { swipeUp() }
        compose.waitForIdle()
        UiTestDevice.waitForStableWindow()
    }

    private fun closeSubPanel(panelTitle: String) {
        compose.onNodeWithContentDescription("关闭$panelTitle").performClick()
        compose.waitForIdle()
        UiTestDevice.waitForStableWindow()
    }

    @Test
    fun capturePlayerPanelsAndLandscape() {
        UiTestDevice.requireDedicatedEmulator()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val output = File(instrumentation.targetContext.getExternalFilesDir(null), "ui-player-preview/$PHASE")
        check(output.mkdirs() || output.isDirectory)
        val manifest = JSONArray()
        compose.waitForIdle()
        UiTestDevice.waitForStableWindow()
        try {
            capture("player-portrait-chrome", manifest, "Chrome")

            openSettingsPanel()
            capture("panel-settings", manifest, "Settings")

            // The settings panel is still open here; click the first row directly.
            clickRow("播放速度")
            capture("panel-speed", manifest, "Speed")
            closeSubPanel("播放速度")

            openSubPanel("字幕与音轨")
            capture("panel-tracks", manifest, "Tracks")
            closeSubPanel("字幕与音轨")

            openSubPanel("章节")
            capture("panel-chapters", manifest, "Chapters")
            closeSubPanel("章节")

            openSubPanel("清晰度")
            capture("panel-quality", manifest, "Quality")
            closeSubPanel("清晰度")

            openSubPanel("选集")
            capture("panel-episodes", manifest, "Episodes")
            closeSubPanel("选集")

            openSettingsPanel()
            scrollSettingsPanelUp()
            clickRow("影片信息")
            capture("panel-info", manifest, "Info")
            closeSubPanel("影片信息")

            compose.onNodeWithContentDescription("进入全屏").performClick()
            compose.waitForIdle()
            UiTestDevice.waitForStableWindow()
            capture("player-landscape-chrome", manifest, "Landscape")
            compose.onNodeWithContentDescription("退出全屏").performClick()
            compose.waitForIdle()
        } finally {
            File(output, "manifest.json").writeText(manifest.toString(2))
        }
    }

    @Test
    fun captureAutoNextCountdown() {
        UiTestDevice.requireDedicatedEmulator()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val output = File(instrumentation.targetContext.getExternalFilesDir(null), "ui-player-preview/$PHASE")
        check(output.mkdirs() || output.isDirectory)
        val manifest = readManifest(output)
        val context = instrumentation.targetContext
        context.startActivity(Intent(context, VideoPlayerPreviewActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("scenario", "autoplay"))
        SystemClock.sleep(2500)
        capture("player-autonext-countdown", manifest, "AutoNext")
        writeManifest(output, manifest)
    }

    private fun readManifest(output: File): JSONArray = try {
        JSONArray(output.resolve("manifest.json").readText())
    } catch (_: Exception) {
        JSONArray()
    }

    private fun writeManifest(output: File, manifest: JSONArray) {
        output.resolve("manifest.json").writeText(manifest.toString(2))
    }

    private companion object {
        const val PHASE = "r13-player"
    }
}
