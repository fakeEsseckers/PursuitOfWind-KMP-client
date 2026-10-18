package io.github.esseckers.pursuitofwind.engine.internal

import io.github.esseckers.pursuitofwind.engine.model.Confidence
import io.github.esseckers.pursuitofwind.engine.model.HourVerdict
import io.github.esseckers.pursuitofwind.engine.model.SessionWindow
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.DurationUnit

/** Median model spread up to this is HIGH confidence, up to [MEDIUM_MAX_SPREAD_KN] MEDIUM, above that LOW. */
private const val HIGH_MAX_SPREAD_KN: Double = 3.0
private const val MEDIUM_MAX_SPREAD_KN: Double = 6.0

/** Spreads are converted from m/s; this keeps "exactly 3 kn" inside the HIGH bucket. */
private const val SPREAD_TOLERANCE_KN: Double = 1e-9

/** Each bridged MARGINAL hour costs this much window quality, down to [MIN_QUALITY]. */
private const val MARGINAL_HOUR_PENALTY: Double = 0.15
private const val MIN_QUALITY: Double = 0.1

/** Confidence from the median of hourly model spreads in knots. No spreads (single-model data) is UNKNOWN. */
internal fun confidenceForSpreads(spreadsKn: List<Double>): Confidence {
    if (spreadsKn.isEmpty()) return Confidence.UNKNOWN
    val median = median(spreadsKn)
    return when {
        median <= HIGH_MAX_SPREAD_KN + SPREAD_TOLERANCE_KN -> Confidence.HIGH
        median <= MEDIUM_MAX_SPREAD_KN + SPREAD_TOLERANCE_KN -> Confidence.MEDIUM
        else -> Confidence.LOW
    }
}

/** Hours with a single model have no spread and are left out of the median. */
internal fun confidenceOf(hours: List<HourVerdict>): Confidence = confidenceForSpreads(hours.mapNotNull { it.wind.modelSpread?.toKnots() })

/** Middle value; the mean of the two middle values for an even count. */
internal fun median(values: List<Double>): Double {
    require(values.isNotEmpty()) { "Median of nothing is undefined" }
    val sorted = values.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
}

/** `usableHours × quality × confidenceFactor`, where quality is 1.0 − 0.15 per MARGINAL hour, floored at 0.1. */
internal fun windowScore(
    usable: Duration,
    marginalHours: Int,
    confidence: Confidence,
): Double {
    val quality = (1.0 - MARGINAL_HOUR_PENALTY * marginalHours).coerceAtLeast(MIN_QUALITY)
    return usable.toDouble(DurationUnit.HOURS) * quality * confidence.scoreFactor
}

/** Scores that differ by less than one millionth only through rounding count as equal when ranking. */
private const val SCORE_TIE_SCALE: Double = 1e6

/** Best first: higher score, then shorter drive, then earlier start, then spot id. */
internal fun windowRanking(driveTo: (SpotId) -> Duration): Comparator<SessionWindow> =
    compareByDescending<SessionWindow> { (it.score * SCORE_TIE_SCALE).roundToLong() }
        .thenBy { driveTo(it.spotId) }
        .thenBy { it.start }
        .thenBy { it.spotId }
