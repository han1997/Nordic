package com.nordic.mediahub

import android.graphics.Bitmap
import com.nordic.mediahub.ui.UiSampleScreen
import com.nordic.mediahub.ui.UiSampleState
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Run with explicit emulator serial via am instrument; never connectedAndroidTest against all devices. */
@RunWith(AndroidJUnit4::class)
class UiCatalogScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<UiCatalogActivity>()

    @Test fun captureDeterministicSamples() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        val phase = arguments.getString("phase") ?: "review"
        require(phase.matches(Regex("[a-z0-9_-]+")))
        val screens = (arguments.getString("screens") ?: "songs,album,player,lyrics,queue,speed,modules,server,settings_rows").split(',')
        val states = (arguments.getString("states") ?: "normal").split(',')
        require(screens.all { id -> UiSampleScreen.entries.any { it.id == id } })
        require(states.all { id -> UiSampleState.entries.any { it.id == id } })
        require(screens.size == screens.distinct().size && states.size == states.distinct().size)
        val fonts = (arguments.getString("fonts") ?: "1,2").split(',').map(String::toFloat)
        val output = File(instrumentation.targetContext.getExternalFilesDir(null), "ui-catalog/$phase")
        check(output.mkdirs() || output.isDirectory)
        val manifest = JSONArray()
        try {
            for (font in fonts) {
                UiTestDevice.setFontScale(font)
                for (screen in screens) for (state in states) for (dark in listOf(false, true)) {
                    compose.runOnUiThread { compose.activity.showSample(screen, state, dark, font) }
                    compose.waitForIdle()
                    compose.onNodeWithTag("ui-catalog-root").assertExists()
                    // Resource covers are decoded asynchronously; no network request or ticking playback is used.
                    val name = "$screen-$state-${if (dark) "dark" else "light"}-font${font.toString().replace('.', '_')}.png"
                    val bitmap = UiTestDevice.captureStableWindow()
                    val statusHeight = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)!!
                        .getInsets(WindowInsetsCompat.Type.statusBars()).top
                    val statusBarDark = UiTestDevice.expectsDarkStatusBar(screen, dark)
                    assertTrue("Status icons must match $screen/$state dark=$dark font=$font",
                        UiTestDevice.hasExpectedSystemBarInk(bitmap, statusBarDark, statusHeight))
                    File(output, name).outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                    bitmap.recycle()
                    manifest.put(JSONObject().put("file", name).put("screen", screen).put("state", state).put("dark", dark).put("fontScale", font)
                        .put("systemFontScale", instrumentation.targetContext.resources.configuration.fontScale).put("statusBarDarkBackground", statusBarDark).put("statusBarInkVerified", true))
                }
            }
        } finally {
            UiTestDevice.setFontScale(1f)
        }
        File(output, "manifest.json").writeText(manifest.toString(2))
    }
}
