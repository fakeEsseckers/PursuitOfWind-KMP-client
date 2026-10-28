package io.github.esseckers.pursuitofwind.feature.profile.presentation.model

import io.github.esseckers.pursuitofwind.contract.ApiLimits
import io.github.esseckers.pursuitofwind.core.ui.Step
import io.github.esseckers.pursuitofwind.core.ui.StepRange
import io.github.esseckers.pursuitofwind.data.model.Profile
import io.github.esseckers.pursuitofwind.data.model.WindUnit
import io.github.esseckers.pursuitofwind.engine.model.RiderProfile
import io.github.esseckers.pursuitofwind.engine.model.RidingHours
import io.github.esseckers.pursuitofwind.engine.model.SkillLevel
import io.github.esseckers.pursuitofwind.engine.model.WindSpeed

/**
 * The profile as the rider edits it: text exactly as typed, choices as values. It becomes a [Profile] only through
 * validation (`ProfileState.validProfile`), so a half-typed weight never reaches the data layer.
 *
 * @property minWind the personal lower wind limit as typed, in the form's limit unit (`limitUnit`); empty means the
 *   level's default. [maxWind] likewise.
 * @property ridingFromHour the hour riding starts, 0 to 23; [ridingToHour] the hour it ends, up to 24. The steppers keep
 *   the start before the end.
 * @property minWindSpeed the exact speed [minWind] was written from (the saved profile, or a conversion to another
 *   unit); null once the rider types. Checking and converting use it, so switching units back and forth or saving an
 *   untouched limit never rounds it. [maxWindSpeed] likewise.
 */
internal data class ProfileForm(
    val name: String = "",
    val weightKg: String = "",
    val level: SkillLevel = SkillLevel.INTERMEDIATE,
    val unit: WindUnit = WindUnit.KNOTS,
    val kitesM2: Set<Double> = emptySet(),
    val minSessionMinutes: Int = ProfileLimits.DEFAULT_MIN_SESSION_MINUTES,
    val maxDriveMinutes: Int = ProfileLimits.DEFAULT_MAX_DRIVE_MINUTES,
    val ridingFromHour: Int = RidingHours.DEFAULT.fromHour,
    val ridingToHour: Int = RidingHours.DEFAULT.toHour,
    val minWind: String = "",
    val maxWind: String = "",
    val minWindSpeed: WindSpeed? = null,
    val maxWindSpeed: WindSpeed? = null,
)

/** One change the rider makes to the form. */
internal sealed interface ProfileEdit {
    data class Name(
        val text: String,
    ) : ProfileEdit

    data class Weight(
        val text: String,
    ) : ProfileEdit

    data class Level(
        val level: SkillLevel,
    ) : ProfileEdit

    /** Wind shown in [unit]; personal wind limits already typed are converted. */
    data class Units(
        val unit: WindUnit,
    ) : ProfileEdit

    /** The rider owns, or no longer owns, a kite of [sizeM2]. */
    data class Kite(
        val sizeM2: Double,
        val owned: Boolean,
    ) : ProfileEdit

    /** The shortest session worth the drive, one step [step]. */
    data class MinSession(
        val step: Step,
    ) : ProfileEdit

    /** The longest drive the rider takes, one step [step]. */
    data class MaxDrive(
        val step: Step,
    ) : ProfileEdit

    /** The hour riding starts, an hour [step]; it stays before the end. */
    data class RidingFrom(
        val step: Step,
    ) : ProfileEdit

    /** The hour riding ends, an hour [step]; it stays after the start. */
    data class RidingTo(
        val step: Step,
    ) : ProfileEdit

    data class MinWind(
        val text: String,
    ) : ProfileEdit

    data class MaxWind(
        val text: String,
    ) : ProfileEdit
}

/** The fields that can be wrong. Choices (level, unit, kites, steppers) can only hold valid values. */
internal enum class ProfileField {
    NAME,
    WEIGHT,
    MIN_WIND,
    MAX_WIND,
}

internal enum class ProfileError {
    NAME_MISSING,
    WEIGHT_MISSING,
    WEIGHT_OUT_OF_RANGE,
    WIND_NOT_A_NUMBER,
    WIND_NOT_ABOVE_MIN,
}

/** The result of checking a [ProfileForm]: the profile to save, or what to fix. */
internal sealed interface ProfileCheck {
    data class Valid(
        val profile: Profile,
    ) : ProfileCheck

    data class Invalid(
        val errors: Map<ProfileField, ProfileError>,
    ) : ProfileCheck
}

/** A kite size offered in the form, and whether the rider owns it. [enabled] is false once the quiver is full. */
internal data class KiteChoice(
    val sizeM2: Double,
    val owned: Boolean,
    val enabled: Boolean,
)

/** The API's limits of a rider profile (`ApiLimits.RiderProfile`), and the steps the form moves in. */
internal object ProfileLimits {
    /** In characters as the API counts them (`ApiLimits.lengthOf`). */
    const val NAME_MAX_LENGTH = ApiLimits.RiderProfile.DISPLAY_NAME_MAX_LENGTH
    const val WEIGHT_MIN_KG = ApiLimits.RiderProfile.WEIGHT_MIN_KG
    const val WEIGHT_MAX_KG = ApiLimits.RiderProfile.WEIGHT_MAX_KG
    const val MAX_KITES = ApiLimits.RiderProfile.QUIVER_MAX_KITES
    val MIN_SESSION_STEPS =
        StepRange(ApiLimits.RiderProfile.MIN_SESSION_MIN_MINUTES, ApiLimits.RiderProfile.MIN_SESSION_MAX_MINUTES, step = 30)
    val MAX_DRIVE_STEPS = StepRange(min = 0, max = ApiLimits.RiderProfile.MAX_DRIVE_MAX_MINUTES, step = 30)
    val DEFAULT_MIN_SESSION_MINUTES = RiderProfile.DEFAULT_MIN_SESSION.inWholeMinutes.toInt()
    val DEFAULT_MAX_DRIVE_MINUTES = RiderProfile.DEFAULT_MAX_DRIVE.inWholeMinutes.toInt()

    /** Common kite sizes, offered as chips; sizes the rider already owns are added to them. */
    val KITE_SIZES_M2: List<Double> = listOf(5.0, 6.0, 7.0, 8.0, 9.0, 10.0, 11.0, 12.0, 13.0, 14.0, 15.0, 17.0)

    /** The hours riding can start, by the hour: from midnight to an hour before it ends at [toHour]. */
    fun ridingFromSteps(toHour: Int): StepRange = StepRange(min = 0, max = toHour - 1, step = 1)

    /** The hours riding can end, by the hour: from an hour after it starts at [fromHour] to midnight. */
    fun ridingToSteps(fromHour: Int): StepRange = StepRange(min = fromHour + 1, max = ApiLimits.RiderProfile.RIDING_TO_HOUR_MAX, step = 1)
}
