package io.github.esseckers.pursuitofwind.core.map

import io.github.esseckers.pursuitofwind.data.model.GeoPoint
import io.github.esseckers.pursuitofwind.engine.model.Bearing
import io.github.esseckers.pursuitofwind.engine.model.ShoreSectors
import io.github.esseckers.pursuitofwind.engine.model.SpotId

/**
 * What a [SpotMap] draws: the spots of the catalog, and while the rider places a spot, its [pin], an arrow towards its
 * [water] and the [sectors] that follow from it. The map opens at [start]; each [attempt] loads the base map again.
 */
internal data class SpotMapUi(
    val markers: List<SpotMarkerUi> = emptyList(),
    val pin: GeoPoint? = null,
    val water: Bearing? = null,
    val sectors: ShoreSectors? = null,
    val start: MapCameraUi = MapCameraUi.WORLD,
    val attempt: Int = 0,
)

/** A spot of the catalog on the map; the rider's favorites ([followed]) stand out, the [selected] one most. */
internal data class SpotMarkerUi(
    val id: SpotId,
    val name: String,
    val location: GeoPoint,
    val followed: Boolean,
    val selected: Boolean,
)

/** Where the map looks: its [center] and how far in, from 0 (the whole world) to about 20 (a street). */
internal data class MapCameraUi(
    val center: GeoPoint,
    val zoom: Double,
) {
    companion object {
        /** The whole world, for a rider with no spots to start from. */
        val WORLD: MapCameraUi = MapCameraUi(GeoPoint(latitude = 30.0, longitude = 10.0), zoom = 1.5)

        /** Close enough to see a beach and the water by it. */
        const val SPOT_ZOOM: Double = 13.0

        /** A region around one of the rider's spots. */
        const val REGION_ZOOM: Double = 8.0
    }
}
