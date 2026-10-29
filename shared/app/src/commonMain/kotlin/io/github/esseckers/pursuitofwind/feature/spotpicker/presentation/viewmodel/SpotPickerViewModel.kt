package io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.viewmodel

import androidx.lifecycle.viewModelScope
import io.github.esseckers.pursuitofwind.core.launchFinishing
import io.github.esseckers.pursuitofwind.core.mvi.MviViewModel
import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.spotpicker.domain.repository.SpotPickerRepository
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.SpotPickerEffect
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.SpotPickerIntent
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.SpotPickerState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * The spot picker in MVI. While it is shown, the cached catalog and followed spots come in as results; the first time
 * it shows, it fetches both, so a new rider sees the whole catalog. Choices stay a draft until
 * [SpotPickerIntent.Done], which saves what changed and finishes with [SpotPickerEffect.Finished]. A failed refresh or
 * save is part of the state. Coordinates the rider copies go out as [SpotPickerEffect.CopyCoordinates].
 */
internal class SpotPickerViewModel(
    private val repository: SpotPickerRepository,
) : MviViewModel<SpotPickerState, SpotPickerIntent, SpotPickerResult, SpotPickerEffect>(SpotPickerState(), SpotPickerReducer) {
    override fun onIntent(intent: SpotPickerIntent) {
        when (intent) {
            is SpotPickerIntent.Toggle -> {
                dispatchResult(SpotPickerResult.Toggled(intent.spotId))
            }

            is SpotPickerIntent.ChangeDrive -> {
                dispatchResult(SpotPickerResult.DriveChanged(intent.spotId, intent.step))
            }

            SpotPickerIntent.Refresh -> {
                dispatchResult(SpotPickerResult.MapChange.BaseMapRetried)
                refresh()
            }

            SpotPickerIntent.Done -> {
                done()
            }

            is SpotPickerIntent.Show -> {
                dispatchResult(SpotPickerResult.MapChange.ViewChosen(intent.view))
            }

            is SpotPickerIntent.PickOnMap -> {
                dispatchResult(SpotPickerResult.MapChange.Picked(intent.spotId))
            }

            SpotPickerIntent.TapMap -> {
                dispatchResult(SpotPickerResult.MapChange.Picked(null))
            }

            is SpotPickerIntent.BaseMap -> {
                dispatchResult(SpotPickerResult.MapChange.BaseMapLoaded(intent.loaded))
            }

            SpotPickerIntent.RetryBaseMap -> {
                dispatchResult(SpotPickerResult.MapChange.BaseMapRetried)
            }

            is SpotPickerIntent.CopyCoordinates -> {
                copyCoordinates(intent.spotId)
            }
        }
    }

    override fun bind(): Flow<SpotPickerResult> =
        repository.snapshot
            .map<_, SpotPickerResult> { SpotPickerResult.DataChanged(it) }
            .onStart { if (!currentState.inputs.refresh.finished) refresh() }

    /**
     * One refresh at a time; a tap while one runs joins it, and none starts while saving. A refresh already running when
     * the rider saves cannot undo the save: the data layer writes the followed spots one change at a time.
     */
    private fun refresh() {
        if (currentState.inputs.refresh.refreshing || currentState.saving) return
        dispatchResult(SpotPickerResult.RefreshStarted)
        viewModelScope.launchFinishing(
            unfinished = AppError.Unknown("Refresh did not finish"),
            work = { repository.refresh() },
        ) { error -> dispatchResult(SpotPickerResult.RefreshFinished(error)) }
    }

    private fun copyCoordinates(spotId: SpotId) {
        val spots = (currentState.content as? SpotPickerState.Content.Ready)?.spots.orEmpty()
        spots.firstOrNull { it.id == spotId }?.let { postEffect(SpotPickerEffect.CopyCoordinates(it.coordinates)) }
    }

    /** Saves what changed, then finishes; with nothing changed, finishes at once. One save at a time. */
    private fun done() {
        if (currentState.saving) return
        val changes = currentState.changes
        if (changes.isEmpty()) {
            postEffect(SpotPickerEffect.Finished)
            return
        }
        dispatchResult(SpotPickerResult.SaveStarted)
        viewModelScope.launchFinishing(
            unfinished = AppError.Unknown("Saving did not finish"),
            work = { repository.save(changes) },
        ) { error ->
            dispatchResult(SpotPickerResult.SaveFinished(error))
            if (error == null) postEffect(SpotPickerEffect.Finished)
        }
    }
}
