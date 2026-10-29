package io.github.esseckers.pursuitofwind.core.ui

import androidx.compose.runtime.Composable
import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.reason_cold
import io.github.esseckers.pursuitofwind.core.resources.reason_dark
import io.github.esseckers.pursuitofwind.core.resources.reason_day_outside_riding_hours
import io.github.esseckers.pursuitofwind.core.resources.reason_direction_unrideable
import io.github.esseckers.pursuitofwind.core.resources.reason_gusty
import io.github.esseckers.pursuitofwind.core.resources.reason_offshore_danger
import io.github.esseckers.pursuitofwind.core.resources.reason_outside_riding_hours
import io.github.esseckers.pursuitofwind.core.resources.reason_overpowered_for_quiver
import io.github.esseckers.pursuitofwind.core.resources.reason_rain
import io.github.esseckers.pursuitofwind.core.resources.reason_thunderstorm
import io.github.esseckers.pursuitofwind.core.resources.reason_too_far
import io.github.esseckers.pursuitofwind.core.resources.reason_too_light
import io.github.esseckers.pursuitofwind.core.resources.reason_too_strong
import io.github.esseckers.pursuitofwind.core.resources.reason_underpowered_for_quiver
import io.github.esseckers.pursuitofwind.core.resources.reason_window_too_short
import io.github.esseckers.pursuitofwind.core.resources.unit_beaufort
import io.github.esseckers.pursuitofwind.core.resources.unit_kilometers_per_hour
import io.github.esseckers.pursuitofwind.core.resources.unit_knots
import io.github.esseckers.pursuitofwind.core.resources.unit_meters_per_second
import io.github.esseckers.pursuitofwind.core.resources.wind_range
import io.github.esseckers.pursuitofwind.core.resources.wind_single
import io.github.esseckers.pursuitofwind.data.model.WindUnit
import io.github.esseckers.pursuitofwind.engine.model.Reason
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Why an hour is not a GO, in plain words. */
@Suppress("CyclomaticComplexMethod") // One branch per reason: the exhaustive when keeps a new reason from going without copy.
internal fun reasonLabel(reason: Reason): StringResource =
    when (reason) {
        Reason.THUNDERSTORM -> Res.string.reason_thunderstorm
        Reason.OFFSHORE_DANGER -> Res.string.reason_offshore_danger
        Reason.DARK -> Res.string.reason_dark
        Reason.OUTSIDE_RIDING_HOURS -> Res.string.reason_outside_riding_hours
        Reason.TOO_STRONG -> Res.string.reason_too_strong
        Reason.OVERPOWERED_FOR_QUIVER -> Res.string.reason_overpowered_for_quiver
        Reason.GUSTY -> Res.string.reason_gusty
        Reason.DIRECTION_UNRIDEABLE -> Res.string.reason_direction_unrideable
        Reason.TOO_LIGHT -> Res.string.reason_too_light
        Reason.UNDERPOWERED_FOR_QUIVER -> Res.string.reason_underpowered_for_quiver
        Reason.RAIN -> Res.string.reason_rain
        Reason.COLD -> Res.string.reason_cold
        Reason.TOO_FAR -> Res.string.reason_too_far
        Reason.WINDOW_TOO_SHORT -> Res.string.reason_window_too_short
    }

/**
 * Why a day is not a GO, in plain words. A day is OUTSIDE_RIDING_HOURS only when its wind was good outside the riding
 * hours, so it says that; every other reason reads as for an hour.
 */
internal fun dayReasonLabel(reason: Reason): StringResource =
    when (reason) {
        Reason.OUTSIDE_RIDING_HOURS -> Res.string.reason_day_outside_riding_hours
        else -> reasonLabel(reason)
    }

internal fun windUnitLabel(unit: WindUnit): StringResource =
    when (unit) {
        WindUnit.KNOTS -> Res.string.unit_knots
        WindUnit.METERS_PER_SECOND -> Res.string.unit_meters_per_second
        WindUnit.KILOMETERS_PER_HOUR -> Res.string.unit_kilometers_per_hour
        WindUnit.BEAUFORT -> Res.string.unit_beaufort
    }

/** `18–22 kn`, or `20 kn` when both ends are the same. */
@Composable
internal fun windRangeText(
    low: Int,
    high: Int,
    unit: WindUnit,
): String {
    val unitLabel = stringResource(windUnitLabel(unit))
    return when (low) {
        high -> stringResource(Res.string.wind_single, low, unitLabel)
        else -> stringResource(Res.string.wind_range, low, high, unitLabel)
    }
}
