package io.github.esseckers.pursuitofwind.feature.profile.presentation.mapper

import io.github.esseckers.pursuitofwind.core.ui.formatTenths
import io.github.esseckers.pursuitofwind.data.model.Profile
import io.github.esseckers.pursuitofwind.data.model.WindUnit
import io.github.esseckers.pursuitofwind.engine.model.RiderProfile
import io.github.esseckers.pursuitofwind.engine.model.WindSpeed
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.KiteChoice
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileCheck
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileEdit
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileError
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileField
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileForm
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileLimits
import kotlin.time.Duration.Companion.minutes

/** Pure rules of the profile form: filling it from a saved profile, applying an edit, and checking it. */
internal object ProfileFormMapper {
    private val WEIGHT_RANGE = ProfileLimits.WEIGHT_MIN_KG..ProfileLimits.WEIGHT_MAX_KG
    private val MIN_SESSION_RANGE = ProfileLimits.MIN_SESSION_MIN_MINUTES..ProfileLimits.MIN_SESSION_MAX_MINUTES
    private val MAX_DRIVE_RANGE = 0..ProfileLimits.MAX_DRIVE_MAX_MINUTES

    /** The form showing [profile] as saved. */
    fun formOf(profile: Profile): ProfileForm {
        val rider = profile.rider
        val limitUnit = limitUnit(profile.windUnit)
        return ProfileForm(
            name = profile.displayName,
            weightKg = formatTenths(rider.weightKg),
            level = rider.level,
            unit = profile.windUnit,
            kitesM2 = rider.quiverM2.toSet(),
            minSessionMinutes = rider.minSession.inWholeMinutes.toInt(),
            maxDriveMinutes = rider.maxDrive.inWholeMinutes.toInt(),
            minWind = rider.minWind?.let { formatTenths(it.valueIn(limitUnit)) }.orEmpty(),
            maxWind = rider.maxWind?.let { formatTenths(it.valueIn(limitUnit)) }.orEmpty(),
            minWindSpeed = rider.minWind,
            maxWindSpeed = rider.maxWind,
        )
    }

    /** [form] after [edit]. Text arrives already filtered by its field (name length, [acceptsNumber]); steppers are clamped. */
    fun edit(
        form: ProfileForm,
        edit: ProfileEdit,
    ): ProfileForm =
        when (edit) {
            is ProfileEdit.Name -> form.copy(name = edit.text)
            is ProfileEdit.Weight -> form.copy(weightKg = edit.text)
            is ProfileEdit.Level -> form.copy(level = edit.level)
            is ProfileEdit.Units -> withUnit(form, edit.unit)
            is ProfileEdit.Kite -> withKite(form, edit.sizeM2, edit.owned)
            is ProfileEdit.MinSession -> form.copy(minSessionMinutes = edit.minutes.coerceIn(MIN_SESSION_RANGE))
            is ProfileEdit.MaxDrive -> form.copy(maxDriveMinutes = edit.minutes.coerceIn(MAX_DRIVE_RANGE))
            is ProfileEdit.MinWind -> form.copy(minWind = edit.text, minWindSpeed = null)
            is ProfileEdit.MaxWind -> form.copy(maxWind = edit.text, maxWindSpeed = null)
        }

    /** The kite sizes to offer: the common ones and the rider's own, in order. Unowned sizes are off once the quiver is full. */
    fun kiteChoices(form: ProfileForm): List<KiteChoice> {
        val full = form.kitesM2.size >= ProfileLimits.MAX_KITES
        return (ProfileLimits.KITE_SIZES_M2 + form.kitesM2).distinct().sorted().map { size ->
            val owned = size in form.kitesM2
            KiteChoice(sizeM2 = size, owned = owned, enabled = owned || !full)
        }
    }

    /** The profile to save, or every field that needs fixing. */
    fun check(form: ProfileForm): ProfileCheck {
        val errors = mutableMapOf<ProfileField, ProfileError>()
        val name = form.name.trim()
        if (name.isEmpty()) errors[ProfileField.NAME] = ProfileError.NAME_MISSING
        val weight = parseNumber(form.weightKg)
        when (weight) {
            null -> errors[ProfileField.WEIGHT] = ProfileError.WEIGHT_MISSING
            !in WEIGHT_RANGE -> errors[ProfileField.WEIGHT] = ProfileError.WEIGHT_OUT_OF_RANGE
        }
        val (minWind, maxWind) = windLimits(form, errors)
        if (errors.isNotEmpty() || weight == null) return ProfileCheck.Invalid(errors)
        return ProfileCheck.Valid(
            Profile(
                displayName = name,
                rider =
                    RiderProfile(
                        weightKg = weight,
                        level = form.level,
                        quiverM2 = form.kitesM2.sorted(),
                        minWind = (minWind as? WindLimit.Set)?.speed,
                        maxWind = (maxWind as? WindLimit.Set)?.speed,
                        minSession = form.minSessionMinutes.minutes,
                        maxDrive = form.maxDriveMinutes.minutes,
                    ),
                windUnit = form.unit,
            ),
        )
    }

    /** The personal wind limits, each its exact speed or as typed; what is wrong with them goes into [errors]. */
    private fun windLimits(
        form: ProfileForm,
        errors: MutableMap<ProfileField, ProfileError>,
    ): Pair<WindLimit, WindLimit> {
        val limitUnit = limitUnit(form.unit)
        val minWind = form.minWindSpeed?.let { WindLimit.Set(it) } ?: parseLimit(form.minWind, limitUnit)
        val maxWind = form.maxWindSpeed?.let { WindLimit.Set(it) } ?: parseLimit(form.maxWind, limitUnit)
        if (minWind == WindLimit.Invalid) errors[ProfileField.MIN_WIND] = ProfileError.WIND_NOT_A_NUMBER
        if (maxWind == WindLimit.Invalid) errors[ProfileField.MAX_WIND] = ProfileError.WIND_NOT_A_NUMBER
        if (minWind is WindLimit.Set && maxWind is WindLimit.Set && minWind.speed >= maxWind.speed) {
            errors[ProfileField.MAX_WIND] = ProfileError.WIND_NOT_ABOVE_MIN
        }
        return minWind to maxWind
    }

    private fun withUnit(
        form: ProfileForm,
        unit: WindUnit,
    ): ProfileForm {
        val from = limitUnit(form.unit)
        val to = limitUnit(unit)
        val (minWind, minSpeed) = convert(form.minWind, form.minWindSpeed, from, to)
        val (maxWind, maxSpeed) = convert(form.maxWind, form.maxWindSpeed, from, to)
        return form.copy(unit = unit, minWind = minWind, maxWind = maxWind, minWindSpeed = minSpeed, maxWindSpeed = maxSpeed)
    }

    /**
     * A limit in [to] instead of [from], with the exact speed it stands for. The speed is the one it was written from, or
     * the typed number; text that is not a number stays as it is, for the rider to fix.
     */
    private fun convert(
        text: String,
        exact: WindSpeed?,
        from: WindUnit,
        to: WindUnit,
    ): Pair<String, WindSpeed?> {
        val speed = exact ?: (parseLimit(text, from) as? WindLimit.Set)?.speed
        val converted = if (from != to && speed != null) formatTenths(speed.valueIn(to)) else text
        return converted to speed
    }

    private fun withKite(
        form: ProfileForm,
        sizeM2: Double,
        owned: Boolean,
    ): ProfileForm =
        when {
            !owned -> form.copy(kitesM2 = form.kitesM2 - sizeM2)
            form.kitesM2.size >= ProfileLimits.MAX_KITES -> form
            else -> form.copy(kitesM2 = form.kitesM2 + sizeM2)
        }
}
