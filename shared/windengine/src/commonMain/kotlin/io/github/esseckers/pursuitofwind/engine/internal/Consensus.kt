package io.github.esseckers.pursuitofwind.engine.internal

import io.github.esseckers.pursuitofwind.engine.model.Bearing
import io.github.esseckers.pursuitofwind.engine.model.HourWind
import io.github.esseckers.pursuitofwind.engine.model.ModelId
import io.github.esseckers.pursuitofwind.engine.model.ModelWind
import io.github.esseckers.pursuitofwind.engine.model.WindSpeed
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Below this length the sum of the models' unit direction vectors is treated as zero: the models point in opposite
 * directions and their mean direction is undefined.
 */
private const val UNDEFINED_RESULTANT: Double = 1e-6

private const val DEGREES_PER_RADIAN: Double = 180.0 / PI

/** Consensus of all models: arithmetic mean of means and of gusts, vector mean of directions, spread of means. */
internal fun consensusWind(byModel: Map<ModelId, ModelWind>): HourWind {
    val winds = byModel.values
    val means = winds.map { it.mean.metersPerSecond }
    return HourWind(
        mean = WindSpeed(means.average()),
        gust = WindSpeed(winds.map { it.gust.metersPerSecond }.average()),
        bearing = vectorMeanBearing(byModel),
        modelSpread = if (winds.size < 2) null else WindSpeed(means.max() - means.min()),
    )
}

/**
 * Mean direction as the direction of the sum of unit vectors, so 350° and 10° average to 0°, not 180°.
 * When every model reports the same direction it is returned as is: trigonometry would turn 60° into 59.99999999999999°.
 * When the vectors cancel out, the direction of the highest-priority model present wins ([ModelId.PRIORITY]),
 * then the model whose id sorts first, so the result never depends on map order.
 */
internal fun vectorMeanBearing(byModel: Map<ModelId, ModelWind>): Bearing =
    byModel.values
        .map { it.bearing }
        .distinct()
        .singleOrNull() ?: resultantBearing(byModel) ?: priorityBearing(byModel)

/** Direction of the sum of unit vectors, or null when they cancel out. */
private fun resultantBearing(byModel: Map<ModelId, ModelWind>): Bearing? {
    var east = 0.0
    var north = 0.0
    for (wind in byModel.values) {
        val radians = wind.bearing.degrees / DEGREES_PER_RADIAN
        east += sin(radians)
        north += cos(radians)
    }
    return if (hypot(east, north) < UNDEFINED_RESULTANT) null else Bearing(atan2(east, north) * DEGREES_PER_RADIAN)
}

private fun priorityBearing(byModel: Map<ModelId, ModelWind>): Bearing {
    val model = ModelId.PRIORITY.firstOrNull { it in byModel } ?: byModel.keys.minBy { it.value }
    return byModel.getValue(model).bearing
}
