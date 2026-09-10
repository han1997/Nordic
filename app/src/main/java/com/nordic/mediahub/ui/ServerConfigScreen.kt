package com.nordic.mediahub.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/** Compatibility entry point; connection forms now live in the settings navigation. */
@Composable
fun ServerConfigScreen(colorScheme: ColorScheme = MaterialTheme.colorScheme, isDark: Boolean, onThemeToggle: (Boolean) -> Unit) {
    SettingsScreen()
}