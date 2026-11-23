package io.github.esseckers.pursuitofwind.feature.alerts.domain.entity

import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.Profile
import io.github.esseckers.pursuitofwind.engine.model.SpotForecast

/**
 * What a check decides on: the rider's profile and the favorite spots whose forecasts were fetched just now. A favorite
 * whose forecast could not be fetched is not among [forecasts]; why is in [failures], one entry per such spot.
 */
internal data class AlertsSnapshotEntity(
    val profile: Profile,
    val forecasts: List<SpotForecast>,
    val failures: List<AppError> = emptyList(),
)
