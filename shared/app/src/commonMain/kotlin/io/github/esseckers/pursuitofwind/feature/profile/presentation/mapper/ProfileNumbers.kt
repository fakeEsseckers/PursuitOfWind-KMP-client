package io.github.esseckers.pursuitofwind.feature.profile.presentation.mapper

import io.github.esseckers.pursuitofwind.data.model.WindUnit
import io.github.esseckers.pursuitofwind.engine.model.WindSpeed

// Numbers in the profile form: what may be typed, how it is read, and the units wind limits are typed in.

/** Up to three digits, then optionally a point or comma and one digit: `80`, `62.5`, `62,`. */
private val NUMBER_INPUT = Regex("""\d{0,3}([.,]\d?)?""")

/** Whether a number field (weight, wind limits) takes [text]: digits with at most one decimal. */
internal fun acceptsNumber(text: String): Boolean = NUMBER_INPUT.matches(text)

/** `62,5` and `62.5` alike; a lone separator (`62.`, `.5`) reads as the rider meant it, the same on every platform. */
internal fun parseNumber(text: String): Double? {
    if (!acceptsNumber(text)) return null
    val number = text.replace(',', '.').removeSuffix(".")
    return (if (number.startsWith('.')) "0$number" else number).toDoubleOrNull()
}

/** The unit personal wind limits are typed in. Beaufort forces are too coarse for a limit, so they use knots. */
internal fun limitUnit(unit: WindUnit): WindUnit = if (unit == WindUnit.BEAUFORT) WindUnit.KNOTS else unit

/** A personal wind limit as typed: none, a speed, or text that is not a number. */
internal sealed interface WindLimit {
    data object Empty : WindLimit

    data object Invalid : WindLimit

    data class Set(
        val speed: WindSpeed,
    ) : WindLimit
}

/** [text] typed in [unit] as a [WindLimit]. */
internal fun parseLimit(
    text: String,
    unit: WindUnit,
): WindLimit {
    if (text.isBlank()) return WindLimit.Empty
    return parseNumber(text)?.let { WindLimit.Set(speedOf(it, unit)) } ?: WindLimit.Invalid
}

internal fun speedOf(
    value: Double,
    unit: WindUnit,
): WindSpeed =
    when (limitUnit(unit)) {
        WindUnit.METERS_PER_SECOND -> WindSpeed(value)
        WindUnit.KILOMETERS_PER_HOUR -> WindSpeed.kmh(value)
        else -> WindSpeed.knots(value)
    }

internal fun WindSpeed.valueIn(unit: WindUnit): Double =
    when (limitUnit(unit)) {
        WindUnit.METERS_PER_SECOND -> metersPerSecond
        WindUnit.KILOMETERS_PER_HOUR -> toKmh()
        else -> toKnots()
    }
