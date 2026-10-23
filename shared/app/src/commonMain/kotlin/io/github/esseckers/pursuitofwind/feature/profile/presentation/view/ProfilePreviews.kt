package io.github.esseckers.pursuitofwind.feature.profile.presentation.view

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.esseckers.pursuitofwind.core.designsystem.theme.PursuitOfWindTheme
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileMode
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileState

// One preview per state of the profile form, on the data in ProfilePreviewData.

@Composable
private fun ProfilePreview(
    state: ProfileState,
    mode: ProfileMode = ProfileMode.TAB,
    darkTheme: Boolean = false,
) {
    PursuitOfWindTheme(darkTheme = darkTheme) { ProfileScreen(state = state, mode = mode, onIntent = {}) }
}

@Preview
@Composable
private fun ProfileSetUpPreview() = ProfilePreview(ProfilePreviewData.empty, mode = ProfileMode.SET_UP)

@Preview
@Composable
private fun ProfileSetUpDarkPreview() = ProfilePreview(ProfilePreviewData.empty, mode = ProfileMode.SET_UP, darkTheme = true)

@Preview
@Composable
private fun ProfileSavedPreview() = ProfilePreview(ProfilePreviewData.saved)

@Preview
@Composable
private fun ProfileEditedDarkPreview() = ProfilePreview(ProfilePreviewData.edited, darkTheme = true)

@Preview
@Composable
private fun ProfileInvalidPreview() = ProfilePreview(ProfilePreviewData.invalid, mode = ProfileMode.SET_UP)

@Preview
@Composable
private fun ProfileSaveFailedPreview() = ProfilePreview(ProfilePreviewData.saveFailed)

@Preview
@Composable
private fun ProfileLoadingPreview() = ProfilePreview(ProfilePreviewData.loading)

@Preview
@Composable
private fun ProfileSavingPreview() = ProfilePreview(ProfilePreviewData.saving)
