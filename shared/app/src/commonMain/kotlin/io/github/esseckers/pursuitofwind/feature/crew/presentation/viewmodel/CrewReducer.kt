package io.github.esseckers.pursuitofwind.feature.crew.presentation.viewmodel

import io.github.esseckers.pursuitofwind.core.mvi.Reducer
import io.github.esseckers.pursuitofwind.feature.crew.presentation.mapper.CrewStateMapper
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.CrewDialog
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.CrewRefresh
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.CrewState
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.StringResource

/**
 * Records each result in the state's inputs and computes the tab from them; departures show in [riderZone]. Equal
 * content keeps its instance, so unchanged cards skip recomposition.
 */
internal class CrewReducer(
    private val riderZone: TimeZone,
) : Reducer<CrewState, CrewResult> {
    override fun reduce(
        state: CrewState,
        result: CrewResult,
    ): CrewState {
        val inputs = state.inputs
        val next =
            when (result) {
                is CrewResult.CrewsChanged -> {
                    inputs.copy(crews = result.cached, shown = result.shown)
                }

                is CrewResult.DetailsChanged -> {
                    inputs.copy(details = result.details)
                }

                CrewResult.RefreshStarted -> {
                    inputs.copy(refresh = inputs.refresh.copy(refreshing = true))
                }

                is CrewResult.RefreshFinished -> {
                    inputs.copy(
                        refresh = CrewRefresh(refreshing = false, finished = true, error = result.error),
                    )
                }

                is CrewResult.Chosen -> {
                    inputs.copy(chosen = result.crewId, shown = result.shown)
                }

                is CrewResult.Ticked -> {
                    inputs.copy(now = result.now)
                }

                is CrewResult.DialogShown -> {
                    inputs.copy(dialog = result.dialog)
                }

                CrewResult.DialogDismissed -> {
                    inputs.copy(dialog = inputs.dialog?.takeIf { it.sending })
                }

                CrewResult.DialogSending -> {
                    inputs.copy(dialog = inputs.dialog?.withSending(true, error = null))
                }

                is CrewResult.DialogFailed -> {
                    inputs.copy(dialog = inputs.dialog?.withSending(false, error = result.reason))
                }

                is CrewResult.CancelStarted -> {
                    inputs.copy(cancelling = inputs.cancelling + result.rideId)
                }

                is CrewResult.CancelFinished -> {
                    inputs.copy(cancelling = inputs.cancelling - result.rideId)
                }
            }
        val computed = CrewStateMapper.map(next, riderZone)
        return if (computed.content == state.content) computed.copy(content = state.content) else computed
    }

    private fun CrewDialog.withSending(
        sending: Boolean,
        error: StringResource?,
    ): CrewDialog =
        when (this) {
            is CrewDialog.Create -> copy(sending = sending, error = error)
            is CrewDialog.Join -> copy(sending = sending, error = error)
            is CrewDialog.ConfirmCancel -> this
        }
}
