package io.github.esseckers.pursuitofwind.engine.internal

import io.github.esseckers.pursuitofwind.engine.model.Bearing
import io.github.esseckers.pursuitofwind.engine.model.Sector
import io.github.esseckers.pursuitofwind.engine.model.ShoreSectors

/** How far either side of the water the wind is still rideable: up to side-shore and a little past it. */
private const val RIDEABLE_HALF_WIDTH_DEGREES = 100.0

/** How far either side of the land the wind is dangerous: offshore and side-offshore. */
private const val DANGER_HALF_WIDTH_DEGREES = 60.0

private const val HALF_TURN_DEGREES = 180.0

internal fun shoreSectors(water: Bearing): ShoreSectors {
    val land = water.degrees + HALF_TURN_DEGREES
    return ShoreSectors(
        rideable = Sector.of(water.degrees - RIDEABLE_HALF_WIDTH_DEGREES, water.degrees + RIDEABLE_HALF_WIDTH_DEGREES),
        danger = Sector.of(land - DANGER_HALF_WIDTH_DEGREES, land + DANGER_HALF_WIDTH_DEGREES),
    )
}
