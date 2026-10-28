package io.github.esseckers.pursuitofwind.feature.spotmap.presentation.viewmodel

import io.github.esseckers.pursuitofwind.core.domain.mapper.GeoMapper
import io.github.esseckers.pursuitofwind.core.mvi.Reducer
import io.github.esseckers.pursuitofwind.core.ui.SPOT_DRIVE_STEPS
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.spotmap.presentation.mapper.SpotMapStateMapper
import io.github.esseckers.pursuitofwind.feature.spotmap.presentation.model.SpotDraft
import io.github.esseckers.pursuitofwind.feature.spotmap.presentation.model.SpotMapInputs
import io.github.esseckers.pursuitofwind.feature.spotmap.presentation.model.SpotMapState

/**
 * Records each result in the state's inputs and computes the map from them.
 *
 * - While a save runs, the spot stays where it was sent: presses, taps, picks and cancel are dropped. The name and the
 *   drive still change, as the fields show them, and a retry after a failure sends them.
 * - While a spot is being placed or changed, a tap on a marker does not throw it away.
 * - A new spot that is in the catalog but could not be followed becomes the selected spot, so trying again follows it
 *   instead of adding it twice.
 *
 * An equal map keeps its instance, so typing a name does not draw the map again.
 */
internal object SpotMapReducer : Reducer<SpotMapState, SpotMapResult> {
    override fun reduce(
        state: SpotMapState,
        result: SpotMapResult,
    ): SpotMapState {
        val inputs = state.inputs
        val next =
            when (result) {
                is SpotMapResult.DataChanged -> inputs.withSnapshot(result)
                SpotMapResult.RefreshStarted -> inputs.copy(refresh = inputs.refresh.started())
                is SpotMapResult.RefreshFinished -> inputs.copy(refresh = inputs.refresh.finished(result.error))
                is SpotMapResult.Placing -> if (inputs.saving && !result.editsFields()) inputs else place(inputs, result)
                is SpotMapResult.Choosing -> if (inputs.saving) inputs else choose(inputs, result)
                is SpotMapResult.Saving -> save(inputs, result)
                is SpotMapResult.BaseMap -> baseMap(inputs, result)
            }
        val computed = SpotMapStateMapper.map(next)
        return if (computed.map == state.map) computed.copy(map = state.map) else computed
    }

    /** New data; the spot to change becomes the draft once it is known. */
    private fun SpotMapInputs.withSnapshot(result: SpotMapResult.DataChanged): SpotMapInputs {
        val next = copy(snapshot = result.snapshot)
        val editing = SpotMapStateMapper.spot(next, editing)
        return if (draft == null && editing != null) next.copy(draft = SpotMapStateMapper.draftOf(editing, version = 1)) else next
    }

    /** The name and the drive, which stay editable while a save runs. */
    private fun SpotMapResult.Placing.editsFields(): Boolean =
        this is SpotMapResult.Placing.Renamed || this is SpotMapResult.Placing.DriveChanged

    private fun place(
        inputs: SpotMapInputs,
        result: SpotMapResult.Placing,
    ): SpotMapInputs =
        when (result) {
            is SpotMapResult.Placing.LongPressed -> {
                val draft = inputs.draft?.copy(pin = result.point) ?: SpotDraft(pin = result.point)
                inputs.copy(draft = draft, selected = null, nearbyDismissed = false, saveError = null)
            }

            is SpotMapResult.Placing.Tapped -> {
                tap(inputs, result)
            }

            is SpotMapResult.Placing.Renamed -> {
                inputs.copy(draft = inputs.draft?.copy(name = result.name))
            }

            is SpotMapResult.Placing.DriveChanged -> {
                inputs.copy(driveMinutes = SPOT_DRIVE_STEPS.next(inputs.driveMinutes, result.step))
            }

            SpotMapResult.Placing.AddedHere -> {
                inputs.copy(nearbyDismissed = true)
            }

            SpotMapResult.Placing.Cancelled -> {
                cancel(inputs)
            }
        }

    /** With a pin placed, the water lies towards the tap; without one, a tap on the map lets go of the selected spot. */
    private fun tap(
        inputs: SpotMapInputs,
        result: SpotMapResult.Placing.Tapped,
    ): SpotMapInputs {
        val draft = inputs.draft ?: return inputs.copy(selected = null)
        val farEnough = GeoMapper.distanceMeters(draft.pin, result.point) >= SpotMapStateMapper.MIN_WATER_METERS
        // While a nearby spot is offered, the rider answers that first.
        val answering = SpotMapStateMapper.nearby(inputs) != null
        return if (farEnough && !answering) inputs.copy(draft = draft.copy(water = GeoMapper.bearing(draft.pin, result.point))) else inputs
    }

    /**
     * Starts over: a new spot's pin goes, and a change started on the map goes back to its spot, selected. The map
     * opened to change one spot has nothing to go back to; back leaves it.
     */
    private fun cancel(inputs: SpotMapInputs): SpotMapInputs {
        val editing = inputs.editing
        return when {
            inputs.editOnly -> inputs
            editing != null -> select(inputs.copy(editing = null), editing)
            else -> inputs.copy(draft = null, selected = null, nearbyDismissed = false, saveError = null)
        }
    }

    private fun choose(
        inputs: SpotMapInputs,
        result: SpotMapResult.Choosing,
    ): SpotMapInputs =
        when (result) {
            // A spot being placed or changed stays: a tap on a marker next to it was meant for the map.
            is SpotMapResult.Choosing.Selected -> {
                if (inputs.draft != null || inputs.editing != null) inputs else select(inputs, result.spotId)
            }

            SpotMapResult.Choosing.UsedNearby -> {
                SpotMapStateMapper.nearby(inputs)?.let { select(inputs, it.id) } ?: inputs
            }

            SpotMapResult.Choosing.EditStarted -> {
                val spot = SpotMapStateMapper.spot(inputs, inputs.selected)
                val mine = spot != null && spot.createdBy == inputs.snapshot?.userId
                if (spot == null || !mine) {
                    inputs
                } else {
                    inputs.copy(editing = spot.id, draft = SpotMapStateMapper.draftOf(spot, version = 1), selected = null, saveError = null)
                }
            }
        }

    /** Selects [spotId] to follow, with the drive the rider already has to it, if any. */
    private fun select(
        inputs: SpotMapInputs,
        spotId: SpotId,
    ): SpotMapInputs {
        val followed = inputs.snapshot?.mySpots?.firstOrNull { it.spot.id == spotId }
        return inputs.copy(
            selected = spotId,
            draft = null,
            nearbyDismissed = false,
            saveError = null,
            driveMinutes = followed?.drive?.inWholeMinutes?.toInt() ?: inputs.driveMinutes,
        )
    }

    /** Another attempt clears the failure until it fails again, so the message does not stay over a map that loads. */
    private fun baseMap(
        inputs: SpotMapInputs,
        result: SpotMapResult.BaseMap,
    ): SpotMapInputs =
        when (result) {
            is SpotMapResult.BaseMap.Loaded -> inputs.copy(baseMapFailed = !result.loaded)
            SpotMapResult.BaseMap.Retried -> inputs.copy(baseMapFailed = false, baseMapAttempt = inputs.baseMapAttempt + 1)
        }

    private fun save(
        inputs: SpotMapInputs,
        result: SpotMapResult.Saving,
    ): SpotMapInputs =
        when (result) {
            SpotMapResult.Saving.Started -> inputs.copy(saving = true, saveError = null)
            is SpotMapResult.Saving.Added -> select(inputs, result.spotId)
            is SpotMapResult.Saving.Failed -> inputs.copy(saving = false, saveError = result.error)
        }
}
