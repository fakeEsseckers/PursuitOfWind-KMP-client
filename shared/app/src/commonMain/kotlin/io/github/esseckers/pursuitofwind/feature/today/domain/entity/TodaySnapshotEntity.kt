package io.github.esseckers.pursuitofwind.feature.today.domain.entity

import io.github.esseckers.pursuitofwind.data.model.Cached
import io.github.esseckers.pursuitofwind.data.model.MySpot
import io.github.esseckers.pursuitofwind.data.model.Profile
import io.github.esseckers.pursuitofwind.data.model.Ride
import io.github.esseckers.pursuitofwind.engine.model.ForecastHour
import io.github.esseckers.pursuitofwind.engine.model.SpotId

/** Everything Today decides on, as cached right now: profile, followed spots, the favorites' forecasts and rides. */
internal data class TodaySnapshotEntity(
    val profile: Cached<Profile?>,
    val spots: Cached<List<MySpot>>,
    val forecasts: Map<SpotId, Cached<List<ForecastHour>>>,
    val crew: CrewRidesEntity,
)

/** The rides of the rider's crews, and who the rider is, so cards can say "you drive". */
internal data class CrewRidesEntity(
    val rides: List<Ride>,
    val hasCrew: Boolean,
    val userId: String?,
)
