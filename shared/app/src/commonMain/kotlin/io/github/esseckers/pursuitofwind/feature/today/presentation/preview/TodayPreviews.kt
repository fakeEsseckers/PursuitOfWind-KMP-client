package io.github.esseckers.pursuitofwind.feature.today.presentation.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.esseckers.pursuitofwind.core.designsystem.theme.PursuitOfWindTheme
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.TodayState
import io.github.esseckers.pursuitofwind.feature.today.presentation.view.TodayScreen

// One preview per state of the Today screen, on the data in TodayPreviewData.

@Composable
private fun TodayPreview(
    state: TodayState,
    darkTheme: Boolean = false,
) {
    PursuitOfWindTheme(darkTheme = darkTheme) { TodayScreen(state = state, onIntent = {}) }
}

@Preview
@Composable
private fun TodayReadyPreview() = TodayPreview(TodayPreviewData.ready)

@Preview
@Composable
private fun TodayReadyDarkPreview() = TodayPreview(TodayPreviewData.ready, darkTheme = true)

@Preview
@Composable
private fun TodayOfflinePreview() = TodayPreview(TodayPreviewData.offline)

@Preview
@Composable
private fun TodayLoadingPreview() = TodayPreview(TodayPreviewData.loading)

@Preview
@Composable
private fun TodayNoProfilePreview() = TodayPreview(TodayPreviewData.noProfile)

@Preview
@Composable
private fun TodayErrorDarkPreview() = TodayPreview(TodayPreviewData.error, darkTheme = true)
