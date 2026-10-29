package io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.esseckers.pursuitofwind.core.designsystem.theme.PursuitOfWindTheme
import io.github.esseckers.pursuitofwind.core.map.MapLibreSpotMap
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.SpotPickerState
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.view.SpotPickerScreen

// One preview per state of the spot picker, on the data in SpotPickerPreviewData.

@Composable
private fun SpotPickerPreview(
    state: SpotPickerState,
    darkTheme: Boolean = false,
) {
    PursuitOfWindTheme(darkTheme = darkTheme) {
        SpotPickerScreen(state = state, map = MapLibreSpotMap(), onIntent = {}, onBack = {}, onOpenMap = {})
    }
}

@Preview
@Composable
private fun SpotPickerFreshPreview() = SpotPickerPreview(SpotPickerPreviewData.fresh)

@Preview
@Composable
private fun SpotPickerChosenPreview() = SpotPickerPreview(SpotPickerPreviewData.chosen)

@Preview
@Composable
private fun SpotPickerChosenDarkPreview() = SpotPickerPreview(SpotPickerPreviewData.chosen, darkTheme = true)

@Preview
@Composable
private fun SpotPickerOfflinePreview() = SpotPickerPreview(SpotPickerPreviewData.offline)

@Preview
@Composable
private fun SpotPickerLoadingPreview() = SpotPickerPreview(SpotPickerPreviewData.loading)

@Preview
@Composable
private fun SpotPickerEmptyPreview() = SpotPickerPreview(SpotPickerPreviewData.empty)

@Preview
@Composable
private fun SpotPickerErrorDarkPreview() = SpotPickerPreview(SpotPickerPreviewData.error, darkTheme = true)

@Preview
@Composable
private fun SpotPickerOnMapPreview() = SpotPickerPreview(SpotPickerPreviewData.onMap)

@Preview
@Composable
private fun SpotPickerMapOfflineDarkPreview() = SpotPickerPreview(SpotPickerPreviewData.mapOffline, darkTheme = true)

@Preview
@Composable
private fun SpotPickerSaveFailedPreview() = SpotPickerPreview(SpotPickerPreviewData.saveFailed)
