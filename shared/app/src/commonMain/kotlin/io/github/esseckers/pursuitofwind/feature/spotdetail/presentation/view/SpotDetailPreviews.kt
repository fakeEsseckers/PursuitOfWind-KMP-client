package io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.view

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.esseckers.pursuitofwind.core.designsystem.theme.PursuitOfWindTheme
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.SpotDetailState

// One preview per state of the spot detail screen, on the data in SpotDetailPreviewData.

@Composable
private fun SpotDetailPreview(
    state: SpotDetailState,
    darkTheme: Boolean = false,
) {
    PursuitOfWindTheme(darkTheme = darkTheme) { SpotDetailScreen(state = state, onIntent = {}, onBack = {}) }
}

@Preview
@Composable
private fun SpotDetailReadyPreview() = SpotDetailPreview(SpotDetailPreviewData.ready)

@Preview
@Composable
private fun SpotDetailReadyDarkPreview() = SpotDetailPreview(SpotDetailPreviewData.ready, darkTheme = true)

@Preview
@Composable
private fun SpotDetailUnverifiedPreview() = SpotDetailPreview(SpotDetailPreviewData.unverified)

@Preview
@Composable
private fun SpotDetailOfflinePreview() = SpotDetailPreview(SpotDetailPreviewData.offline)

@Preview
@Composable
private fun SpotDetailLoadingPreview() = SpotDetailPreview(SpotDetailPreviewData.loading)

@Preview
@Composable
private fun SpotDetailErrorDarkPreview() = SpotDetailPreview(SpotDetailPreviewData.error, darkTheme = true)
