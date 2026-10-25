package io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.viewmodel

import androidx.lifecycle.viewModelScope
import io.github.esseckers.pursuitofwind.core.launchFinishing
import io.github.esseckers.pursuitofwind.core.mvi.MviViewModel
import io.github.esseckers.pursuitofwind.core.ticks
import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.Crew
import io.github.esseckers.pursuitofwind.data.model.RidePlan
import io.github.esseckers.pursuitofwind.feature.rideoffer.domain.entity.RideDraftEntity
import io.github.esseckers.pursuitofwind.feature.rideoffer.domain.mapper.DepartureMapper
import io.github.esseckers.pursuitofwind.feature.rideoffer.domain.repository.RideOfferRepository
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.model.RideOfferEffect
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.model.RideOfferIntent
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.model.RideOfferState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Offers [draft] to one of the rider's crews in MVI: the crew (the first unless the rider picks another), the free seats
 * and the meeting point. It decides the crew and when the car leaves; [riderZone] is where the rider is. When the offer
 * is out, [RideOfferEffect.Offered] closes the form; a failure stays in the form with its reason.
 */
internal class RideOfferViewModel(
    private val draft: RideDraftEntity,
    private val repository: RideOfferRepository,
    private val clock: Clock,
    riderZone: TimeZone,
) : MviViewModel<RideOfferState, RideOfferIntent, RideOfferResult, RideOfferEffect>(
        RideOfferState(),
        RideOfferReducer(draft, riderZone),
    ) {
    override fun onIntent(intent: RideOfferIntent) {
        val inputs = currentState.inputs
        when (intent) {
            is RideOfferIntent.ChooseCrew -> choose(intent.crewId)
            RideOfferIntent.FewerSeats -> dispatchResult(RideOfferResult.SeatsChanged(inputs.seats - 1))
            RideOfferIntent.MoreSeats -> dispatchResult(RideOfferResult.SeatsChanged(inputs.seats + 1))
            is RideOfferIntent.ChangeMeetingPoint -> dispatchResult(RideOfferResult.MeetingPointChanged(intent.text))
            RideOfferIntent.Submit -> submit()
        }
    }

    override fun bind(): Flow<RideOfferResult> =
        merge(
            repository.crews.map { cached ->
                // Put in the state now, not later with the list, which could arrive after a newer pick and undo it.
                dispatchResult(RideOfferResult.Chosen(crewFor(cached.value, currentState.inputs.chosen)))
                RideOfferResult.CrewsChanged(cached)
            },
            repository.spot(draft.spotId).map { RideOfferResult.SpotChanged(it) },
            clock.ticks(TICK).map { RideOfferResult.Ticked(it, departure(it)) },
        )

    /** Only a crew the rider has: a chip tapped as the list changed may name one they have left. */
    private fun choose(crewId: String) {
        val crews =
            currentState.inputs.crews
                ?.value
                .orEmpty()
        if (crews.any { it.id == crewId }) dispatchResult(RideOfferResult.Chosen(crewId))
    }

    private fun submit() {
        val inputs = currentState.inputs
        val crewId = inputs.chosen
        if (!currentState.canSubmit || crewId == null) return
        // The time is read now, not at the last tick, so a ride offered just after the quarter hour does not leave in the
        // past, and the form shows the departure that is sent.
        val now = clock.now()
        val departAt = departure(now)
        dispatchResult(RideOfferResult.Ticked(now, departAt))
        dispatchResult(RideOfferResult.Sending)
        val plan = RidePlan(draft.spotId, draft.windowStart, draft.windowEnd, departAt, inputs.meetingPoint.trim(), inputs.seats)
        viewModelScope.launchFinishing(
            unfinished = AppError.Unknown("Offering did not finish"),
            work = { repository.offer(crewId, plan) },
        ) { error -> if (error == null) postEffect(RideOfferEffect.Offered) else dispatchResult(RideOfferResult.Failed(error)) }
    }

    /** The crew the ride goes to: [chosen] while it is still one of [crews], or else the first. */
    private fun crewFor(
        crews: List<Crew>,
        chosen: String?,
    ): String? = (crews.firstOrNull { it.id == chosen } ?: crews.firstOrNull())?.id

    /**
     * When the ride leaves at [now]: at the drafted time to leave while it is ahead, else at the next quarter hour, and
     * never after the window starts.
     */
    private fun departure(now: Instant): Instant = DepartureMapper.departure(draft.departAt, draft.windowStart, now)

    private companion object {
        /** How often the time is read again, so the departure shown moves on once the drafted one has passed. */
        val TICK = 1.minutes
    }
}
