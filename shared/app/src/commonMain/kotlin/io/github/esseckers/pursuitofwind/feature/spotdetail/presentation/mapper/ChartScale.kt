package io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.mapper

import io.github.esseckers.pursuitofwind.data.model.WindUnit
import io.github.esseckers.pursuitofwind.engine.model.WindSpeed
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.GridLineUi

/** The top of the chart and its horizontal lines, in round values of the rider's unit. */
internal data class ChartScale(
    val maxMs: Double,
    val grid: List<GridLineUi>,
)

/** The chart never zooms in further than this, so a calm day does not look windy. About 16 kn. */
private const val MIN_SCALE_MS = 8.0
private const val KNOTS_STEP = 10
private const val MPS_STEP = 5
private const val KMH_STEP = 20
private const val BEAUFORT_STEP = 2
private const val BEAUFORT_MAX = 12
private const val BEAUFORT_SEARCH_STEP_MS = 0.05

/**
 * A scale that fits wind up to [highestMs]: the top is the first grid line at or above it. Grid lines sit every 10 kn,
 * 5 m/s or 20 km/h; in Beaufort at the lower bound of every second force, where the label changes.
 */
internal fun chartScale(
    highestMs: Double,
    unit: WindUnit,
): ChartScale {
    val target = maxOf(highestMs, MIN_SCALE_MS)
    val lines = mutableListOf<GridLineUi>()
    for (line in gridLines(unit)) {
        lines += line
        if (line.ms >= target) break
    }
    return ChartScale(maxMs = maxOf(lines.last().ms, target), grid = lines)
}

private fun gridLines(unit: WindUnit): Sequence<GridLineUi> =
    when (unit) {
        WindUnit.KNOTS -> steps(KNOTS_STEP).map { GridLineUi(WindSpeed.knots(it.toDouble()).metersPerSecond, it) }
        WindUnit.METERS_PER_SECOND -> steps(MPS_STEP).map { GridLineUi(it.toDouble(), it) }
        WindUnit.KILOMETERS_PER_HOUR -> steps(KMH_STEP).map { GridLineUi(WindSpeed.kmh(it.toDouble()).metersPerSecond, it) }
        WindUnit.BEAUFORT -> steps(BEAUFORT_STEP).takeWhile { it <= BEAUFORT_MAX }.map { GridLineUi(beaufortLowerBoundMs(it), it) }
    }

private fun steps(step: Int): Sequence<Int> = generateSequence(step) { it + step }

/** The lowest wind of Beaufort [force], found from the engine's own scale so the two can never disagree. */
private fun beaufortLowerBoundMs(force: Int): Double {
    var steps = 0
    while (WindSpeed(steps * BEAUFORT_SEARCH_STEP_MS).toBeaufort() < force) steps++
    return steps * BEAUFORT_SEARCH_STEP_MS
}
