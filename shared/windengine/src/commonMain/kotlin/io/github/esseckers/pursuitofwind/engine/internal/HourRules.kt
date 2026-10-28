package io.github.esseckers.pursuitofwind.engine.internal

import io.github.esseckers.pursuitofwind.engine.model.ForecastHour
import io.github.esseckers.pursuitofwind.engine.model.HourVerdict
import io.github.esseckers.pursuitofwind.engine.model.HourWind
import io.github.esseckers.pursuitofwind.engine.model.Reason
import io.github.esseckers.pursuitofwind.engine.model.RiderProfile
import io.github.esseckers.pursuitofwind.engine.model.SpotConfig
import io.github.esseckers.pursuitofwind.engine.model.ThunderRisk
import io.github.esseckers.pursuitofwind.engine.model.Verdict
import kotlinx.datetime.toLocalDateTime

/** Gust spread above the rider's tolerance by more than this is NO_GO; by less, MARGINAL. */
private const val GUSTY_NO_GO_EXCESS_KN: Double = 3.0

private const val RAIN_ABOVE_MM: Double = 2.0
private const val COLD_BELOW_C: Double = 5.0

/**
 * Wind limits are inclusive. Wind is averaged across models and converted between units, so values that are equal
 * on paper can differ in the last bits; this tolerance keeps "exactly 28 kn" inside a 28 kn limit.
 */
private const val KNOT_TOLERANCE: Double = 1e-9

private data class RuleResult(
    val verdict: Verdict,
    val reason: Reason,
)

/** Applies every hour rule and the kite sizing; the hour gets the worst result. */
internal fun hourVerdict(
    hour: ForecastHour,
    profile: RiderProfile,
    spot: SpotConfig,
): HourVerdict {
    val wind = consensusWind(hour.byModel)
    val results =
        weatherResults(hour) + timeResults(hour, profile, spot) + directionResults(wind, spot) + windResults(wind, profile.windRange())
    val kite = sizeKite(profile.weightKg, profile.quiverM2, wind.mean, wind.gust)
    return HourVerdict(
        start = hour.start,
        verdict = (results.map { it.verdict } + kite.verdict).max(),
        reasons = (results.map { it.reason } + listOfNotNull(kite.reason)).distinct().sorted(),
        recommendedKiteM2 = kite.kiteM2,
        wind = wind,
    )
}

private fun weatherResults(hour: ForecastHour): List<RuleResult> =
    buildList {
        if (hour.thunderRisk == ThunderRisk.HIGH) add(RuleResult(Verdict.NO_GO, Reason.THUNDERSTORM))
        if (!hour.isDaylight) add(RuleResult(Verdict.NO_GO, Reason.DARK))
        if (hour.precipMm > RAIN_ABOVE_MM) add(RuleResult(Verdict.MARGINAL, Reason.RAIN))
        if (hour.tempC < COLD_BELOW_C) add(RuleResult(Verdict.MARGINAL, Reason.COLD))
    }

/** An hour outside the rider's riding hours, in the spot's local time, is not a riding hour. */
private fun timeResults(
    hour: ForecastHour,
    profile: RiderProfile,
    spot: SpotConfig,
): List<RuleResult> =
    if (hour.start.toLocalDateTime(spot.timeZone) in profile.ridingHours) {
        emptyList()
    } else {
        listOf(RuleResult(Verdict.NO_GO, Reason.OUTSIDE_RIDING_HOURS))
    }

/** A dangerous direction wins over an unrideable one: the hour reports only OFFSHORE_DANGER. */
private fun directionResults(
    wind: HourWind,
    spot: SpotConfig,
): List<RuleResult> =
    when {
        spot.dangerSectors.any { wind.bearing in it } -> listOf(RuleResult(Verdict.NO_GO, Reason.OFFSHORE_DANGER))
        spot.rideableSectors.none { wind.bearing in it } -> listOf(RuleResult(Verdict.NO_GO, Reason.DIRECTION_UNRIDEABLE))
        else -> emptyList()
    }

private fun windResults(
    wind: HourWind,
    range: WindRange,
): List<RuleResult> =
    buildList {
        val meanKn = wind.mean.toKnots()
        if (meanKn > range.max.toKnots() + KNOT_TOLERANCE) add(RuleResult(Verdict.NO_GO, Reason.TOO_STRONG))
        if (meanKn < range.min.toKnots() - KNOT_TOLERANCE) add(RuleResult(Verdict.NO_GO, Reason.TOO_LIGHT))
        val gustExcessKn = wind.gust.toKnots() - meanKn - range.gustTolerance.toKnots()
        when {
            gustExcessKn > GUSTY_NO_GO_EXCESS_KN + KNOT_TOLERANCE -> add(RuleResult(Verdict.NO_GO, Reason.GUSTY))
            gustExcessKn > KNOT_TOLERANCE -> add(RuleResult(Verdict.MARGINAL, Reason.GUSTY))
        }
    }
