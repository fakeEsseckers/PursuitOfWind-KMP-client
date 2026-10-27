package io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.viewmodel

import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.Cached
import io.github.esseckers.pursuitofwind.data.model.Crew
import io.github.esseckers.pursuitofwind.data.model.Spot
import kotlin.time.Instant

/** What happened in the ride offer form, as the reducer sees it. */
internal sealed interface RideOfferResult {
    /** The time is [now], and the ride leaves at [departAt]. */
    data class Ticked(
        val now: Instant,
        val departAt: Instant,
    ) : RideOfferResult

    /** The rider's crews are [cached]. */
    data class CrewsChanged(
        val cached: Cached<List<Crew>>,
    ) : RideOfferResult

    data class SpotChanged(
        val spot: Spot?,
    ) : RideOfferResult

    /** The ride goes to the crew [crewId] (null without a crew). */
    data class Chosen(
        val crewId: String?,
    ) : RideOfferResult

    data class SeatsChanged(
        val seats: Int,
    ) : RideOfferResult

    data class MeetingPointChanged(
        val text: String,
    ) : RideOfferResult

    data object Sending : RideOfferResult

    data class Failed(
        val error: AppError,
    ) : RideOfferResult
}
