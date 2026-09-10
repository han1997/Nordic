package com.nordic.mediahub.ui

import androidx.compose.runtime.staticCompositionLocalOf

/** In-memory only: a form can guard tab changes without serializing its password into saved state. */
internal class SettingsNavigationGuard {
    var handler: ((() -> Unit) -> Unit)? = null
    fun navigate(action: () -> Unit) { handler?.invoke(action) ?: action() }
}
internal val LocalSettingsNavigationGuard = staticCompositionLocalOf<SettingsNavigationGuard?> { null }
internal val LocalConfigurationError = staticCompositionLocalOf<String?> { null }