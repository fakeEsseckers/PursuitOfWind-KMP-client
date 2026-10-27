package io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.viewmodel

import io.github.esseckers.pursuitofwind.core.mvi.Reducer
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.mapper.SpotDetailStateMapper
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.SpotDetailState
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.SpotDetailState.Content
import kotlinx.datetime.TimeZone

/**
 * Records each result in the state's inputs and computes the screen from them; [riderZone] is where the rider is.
 * Parts of the screen that did not change keep their instances: a tap on an hour changes the details, not the chart, so
 * the chart is not drawn again.
 */
internal class SpotDetailReducer(
    private val riderZone: TimeZone,
) : Reducer<SpotDetailState, SpotDetailResult> {
    override fun reduce(
        state: SpotDetailState,
        result: SpotDetailResult,
    ): SpotDetailState {
        val inputs = state.inputs
        val next =
            when (result) {
                is SpotDetailResult.DataChanged -> {
                    inputs.copy(snapshot = result.snapshot)
                }

                is SpotDetailResult.ClockTicked -> {
                    inputs.copy(now = result.now)
                }

                is SpotDetailResult.RefreshStarted -> {
                    inputs.copy(refresh = inputs.refresh.started(at = result.at))
                }

                is SpotDetailResult.RefreshFinished -> {
                    inputs.copy(refresh = inputs.refresh.finished(result.error))
                }

                is SpotDetailResult.Selected -> {
                    inputs.copy(selection = result.selection)
                }
            }
        val computed = SpotDetailStateMapper.map(next, riderZone)
        return computed.copy(content = keepUnchanged(state.content, computed.content))
    }

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
