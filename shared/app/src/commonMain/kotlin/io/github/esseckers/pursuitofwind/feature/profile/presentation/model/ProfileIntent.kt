package io.github.esseckers.pursuitofwind.feature.profile.presentation.model

import io.github.esseckers.pursuitofwind.data.model.AppLanguage

/** What the rider can ask of the profile form. */
internal sealed interface ProfileIntent {
    data class Edit(
        val edit: ProfileEdit,
    ) : ProfileIntent

    /** Check the form and save it; with nothing changed, only confirm. */
    data object Save : ProfileIntent

    /** Show the app in [language] from now on. */
    data class ChooseLanguage(
        val language: AppLanguage,
    ) : ProfileIntent
}
