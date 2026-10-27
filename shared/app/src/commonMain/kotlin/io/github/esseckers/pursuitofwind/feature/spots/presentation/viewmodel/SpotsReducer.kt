package io.github.esseckers.pursuitofwind.feature.spots.presentation.viewmodel

import io.github.esseckers.pursuitofwind.core.mvi.Reducer
import io.github.esseckers.pursuitofwind.feature.spots.presentation.mapper.SpotsStateMapper
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.SpotsInputs
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.SpotsState

/**
 * Records each result in the state's inputs and computes the tab from them. An edit stays on top of the cache until its
 * save ends, and a removed spot stays hidden until its removal ends: whatever the cache emits meanwhile, an earlier
 * value never shows. A write ends with the cache read after it, so the overlay goes even when the tab was not watching
 * the cache. Equal content keeps its instance, so unchanged cards skip recomposition.
 */
internal object SpotsReducer : Reducer<SpotsState, SpotsResult> {
    override fun reduce(
        state: SpotsState,
        result: SpotsResult,
    ): SpotsState {
        val inputs = state.inputs
        val next =
            when (result) {
                is SpotsResult.DataChanged -> {
                    inputs.copy(cached = result.cached)
                }

                SpotsResult.RefreshStarted -> {
                    inputs.copy(refresh = inputs.refresh.started())
                }

                is SpotsResult.RefreshFinished -> {
                    inputs.copy(
                        refresh = inputs.refresh.finished(result.error),
                    )
                }

                is SpotsResult.Edited -> {
                    inputs.copy(edits = inputs.edits + (result.spotId to result.edit), saving = inputs.saving + result.spotId)
                }

                is SpotsResult.SaveFinished -> {
                    inputs.copy(
                        cached = result.cached ?: inputs.cached,
                        edits = inputs.edits - result.spotId,
                        saving = inputs.saving - result.spotId,
                    )
                }

                is SpotsResult.SaveFailed -> {
                    inputs.copy(edits = inputs.edits - result.spotId, saving = inputs.saving - result.spotId)
                }

                // A removed spot is followed again only through "Undo", with the values it had when removed.
                is SpotsResult.RemoveStarted -> {
                    inputs.copy(edits = inputs.edits - result.spotId, removing = inputs.removing + result.spotId)
                }

                is SpotsResult.RemoveFinished -> {
                    inputs.copy(cached = result.cached ?: inputs.cached, removing = inputs.removing - result.spotId)
                }

                is SpotsResult.RemoveFailed -> {
                    inputs.copy(removing = inputs.removing - result.spotId)
                }

                is SpotsResult.Restore -> {
                    inputs.restore(result)
                }
            }
        val computed = SpotsStateMapper.map(next)
        return if (computed.content == state.content) computed.copy(content = state.content) else computed
    }

    /** A spot shows as it was from "Undo" on, and the cache shows it from the end of its restore on. */
    private fun SpotsInputs.restore(result: SpotsResult.Restore): SpotsInputs =
        when (result) {
            is SpotsResult.RestoreStarted -> copy(restoring = restoring + (result.spot.id to result.spot))
            is SpotsResult.RestoreFinished -> copy(cached = result.cached ?: cached, restoring = restoring - result.spotId)
            is SpotsResult.RestoreFailed -> copy(restoring = restoring - result.spotId)
        }
}
