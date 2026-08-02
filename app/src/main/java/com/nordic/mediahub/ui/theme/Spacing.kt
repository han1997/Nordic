package com.nordic.mediahub.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Design-token spacing scale for the Nordic media hub UI.
 *
 * Replaces scattered `Modifier.padding(<magic>.dp)` / `Spacer(<magic>.dp)` /
 * `Arrangement.spacedBy(<magic>.dp)` literals with a finite named set.
 *
 * Convergence map (driven by `.trellis/tasks/08-02-ui`):
 *   - {3,4,5}    -> [xs]
 *   - {6,7,8,9}  -> [sm]
 *   - {10,12,13,14} -> [md]
 *   - {15,16,18} -> [lg]
 *   - {20,22}    -> [xl]
 *   - {24,28}    -> [xxl]
 *   - {30,34}    -> [xxxl]
 *
 * [content] is a semantic alias for the standard screen-content inset (matches [lg]).
 */
object NordicSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp

    val content = 16.dp
}
