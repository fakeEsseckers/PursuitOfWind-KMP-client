package io.github.esseckers.pursuitofwind.feature.profile.presentation.viewmodel

import androidx.lifecycle.viewModelScope
import io.github.esseckers.pursuitofwind.core.mvi.MviViewModel
import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.Outcome
import io.github.esseckers.pursuitofwind.data.model.Profile
import io.github.esseckers.pursuitofwind.data.model.errorOrNull
import io.github.esseckers.pursuitofwind.data.model.valueOrNull
import io.github.esseckers.pursuitofwind.feature.profile.domain.repository.ProfileEditRepository
import io.github.esseckers.pursuitofwind.feature.profile.presentation.mapper.ProfileFormMapper
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileCheck
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileEffect
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileIntent
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The profile form in MVI, for onboarding and the Profile tab alike. The saved profile comes in while the form is
 * shown; edits change the draft only. [ProfileIntent.Save] checks the draft, saves it when it differs from the saved
 * profile, and confirms with [ProfileEffect.Saved]. A failed save is part of the state, so the rider can try again.
 */
internal class ProfileViewModel(
    private val repository: ProfileEditRepository,
) : MviViewModel<ProfileState, ProfileIntent, ProfileResult, ProfileEffect>(ProfileState(), ProfileReducer) {
    override fun onIntent(intent: ProfileIntent) {
        when (intent) {
            is ProfileIntent.Edit -> dispatch(ProfileResult.Edited(intent.edit))
            ProfileIntent.Save -> save()
        }
    }

    override fun bind(): Flow<ProfileResult> = repository.profile.map { ProfileResult.Loaded(it) }

    /** One save at a time; a tap while the form is loading or saving does nothing. */
    private fun save() {
        if (currentState.loading || currentState.saving) return
        dispatch(ProfileResult.Submitted)
        val profile = (ProfileFormMapper.check(currentState.form) as? ProfileCheck.Valid)?.profile
        when (profile) {
            // Not valid: the errors show now.
            null -> Unit

            currentState.inputs.saved -> post(ProfileEffect.Saved)

            else -> store(profile)
        }
    }

    private fun store(profile: Profile) {
        dispatch(ProfileResult.SaveStarted)
        viewModelScope.launch {
            // Ends the save whatever happens, so a cancelled one never blocks the next.
            var outcome: Outcome<Profile> = Outcome.Failure(AppError.Unknown("Saving did not finish"))
            try {
                outcome = repository.save(profile)
            } finally {
                dispatch(ProfileResult.SaveFinished(error = outcome.errorOrNull(), saved = outcome.valueOrNull()))
            }
            if (outcome is Outcome.Success) post(ProfileEffect.Saved)
        }
    }
}
