package io.github.esseckers.pursuitofwind.feature.profile.presentation.preview

import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.Profile
import io.github.esseckers.pursuitofwind.data.model.WindUnit
import io.github.esseckers.pursuitofwind.engine.model.RiderProfile
import io.github.esseckers.pursuitofwind.engine.model.SkillLevel
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileEdit
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileState
import io.github.esseckers.pursuitofwind.feature.profile.presentation.viewmodel.ProfileReducer
import io.github.esseckers.pursuitofwind.feature.profile.presentation.viewmodel.ProfileResult

/**
 * States of the profile form for previews and UI tests: a new rider, the sample rider Alex (80 kg, intermediate,
 * kites 7 / 9 / 12 m², knots), a form with errors, and a failed save. They are built by the real reducer, so they are
 * what the app would show.
 */
internal object ProfilePreviewData {
    val alex =
        Profile(
            displayName = "Alex",
            rider = RiderProfile(weightKg = 80.0, level = SkillLevel.INTERMEDIATE, quiverM2 = listOf(7.0, 9.0, 12.0)),
            windUnit = WindUnit.KNOTS,
        )

    val loading = ProfileState()

    /** A rider without a profile: an empty form with the defaults. */
    val empty = reduce(ProfileResult.Loaded(null))

    /** Alex's saved profile, nothing changed. */
    val saved = reduce(ProfileResult.Loaded(alex))

    /** Alex's profile with the weight changed, not saved yet. */
    val edited = reduce(ProfileResult.Loaded(alex), ProfileResult.Edited(ProfileEdit.Weight("78")))

    /** A new rider tried to continue without a name, with a weight out of range and a wind range upside down. */
    val invalid =
        reduce(
            ProfileResult.Loaded(null),
            ProfileResult.Edited(ProfileEdit.Weight("250")),
            ProfileResult.Edited(ProfileEdit.MinWind("25")),
            ProfileResult.Edited(ProfileEdit.MaxWind("15")),
            ProfileResult.Submitted,
        )

    /** Saving failed because the device is offline. */
    val saveFailed =
        reduce(
            ProfileResult.Loaded(alex),
            ProfileResult.Edited(ProfileEdit.Weight("78")),
            ProfileResult.Submitted,
            ProfileResult.SaveStarted,
            ProfileResult.SaveFinished(AppError.Network),
        )

    /** Saving right now. */
    val saving =
        reduce(
            ProfileResult.Loaded(alex),
            ProfileResult.Edited(ProfileEdit.Weight("78")),
            ProfileResult.Submitted,
            ProfileResult.SaveStarted,
        )

    private fun reduce(vararg results: ProfileResult): ProfileState = results.fold(ProfileState(), ProfileReducer::reduce)
}
