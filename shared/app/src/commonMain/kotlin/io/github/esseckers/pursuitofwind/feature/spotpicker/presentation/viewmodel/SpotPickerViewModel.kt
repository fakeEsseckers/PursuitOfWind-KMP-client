package io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.viewmodel

import androidx.lifecycle.viewModelScope
import io.github.esseckers.pursuitofwind.core.mvi.MviViewModel
import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.feature.spotpicker.domain.repository.SpotPickerRepository
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.mapper.SpotPickerStateMapper
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.SpotPickerEffect
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.SpotPickerIntent
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model.SpotPickerState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

/**
 * The spot picker in MVI. While it is shown, the cached catalog and followed spots come in as results; the first time
 * it shows, it fetches both, so a new rider sees the whole catalog. Choices stay a draft until
 * [SpotPickerIntent.Done], which saves what changed and finishes with [SpotPickerEffect.Finished]. A failed refresh or
 * save is part of the state.
 */
internal class SpotPickerViewModel(
    private val repository: SpotPickerRepository,
) : MviViewModel<SpotPickerState, SpotPickerIntent, SpotPickerResult, SpotPickerEffect>(SpotPickerState(), SpotPickerReducer) {
    override fun onIntent(intent: SpotPickerIntent) {
        when (intent) {
            is SpotPickerIntent.Toggle -> dispatch(SpotPickerResult.Toggled(intent.spotId))
            is SpotPickerIntent.ChangeDrive -> dispatch(SpotPickerResult.DriveChanged(intent.spotId, intent.minutes))
            SpotPickerIntent.Refresh -> refresh()
            SpotPickerIntent.Done -> done()
        }
    }

    override fun bind(): Flow<SpotPickerResult> =
        repository.snapshot
            .map<_, SpotPickerResult> { SpotPickerResult.DataChanged(it) }
            .onStart { if (!currentState.inputs.refresh.finished) refresh() }

    /** The running refresh, which a save waits for: both write the followed spots. */
    private var refreshJob: Job? = null

    /** One refresh at a time; a tap while one runs joins it, and none starts while saving. */
    private fun refresh() {
        if (currentState.inputs.refresh.refreshing || currentState.saving) return
        dispatch(SpotPickerResult.RefreshStarted)
        refreshJob =
            viewModelScope.launch {
                // Ends the refresh whatever happens, so a cancelled one never blocks the next.
                var error: AppError? = AppError.Unknown("Refresh did not finish")
                try {
                    error = repository.refresh()
                } finally {
                    dispatch(SpotPickerResult.RefreshFinished(error))
                }
            }
    }

    /** Saves what changed, then finishes; with nothing changed, finishes at once. One save at a time. */
    private fun done() {
        if (currentState.saving) return
        val changes = SpotPickerStateMapper.changes(currentState.inputs)
        if (changes.isEmpty()) {
            post(SpotPickerEffect.Finished)
            return
        }
        dispatch(SpotPickerResult.SaveStarted)
        viewModelScope.launch {
            var error: AppError? = AppError.Unknown("Saving did not finish")
            try {
                // A refresh that replaces the followed spots after this save would drop what was just chosen.
                refreshJob?.join()
                error = repository.save(changes)
            } finally {
                dispatch(SpotPickerResult.SaveFinished(error))
            }
            if (error == null) post(SpotPickerEffect.Finished)
        }
    }
}
