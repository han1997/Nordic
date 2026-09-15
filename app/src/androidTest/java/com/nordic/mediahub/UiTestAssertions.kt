package com.nordic.mediahub

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/** A form can contain a horizontal type picker as well as its vertical page list. */
internal fun hasVerticalLazyScrollAction(): SemanticsMatcher =
    hasScrollToIndexAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)

internal fun hasPlayerControlsScrollAction(): SemanticsMatcher =
    SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and
        hasAnyDescendant(hasContentDescription("播放", substring = false))

/** Android 14 scales fonts nonlinearly; check rendered lines, not a guessed dp height. */
internal fun SemanticsNodeInteraction.assertTextHasNoVerticalOverflow(): SemanticsNodeInteraction = apply {
    performSemanticsAction(SemanticsActions.GetTextLayoutResult) { getLayout ->
        val layouts = mutableListOf<TextLayoutResult>()
        assertTrue("Text must expose its measured layout", getLayout(layouts) && layouts.isNotEmpty())
        layouts.forEach { assertFalse("Text must not be vertically clipped", it.didOverflowHeight) }
    }
}

internal fun SemanticsNodeInteraction.assertMinimumTouchTarget(): SemanticsNodeInteraction =
    assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)

/** assertIsDisplayed accepts partial intersection; controls must be fully inside the viewport. */
internal fun SemanticsNodeInteraction.assertIsFullyVisible(): SemanticsNodeInteraction = apply {
    val node = fetchSemanticsNode()
    val visible = node.boundsInWindow
    assertTrue("Control must not be clipped: visible=$visible, size=${node.size}, root=${node.boundsInRoot}",
        visible.width + 1f >= node.size.width && visible.height + 1f >= node.size.height)
}
