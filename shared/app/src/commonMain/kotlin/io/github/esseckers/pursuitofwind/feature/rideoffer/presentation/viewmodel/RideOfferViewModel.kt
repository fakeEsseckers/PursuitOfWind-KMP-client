package io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.viewmodel

import androidx.lifecycle.viewModelScope
import io.github.esseckers.pursuitofwind.core.mvi.MviViewModel
import io.github.esseckers.pursuitofwind.core.ui.nextQuarterHour
import io.github.esseckers.pursuitofwind.data.model.Crew
import io.github.esseckers.pursuitofwind.data.model.RidePlan
import io.github.esseckers.pursuitofwind.feature.rideoffer.domain.model.RideDraft
import io.github.esseckers.pursuitofwind.feature.rideoffer.domain.repository.RideOfferRepository
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.model.RideOfferEffect
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.model.RideOfferIntent
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.model.RideOfferState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Offers [draft] to one of the rider's crews in MVI: the crew (the first unless the rider picks another), the free seats
 * and the meeting point. It decides the crew and when the car leaves; [riderZone] is where the rider is. When the offer
 * is out, [RideOfferEffect.Offered] closes the form; a failure stays in the form with its reason.
 */
internal class RideOfferViewModel(
    private val draft: RideDraft,
    private val repository: RideOfferRepository,
    private val clock: Clock,
    riderZone: TimeZone,
    private val tick: Duration = 1.minutes,
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
            repository.crews.map { RideOfferResult.CrewsChanged(it, crewFor(it.value, currentState.inputs.chosen)) },
            repository.spot(draft.spotId).map { RideOfferResult.SpotChanged(it) },
            ticks().map { RideOfferResult.Ticked(it, departure(it)) },
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
        viewModelScope.launch {
            val error = repository.offer(crewId, plan)
            if (error == null) postEffect(RideOfferEffect.Offered) else dispatchResult(RideOfferResult.Failed(error))
        }
    }

    /** The crew the ride goes to: [chosen] while it is still one of [crews], or else the first. */
    private fun crewFor(
        crews: List<Crew>,
        chosen: String?,
    ): String? = (crews.firstOrNull { it.id == chosen } ?: crews.firstOrNull())?.id

    /**
     * When the ride leaves at [now]: as drafted, or once that has passed while the form was open, at the next quarter
     * hour, and never after the window starts.
     */
    private fun departure(now: Instant): Instant =
        if (draft.departAt > now) draft.departAt else minOf(nextQuarterHour(now), draft.windowStart)

    /** The clock's time now and after every [tick], so the departure shown moves on once the drafted one has passed. */
    private fun ticks(): Flow<Instant> =
        flow {
            while (true) {
                emit(clock.now())
                delay(tick)
            }
        }.distinctUntilChanged()
}
