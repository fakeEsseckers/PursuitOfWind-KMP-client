package io.github.esseckers.pursuitofwind.feature.spotmap.presentation.preview

import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.Cached
import io.github.esseckers.pursuitofwind.data.model.GeoPoint
import io.github.esseckers.pursuitofwind.data.model.MySpot
import io.github.esseckers.pursuitofwind.data.model.Spot
import io.github.esseckers.pursuitofwind.engine.DecisionEngine
import io.github.esseckers.pursuitofwind.engine.model.Bearing
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.spotmap.domain.entity.SpotMapSnapshotEntity
import io.github.esseckers.pursuitofwind.feature.spotmap.presentation.model.SpotMapInputs
import io.github.esseckers.pursuitofwind.feature.spotmap.presentation.model.SpotMapState
import io.github.esseckers.pursuitofwind.feature.spotmap.presentation.viewmodel.SpotMapReducer
import io.github.esseckers.pursuitofwind.feature.spotmap.presentation.viewmodel.SpotMapResult
import kotlinx.datetime.TimeZone
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * States of the map of spots for previews and tests: three spots on one coast, Nordstrand Bay followed, Dune Point
 * added by the rider ([RIDER]) and Salt Lagoon by someone else. Built by the real reducer.
 */
internal object SpotMapPreviewData {
    const val RIDER = "usr_rider"

    val nordstrand = spot("nordstrand-bay", "Nordstrand Bay", GeoPoint(54.50, 8.80), water = 270.0, createdBy = "usr_andrei")
    val saltLagoon = spot("salt-lagoon", "Salt Lagoon", GeoPoint(54.45, 8.95), water = 0.0, createdBy = "usr_andrei")
    val dune = spot("dune-point", "Dune Point", GeoPoint(54.60, 8.70), water = 315.0, createdBy = RIDER)

    /** Far from every spot, on the same coast. */
    val beach = GeoPoint(54.40, 8.60)

    /** About 200 m west of [beach]: the water. */
    val westOfBeach = GeoPoint(54.40, 8.597)

    /** About 300 m north of Nordstrand Bay: the same beach. */
    val nearNordstrand = GeoPoint(54.5027, 8.80)

    private val fetchedAt = Instant.parse("2026-10-24T06:00:00Z")
    private val catalog = listOf(dune, nordstrand, saltLagoon)
    private val following = listOf(MySpot(nordstrand, 2.hours, favorite = true), MySpot(dune, 1.hours, favorite = true))
    private val loaded = SpotMapResult.DataChanged(SpotMapSnapshotEntity(Cached(catalog, fetchedAt), following, RIDER))

    val loading = SpotMapState()

    /** The spots, nothing placed or tapped yet. */
    val browsing = reduce(loaded, refreshed())

    /** The saved spots while offline, and a map that could not load. */
    val offline = reduce(loaded, refreshed(AppError.Network), SpotMapResult.BaseMap.Loaded(loaded = false))

    /** A pin on a beach without a spot; the water not tapped yet. */
    val pinned = reduce(loaded, refreshed(), SpotMapResult.Placing.LongPressed(beach))

    /** The pin, its water and its name: ready to save. */
    val placed =
        reduce(
            loaded,
            refreshed(),
            SpotMapResult.Placing.LongPressed(beach),
            SpotMapResult.Placing.Tapped(westOfBeach),
            SpotMapResult.Placing.Renamed("Sand Spit"),
        )

    /** Saving the new spot failed. */
    val saveFailed =
        reduce(
            loaded,
            refreshed(),
            SpotMapResult.Placing.LongPressed(beach),
            SpotMapResult.Placing.Tapped(westOfBeach),
            SpotMapResult.Placing.Renamed("Sand Spit"),
            SpotMapResult.Saving.Started,
            SpotMapResult.Saving.Failed(AppError.Network),
        )

    /** The pin landed next to Nordstrand Bay. */
    val nearby = reduce(loaded, refreshed(), SpotMapResult.Placing.LongPressed(nearNordstrand))

    /** Salt Lagoon tapped: someone else's spot, not followed yet. */
    val selected = reduce(loaded, refreshed(), SpotMapResult.Choosing.Selected(saltLagoon.id))

    /** Dune Point tapped: the rider's own spot, followed. */
    val selectedMine = reduce(loaded, refreshed(), SpotMapResult.Choosing.Selected(dune.id))

    /** Dune Point opened to be changed. */
    val editing = reduceFrom(SpotMapState(inputs = SpotMapInputs(editing = dune.id, editOnly = true)), loaded, refreshed())

    private fun refreshed(error: AppError? = null) = SpotMapResult.RefreshFinished(error)

    private fun reduce(vararg results: SpotMapResult): SpotMapState = reduceFrom(SpotMapState(), *results)

    private fun reduceFrom(
        start: SpotMapState,
        vararg results: SpotMapResult,
    ): SpotMapState = results.fold(start, SpotMapReducer::reduce)

    private fun spot(
        id: String,
        name: String,
        location: GeoPoint,
        water: Double,
        createdBy: String,
    ): Spot {
        val sectors = DecisionEngine.sectorsFacing(Bearing(water))
        return Spot(
            id = SpotId(id),
            name = name,
            location = location,
            timeZone = TimeZone.of("Europe/Berlin"),
            rideableSectors = listOf(sectors.rideable),
            dangerSectors = listOf(sectors.danger),
            water = Bearing(water),
            createdBy = createdBy,
        )
    }
}
