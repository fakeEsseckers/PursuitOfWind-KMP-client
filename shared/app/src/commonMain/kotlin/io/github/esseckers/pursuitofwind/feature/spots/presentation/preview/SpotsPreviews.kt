package io.github.esseckers.pursuitofwind.feature.spots.presentation.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.esseckers.pursuitofwind.core.designsystem.theme.PursuitOfWindTheme
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.SpotsState
import io.github.esseckers.pursuitofwind.feature.spots.presentation.view.SpotsScreen

// One preview per state of the Spots tab, on the data in SpotsPreviewData.

@Composable
private fun SpotsPreview(
    state: SpotsState,
    darkTheme: Boolean = false,
) {
    PursuitOfWindTheme(darkTheme = darkTheme) { SpotsScreen(state = state, onIntent = {}) }
}

@Preview
@Composable
private fun SpotsReadyPreview() = SpotsPreview(SpotsPreviewData.ready)

@Preview
@Composable
private fun SpotsReadyDarkPreview() = SpotsPreview(SpotsPreviewData.ready, darkTheme = true)

@Preview
@Composable
private fun SpotsSavingPreview() = SpotsPreview(SpotsPreviewData.saving)

@Preview
@Composable
private fun SpotsOfflinePreview() = SpotsPreview(SpotsPreviewData.offline)

@Preview
@Composable
private fun SpotsLoadingPreview() = SpotsPreview(SpotsPreviewData.loading)

@Preview
@Composable
private fun SpotsEmptyPreview() = SpotsPreview(SpotsPreviewData.empty)

@Preview
@Composable
private fun SpotsErrorDarkPreview() = SpotsPreview(SpotsPreviewData.error, darkTheme = true)
