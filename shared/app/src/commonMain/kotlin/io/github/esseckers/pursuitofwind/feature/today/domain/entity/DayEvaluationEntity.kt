package io.github.esseckers.pursuitofwind.feature.today.domain.entity

import io.github.esseckers.pursuitofwind.engine.model.DayDecision
import io.github.esseckers.pursuitofwind.engine.model.WindSpeed

/**
 * One day as the engine decides it for the rider: the [decision], and on a GO day the lowest and highest consensus mean
 * wind of the hours the rider can actually ride ([windLow], [windHigh]; null when none is left to ride).
 */
internal data class DayEvaluationEntity(
    val decision: DayDecision,
    val windLow: WindSpeed? = null,
    val windHigh: WindSpeed? = null,
)
