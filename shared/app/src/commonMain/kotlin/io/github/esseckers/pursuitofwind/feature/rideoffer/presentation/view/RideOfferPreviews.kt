package io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.view

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.esseckers.pursuitofwind.core.designsystem.theme.PursuitOfWindTheme
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.model.RideOfferState

// One preview per state of the ride offer form, on the data in RideOfferPreviewData.

@Composable
private fun RideOfferPreview(
    state: RideOfferState,
    darkTheme: Boolean = false,
) {
    PursuitOfWindTheme(darkTheme = darkTheme) { RideOfferScreen(state = state, onIntent = {}, onBack = {}) }
}

@Preview
@Composable
private fun RideOfferEmptyPreview() = RideOfferPreview(RideOfferPreviewData.empty)

@Preview
@Composable
private fun RideOfferFilledDarkPreview() = RideOfferPreview(RideOfferPreviewData.filled, darkTheme = true)

@Preview
@Composable
private fun RideOfferSendingPreview() = RideOfferPreview(RideOfferPreviewData.sending)

@Preview
@Composable
private fun RideOfferFailedPreview() = RideOfferPreview(RideOfferPreviewData.failed)

@Preview
@Composable
private fun RideOfferNoCrewPreview() = RideOfferPreview(RideOfferPreviewData.noCrew)
