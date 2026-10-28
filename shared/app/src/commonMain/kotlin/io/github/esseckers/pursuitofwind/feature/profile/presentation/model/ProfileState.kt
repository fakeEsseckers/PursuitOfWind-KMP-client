package io.github.esseckers.pursuitofwind.feature.profile.presentation.model

import io.github.esseckers.pursuitofwind.core.ui.StepperUi
import io.github.esseckers.pursuitofwind.data.model.AppLanguage
import io.github.esseckers.pursuitofwind.data.model.Profile
import org.jetbrains.compose.resources.StringResource

/**
 * The MVI state of the profile form.
 *
 * @property loading true until the saved profile has been read; the form is filled from it once.
 * @property formVersion changes whenever the form's text changes from outside its text fields (filled from the saved
 *   profile, wind limits converted to a new unit). Text fields keep their own cursor and start over from the form when
 *   it changes.
 * @property minSession the minimum session stepper, in minutes; [maxDrive] likewise for the longest drive.
 * @property ridingFrom the stepper of the hour riding starts; [ridingTo] of the hour it ends.
 * @property errors what to fix, shown once the rider has tried to save.
 * @property validProfile the form as a profile, ready to save; null while a field needs fixing.
 * @property hasChanges whether the form differs from the saved profile (always true before there is one).
 * @property saveError why the last save failed; cleared when the next one starts.
 * @property language the app's language; it is not part of the draft and applies as soon as it is picked.
 * @property inputs what the reducer computes the rest from. The view does not read it.
 */
internal data class ProfileState(
    val loading: Boolean = true,
    val form: ProfileForm = ProfileForm(),
    val formVersion: Int = 0,
    val kites: List<KiteChoice> = emptyList(),
    val minSession: StepperUi = ProfileLimits.MIN_SESSION_STEPS.stepper(form.minSessionMinutes),
    val maxDrive: StepperUi = ProfileLimits.MAX_DRIVE_STEPS.stepper(form.maxDriveMinutes),
    val ridingFrom: StepperUi = ProfileLimits.ridingFromSteps(form.ridingToHour).stepper(form.ridingFromHour),
    val ridingTo: StepperUi = ProfileLimits.ridingToSteps(form.ridingFromHour).stepper(form.ridingToHour),
    val errors: Map<ProfileField, ProfileError> = emptyMap(),
    val validProfile: Profile? = null,
    val hasChanges: Boolean = true,
    val saving: Boolean = false,
    val saveError: StringResource? = null,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val inputs: ProfileInputs = ProfileInputs(),
)

/** The saved profile ([loaded] once it was read, even when there is none), and whether the rider has tried to save. */
internal data class ProfileInputs(
    val saved: Profile? = null,
    val loaded: Boolean = false,
    val submitted: Boolean = false,
)

/** Where the form is shown: as the first step of onboarding, or as the Profile tab. */
internal enum class ProfileMode {
    /** "About you": back to Welcome, and "Continue" to the spots once saved. */
    SET_UP,

    /** "Profile": saved in place, with a short confirmation. */
    TAB,
}
