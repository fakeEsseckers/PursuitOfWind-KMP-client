package io.github.esseckers.pursuitofwind.feature.profile.presentation.viewmodel

import androidx.lifecycle.viewModelScope
import io.github.esseckers.pursuitofwind.core.launchFinishing
import io.github.esseckers.pursuitofwind.core.mvi.MviViewModel
import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.AppLanguage
import io.github.esseckers.pursuitofwind.data.model.Outcome
import io.github.esseckers.pursuitofwind.data.model.Profile
import io.github.esseckers.pursuitofwind.data.model.errorOrNull
import io.github.esseckers.pursuitofwind.data.model.valueOrNull
import io.github.esseckers.pursuitofwind.feature.profile.domain.repository.ProfileEditRepository
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
 * The app's language is not part of the draft: picking one stores it at once. Neither are the alerts about new session
 * windows: turning them on asks the system through [ProfileEffect.AskForNotifications], and its yes stores the choice.
 */
internal class ProfileViewModel(
    private val repository: ProfileEditRepository,
) : MviViewModel<ProfileState, ProfileIntent, ProfileResult, ProfileEffect>(ProfileState(), ProfileReducer) {
    override fun onIntent(intent: ProfileIntent) {
        when (intent) {
            is ProfileIntent.Edit -> dispatchResult(ProfileResult.Edited(intent.edit))
            ProfileIntent.Save -> save()
            is ProfileIntent.ChooseLanguage -> chooseLanguage(intent.language)
            is ProfileIntent.SetAlerts -> if (intent.on) postEffect(ProfileEffect.AskForNotifications) else setAlerts(on = false)
            is ProfileIntent.NotificationsAnswered -> notificationsChecked(intent.allowed, answer = true)
            is ProfileIntent.NotificationsChecked -> notificationsChecked(intent.allowed, answer = false)
            ProfileIntent.OpenNotificationSettings -> postEffect(ProfileEffect.OpenNotificationSettings)
        }
    }

    override fun bind(): Flow<ProfileResult> =
        merge(
            repository.profile.map { ProfileResult.Loaded(it) },
            repository.language.map { ProfileResult.LanguageChanged(it) },
            repository.alerts.map { ProfileResult.AlertsChosen(it) },
        )

    /** The choice is stored and comes back through [bind]; the whole app switches as it is stored. */
    private fun chooseLanguage(language: AppLanguage) {
        viewModelScope.launch { repository.setLanguage(language) }
    }

    /**
     * What the system says about the app's notifications: its [answer] to the rider turning the alerts on, or a look at
     * it when the screen comes back. A yes stores the rider's wish, also one the system refused earlier and now allows,
     * as after a visit to its settings.
     */
    private fun notificationsChecked(
        allowed: Boolean,
        answer: Boolean,
    ) {
        val wanted = answer || currentState.inputs.alertsRefused
        dispatchResult(ProfileResult.NotificationsChecked(allowed, refused = answer && !allowed))
        if (allowed && wanted) setAlerts(on = true)
    }

    /** Like the language, the choice is stored and comes back through [bind]. */
    private fun setAlerts(on: Boolean) {
        viewModelScope.launch { repository.setAlerts(on) }
    }

    /** One save at a time; a tap while the form is loading or saving does nothing. */
    private fun save() {
        if (currentState.loading || currentState.saving) return
        dispatchResult(ProfileResult.Submitted)
        when (val profile = currentState.validProfile) {
            // Not valid: the errors show now.
            null -> Unit

            currentState.inputs.saved -> postEffect(ProfileEffect.Saved)

            else -> store(profile)
        }
    }

    private fun store(profile: Profile) {
        dispatchResult(ProfileResult.SaveStarted)
        viewModelScope.launchFinishing(
            unfinished = Outcome.Failure(AppError.Unknown("Saving did not finish")),
            work = { repository.save(profile) },
        ) { outcome ->
            dispatchResult(ProfileResult.SaveFinished(error = outcome.errorOrNull(), saved = outcome.valueOrNull()))
            if (outcome is Outcome.Success) postEffect(ProfileEffect.Saved)
        }
    }
}
