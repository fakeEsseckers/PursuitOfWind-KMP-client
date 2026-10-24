package io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.mapper

import io.github.esseckers.pursuitofwind.core.ui.TimeRange
import io.github.esseckers.pursuitofwind.core.ui.clockTime
import io.github.esseckers.pursuitofwind.core.ui.dayLabelOf
import io.github.esseckers.pursuitofwind.core.ui.message
import io.github.esseckers.pursuitofwind.feature.rideoffer.domain.entity.RideDraftEntity
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.model.CrewOption
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.model.OfferSummary
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.model.RideOfferInputs
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.model.RideOfferState
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Computes the ride offer form from its [RideOfferInputs], for [RideDraftEntity]. The crew and the departure are the
 * ViewModel's choice; the form shows them. Times are local to the spot, the departure to the rider ([TimeZone]).
 */
internal object RideOfferStateMapper {
    fun map(
        inputs: RideOfferInputs,
        draft: RideDraftEntity,
        riderZone: TimeZone,
    ): RideOfferState {
        val crews = inputs.crews?.value.orEmpty()
        return RideOfferState(
            summary = summary(inputs, draft, riderZone),
            crews = crews.map { CrewOption(it.id, it.name, selected = it.id == inputs.chosen) },
            noCrew = inputs.crews != null && crews.isEmpty(),
            seats = inputs.seats,
            canFewer = inputs.seats > RideOfferInputs.SEATS.first,
            canMore = inputs.seats < RideOfferInputs.SEATS.last,
            meetingPoint = inputs.meetingPoint,
            canSubmit = inputs.chosen != null && inputs.meetingPoint.isNotBlank() && !inputs.sending,
            sending = inputs.sending,
            error = inputs.error?.message(),
            inputs = inputs,
        )
    }

    /** The day is named as seen from where the rider is now. */
    private fun summary(
        inputs: RideOfferInputs,
        draft: RideDraftEntity,
        riderZone: TimeZone,
    ): OfferSummary? {
        val spot = inputs.spot
        val now = inputs.now
        val departAt = inputs.departAt
        if (spot == null || now == null || departAt == null) return null
        val zone = spot.timeZone
        return OfferSummary(
            spotName = spot.name,
            day = dayLabelOf(draft.windowStart.toLocalDateTime(zone).date, now.toLocalDateTime(riderZone).date),
            window = TimeRange(draft.windowStart.clockTime(zone), draft.windowEnd.clockTime(zone)),
            departAt = departAt.clockTime(riderZone),
        )
    }
}
