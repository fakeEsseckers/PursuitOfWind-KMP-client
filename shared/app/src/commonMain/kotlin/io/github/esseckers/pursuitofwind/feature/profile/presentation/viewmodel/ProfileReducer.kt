package io.github.esseckers.pursuitofwind.feature.profile.presentation.viewmodel

import io.github.esseckers.pursuitofwind.core.mvi.Reducer
import io.github.esseckers.pursuitofwind.core.ui.message
import io.github.esseckers.pursuitofwind.feature.profile.presentation.mapper.ProfileFormMapper
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileCheck
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileEdit
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileForm
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileLimits
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileState

/**
 * Folds each result into the form and recomputes what follows from it: the kite chips, the steppers, the errors once
 * the rider has tried to save, the profile to save, and whether anything changed. The first [ProfileResult.Loaded]
 * fills the form; later ones (after a save) only update what the form is compared with, so a draft is never overwritten.
 */
internal object ProfileReducer : Reducer<ProfileState, ProfileResult> {
    override fun reduce(
        state: ProfileState,
        result: ProfileResult,
    ): ProfileState {
        val inputs = state.inputs
        val next =
            when (result) {
                is ProfileResult.Loaded -> {
                    val fill = !inputs.loaded
                    state.copy(
                        loading = false,
                        form = if (fill) result.profile?.let(ProfileFormMapper::formOf) ?: ProfileForm() else state.form,
                        formVersion = if (fill) state.formVersion + 1 else state.formVersion,
                        inputs = inputs.copy(saved = result.profile, loaded = true),
                    )
                }

                is ProfileResult.Edited -> {
                    val form = ProfileFormMapper.edit(state.form, result.edit)
                    // A new unit rewrites the wind limits; text fields must take the converted values.
                    val rewritten = result.edit is ProfileEdit.Units && form.windLimits() != state.form.windLimits()
                    state.copy(form = form, formVersion = if (rewritten) state.formVersion + 1 else state.formVersion)
                }

                ProfileResult.Submitted -> {
                    state.copy(inputs = inputs.copy(submitted = true))
                }

                ProfileResult.SaveStarted -> {
                    state.copy(saving = true, saveError = null)
                }

                is ProfileResult.LanguageChanged -> {
                    state.copy(language = result.language)
                }

                is ProfileResult.SaveFinished -> {
                    // The saved profile counts at once, before the cache emits it, so "Save" is not offered again meanwhile.
                    state.copy(
                        saving = false,
                        saveError = result.error?.message(),
                        inputs = inputs.copy(saved = result.saved ?: inputs.saved),
                    )
                }
            }
        return next.derived()
    }

    private fun ProfileForm.windLimits(): Pair<String, String> = minWind to maxWind

    private fun ProfileState.derived(): ProfileState {
        val check = ProfileFormMapper.check(form)
        val valid = (check as? ProfileCheck.Valid)?.profile
        val saved = inputs.saved
        return copy(
            kites = ProfileFormMapper.kiteChoices(form).takeUnless { it == kites } ?: kites,
            minSession = ProfileLimits.MIN_SESSION_STEPS.stepper(form.minSessionMinutes),
            maxDrive = ProfileLimits.MAX_DRIVE_STEPS.stepper(form.maxDriveMinutes),
            errors = if (inputs.submitted) (check as? ProfileCheck.Invalid)?.errors.orEmpty() else emptyMap(),
            validProfile = valid,
            // The same profile typed differently (`62,5` for 62.5 kg) is no change.
            hasChanges = saved == null || (form != ProfileFormMapper.formOf(saved) && valid != saved),
        )
    }
}
