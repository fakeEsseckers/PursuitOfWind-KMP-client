package io.github.esseckers.pursuitofwind.feature.profile.presentation.viewmodel

import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.Profile
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileEdit

/** What happened, as the reducer sees it. */
internal sealed interface ProfileResult {
    /** The saved profile, null while there is none. */
    data class Loaded(
        val profile: Profile?,
    ) : ProfileResult

    data class Edited(
        val edit: ProfileEdit,
    ) : ProfileResult

    /** The rider tried to save: errors show from now on. */
    data object Submitted : ProfileResult

    data object SaveStarted : ProfileResult

    /** The save ended: [saved] as the server stored it, or failing with [error]. */
    data class SaveFinished(
        val error: AppError?,
        val saved: Profile? = null,
    ) : ProfileResult
}
