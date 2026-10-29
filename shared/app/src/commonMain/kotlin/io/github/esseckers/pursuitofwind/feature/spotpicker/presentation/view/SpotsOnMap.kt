package io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.esseckers.pursuitofwind.core.designsystem.component.ActionButton
import io.github.esseckers.pursuitofwind.core.designsystem.component.RefreshNoticeBanner
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Sizes
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Spacing
import io.github.esseckers.pursuitofwind.core.map.SpotMap
import io.github.esseckers.pursuitofwind.core.map.SpotMapUi
import io.github.esseckers.pursuitofwind.core.refresh.RefreshNotice
import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.banner_spots_failed
import io.github.esseckers.pursuitofwind.core.resources.banner_spots_offline
import io.github.esseckers.pursuitofwind.core.resources.spot_map_description
import io.github.esseckers.pursuitofwind.core.resources.spot_map_offline
import io.github.esseckers.pursuitofwind.core.resources.spot_map_select
import io.github.esseckers.pursuitofwind.core.resources.spots_map_hint
import io.github.esseckers.pursuitofwind.core.resources.spots_open_map
import io.github.esseckers.pursuitofwind.core.resources.spots_view_list
import io.github.esseckers.pursuitofwind.core.resources.spots_view_map
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.PickSpotUi
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.PickerView
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.SpotPickerIntent
import org.jetbrains.compose.resources.stringResource

/** "List | Map": how the catalog shows. */
@Composable
internal fun ViewSwitch(
    view: PickerView,
    onIntent: (SpotPickerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels = mapOf(PickerView.LIST to Res.string.spots_view_list, PickerView.MAP to Res.string.spots_view_map)
    SingleChoiceSegmentedButtonRow(modifier = modifier.widthIn(max = Sizes.contentMaxWidth).fillMaxWidth().testTag("spots.view")) {
        PickerView.entries.forEachIndexed { index, choice ->
            SegmentedButton(
                selected = choice == view,
                onClick = { onIntent(SpotPickerIntent.Show(choice)) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = PickerView.entries.size),
                modifier = Modifier.testTag("spots.view.${choice.name}"),
            ) {
                Text(stringResource(labels.getValue(choice)))
            }
        }
    }
}

/** What the panel under the map says first: a catalog that did not refresh ([catalog]), and a base map that failed. */
internal data class MapNotices(
    val catalog: RefreshNotice?,
    val baseMapFailed: Boolean,
)

/**
 * The catalog on the map, with what was picked on it under the map as in the list ([spot]): a tap on a spot picks it,
 * a tap beside the spots puts it away. The panel takes at most part of the map's height, so the map stays there to
 * look at, and the map keeps its credits above it. Screen readers hear what the map is and can pick each spot on it.
 */
@Composable
internal fun SpotsOnMap(
    shown: SpotMapUi,
    spot: PickSpotUi?,
    notices: MapNotices,
    map: SpotMap,
    onIntent: (SpotPickerIntent) -> Unit,
    onOpenMap: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        var panelHeight by remember { mutableIntStateOf(0) }
        val covered = PaddingValues(bottom = with(LocalDensity.current) { panelHeight.toDp() })
        val description = stringResource(Res.string.spot_map_description)
        val actions =
            shown.markers.map { marker ->
                CustomAccessibilityAction(stringResource(Res.string.spot_map_select, marker.name)) {
                    onIntent(SpotPickerIntent.PickOnMap(marker.id))
                    true
                }
            }
        map.Show(
            map = shown,
            covered = covered,
            onLongPress = {},
            onTap = { onIntent(SpotPickerIntent.TapMap) },
            onSpot = { onIntent(SpotPickerIntent.PickOnMap(it)) },
            onBaseMap = { onIntent(SpotPickerIntent.BaseMap(loaded = it)) },
            modifier =
                Modifier.fillMaxSize().semantics {
                    contentDescription = description
                    customActions = actions
                },
        )
        MapPanel(
            spot = spot,
            notices = notices,
            onIntent = onIntent,
            onOpenMap = onOpenMap,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .heightIn(max = maxHeight * PANEL_MAX_SHARE)
                    .onSizeChanged { panelHeight = it.height },
        )
    }
}

/**
 * Under the map: a catalog that did not refresh and a base map that did not load, then the spot picked on it or, until
 * one is, how to pick one. Screen readers hear the spot once it is picked.
 */
@Composable
private fun MapPanel(
    spot: PickSpotUi?,
    notices: MapNotices,
    onIntent: (SpotPickerIntent) -> Unit,
    onOpenMap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .widthIn(max = Sizes.contentMaxWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.m)
                .testTag("spots.mapPanel"),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        notices.catalog?.let {
            RefreshNoticeBanner(
                notice = it,
                offline = Res.string.banner_spots_offline,
                failed = Res.string.banner_spots_failed,
                tagPrefix = "spots",
                onRetry = { onIntent(SpotPickerIntent.Refresh) },
            )
        }
        if (notices.baseMapFailed) {
            RefreshNoticeBanner(
                notice = RefreshNotice.OFFLINE,
                offline = Res.string.spot_map_offline,
                failed = Res.string.spot_map_offline,
                tagPrefix = "spots.basemap",
                onRetry = { onIntent(SpotPickerIntent.RetryBaseMap) },
            )
        }
        Box(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
            if (spot == null) {
                MapHint(onOpenMap)
            } else {
                SpotRow(
                    spot = spot,
                    onToggle = { onIntent(SpotPickerIntent.Toggle(spot.id)) },
                    onDrive = { step -> onIntent(SpotPickerIntent.ChangeDrive(spot.id, step)) },
                    onCopy = { onIntent(SpotPickerIntent.CopyCoordinates(spot.id)) },
                )
            }
        }
    }
}

/** Until a spot is picked: how to pick one, and where to add one that is missing. */
@Composable
private fun MapHint(onOpenMap: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.testTag("spots.mapHint"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.m),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.spots_map_hint),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            ActionButton(text = stringResource(Res.string.spots_open_map), onClick = onOpenMap, modifier = Modifier.testTag("spots.mapAdd"))
        }
    }
}

/** How much of the map's height the panel may take. */
private const val PANEL_MAX_SHARE = 0.6f
