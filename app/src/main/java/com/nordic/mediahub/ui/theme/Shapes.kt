package com.nordic.mediahub.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Design-token shape scale for the Nordic media hub UI.
 *
 * Replaces the scattered `RoundedCornerShape(<magic>.dp)` literals and unifies
 * `CircleShape` / `RoundedCornerShape(999.dp)` into a single [full] pill token.
 *
 * Convergence map (driven by `.trellis/tasks/08-02-ui`):
 *   - {8,10,12,13}        -> [sm]  (small cards / chips)
 *   - {14,16,18}          -> [md]  (default cards)
 *   - {18,20}             -> [lg]  (large cards / cover art)
 *   - {24,28,30}          -> [xl]  (sheets / surfaces)
 *   - {999, CircleShape}  -> [full] (pills / circles)
 */
object NordicShapes {
    val none = RoundedCornerShape(0.dp)
    val sm = RoundedCornerShape(12.dp)
    val md = RoundedCornerShape(16.dp)
    val lg = RoundedCornerShape(20.dp)
    val xl = RoundedCornerShape(24.dp)

    // Percent-based full pill — replaces both RoundedCornerShape(999.dp) and CircleShape.
    val full = RoundedCornerShape(50)
}

/** Material3 [Shapes] wiring so `MaterialTheme.shapes` resolves to the Nordic scale. */
val NordicShapesMaterial = Shapes(
    extraSmall = NordicShapes.sm,
    small = NordicShapes.sm,
    medium = NordicShapes.md,
    large = NordicShapes.lg,
    extraLarge = NordicShapes.xl
)
