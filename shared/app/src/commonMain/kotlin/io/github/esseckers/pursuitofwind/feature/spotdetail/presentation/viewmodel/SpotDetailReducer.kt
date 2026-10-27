package io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.viewmodel

import io.github.esseckers.pursuitofwind.core.mvi.Reducer
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.mapper.SpotDetailStateMapper
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.SpotDetailInputs
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.SpotDetailState
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.SpotDetailState.Content
import kotlinx.datetime.TimeZone

/**
 * Records each result in the state's inputs and computes the screen from them; [riderZone] is where the rider is.
 * Parts of the screen that did not change keep their instances: a tap on an hour changes the details, not the chart, so
 * the chart is not drawn again.
 *
 * A selection reuses the engine's answer kept in the state; every other result maps everything again, which also keeps
 * that answer up to date with the data and the time.
 */
internal class SpotDetailReducer(
    private val riderZone: TimeZone,
) : Reducer<SpotDetailState, SpotDetailResult> {
    override fun reduce(
        state: SpotDetailState,
        result: SpotDetailResult,
    ): SpotDetailState {
        val inputs = state.inputs
        val computed =
            when (result) {
                is SpotDetailResult.DataChanged -> {
                    map(inputs.copy(snapshot = result.snapshot))
                }

                is SpotDetailResult.ClockTicked -> {
                    map(inputs.copy(now = result.now))
                }

                is SpotDetailResult.RefreshStarted -> {
                    map(inputs.copy(refresh = inputs.refresh.started(at = result.at)))
                }

                is SpotDetailResult.RefreshFinished -> {
                    map(inputs.copy(refresh = inputs.refresh.finished(result.error)))
                }

                // A tap on an hour: the forecast is the same, so only the hour shown is worked out.
                is SpotDetailResult.Selected -> {
                    SpotDetailStateMapper.select(state, result.selection, riderZone)
                }
            }
        return computed.copy(content = keepUnchanged(state.content, computed.content))
    }

    private fun map(inputs: SpotDetailInputs): SpotDetailState = SpotDetailStateMapper.map(inputs, riderZone)

    private fun keepUnchanged(
        previous: Content,
        next: Content,
    ): Content =
        when (previous) {
            next -> {
                previous
            }

            is Content.Ready if next is Content.Ready -> {
                Content.Ready(
                    chart = if (next.chart == previous.chart) previous.chart else next.chart,
                    days = if (next.days == previous.days) previous.days else next.days,
                    windows = if (next.windows == previous.windows) previous.windows else next.windows,
                    selected = if (next.selected == previous.selected) previous.selected else next.selected,
                    nowOffset = next.nowOffset,
                )
            }

            else -> {
                next
            }
        }
}
