package io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.viewmodel

import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.Cached
import io.github.esseckers.pursuitofwind.data.model.Crew
import io.github.esseckers.pursuitofwind.data.model.Spot
import kotlin.time.Instant

/** What happened in the ride offer form, as the reducer sees it. */
internal sealed interface RideOfferResult {
    data class Ticked(
        val now: Instant,
    ) : RideOfferResult

    data class CrewsChanged(
        val cached: Cached<List<Crew>>,
    ) : RideOfferResult

    data class SpotChanged(
        val spot: Spot?,
    ) : RideOfferResult

    data class Chosen(
        val crewId: String,
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
