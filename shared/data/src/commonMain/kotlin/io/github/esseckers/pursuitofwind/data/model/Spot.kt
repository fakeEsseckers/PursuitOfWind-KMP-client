package io.github.esseckers.pursuitofwind.data.model

import io.github.esseckers.pursuitofwind.engine.model.Bearing
import io.github.esseckers.pursuitofwind.engine.model.Sector
import io.github.esseckers.pursuitofwind.engine.model.SpotConfig
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import kotlinx.datetime.TimeZone
import kotlin.time.Duration

/**
 * A spot from the catalog, which riders fill by adding spots. [water] is the direction from the beach to open water;
 * the sectors of a spot a rider added come from it. Only the user [createdBy] can change the spot.
 */
public data class Spot(
    val id: SpotId,
    val name: String,
    val location: GeoPoint,
    val timeZone: TimeZone,
    val rideableSectors: List<Sector>,
    val dangerSectors: List<Sector>,
    val water: Bearing,
    val createdBy: String,
)

public data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
)

/** A spot the rider follows, with the one-way [drive] from home. Only [favorite] spots take part in the daily decision. */
public data class MySpot(
    val spot: Spot,
    val drive: Duration,
    val favorite: Boolean,
) {
    /** The spot as the decision engine sees it. */
    public fun toSpotConfig(): SpotConfig =
        SpotConfig(
            id = spot.id,
            name = spot.name,
            timeZone = spot.timeZone,
            rideableSectors = spot.rideableSectors,
            dangerSectors = spot.dangerSectors,
            drive = drive,
        )
}
