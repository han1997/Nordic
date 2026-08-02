package com.nordic.mediahub.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
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
 * Material3 [Typography] for the Nordic media hub UI.
 *
 * Replaces scattered `fontSize = <magic>.sp` + `fontWeight` literals with a
 * finite named scale wired through `MaterialTheme.typography`.
 *
 * Convergence map (driven by `.trellis/tasks/08-02-ui`):
 *   - {28, 30, 32, 36, 38, 54} -> [displaySmall]  (hero / decorative glyphs)
 *   - {20, 22, 24, 26}         -> [headlineMedium]
 *   - {15, 16, 17, 18, 19}     -> [titleMedium]
 *   - {14} (SemiBold)          -> [titleSmall]
 *   - {14} (Normal/Medium)     -> [bodyMedium]
 *   - {13}                     -> [labelLarge]
 *   - {11, 12}                 -> [bodySmall]
 *
 * When a call site needs a weight that differs from the token's default, pass
 * `style = MaterialTheme.typography.<slot>` together with an explicit
 * `fontWeight = FontWeight.<x>` override (the explicit param wins).
 */
val NordicTypography = Typography(
    displaySmall = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium)
)
