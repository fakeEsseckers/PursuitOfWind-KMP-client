package io.github.esseckers.pursuitofwind.data.model

import io.github.esseckers.pursuitofwind.engine.model.Sector
import io.github.esseckers.pursuitofwind.engine.model.SpotConfig
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import kotlinx.datetime.TimeZone
import kotlin.time.Duration

/** A spot from the catalog. [verified] is false while its sectors have not been checked by someone who rides there. */
public data class Spot(
    val id: SpotId,
    val name: String,
    val location: GeoPoint,
    val timeZone: TimeZone,
    val rideableSectors: List<Sector>,
    val dangerSectors: List<Sector>,
    val verified: Boolean,
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
