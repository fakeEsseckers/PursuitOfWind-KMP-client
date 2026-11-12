package io.github.esseckers.pursuitofwind.feature.alerts.domain.entity

import io.github.esseckers.pursuitofwind.data.model.Profile
import io.github.esseckers.pursuitofwind.engine.model.SpotForecast

/** What a check decides on: the rider's profile and the favorite spots with the forecasts fetched just now. */
internal data class AlertsSnapshotEntity(
    val profile: Profile,
    val forecasts: List<SpotForecast>,
)
