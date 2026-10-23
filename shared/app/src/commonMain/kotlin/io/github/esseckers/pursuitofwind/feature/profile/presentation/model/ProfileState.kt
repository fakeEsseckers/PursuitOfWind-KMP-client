package io.github.esseckers.pursuitofwind.feature.profile.presentation.model

import io.github.esseckers.pursuitofwind.data.model.Profile
import org.jetbrains.compose.resources.StringResource

/**
 * The MVI state of the profile form.
 *
 * @property loading true until the saved profile has been read; the form is filled from it once.
 * @property formVersion changes whenever the form's text changes from outside its text fields (filled from the saved
 *   profile, wind limits converted to a new unit). Text fields keep their own cursor and start over from the form when
 *   it changes.
 * @property errors what to fix, shown once the rider has tried to save.
 * @property hasChanges whether the form differs from the saved profile (always true before there is one).
 * @property saveError why the last save failed; cleared when the next one starts.
 * @property inputs what the reducer computes the rest from. The view does not read it.
 */
internal data class ProfileState(
    val loading: Boolean = true,
    val form: ProfileForm = ProfileForm(),
    val formVersion: Int = 0,
    val kites: List<KiteChoice> = emptyList(),
    val errors: Map<ProfileField, ProfileError> = emptyMap(),
    val hasChanges: Boolean = true,
    val saving: Boolean = false,
    val saveError: StringResource? = null,
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
