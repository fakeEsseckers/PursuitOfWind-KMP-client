package io.github.esseckers.pursuitofwind.feature.spotdetail.domain.entity

import io.github.esseckers.pursuitofwind.engine.model.ForecastHour
import io.github.esseckers.pursuitofwind.engine.model.HourVerdict
import io.github.esseckers.pursuitofwind.engine.model.SessionWindow
import kotlinx.datetime.TimeZone

/**
 * One spot's forecast as the engine judges it for the rider: the [hours] to show (oldest first), the [verdicts] of
 * those hours in the same order, the session [windows] found in them, and the spot's time [zone].
 */
internal data class SpotEvaluationEntity(
    val hours: List<ForecastHour>,
    val verdicts: List<HourVerdict>,
    val windows: List<SessionWindow>,
    val zone: TimeZone,
)
