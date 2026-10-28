package io.github.esseckers.pursuitofwind.engine.internal

import io.github.esseckers.pursuitofwind.engine.model.Confidence
import io.github.esseckers.pursuitofwind.engine.model.HourVerdict
import io.github.esseckers.pursuitofwind.engine.model.SessionWindow
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import kotlin.time.Duration

/** Median model spread up to this is HIGH confidence, up to [MEDIUM_MAX_SPREAD_KN] MEDIUM, above that LOW. */
private const val HIGH_MAX_SPREAD_KN: Double = 3.0
private const val MEDIUM_MAX_SPREAD_KN: Double = 6.0

/** Spreads are converted from m/s; this keeps "exactly 3 kn" inside the HIGH bucket. */
private const val SPREAD_TOLERANCE_KN: Double = 1e-9

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

/**
 * Best first: the longer usable riding time, then the higher confidence, then the shorter drive, then the earlier
 * start, then the spot id. Riders want the longest session; confidence only decides between equally long ones.
 */
internal fun windowRanking(driveTo: (SpotId) -> Duration): Comparator<SessionWindow> =
    compareByDescending<SessionWindow> { it.usable }
        .thenByDescending { it.confidence.preference }
        .thenBy { driveTo(it.spotId) }
        .thenBy { it.start }
        .thenBy { it.spotId }
