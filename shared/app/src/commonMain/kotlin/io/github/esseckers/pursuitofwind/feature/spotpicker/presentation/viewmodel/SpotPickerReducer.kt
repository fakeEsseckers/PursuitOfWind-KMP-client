package io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.viewmodel

import io.github.esseckers.pursuitofwind.core.mvi.Reducer
import io.github.esseckers.pursuitofwind.core.ui.SPOT_DRIVE_STEPS
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.mapper.SpotPickerStateMapper
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.SpotChoice
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.SpotPickerInputs
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.SpotPickerState

/**
 * Records each result in the state's inputs and computes the screen from them. The rider's choices stay on top of the
 * cached spots until they are saved; equal content keeps its instance, so unchanged rows skip recomposition.
 */
internal object SpotPickerReducer : Reducer<SpotPickerState, SpotPickerResult> {
    override fun reduce(
        state: SpotPickerState,
        result: SpotPickerResult,
    ): SpotPickerState {
        val inputs = state.inputs
        val next =
            when (result) {
                is SpotPickerResult.DataChanged -> {
                    inputs.copy(snapshot = result.snapshot)
                }

                is SpotPickerResult.Toggled -> {
                    inputs.choose(result.spotId) { it.copy(selected = !it.selected) }
                }

                is SpotPickerResult.DriveChanged -> {
                    inputs.choose(result.spotId) { it.copy(driveMinutes = SPOT_DRIVE_STEPS.next(it.driveMinutes, result.step)) }
                }

                SpotPickerResult.RefreshStarted -> {
                    inputs.copy(refresh = inputs.refresh.started())
                }

                is SpotPickerResult.RefreshFinished -> {
                    inputs.copy(refresh = inputs.refresh.finished(result.error))
                }

                SpotPickerResult.SaveStarted -> {
                    inputs.copy(saving = true, saveError = null)
                }

                is SpotPickerResult.SaveFinished -> {
                    inputs.copy(saving = false, saveError = result.error)
                }

                is SpotPickerResult.MapChange -> {
                    mapChange(inputs, result)
                }
            }
        val computed = SpotPickerStateMapper.map(next)
        return if (computed.content == state.content) computed.copy(content = state.content) else computed
    }

    /**
     * Another attempt clears the failure until it fails again, so the message does not stay over a map that loads. Only
     * a map that failed is tried again, so a refresh leaves a loaded map where the rider moved it.
     */
    private fun mapChange(
        inputs: SpotPickerInputs,
        change: SpotPickerResult.MapChange,
    ): SpotPickerInputs =
        when (change) {
            is SpotPickerResult.MapChange.ViewChosen -> {
                inputs.copy(view = change.view)
            }

            is SpotPickerResult.MapChange.Picked -> {
                inputs.copy(mapSpot = change.spotId)
            }

            is SpotPickerResult.MapChange.BaseMapLoaded -> {
                inputs.copy(baseMapFailed = !change.loaded)
            }

            SpotPickerResult.MapChange.BaseMapRetried -> {
                if (inputs.baseMapFailed) inputs.copy(baseMapFailed = false, baseMapAttempt = inputs.baseMapAttempt + 1) else inputs
            }
        }

    private fun SpotPickerInputs.choose(
        spotId: SpotId,
        change: (SpotChoice) -> SpotChoice,
    ): SpotPickerInputs = copy(choices = choices + (spotId to change(SpotPickerStateMapper.choiceOf(this, spotId))))
}
