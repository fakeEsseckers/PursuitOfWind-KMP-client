package io.github.esseckers.pursuitofwind.feature.spotmap.domain.entity

import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.Cached
import io.github.esseckers.pursuitofwind.data.model.GeoPoint
import io.github.esseckers.pursuitofwind.data.model.MySpot
import io.github.esseckers.pursuitofwind.data.model.Spot
import io.github.esseckers.pursuitofwind.engine.model.Bearing
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import kotlin.time.Duration

/** What the map shows, as cached right now: every spot riders added, the ones the rider follows, and who the rider is. */
internal data class SpotMapSnapshotEntity(
    val catalog: Cached<List<Spot>>,
    val mySpots: List<MySpot>,
    val userId: String?,
)

/** A spot the rider adds: its [name], where it is, where its [water] lies from there, and the [drive] to follow it with. */
internal data class NewSpotEntity(
    val name: String,
    val location: GeoPoint,
    val water: Bearing,
    val drive: Duration,
)

/** A change to a spot the rider added: its new [name], place and [water]. */
internal data class SpotEditEntity(
    val spotId: SpotId,
    val name: String,
    val location: GeoPoint,
    val water: Bearing,
)

/**
 * How adding a spot went. [spotId] is set once the spot is in the catalog, [error] when a step failed: with both set,
 * the spot was added but not followed yet, so what is left is to follow it.
 */
internal data class AddedSpotEntity(
    val spotId: SpotId?,
    val error: AppError?,
)
