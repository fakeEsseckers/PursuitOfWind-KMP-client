package io.github.esseckers.pursuitofwind.feature.profile.presentation.viewmodel

import androidx.lifecycle.viewModelScope
import io.github.esseckers.pursuitofwind.core.mvi.MviViewModel
import io.github.esseckers.pursuitofwind.core.mvi.launchFinishing
import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.AppLanguage
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
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * The profile form in MVI, for onboarding and the Profile tab alike. The saved profile comes in while the form is
 * shown; edits change the draft only. [ProfileIntent.Save] checks the draft, saves it when it differs from the saved
 * profile, and confirms with [ProfileEffect.Saved]. A failed save is part of the state, so the rider can try again.
 * The app's language is not part of the draft: picking one stores it at once.
 */
internal class ProfileViewModel(
    private val repository: ProfileEditRepository,
) : MviViewModel<ProfileState, ProfileIntent, ProfileResult, ProfileEffect>(ProfileState(), ProfileReducer) {
    override fun onIntent(intent: ProfileIntent) {
        when (intent) {
            is ProfileIntent.Edit -> dispatchResult(ProfileResult.Edited(intent.edit))
            ProfileIntent.Save -> save()
            is ProfileIntent.ChooseLanguage -> chooseLanguage(intent.language)
        }
    }

    override fun bind(): Flow<ProfileResult> =
        merge(
            repository.profile.map { ProfileResult.Loaded(it) },
            repository.language.map { ProfileResult.LanguageChanged(it) },
        )

    /** The choice is stored and comes back through [bind]; the whole app switches as it is stored. */
    private fun chooseLanguage(language: AppLanguage) {
        viewModelScope.launch { repository.setLanguage(language) }
    }

    /** One save at a time; a tap while the form is loading or saving does nothing. */
    private fun save() {
        if (currentState.loading || currentState.saving) return
        dispatchResult(ProfileResult.Submitted)
        when (val profile = (ProfileFormMapper.check(currentState.form) as? ProfileCheck.Valid)?.profile) {
            // Not valid: the errors show now.
            null -> Unit

            currentState.inputs.saved -> postEffect(ProfileEffect.Saved)

            else -> store(profile)
        }
    }

    private fun store(profile: Profile) {
        dispatchResult(ProfileResult.SaveStarted)
        viewModelScope.launchFinishing<Outcome<Profile>>(
            unfinished = Outcome.Failure(AppError.Unknown("Saving did not finish")),
            work = { repository.save(profile) },
        ) { outcome ->
            dispatchResult(ProfileResult.SaveFinished(error = outcome.errorOrNull(), saved = outcome.valueOrNull()))
            if (outcome is Outcome.Success) postEffect(ProfileEffect.Saved)
        }
    }
}
