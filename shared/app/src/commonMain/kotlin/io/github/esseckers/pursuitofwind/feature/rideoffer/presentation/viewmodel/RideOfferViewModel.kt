package io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.viewmodel

import androidx.lifecycle.viewModelScope
import io.github.esseckers.pursuitofwind.core.mvi.MviViewModel
import io.github.esseckers.pursuitofwind.data.model.RidePlan
import io.github.esseckers.pursuitofwind.feature.rideoffer.domain.model.RideDraft
import io.github.esseckers.pursuitofwind.feature.rideoffer.domain.repository.RideOfferRepository
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.mapper.RideOfferStateMapper
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
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Offers [draft] to one of the rider's crews in MVI, by the form's rules in [mapper]: the crew (the first unless the
 * rider picks another), the free seats and the meeting point. When the offer is out, [RideOfferEffect.Offered] closes
 * the form; a failure stays in the form with its reason.
 */
internal class RideOfferViewModel(
    private val draft: RideDraft,
    private val repository: RideOfferRepository,
    private val mapper: RideOfferStateMapper,
    private val clock: Clock,
    private val tick: Duration = 1.minutes,
) : MviViewModel<RideOfferState, RideOfferIntent, RideOfferResult, RideOfferEffect>(RideOfferState(), RideOfferReducer(mapper)) {
    override fun onIntent(intent: RideOfferIntent) {
        val inputs = currentState.inputs
        when (intent) {
            is RideOfferIntent.ChooseCrew -> dispatch(RideOfferResult.Chosen(intent.crewId))
            RideOfferIntent.FewerSeats -> dispatch(RideOfferResult.SeatsChanged(inputs.seats - 1))
            RideOfferIntent.MoreSeats -> dispatch(RideOfferResult.SeatsChanged(inputs.seats + 1))
            is RideOfferIntent.ChangeMeetingPoint -> dispatch(RideOfferResult.MeetingPointChanged(intent.text))
            RideOfferIntent.Submit -> submit()
        }
    }

    override fun bind(): Flow<RideOfferResult> =
        merge(
            repository.crews.map { RideOfferResult.CrewsChanged(it) },
            repository.spot(draft.spotId).map { RideOfferResult.SpotChanged(it) },
            ticks().map { RideOfferResult.Ticked(it) },
        )

    private fun submit() {
        val inputs = currentState.inputs
        val crew = mapper.crewFor(inputs)
        if (!currentState.canSubmit || crew == null) return
        // The time is read now, not at the last tick, so a ride offered just after the quarter hour does not leave in the
        // past, and the form shows the departure that is sent.
        dispatch(RideOfferResult.Ticked(clock.now()))
        val departAt = mapper.departure(currentState.inputs)
        dispatch(RideOfferResult.Sending)
        val plan = RidePlan(draft.spotId, draft.windowStart, draft.windowEnd, departAt, inputs.meetingPoint.trim(), inputs.seats)
        viewModelScope.launch {
            val error = repository.offer(crew.id, plan)
            if (error == null) post(RideOfferEffect.Offered) else dispatch(RideOfferResult.Failed(error))
        }
    }

    /** The clock's time now and after every [tick], so the departure shown moves on once the drafted one has passed. */
    private fun ticks(): Flow<Instant> =
        flow {
            while (true) {
                emit(clock.now())
                delay(tick)
            }
        }.distinctUntilChanged()
}
