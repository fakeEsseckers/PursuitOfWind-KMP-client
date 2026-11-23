package io.github.esseckers.pursuitofwind.feature.profile.presentation.viewmodel

import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.AppLanguage
import io.github.esseckers.pursuitofwind.data.model.Profile
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.AlertsWish
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

    /** The app's language, as stored. */
    data class LanguageChanged(
        val language: AppLanguage,
    ) : ProfileResult

    /** What the alerts switch follows: the rider's choice, and the system's say on notifications. */
    sealed interface Alerts : ProfileResult

    /** The rider's choice to be told about new session windows, as stored. */
    data class AlertsChosen(
        val chosen: Boolean,
    ) : Alerts

    /** Whether the system shows the app's notifications. */
    data class NotificationsChecked(
        val allowed: Boolean,
    ) : Alerts

    /** The rider's wish for alerts changed: the system is being asked, it said no, or the wish is gone. */
    data class AlertsWished(
        val wish: AlertsWish,
    ) : Alerts

    /** The save ended: [saved] as the server stored it, or failing with [error]. */
    data class SaveFinished(
        val error: AppError?,
        val saved: Profile? = null,
    ) : ProfileResult
}
