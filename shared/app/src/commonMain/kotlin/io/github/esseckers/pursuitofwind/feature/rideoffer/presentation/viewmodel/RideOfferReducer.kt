package io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.viewmodel

import io.github.esseckers.pursuitofwind.contract.ApiLimits
import io.github.esseckers.pursuitofwind.core.mvi.Reducer
import io.github.esseckers.pursuitofwind.feature.rideoffer.domain.entity.RideDraftEntity
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.mapper.RideOfferStateMapper
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.model.RideOfferInputs
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.model.RideOfferState
import kotlinx.datetime.TimeZone

/** Records each result in the inputs and computes the form from them, for [draft]; [riderZone] is where the rider is. */
internal class RideOfferReducer(
    private val draft: RideDraftEntity,
    private val riderZone: TimeZone,
) : Reducer<RideOfferState, RideOfferResult> {
    override fun reduce(
        state: RideOfferState,
        result: RideOfferResult,
    ): RideOfferState {
        val inputs = state.inputs
        val next =
            when (result) {
                is RideOfferResult.Ticked -> {
                    inputs.copy(now = result.now, departAt = result.departAt)
                }

                is RideOfferResult.CrewsChanged -> {
                    inputs.copy(crews = result.cached)
                }

                is RideOfferResult.SpotChanged -> {
                    inputs.copy(spot = result.spot)
                }

                is RideOfferResult.Chosen -> {
                    inputs.copy(chosen = result.crewId)
                }

                is RideOfferResult.SeatsChanged -> {
                    inputs.copy(seats = result.seats.coerceIn(RideOfferInputs.SEATS))
                }

                is RideOfferResult.MeetingPointChanged -> {
                    inputs.copy(
                        meetingPoint = ApiLimits.cut(result.text, RideOfferInputs.MAX_MEETING_POINT),
                    )
                }

                RideOfferResult.Sending -> {
                    inputs.copy(sending = true, error = null)
                }

                is RideOfferResult.Failed -> {
                    inputs.copy(sending = false, error = result.error)
                }
            }
        return RideOfferStateMapper.map(next, draft, riderZone)
    }
}
