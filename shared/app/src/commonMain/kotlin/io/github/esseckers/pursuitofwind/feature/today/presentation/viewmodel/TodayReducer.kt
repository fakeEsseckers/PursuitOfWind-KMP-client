package io.github.esseckers.pursuitofwind.feature.today.presentation.viewmodel

import io.github.esseckers.pursuitofwind.core.mvi.Reducer
import io.github.esseckers.pursuitofwind.feature.today.presentation.mapper.TodayStateMapper
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.TodayState
import kotlinx.datetime.TimeZone

/**
 * Records each result in the state's inputs and computes the screen from them; [riderZone] is where the rider is.
 * Most results (a tick, a cache write during a refresh) leave the cards as they were; their content keeps its instance.
 */
internal class TodayReducer(
    private val riderZone: TimeZone,
) : Reducer<TodayState, TodayResult> {
    override fun reduce(
        state: TodayState,
        result: TodayResult,
    ): TodayState {
        val inputs = state.inputs
        val next =
            when (result) {
                is TodayResult.DataChanged -> {
                    inputs.copy(snapshot = result.snapshot)
                }

                is TodayResult.ClockTicked -> {
                    inputs.copy(now = result.now)
                }

                is TodayResult.RefreshStarted -> {
                    inputs.copy(refresh = inputs.refresh.copy(refreshing = true, startedAt = result.at))
                }

                is TodayResult.RefreshFinished -> {
                    inputs.copy(refresh = inputs.refresh.copy(refreshing = false, last = result.result))
                }
            }
        val computed = TodayStateMapper.map(next, riderZone)
        // Compose compares the cards by instance: keeping equal content lets unchanged cards skip recomposition.
        return if (computed.content == state.content) computed.copy(content = state.content) else computed
    }
}
