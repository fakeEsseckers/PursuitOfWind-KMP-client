package io.github.esseckers.pursuitofwind.feature.crew.presentation.viewmodel

import io.github.esseckers.pursuitofwind.core.mvi.Reducer
import io.github.esseckers.pursuitofwind.feature.crew.presentation.mapper.CrewStateMapper
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.CrewDialog
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.CrewInputs
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.CrewState
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.crewToShow
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
                    inputs.copy(crews = result.cached).withShown()
                }

                is CrewResult.DetailsChanged -> {
                    inputs.copy(details = result.details)
                }

                CrewResult.RefreshStarted -> {
                    inputs.copy(refresh = inputs.refresh.started())
                }

                is CrewResult.RefreshFinished -> {
                    inputs.copy(
                        refresh = inputs.refresh.finished(result.error),
                    )
                }

                is CrewResult.Choice -> {
                    inputs.choose(result).withShown()
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

    /** The crew the rider wants: one picked, or one just made or joined until its first refresh is done. */
    private fun CrewInputs.choose(choice: CrewResult.Choice): CrewInputs =
        when (choice) {
            is CrewResult.Chosen -> copy(chosen = choice.crewId, arriving = null)
            is CrewResult.Arrived -> copy(chosen = choice.crewId, arriving = choice.crewId)
            is CrewResult.ArrivalSettled -> copy(arriving = arriving?.takeUnless { it == choice.crewId })
        }

    /** The crew on screen: one [CrewInputs.arriving] that the list does not hold yet, else [crewToShow]. */
    private fun CrewInputs.withShown(): CrewInputs {
        val listed = crews?.value.orEmpty()
        val waiting = arriving?.takeIf { id -> listed.none { it.id == id } }
        return copy(shown = waiting ?: crewToShow(listed, chosen)?.id)
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
