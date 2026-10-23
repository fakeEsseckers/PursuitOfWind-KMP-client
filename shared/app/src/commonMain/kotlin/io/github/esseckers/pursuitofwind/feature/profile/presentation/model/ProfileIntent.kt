package io.github.esseckers.pursuitofwind.feature.profile.presentation.model

/** What the rider can ask of the profile form. */
internal sealed interface ProfileIntent {
    data class Edit(
        val edit: ProfileEdit,
    ) : ProfileIntent

    /** Check the form and save it; with nothing changed, only confirm. */
    data object Save : ProfileIntent
}
