package io.github.esseckers.pursuitofwind.feature.spotdetail.domain.entity

import io.github.esseckers.pursuitofwind.data.model.Cached
import io.github.esseckers.pursuitofwind.data.model.MySpot
import io.github.esseckers.pursuitofwind.data.model.Profile
import io.github.esseckers.pursuitofwind.engine.model.ForecastHour

/**
 * Everything the spot detail screen shows, from the cache: the rider's [profile], the followed [spot] (null when the
 * rider does not follow it), and its [forecast] hours.
 */
internal data class SpotDetailSnapshotEntity(
    val profile: Cached<Profile?>,
    val spot: Cached<MySpot?>,
    val forecast: Cached<List<ForecastHour>>,
)
