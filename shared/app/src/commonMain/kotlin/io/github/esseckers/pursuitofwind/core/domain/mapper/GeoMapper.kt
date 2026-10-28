package io.github.esseckers.pursuitofwind.core.domain.mapper

import io.github.esseckers.pursuitofwind.data.model.GeoPoint
import io.github.esseckers.pursuitofwind.engine.model.Bearing
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Distances and directions on the Earth taken as a sphere, which is within half a percent of the real thing: plenty
 * for spots a few kilometres apart and for the direction of a spot's water.
 */
internal object GeoMapper {
    /** Mean radius of the Earth. */
    private const val EARTH_RADIUS_M = 6_371_000.0
    private const val HALF_TURN_DEG = 180.0
    private const val FULL_TURN_DEG = 360.0

    /** The great-circle distance from [from] to [to], in metres. */
    fun distanceMeters(
        from: GeoPoint,
        to: GeoPoint,
    ): Double {
        val lat1 = from.latitude.radians()
        val lat2 = to.latitude.radians()
        val dLat = lat2 - lat1
        val dLon = (to.longitude - from.longitude).radians()
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    /** The direction from [from] towards [to], clockwise from true north, as the first step of the shortest way. */
    fun bearing(
        from: GeoPoint,
        to: GeoPoint,
    ): Bearing {
        val lat1 = from.latitude.radians()
        val lat2 = to.latitude.radians()
        val dLon = (to.longitude - from.longitude).radians()
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return Bearing(atan2(y, x).degrees())
    }

    /** [degrees] of longitude, however many turns east or west, in [-180, 180). */
    fun normalizeLongitude(degrees: Double): Double {
        val turned = (degrees + HALF_TURN_DEG) % FULL_TURN_DEG
        return (if (turned < 0) turned + FULL_TURN_DEG else turned) - HALF_TURN_DEG
    }

    private fun Double.radians(): Double = this * PI / HALF_TURN_DEG

    private fun Double.degrees(): Double = this * HALF_TURN_DEG / PI
}
