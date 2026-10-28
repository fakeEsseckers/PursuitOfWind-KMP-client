package io.github.esseckers.pursuitofwind.feature.spotmap.presentation.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.esseckers.pursuitofwind.core.designsystem.theme.PursuitOfWindTheme
import io.github.esseckers.pursuitofwind.core.map.MapLibreSpotMap
import io.github.esseckers.pursuitofwind.feature.spotmap.presentation.model.SpotMapState
import io.github.esseckers.pursuitofwind.feature.spotmap.presentation.view.SpotMapScreen

// One preview per panel of the map of spots, on the data in SpotMapPreviewData. MapLibre draws a gray box in a
// preview: the panels are what these show.

@Composable
private fun SpotMapPreview(
    state: SpotMapState,
    darkTheme: Boolean = false,
) {
    PursuitOfWindTheme(darkTheme = darkTheme) { SpotMapScreen(state = state, map = MapLibreSpotMap(), onIntent = {}, onBack = {}) }
}

@Preview
@Composable
private fun SpotMapLoadingPreview() = SpotMapPreview(SpotMapPreviewData.loading)

@Preview
@Composable
private fun SpotMapBrowsingPreview() = SpotMapPreview(SpotMapPreviewData.browsing)

@Preview
@Composable
private fun SpotMapOfflineDarkPreview() = SpotMapPreview(SpotMapPreviewData.offline, darkTheme = true)

@Preview
@Composable
private fun SpotMapPinnedPreview() = SpotMapPreview(SpotMapPreviewData.pinned)

@Preview
@Composable
private fun SpotMapPlacedPreview() = SpotMapPreview(SpotMapPreviewData.placed)

@Preview
@Composable
private fun SpotMapPlacedDarkPreview() = SpotMapPreview(SpotMapPreviewData.placed, darkTheme = true)

@Preview
@Composable
private fun SpotMapSaveFailedPreview() = SpotMapPreview(SpotMapPreviewData.saveFailed)

@Preview
@Composable
private fun SpotMapNearbyPreview() = SpotMapPreview(SpotMapPreviewData.nearby)

@Preview
@Composable
private fun SpotMapSelectedPreview() = SpotMapPreview(SpotMapPreviewData.selected)

@Preview
@Composable
private fun SpotMapSelectedMinePreview() = SpotMapPreview(SpotMapPreviewData.selectedMine)

@Preview
@Composable
private fun SpotMapEditingPreview() = SpotMapPreview(SpotMapPreviewData.editing)
