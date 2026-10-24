package io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.view

import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.Cached
import io.github.esseckers.pursuitofwind.data.model.Crew
import io.github.esseckers.pursuitofwind.data.model.GeoPoint
import io.github.esseckers.pursuitofwind.data.model.Spot
import io.github.esseckers.pursuitofwind.engine.model.Sector
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.rideoffer.domain.model.RideDraft
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.model.RideOfferState
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.viewmodel.RideOfferReducer
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.viewmodel.RideOfferResult
import kotlinx.datetime.TimeZone
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * States of the ride offer form for previews and UI tests: Alex offers a ride to Nordstrand Bay on Saturday, 13:00 to
 * 17:00 Berlin time, leaving at 10:30, seen on Thursday. Built by the real reducer.
 */
internal object RideOfferPreviewData {
    val zone: TimeZone = TimeZone.of("Europe/Berlin")
    private val now = Instant.parse("2026-10-22T06:00:00Z")

    val draft =
        RideDraft(
            spotId = SpotId("nordstrand-bay"),
            windowStart = Instant.parse("2026-10-24T11:00:00Z"),
            windowEnd = Instant.parse("2026-10-24T15:00:00Z"),
            departAt = Instant.parse("2026-10-24T08:30:00Z"),
        )

    val nordstrand =
        Spot(SpotId("nordstrand-bay"), "Nordstrand Bay", GeoPoint(54.5, 8.8), zone, listOf(Sector.of(180.0, 300.0)), emptyList(), true)
    val weekendCrew = Crew("crw_weekend", "Weekend Crew", "WIND-4KQ7", memberCount = 4, createdAt = now - 30.days)
    val nightOwls = Crew("crw_owls", "Night Owls", "WIND-7HPX", memberCount = 2, createdAt = now - 3.days)

    val empty =
        reduce(
            RideOfferResult.Ticked(now, draft.departAt),
            RideOfferResult.CrewsChanged(Cached(listOf(weekendCrew, nightOwls), now), chosen = weekendCrew.id),
            RideOfferResult.SpotChanged(nordstrand),
        )

    val filled = reduce(RideOfferResult.MeetingPointChanged("Hauptbahnhof, north exit"), RideOfferResult.SeatsChanged(2), from = empty)

    val sending = reduce(RideOfferResult.Sending, from = filled)

    val failed = reduce(RideOfferResult.Failed(AppError.Network), from = sending)

    val noCrew =
        reduce(
            RideOfferResult.Ticked(now, draft.departAt),
            RideOfferResult.CrewsChanged(Cached(emptyList(), now), chosen = null),
            RideOfferResult.SpotChanged(nordstrand),
        )

    private fun reduce(
        vararg results: RideOfferResult,
        from: RideOfferState = RideOfferState(),
    ): RideOfferState = results.fold(from, RideOfferReducer(draft, zone)::reduce)
}
