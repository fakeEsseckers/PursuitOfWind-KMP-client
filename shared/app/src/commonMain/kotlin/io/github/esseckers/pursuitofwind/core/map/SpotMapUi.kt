package io.github.esseckers.pursuitofwind.core.map

import io.github.esseckers.pursuitofwind.data.model.GeoPoint
import io.github.esseckers.pursuitofwind.engine.model.Bearing
import io.github.esseckers.pursuitofwind.engine.model.ShoreSectors
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log2

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

/**
 * Where the map looks: its [center] and how far in, from 0 (the whole world) to about 20 (a street). A map that opens
 * here moves, once it knows its size, to bring all of [fit] into sight, no closer than a [REGION_ZOOM] region.
 */
internal data class MapCameraUi(
    val center: GeoPoint,
    val zoom: Double,
    val fit: List<GeoPoint> = emptyList(),
) {
    companion object {
        /** The whole world, for a rider with no spots to start from. */
        val WORLD: MapCameraUi = MapCameraUi(GeoPoint(latitude = 30.0, longitude = 10.0), zoom = 1.5)

        /** Close enough to see a beach and the water by it. */
        const val SPOT_ZOOM: Double = 13.0

        /** A region around one of the rider's spots. */
        const val REGION_ZOOM: Double = 8.0

        /**
         * Degrees of longitude a phone sees at zoom 0, across the narrower side of the map: about 300 dp of a world
         * 512 dp wide. Each zoom level halves it.
         */
        private const val SIGHT_DEG = 210.0

        /** The whole world, as far out as the map goes. */
        private const val WHOLE_WORLD_ZOOM = 0.0

        /** Room around the outermost spots, and for the panel over the bottom of the map. */
        private const val MARGIN_ZOOM = 0.75

        /** Spots closer than this still get a region, not a street. */
        private const val MIN_SPAN_DEG = 0.01
        private const val HALF_TURN_DEG = 180.0

        /**
         * Where to look so that all of [points] are in sight: the map fits them exactly once it knows its size
         * ([fit]), and opens at a first guess for a phone so that move stays small: their middle, about as close as
         * fits them, from the whole world to a [REGION_ZOOM] region around a single spot. Spots on both sides of the
         * date line make a view of the whole world centred between them, which may show neither side.
         */
        fun showing(points: List<GeoPoint>): MapCameraUi {
            if (points.isEmpty()) return WORLD
            val south = points.minOf { it.latitude }
            val north = points.maxOf { it.latitude }
            val west = points.minOf { it.longitude }
            val east = points.maxOf { it.longitude }
            val middle = GeoPoint((south + north) / 2, (west + east) / 2)
            // Mercator stretches latitude by 1 / cos(latitude), so a span far north takes more of the screen.
            val stretch = 1 / cos(middle.latitude * PI / HALF_TURN_DEG)
            val span = maxOf(east - west, (north - south) * stretch, MIN_SPAN_DEG)
            val zoom = log2(SIGHT_DEG / span) - MARGIN_ZOOM
            return MapCameraUi(middle, zoom.coerceIn(WHOLE_WORLD_ZOOM, REGION_ZOOM), fit = points)
        }
    }
}
