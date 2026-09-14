package com.nordic.mediahub

import android.os.ParcelFileDescriptor
import android.graphics.Bitmap
import android.graphics.Color
import java.io.File
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.math.abs

/** System settings are changed only on this task's dedicated emulator, never on a physical device. */
internal object UiTestDevice {
    private fun shell(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText().trim() }
    }

    fun requireDedicatedEmulator() {
        check(shell("getprop ro.kernel.qemu") == "1") { "UI checks require the dedicated emulator" }
        val name = shell("getprop ro.boot.qemu.avd_name").ifBlank { shell("getprop ro.kernel.qemu.avd_name") }
        check(name == "nordic-ui-api34") { "Refusing to change another device: $name" }
    }

    /** Pixel guard for stale light/dark status icons; body contrast is tested separately. */
    fun hasExpectedSystemBarInk(bitmap: Bitmap, dark: Boolean, statusBarHeight: Int): Boolean {
        require(statusBarHeight > 0)
        var inkPixels = 0
        var lightPixels = 0
        var sampledPixels = 0
        for (y in 0 until statusBarHeight.coerceAtMost(bitmap.height)) {
            for (x in bitmap.width / 12 until bitmap.width / 3) {
                val color = bitmap.getPixel(x, y)
                val brightness = (Color.red(color) + Color.green(color) + Color.blue(color)) / 3
                if (brightness >= 128) lightPixels++
                sampledPixels++
                if ((dark && brightness > 180) || (!dark && brightness < 100)) inkPixels++
            }
        }
        val lightBackground = lightPixels > sampledPixels / 2
        return sampledPixels > 0 && lightBackground != dark && inkPixels >= 48
    }

    /** Compose idle can precede Android's window transition / final rendered frame. */
    fun captureStableWindow(): Bitmap {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        SystemClock.sleep(700)
        return checkNotNull(instrumentation.uiAutomation.takeScreenshot())
    }

    fun captureInteractionImage(name: String) {
        requireDedicatedEmulator()
        require(name.matches(Regex("[a-z-]+\\.png")))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val phase = InstrumentationRegistry.getArguments().getString("phase") ?: "interaction"
        require(phase.matches(Regex("[a-z0-9_-]+")))
        val output = File(instrumentation.targetContext.getExternalFilesDir(null), "ui-interactions/$phase")
        check(output.mkdirs() || output.isDirectory)
        val bitmap = captureStableWindow()
        File(output, name).outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }

    fun withSoftwareKeyboard(block: () -> Unit) {
        requireDedicatedEmulator()
        val previous = shell("settings get secure show_ime_with_hard_keyboard")
        check(previous in listOf("null", "0", "1")) { "Unexpected keyboard setting" }
        try {
            shell("settings put secure show_ime_with_hard_keyboard 1")
            block()
        } finally {
            if (previous == "null") shell("settings delete secure show_ime_with_hard_keyboard")
            else shell("settings put secure show_ime_with_hard_keyboard $previous")
        }
    }

    fun setFontScale(font: Float) {
        require(font in listOf(1f, 1.5f, 2f))
        requireDedicatedEmulator()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        shell("settings put system font_scale $font")
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (abs(instrumentation.targetContext.resources.configuration.fontScale - font) > 0.001f) {
            check(SystemClock.uptimeMillis() < deadline) { "System font scale did not update" }
            SystemClock.sleep(50)
        }
        instrumentation.waitForIdleSync()
    }
}
