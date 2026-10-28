package io.github.esseckers.pursuitofwind.feature.spotmap.presentation.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import io.github.esseckers.pursuitofwind.core.designsystem.component.BarIconButton
import io.github.esseckers.pursuitofwind.core.designsystem.component.RefreshNoticeBanner
import io.github.esseckers.pursuitofwind.core.designsystem.component.ScreenTopBar
import io.github.esseckers.pursuitofwind.core.designsystem.component.contentEndInsets
import io.github.esseckers.pursuitofwind.core.designsystem.component.screenInsets
import io.github.esseckers.pursuitofwind.core.designsystem.icon.AppIcons
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Sizes
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Spacing
import io.github.esseckers.pursuitofwind.core.map.SpotMap
import io.github.esseckers.pursuitofwind.core.map.SpotMapUi
import io.github.esseckers.pursuitofwind.core.refresh.RefreshNotice
import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.action_back
import io.github.esseckers.pursuitofwind.core.resources.action_refresh
import io.github.esseckers.pursuitofwind.core.resources.banner_spots_failed
import io.github.esseckers.pursuitofwind.core.resources.banner_spots_offline
import io.github.esseckers.pursuitofwind.core.resources.spot_map_description
import io.github.esseckers.pursuitofwind.core.resources.spot_map_loading
import io.github.esseckers.pursuitofwind.core.resources.spot_map_offline
import io.github.esseckers.pursuitofwind.core.resources.spot_map_select
import io.github.esseckers.pursuitofwind.core.resources.spot_map_title
import io.github.esseckers.pursuitofwind.core.resources.spot_map_title_edit
import io.github.esseckers.pursuitofwind.feature.spotmap.presentation.model.SpotMapIntent
import io.github.esseckers.pursuitofwind.feature.spotmap.presentation.model.SpotMapState
import org.jetbrains.compose.resources.stringResource

/**
 * The map of spots with the panel under it that says what to do next. Stateless; `SpotMapRoute` connects it to its
 * ViewModel. [map] draws the map, MapLibre on a phone and a scripted one in tests.
 */
@Composable
internal fun SpotMapScreen(
    state: SpotMapState,
    map: SpotMap,
    onIntent: (SpotMapIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val editing = state.inputs.editing != null
    Scaffold(
        modifier = modifier,
        topBar = {
            ScreenTopBar(
                title = stringResource(if (editing) Res.string.spot_map_title_edit else Res.string.spot_map_title),
                backLabel = stringResource(Res.string.action_back),
                onBack = onBack,
                actions = {
                    BarIconButton(
                        icon = AppIcons.Refresh,
                        label = stringResource(Res.string.action_refresh),
                        onClick = { onIntent(SpotMapIntent.Refresh) },
                        modifier = Modifier.testTag("spotmap.refresh"),
                    )
                },
            )
        },
        contentWindowInsets = screenInsets(),
    ) { padding ->
        // The keyboard pushes the panel up, so the name being typed stays in sight.
        Box(modifier = Modifier.fillMaxSize().padding(padding).imePadding()) {
            val shown = state.map
            if (shown == null) Loading() else MapWithPanel(state = state, shown = shown, map = map, onIntent = onIntent)
        }
    }
}

/**
 * The map under the notice and the panel. The panel takes at most part of the map's height, so the map stays there to
 * press and tap; a taller panel (a large font, a phone on its side) scrolls. The map keeps its credits above the
 * panel. Screen readers hear what the map is and can pick each spot on it; placing a spot needs the map itself.
 */
@Composable
private fun MapWithPanel(
    state: SpotMapState,
    shown: SpotMapUi,
    map: SpotMap,
    onIntent: (SpotMapIntent) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        var panelHeight by remember { mutableIntStateOf(0) }
        val covered = PaddingValues(bottom = with(LocalDensity.current) { panelHeight.toDp() })
        val description = stringResource(Res.string.spot_map_description)
        val pickable = if (shown.pin == null) shown.markers else emptyList()
        val actions =
            pickable.map { marker ->
                CustomAccessibilityAction(stringResource(Res.string.spot_map_select, marker.name)) {
                    onIntent(SpotMapIntent.SelectSpot(marker.id))
                    true
                }
            }
        map.Show(
            map = shown,
            covered = covered,
            onLongPress = { onIntent(SpotMapIntent.LongPress(it)) },
            onTap = { onIntent(SpotMapIntent.Tap(it)) },
            onSpot = { onIntent(SpotMapIntent.SelectSpot(it)) },
            onBaseMap = { onIntent(SpotMapIntent.BaseMap(loaded = it)) },
            modifier =
                Modifier.fillMaxSize().semantics {
                    contentDescription = description
                    customActions = actions
                },
        )
        Bottom(
            state = state,
            onIntent = onIntent,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .heightIn(max = maxHeight * PANEL_MAX_SHARE)
                    .onSizeChanged { panelHeight = it.height },
        )
    }
}

/**
 * What says the map may be missing or old, and the panel, over the bottom of the map; [modifier] caps their height, and
 * they scroll within it.
 */
@Composable
private fun Bottom(
    state: SpotMapState,
    onIntent: (SpotMapIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .widthIn(max = Sizes.contentMaxWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(contentEndInsets())
                .padding(Spacing.m)
                .testTag("spotmap.bottom"),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        if (state.baseMapFailed) {
            RefreshNoticeBanner(
                notice = RefreshNotice.OFFLINE,
                offline = Res.string.spot_map_offline,
                failed = Res.string.spot_map_offline,
                tagPrefix = "spotmap.basemap",
                onRetry = { onIntent(SpotMapIntent.RetryBaseMap) },
            )
        }
        state.notice?.let {
            RefreshNoticeBanner(
                notice = it,
                offline = Res.string.banner_spots_offline,
                failed = Res.string.banner_spots_failed,
                tagPrefix = "spotmap",
                onRetry = { onIntent(SpotMapIntent.Refresh) },
            )
        }
        SpotMapPanelCard(panel = state.panel, onIntent = onIntent)
    }
}

@Composable
private fun Loading() {
    val description = stringResource(Res.string.spot_map_loading)
    Box(
        modifier = Modifier.fillMaxSize().semantics { contentDescription = description }.testTag("spotmap.loading"),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

/** How much of the map's height the notice and the panel may take. */
private const val PANEL_MAX_SHARE = 0.6f
