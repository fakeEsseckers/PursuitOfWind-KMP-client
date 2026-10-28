package io.github.esseckers.pursuitofwind.feature.spots.presentation.preview

import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.Cached
import io.github.esseckers.pursuitofwind.data.model.GeoPoint
import io.github.esseckers.pursuitofwind.data.model.MySpot
import io.github.esseckers.pursuitofwind.data.model.Spot
import io.github.esseckers.pursuitofwind.engine.model.Bearing
import io.github.esseckers.pursuitofwind.engine.model.Sector
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.RemovedSpot
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.SpotEdit
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.SpotsState
import io.github.esseckers.pursuitofwind.feature.spots.presentation.viewmodel.SpotsReducer
import io.github.esseckers.pursuitofwind.feature.spots.presentation.viewmodel.SpotsResult
import kotlinx.datetime.TimeZone
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * States of the Spots tab for previews and UI tests: Nordstrand Bay (a favorite, 2 h away), Lake Weiss (added by the
 * rider, [RIDER], 45 min) and Salt Lagoon (followed but not a favorite, 2 h 40 min). Built by the real reducer.
 */
internal object SpotsPreviewData {
    const val RIDER = "usr_rider"

    val nordstrand = MySpot(spot("nordstrand-bay", "Nordstrand Bay"), 2.hours, favorite = true)
    val lakeWeiss = MySpot(spot("lake-weiss", "Lake Weiss", createdBy = RIDER), 45.minutes, favorite = true)
    val saltLagoon = MySpot(spot("salt-lagoon", "Salt Lagoon"), 160.minutes, favorite = false)

    private val fetchedAt = Instant.parse("2026-10-24T06:00:00Z")
    private val following = listOf(lakeWeiss, nordstrand, saltLagoon)

    val loading = SpotsState()

    val ready = reduce(SpotsResult.DataChanged(Cached(following, fetchedAt), RIDER), SpotsResult.RefreshFinished(null))

    /** Salt Lagoon just made a favorite, not saved yet. */
    val saving = reduce(SpotsResult.Edited(saltLagoon.spot.id, SpotEdit(driveMinutes = 160, favorite = true)), from = ready)

    /** Nordstrand Bay removed, and back in its place after "Undo" while it is followed again. */
    val restoring =
        reduce(
            SpotsResult.DataChanged(Cached(listOf(lakeWeiss, saltLagoon), fetchedAt), RIDER),
            SpotsResult.RefreshFinished(null),
            SpotsResult.RestoreStarted(RemovedSpot(nordstrand.spot.id, "Nordstrand Bay", 2.hours, favorite = true)),
        )

    /** The saved spots shown while offline. */
    val offline = reduce(SpotsResult.DataChanged(Cached(following, fetchedAt), RIDER), SpotsResult.RefreshFinished(AppError.Network))

    /** The rider follows no spot. */
    val empty = reduce(SpotsResult.DataChanged(Cached(emptyList(), fetchedAt), RIDER), SpotsResult.RefreshFinished(null))

    /** Nothing cached and no connection. */
    val error = reduce(SpotsResult.DataChanged(Cached(emptyList(), null), RIDER), SpotsResult.RefreshFinished(AppError.Network))

    private fun reduce(
        vararg results: SpotsResult,
        from: SpotsState = SpotsState(),
    ): SpotsState = results.fold(from, SpotsReducer::reduce)

    private fun spot(
        id: String,
        name: String,
        createdBy: String = "usr_andrei",
    ): Spot =
        Spot(
            id = SpotId(id),
            name = name,
            location = GeoPoint(54.5, 8.8),
            timeZone = TimeZone.of("Europe/Berlin"),
            rideableSectors = listOf(Sector.of(180.0, 300.0)),
            dangerSectors = emptyList(),
            water = Bearing(240.0),
            createdBy = createdBy,
        )
}
