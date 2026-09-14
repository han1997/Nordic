package com.nordic.mediahub

import android.content.res.Configuration
import android.os.Bundle
import androidx.core.view.WindowCompat
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import com.nordic.mediahub.ui.UiCatalogContent
import com.nordic.mediahub.ui.UiSampleRequest
import com.nordic.mediahub.ui.UiSampleScreen
import com.nordic.mediahub.ui.UiSampleState
import com.nordic.mediahub.ui.theme.NordicTheme

/** Debug-only, deterministic UI. Never constructs repositories, playback engines or download managers. */
class UiCatalogActivity : ComponentActivity() {
    private var request by mutableStateOf(UiSampleRequest())
    val recordedEvents = mutableListOf<String>()

    fun showSample(screen: String, state: String = "normal", dark: Boolean = false, fontScale: Float = 1f) {
        recordedEvents.clear()
        request = UiSampleRequest(
            screen = UiSampleScreen.entries.firstOrNull { it.id == screen } ?: UiSampleScreen.Catalog,
            state = UiSampleState.entries.firstOrNull { it.id == state } ?: UiSampleState.Normal,
            dark = dark,
            fontScale = fontScale.takeIf { it.isFinite() }?.coerceIn(1f, 2f) ?: 1f
        )
    }

    private fun updateSystemBars() {
        val style = if (request.dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !request.dark
            isAppearanceLightNavigationBars = !request.dark
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) updateSystemBars()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateSystemBars()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showSample(intent.getStringExtra("screen") ?: "catalog", intent.getStringExtra("state") ?: "normal",
            intent.getBooleanExtra("dark", false), intent.getFloatExtra("font_scale", 1f))
        enableEdgeToEdge()
        setContent {
            val density = LocalDensity.current
            val config = Configuration(LocalConfiguration.current).apply { fontScale = request.fontScale }
            SideEffect { updateSystemBars() }
            CompositionLocalProvider(LocalDensity provides Density(density.density, request.fontScale), LocalConfiguration provides config) {
                NordicTheme(darkTheme = request.dark) {
                    Surface(Modifier.fillMaxSize().testTag("ui-catalog-root"), color = MaterialTheme.colorScheme.background) {
                        key(request) {
                            UiCatalogContent(request, onNavigate = { next -> request = request.copy(screen = next) },
                                onOptionsChange = { dark, font, state -> request = request.copy(dark = dark, fontScale = font, state = state) },
                                onEvent = { recordedEvents += it })
                        }
                    }
                }
            }
        }
    }
}
