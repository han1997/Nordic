package com.nordic.mediahub.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkBackground,
    primaryContainer = DarkPrimary.copy(alpha = 0.18f),
    onPrimaryContainer = DarkPrimary,
    secondary = DarkSecondary,
    onSecondary = Color(0xFF061019),
    secondaryContainer = DarkSecondary.copy(alpha = 0.16f),
    onSecondaryContainer = DarkSecondary,
    tertiary = DarkPrimary,
    onTertiary = DarkBackground,
    tertiaryContainer = DarkPrimary.copy(alpha = 0.18f),
    onTertiaryContainer = DarkPrimary,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    surfaceTint = DarkPrimary,
    outline = Color(0xFF7F7D90),
    outlineVariant = Color(0xFF3D3B4A),
    error = Color(0xFFFFB2BD),
    onError = Color(0xFF5C1525),
    errorContainer = Color(0xFF402028),
    onErrorContainer = Color(0xFFFFD9DF),
    inverseSurface = LightBackground,
    inverseOnSurface = LightTextPrimary,
    inversePrimary = LightPrimary,
    scrim = Color.Black
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color(0xFFFAF8FF),
    primaryContainer = LightPrimary.copy(alpha = 0.14f),
    onPrimaryContainer = Color(0xFF4B327F),
    secondary = LightSecondary,
    onSecondary = Color(0xFF061019),
    secondaryContainer = LightSecondary.copy(alpha = 0.12f),
    onSecondaryContainer = Color(0xFF064961),
    tertiary = LightPrimary,
    onTertiary = Color(0xFFFAF8FF),
    tertiaryContainer = LightPrimary.copy(alpha = 0.14f),
    onTertiaryContainer = Color(0xFF4B327F),
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    surfaceTint = LightPrimary,
    outline = Color(0xFF7B788B),
    outlineVariant = Color(0xFFD9D7E2),
    error = Color(0xFFBA2445),
    onError = Color.White,
    errorContainer = Color(0xFFFFE3E8),
    onErrorContainer = Color(0xFF72192E),
    inverseSurface = DarkSurfaceVariant,
    inverseOnSurface = DarkTextPrimary,
    inversePrimary = DarkPrimary,
    scrim = Color.Black
)

/** Also used by contrast/contract tests; the returned schemes are the ones the app renders. */
internal fun nordicColorScheme(darkTheme: Boolean): ColorScheme =
    if (darkTheme) DarkColorScheme else LightColorScheme

@Composable
fun NordicTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = nordicColorScheme(darkTheme),
        typography = NordicTypography,
        shapes = NordicShapesMaterial,
        content = content
    )
}
