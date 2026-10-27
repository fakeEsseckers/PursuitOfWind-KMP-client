package io.github.esseckers.pursuitofwind.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.LocalListDetailSceneScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Sizes
import io.github.esseckers.pursuitofwind.feature.spots.presentation.view.NoSpotChosen

// The Spots list and a spot's forecast side by side on a wide window: the list-detail scene's pieces (ADR 0009).

/**
 * The Spots tab is the list pane of a list-detail scene: with room for two panes, a spot it opens shows beside it, and
 * [NoSpotChosen] fills the other pane until then. The pane is as wide as a phone's list plus the rail the tab carries.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
internal val spotListPane: Map<String, Any> =
    ListDetailSceneStrategy.listPane(detailPlaceholder = { NoSpotChosen() }) +
        ListDetailSceneStrategy.preferredPaneSize(width = Sizes.listPaneWidth)

/** A spot opened from the Spots list: the detail pane beside it. Opened from elsewhere, it is a screen of its own. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
internal val spotDetailPane: Map<String, Any> = ListDetailSceneStrategy.detailPane()

/**
 * How many panes fit side by side: two need an expanded width and the rail. A phone in landscape is wide enough but
 * short, so its tabs are a bar, and a list-detail scene would put that bar under the list alone; it gets one pane.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun paneDirective(): PaneScaffoldDirective {
    val directive = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())
    return if (tabsLayout().isRail) directive else directive.copy(maxHorizontalPartitions = 1)
}

/**
 * [content] as a pane of a list-detail scene, or as a whole screen outside one. A pane does not reach the window's edge
 * on the side [away] (the list's end, the detail's start), so it leaves the system bars and cutouts on that side to the
 * pane next to it. Outside a scene nothing changes.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun PaneInsets(
    away: WindowInsetsSides,
    content: @Composable () -> Unit,
) {
    val inScene = LocalListDetailSceneScope.current != null
    val modifier = if (inScene) Modifier.consumeWindowInsets(WindowInsets.safeDrawing.only(away)) else Modifier
    Box(modifier = modifier.fillMaxSize()) { content() }
}
