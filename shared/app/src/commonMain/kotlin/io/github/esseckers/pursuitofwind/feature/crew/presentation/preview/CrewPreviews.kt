package io.github.esseckers.pursuitofwind.feature.crew.presentation.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.esseckers.pursuitofwind.core.designsystem.theme.PursuitOfWindTheme
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.CrewState
import io.github.esseckers.pursuitofwind.feature.crew.presentation.view.CrewScreen

// One preview per state of the Crew tab, on the data in CrewPreviewData.

@Composable
private fun CrewPreview(
    state: CrewState,
    darkTheme: Boolean = false,
) {
    PursuitOfWindTheme(darkTheme = darkTheme) { CrewScreen(state = state, onIntent = {}) }
}

@Preview
@Composable
private fun CrewReadyPreview() = CrewPreview(CrewPreviewData.ready)

@Preview
@Composable
private fun CrewReadyDarkPreview() = CrewPreview(CrewPreviewData.ready, darkTheme = true)

@Preview
@Composable
private fun CrewTakingSeatPreview() = CrewPreview(CrewPreviewData.takingSeat)

@Preview
@Composable
private fun CrewOfflinePreview() = CrewPreview(CrewPreviewData.offline)

@Preview
@Composable
private fun CrewLoadingPreview() = CrewPreview(CrewPreviewData.loading)

@Preview
@Composable
private fun CrewNoCrewPreview() = CrewPreview(CrewPreviewData.noCrew)

@Preview
@Composable
private fun CrewErrorDarkPreview() = CrewPreview(CrewPreviewData.error, darkTheme = true)

@Preview
@Composable
private fun CrewCreatingPreview() = CrewPreview(CrewPreviewData.creating)

@Preview
@Composable
private fun CrewJoinFailedPreview() = CrewPreview(CrewPreviewData.joinFailed)

@Preview
@Composable
private fun CrewConfirmingCancelPreview() = CrewPreview(CrewPreviewData.confirmingCancel)
