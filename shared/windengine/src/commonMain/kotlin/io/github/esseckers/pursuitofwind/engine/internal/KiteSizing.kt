package io.github.esseckers.pursuitofwind.engine.internal

import io.github.esseckers.pursuitofwind.engine.model.Reason
import io.github.esseckers.pursuitofwind.engine.model.Verdict
import io.github.esseckers.pursuitofwind.engine.model.WindSpeed
import kotlin.math.abs

/** Share of the gust spread that counts towards the wind the kite has to handle. */
private const val GUST_WEIGHT: Double = 0.3

/** Empirical factor of the classic "weight / wind" sizing rule (kg, knots → m²). */
private const val SIZING_FACTOR: Double = 2.2

/** A kite fits if it is within this band around the ideal size. */
private const val SMALLEST_FIT: Double = 0.8
private const val LARGEST_FIT: Double = 1.15

/** Sizes closer than this are a tie; also absorbs rounding at the edges of the fit band. */
private const val SIZE_TOLERANCE_M2: Double = 1e-9

/** Kite sizing outcome for one hour. [reason] is null exactly when [verdict] is GO. */
internal data class KiteSizing(
    val kiteM2: Double?,
    val verdict: Verdict,
    val reason: Reason?,
)

private val SKIPPED = KiteSizing(kiteM2 = null, verdict = Verdict.GO, reason = null)

/**
 * Ideal kite size in m² for a rider of [weightKg]: `weightKg / effectiveKn × 2.2`, where
 * `effectiveKn = mean + 0.3 × (gust − mean)` in knots. No wind at all means an infinitely large kite.
 */
internal fun idealKiteM2(
    weightKg: Double,
    mean: WindSpeed,
    gust: WindSpeed,
): Double {
    val meanKn = mean.toKnots()
    val effectiveKn = meanKn + GUST_WEIGHT * (gust.toKnots() - meanKn)
    return if (effectiveKn <= 0.0) Double.POSITIVE_INFINITY else weightKg / effectiveKn * SIZING_FACTOR
}

/**
 * Picks a kite from [quiverM2]. A heuristic for decision support, not safety advice.
 *
 * - Sizes within [0.8 × ideal, 1.15 × ideal] fit; the one closest to ideal wins, the smaller one on a tie. GO.
 * - Nothing fits and ideal is below the smallest kite: NO_GO OVERPOWERED_FOR_QUIVER.
 * - Nothing fits and ideal is above the largest kite: NO_GO UNDERPOWERED_FOR_QUIVER.
 * - Nothing fits and ideal falls into a gap: the largest kite below ideal, MARGINAL UNDERPOWERED_FOR_QUIVER.
 * - Empty quiver: sizing is skipped.
 */
internal fun sizeKite(
    weightKg: Double,
    quiverM2: List<Double>,
    mean: WindSpeed,
    gust: WindSpeed,
): KiteSizing {
    if (quiverM2.isEmpty()) return SKIPPED
    val ideal = idealKiteM2(weightKg, mean, gust)
    val fitting = quiverM2.filter { it >= ideal * SMALLEST_FIT - SIZE_TOLERANCE_M2 && it <= ideal * LARGEST_FIT + SIZE_TOLERANCE_M2 }
    return when {
        fitting.isNotEmpty() -> KiteSizing(closestTo(ideal, fitting), Verdict.GO, null)
        ideal < quiverM2.min() -> KiteSizing(null, Verdict.NO_GO, Reason.OVERPOWERED_FOR_QUIVER)
        ideal > quiverM2.max() -> KiteSizing(null, Verdict.NO_GO, Reason.UNDERPOWERED_FOR_QUIVER)
        else -> KiteSizing(quiverM2.filter { it <= ideal }.max(), Verdict.MARGINAL, Reason.UNDERPOWERED_FOR_QUIVER)
    }
}

/** The size closest to [ideal]; the smaller one when two are equally close. */
private fun closestTo(
    ideal: Double,
    sizes: List<Double>,
): Double {
    val smallestDistance = sizes.minOf { abs(it - ideal) }
    return sizes.filter { abs(it - ideal) - smallestDistance <= SIZE_TOLERANCE_M2 }.min()
}
