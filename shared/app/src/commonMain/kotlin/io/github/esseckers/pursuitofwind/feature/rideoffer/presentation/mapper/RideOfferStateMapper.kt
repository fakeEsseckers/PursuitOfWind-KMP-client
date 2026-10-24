package io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.mapper

import io.github.esseckers.pursuitofwind.core.ui.TimeRange
import io.github.esseckers.pursuitofwind.core.ui.clockTime
import io.github.esseckers.pursuitofwind.core.ui.dayLabelOf
import io.github.esseckers.pursuitofwind.core.ui.message
import io.github.esseckers.pursuitofwind.core.ui.nextQuarterHour
import io.github.esseckers.pursuitofwind.data.model.Crew
import io.github.esseckers.pursuitofwind.feature.rideoffer.domain.model.RideDraft
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.model.CrewOption
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.model.OfferSummary
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.model.RideOfferInputs
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.model.RideOfferState
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/** Pure rules of the ride offer form, for [draft]; the day is named as seen from [today], where the rider is. */
internal class RideOfferStateMapper(
    private val draft: RideDraft,
    private val today: LocalDate,
    private val riderZone: TimeZone,
) {
    fun map(inputs: RideOfferInputs): RideOfferState {
        val crews = inputs.crews?.value.orEmpty()
        val crew = crewFor(inputs)
        return RideOfferState(
            summary = summary(inputs),
            crews = crews.map { CrewOption(it.id, it.name, selected = it.id == crew?.id) },
            noCrew = inputs.crews != null && crews.isEmpty(),
            seats = inputs.seats,
            canFewer = inputs.seats > RideOfferInputs.SEATS.first,
            canMore = inputs.seats < RideOfferInputs.SEATS.last,
            meetingPoint = inputs.meetingPoint,
            canSubmit = crew != null && inputs.meetingPoint.isNotBlank() && !inputs.sending,
            sending = inputs.sending,
            error = inputs.error?.message(),
            inputs = inputs,
        )
    }

    /**
     * When the ride leaves: as drafted, or once that has passed while the form was open, at the next quarter hour, and
     * never after the window starts.
     */
    fun departure(inputs: RideOfferInputs): Instant {
        val now = inputs.now
        return if (now == null || draft.departAt > now) draft.departAt else minOf(nextQuarterHour(now), draft.windowStart)
    }

    /** The crew the ride goes to: the one the rider chose, or else the first. */
    fun crewFor(inputs: RideOfferInputs): Crew? {
        val crews = inputs.crews?.value.orEmpty()
        return crews.firstOrNull { it.id == inputs.chosen } ?: crews.firstOrNull()
    }

    companion object {
        /** The mapper for [draft] as seen at [clock]'s time in [riderZone]. */
        fun at(
            draft: RideDraft,
            clock: Clock,
            riderZone: TimeZone,
        ): RideOfferStateMapper = RideOfferStateMapper(draft, clock.now().toLocalDateTime(riderZone).date, riderZone)
    }

    private fun summary(inputs: RideOfferInputs): OfferSummary? {
        val spot = inputs.spot ?: return null
        val zone = spot.timeZone
        return OfferSummary(
            spotName = spot.name,
            day = dayLabelOf(draft.windowStart.toLocalDateTime(zone).date, today),
            window = TimeRange(draft.windowStart.clockTime(zone), draft.windowEnd.clockTime(zone)),
            departAt = departure(inputs).clockTime(riderZone),
        )
    }
}
