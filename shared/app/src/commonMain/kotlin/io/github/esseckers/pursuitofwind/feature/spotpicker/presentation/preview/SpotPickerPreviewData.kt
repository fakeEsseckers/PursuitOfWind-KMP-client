package io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.preview

import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.Cached
import io.github.esseckers.pursuitofwind.data.model.GeoPoint
import io.github.esseckers.pursuitofwind.data.model.MySpot
import io.github.esseckers.pursuitofwind.data.model.Spot
import io.github.esseckers.pursuitofwind.engine.model.Bearing
import io.github.esseckers.pursuitofwind.engine.model.Sector
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.spotpicker.domain.entity.SpotPickerSnapshotEntity
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.SpotPickerState
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.viewmodel.SpotPickerReducer
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.viewmodel.SpotPickerResult
import kotlinx.datetime.TimeZone
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * States of the spot picker for previews and UI tests: a catalog of four spots, one of them not checked by a local
 * rider yet, with Nordstrand Bay followed as a favorite at 2 h. Built by the real reducer.
 */
internal object SpotPickerPreviewData {
    val nordstrand = spot("nordstrand-bay", "Nordstrand Bay")
    val saltLagoon = spot("salt-lagoon", "Salt Lagoon")
    val lakeWeiss = spot("lake-weiss", "Lake Weiss")
    val dune = spot("dune-point", "Dune Point")

    private val fetchedAt = Instant.parse("2026-10-24T06:00:00Z")
    private val catalog = listOf(dune, lakeWeiss, nordstrand, saltLagoon)
    private val following = listOf(MySpot(nordstrand, 2.hours, favorite = true))

    val loading = SpotPickerState()

    /** A new rider: the catalog, nothing chosen yet. */
    val fresh = reduce(SpotPickerResult.DataChanged(SpotPickerSnapshotEntity(Cached(catalog, fetchedAt), emptyList())), refreshed())

    /** Nordstrand Bay followed, and Lake Weiss just chosen. */
    val chosen =
        reduce(
            SpotPickerResult.DataChanged(SpotPickerSnapshotEntity(Cached(catalog, fetchedAt), following)),
            refreshed(),
            SpotPickerResult.Toggled(lakeWeiss.id),
        )

    /** The saved catalog shown while offline. */
    val offline =
        reduce(
            SpotPickerResult.DataChanged(SpotPickerSnapshotEntity(Cached(catalog, fetchedAt), following)),
            refreshed(AppError.Network),
        )

    /** The catalog came back empty. */
    val empty = reduce(SpotPickerResult.DataChanged(SpotPickerSnapshotEntity(Cached(emptyList(), fetchedAt), emptyList())), refreshed())

    /** Nothing cached and no connection. */
    val error =
        reduce(SpotPickerResult.DataChanged(SpotPickerSnapshotEntity(Cached(emptyList(), null), emptyList())), refreshed(AppError.Network))

    /** Saving the choice failed. */
    val saveFailed =
        reduce(
            SpotPickerResult.DataChanged(SpotPickerSnapshotEntity(Cached(catalog, fetchedAt), following)),
            refreshed(),
            SpotPickerResult.Toggled(lakeWeiss.id),
            SpotPickerResult.SaveStarted,
            SpotPickerResult.SaveFinished(AppError.Server(503)),
        )

    private fun refreshed(error: AppError? = null) = SpotPickerResult.RefreshFinished(error)

    private fun reduce(vararg results: SpotPickerResult): SpotPickerState = results.fold(SpotPickerState(), SpotPickerReducer::reduce)

    private fun spot(
        id: String,
        name: String,
    ): Spot =
        Spot(
            id = SpotId(id),
            name = name,
            location = GeoPoint(54.5, 8.8),
            timeZone = TimeZone.of("Europe/Berlin"),
            rideableSectors = listOf(Sector.of(180.0, 300.0)),
            dangerSectors = emptyList(),
            water = Bearing(240.0),
            createdBy = "usr_andrei",
        )
}
