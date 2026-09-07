package com.nordic.mediahub.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Design-token alpha tiers for *secondary text* (`onSurface.copy(alpha = ...)`).
 *
 * Collapses the 11 scattered secondary-text alpha magic numbers into 3 named
 * tiers. State-semantic alpha (empty / loading containers in `MediaStateComponents`)
 * is intentionally NOT folded here — those remain local constants.
 *
 * Convergence map (driven by `.trellis/tasks/08-02-ui`):
 *   - {0.64, 0.66, 0.68, 0.7, 0.72, 0.74, 0.76, 0.78} -> [medium] (prominent secondary)
 *   - {0.4 (descriptive), 0.44, 0.46, 0.48, 0.5, 0.52, 0.54, 0.56, 0.58, 0.6} -> [subtle]
 *   - {0.3, 0.4 (hint/placeholder)} -> [faint]
 */
object NordicAlpha {
    val medium = 0.68f
    val subtle = 0.5f
    val faint = 0.3f
}

/**
 * Complete native typography, including the slots used internally by Material
 * dialogs, fields and menus. No component silently falls back to a different scale.
 * Explicit line heights and zero tracking keep Chinese and Latin UI copy aligned.
 */
private fun nordicTextStyle(size: Int, lineHeight: Int, weight: FontWeight) = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = 0.sp
)

val NordicTypography = Typography(
    displayLarge = nordicTextStyle(40, 48, FontWeight.Bold),
    displayMedium = nordicTextStyle(36, 44, FontWeight.Bold),
    displaySmall = nordicTextStyle(32, 40, FontWeight.Bold),
    headlineLarge = nordicTextStyle(28, 36, FontWeight.Bold),
    headlineMedium = nordicTextStyle(22, 28, FontWeight.Bold),
    headlineSmall = nordicTextStyle(20, 28, FontWeight.SemiBold),
    titleLarge = nordicTextStyle(20, 28, FontWeight.SemiBold),
    titleMedium = nordicTextStyle(16, 22, FontWeight.SemiBold),
    titleSmall = nordicTextStyle(14, 20, FontWeight.SemiBold),
    bodyLarge = nordicTextStyle(16, 24, FontWeight.Normal),
    bodyMedium = nordicTextStyle(14, 20, FontWeight.Normal),
    bodySmall = nordicTextStyle(12, 18, FontWeight.Medium),
    labelLarge = nordicTextStyle(13, 18, FontWeight.SemiBold),
    labelMedium = nordicTextStyle(12, 16, FontWeight.Medium),
    labelSmall = nordicTextStyle(11, 16, FontWeight.Medium)
)
