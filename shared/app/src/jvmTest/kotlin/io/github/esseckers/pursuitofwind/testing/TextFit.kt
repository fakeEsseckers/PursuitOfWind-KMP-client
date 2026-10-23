package io.github.esseckers.pursuitofwind.testing

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density

/** A 411 × 891 dp phone at 420 dpi, for checking layouts at a large font size. */
internal val PHONE_SIZE = Size(1080f, 2340f)
internal val PHONE_DENSITY = Density(2.625f)

/** What [checkTexts] found: every text that is laid out, and the ones that are cut off. */
internal data class TextFit(
    val checked: Set<String>,
    val cutOff: Set<String>,
)

/**
 * Measures every text laid out right now. A text is cut off when it is clipped by its own box or pushed past the left
 * or right edge of the screen; below the screen is fine, because the screen scrolls.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.checkTexts(): TextFit {
    val screenWidth = onRoot().fetchSemanticsNode().size.width
    val checked = mutableSetOf<String>()
    val cutOff = mutableSetOf<String>()
    onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
        .fetchSemanticsNodes()
        .forEach { node ->
            val text =
                node.config
                    .getOrNull(SemanticsProperties.Text)
                    ?.joinToString()
                    .orEmpty()
            val layouts = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            checked += text
            if (layouts.any { it.didOverflowHeight } || node.boundsInRoot.left < 0f || node.boundsInRoot.right > screenWidth) {
                cutOff += text
            }
        }
    return TextFit(checked, cutOff)
}
