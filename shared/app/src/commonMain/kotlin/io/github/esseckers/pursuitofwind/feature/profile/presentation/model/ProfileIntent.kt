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

    /** Tell the rider about new session windows from now on, or stop. Turning it on asks the system first. */
    data class SetAlerts(
        val on: Boolean,
    ) : ProfileIntent

    /**
     * Whether the system shows the app's notifications right now: its answer to [ProfileEffect.AskForNotifications],
     * and what it says each time the screen comes back, also from the system's own dialog or settings.
     */
    data class NotificationsChecked(
        val allowed: Boolean,
    ) : ProfileIntent

    data object OpenNotificationSettings : ProfileIntent
}
