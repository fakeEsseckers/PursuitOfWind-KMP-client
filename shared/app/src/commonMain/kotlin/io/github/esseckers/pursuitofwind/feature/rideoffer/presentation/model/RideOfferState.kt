package io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.model

import io.github.esseckers.pursuitofwind.contract.ApiLimits
import io.github.esseckers.pursuitofwind.core.ui.DayLabel
import io.github.esseckers.pursuitofwind.core.ui.TimeRange
import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.Cached
import io.github.esseckers.pursuitofwind.data.model.Crew
import io.github.esseckers.pursuitofwind.data.model.Spot
import org.jetbrains.compose.resources.StringResource
import kotlin.time.Instant

/**
 * The form that offers a ride: the window it is for ([summary], null until the spot and the time are known), the crew it goes to,
 * the free seats and the meeting point. [canSubmit] once there is a crew and a meeting point; [sending] while the offer
 * goes out; [error] when it failed. [noCrew] when the rider belongs to none. [inputs] is what it was computed from.
 */
internal data class RideOfferState(
    val summary: OfferSummary? = null,
    val crews: List<CrewOption> = emptyList(),
    val noCrew: Boolean = false,
    val seats: Int = RideOfferInputs.DEFAULT_SEATS,
    val canFewer: Boolean = true,
    val canMore: Boolean = true,
    val meetingPoint: String = "",
    val canSubmit: Boolean = false,
    val sending: Boolean = false,
    val error: StringResource? = null,
    val inputs: RideOfferInputs = RideOfferInputs(),
)

/** The ride's [day] and riding [window] at [spotName], local to the spot, and the departure in the rider's time zone. */
internal data class OfferSummary(
    val spotName: String,
    val day: DayLabel,
    val window: TimeRange,
    val departAt: String,
)

internal data class CrewOption(
    val id: String,
    val name: String,
    val selected: Boolean,
)

/**
 * What [RideOfferState] is computed from. [now] names the day; [departAt] and [chosen], the crew the ride goes to, are
 * the ViewModel's choice.
 */
internal data class RideOfferInputs(
    val now: Instant? = null,
    val departAt: Instant? = null,
    val crews: Cached<List<Crew>>? = null,
    val spot: Spot? = null,
    val chosen: String? = null,
    val seats: Int = DEFAULT_SEATS,
    val meetingPoint: String = "",
    val sending: Boolean = false,
    val error: AppError? = null,
) {
    companion object {
        const val DEFAULT_SEATS = 3

        /** The API's range of passenger seats. */
        val SEATS = ApiLimits.Ride.SEATS_MIN..ApiLimits.Ride.SEATS_MAX

        /** The API's longest meeting point, in characters as it counts them (`ApiLimits.lengthOf`). */
        const val MAX_MEETING_POINT = ApiLimits.Ride.MEETING_POINT_MAX_LENGTH
    }
}
