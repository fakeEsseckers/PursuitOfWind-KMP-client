package io.github.esseckers.pursuitofwind.feature.spots.presentation.viewmodel

import androidx.lifecycle.viewModelScope
import io.github.esseckers.pursuitofwind.core.launchFinishing
import io.github.esseckers.pursuitofwind.core.mvi.MviViewModel
import io.github.esseckers.pursuitofwind.core.ui.message
import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.Outcome
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.spots.domain.entity.SpotSettingsEntity
import io.github.esseckers.pursuitofwind.feature.spots.domain.repository.SpotsRepository
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.RemovedSpot
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.SpotDrive
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.SpotEdit
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.SpotsEffect
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.SpotsInputs
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.SpotsIntent
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.SpotsState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlin.time.Duration.Companion.minutes

/**
 * The Spots tab in MVI. While it is shown, the cached spots come in as results; the first time it shows, it fetches
 * them. Changes are saved as the rider makes them and show at once:
 *
 * - One save at a time per spot. Taps while it runs only change the edit in the state, and the running save sends the
 *   latest one when it is done, so quick taps cost a request or two, not one each.
 * - The repository runs the writes (saves, removals, undos) one at a time in the order they were made, and finishes
 *   each even when the rider leaves the tab meanwhile. The shared repository runs a refresh and writes from any screen
 *   one at a time, so a refresh never brings back the spots from before a write.
 * - A failed save puts the spot back as cached and says why ([SpotsEffect.SaveFailed]); a removal offers to undo
 *   ([SpotsEffect.Removed]).
 */
internal class SpotsViewModel(
    private val repository: SpotsRepository,
) : MviViewModel<SpotsState, SpotsIntent, SpotsResult, SpotsEffect>(SpotsState(), SpotsReducer) {
    override fun onIntent(intent: SpotsIntent) {
        when (intent) {
            SpotsIntent.Refresh -> refresh()
            is SpotsIntent.ToggleFavorite -> edit(intent.spotId) { it.copy(favorite = !it.favorite) }
            is SpotsIntent.ChangeDrive -> edit(intent.spotId) { it.copy(driveMinutes = intent.minutes.coerceIn(0, SpotDrive.MAX_MINUTES)) }
            is SpotsIntent.Remove -> remove(intent.spotId)
            is SpotsIntent.Undo -> undo(intent.spot)
            is SpotsIntent.OpenSpot -> postEffect(SpotsEffect.OpenSpot(intent.spotId))
            SpotsIntent.AddSpots -> postEffect(SpotsEffect.AddSpots)
        }
    }

    override fun bind(): Flow<SpotsResult> =
        repository.spots
            .map<_, SpotsResult> { SpotsResult.DataChanged(it) }
            .onStart { if (!currentState.inputs.refresh.finished) refresh() }

    /** One refresh at a time; a second pull while one runs does nothing. */
    private fun refresh() {
        if (currentState.inputs.refresh.refreshing) return
        dispatchResult(SpotsResult.RefreshStarted)
        viewModelScope.launchFinishing(
            unfinished = AppError.Unknown("Refresh did not finish"),
            work = { repository.refresh() },
        ) { error -> dispatchResult(SpotsResult.RefreshFinished(error)) }
    }

    private fun edit(
        spotId: SpotId,
        change: (SpotEdit) -> SpotEdit,
    ) {
        val inputs = currentState.inputs
        if (spotId in inputs.removing) return
        val edit = change(editOf(inputs, spotId) ?: return)
        dispatchResult(SpotsResult.Edited(spotId, edit))
        if (spotId !in inputs.saving) save(spotId)
    }

    /**
     * Saves the rider's edit of [spotId] until its last value is saved, or one fails. The edit is read from the state
     * after each request: a removal drops it, which stops the save.
     */
    private fun save(spotId: SpotId) {
        viewModelScope.launchFinishing(
            unfinished = Outcome.Failure(AppError.Unknown("Saving did not finish")),
            work = {
                repository.save(spotId) {
                    currentState.inputs.edits[spotId]?.let { SpotSettingsEntity(it.driveMinutes.minutes, it.favorite) }
                }
            },
        ) { outcome ->
            when (outcome) {
                is Outcome.Success -> {
                    dispatchResult(SpotsResult.SaveFinished(spotId, outcome.value))
                }

                is Outcome.Failure -> {
                    dispatchResult(SpotsResult.SaveFailed(spotId))
                    postEffect(SpotsEffect.SaveFailed(outcome.error.message()))
                }
            }
        }
    }

    private fun remove(spotId: SpotId) {
        val inputs = currentState.inputs
        if (spotId in inputs.removing) return
        val edit = editOf(inputs, spotId) ?: return
        val listed =
            inputs.cached
                ?.value
                ?.firstOrNull { it.spot.id == spotId }
                ?.spot
        val removed = RemovedSpot(spotId, listed?.name.orEmpty(), listed?.verified ?: true, edit.driveMinutes.minutes, edit.favorite)
        // Drops the unsent edit, so a save still running stops after its current request instead of following it again.
        dispatchResult(SpotsResult.RemoveStarted(spotId))
        viewModelScope.launchFinishing(
            unfinished = Outcome.Failure(AppError.Unknown("Removing did not finish")),
            work = { repository.remove(spotId) },
        ) { outcome ->
            when (outcome) {
                is Outcome.Success -> {
                    dispatchResult(SpotsResult.RemoveFinished(spotId, outcome.value))
                    postEffect(SpotsEffect.Removed(removed))
                }

                is Outcome.Failure -> {
                    dispatchResult(SpotsResult.RemoveFailed(spotId))
                    postEffect(SpotsEffect.SaveFailed(outcome.error.message()))
                }
            }
        }
    }

    /** Follows [spot] again as it was. It shows again at once, marked as coming back, until that is saved. */
    private fun undo(spot: RemovedSpot) {
        if (spot.id in currentState.inputs.restoring) return
        dispatchResult(SpotsResult.RestoreStarted(spot))
        viewModelScope.launchFinishing(
            unfinished = Outcome.Failure(AppError.Unknown("Restoring did not finish")),
            work = { repository.restore(spot.id, SpotSettingsEntity(spot.drive, spot.favorite)) },
        ) { outcome ->
            when (outcome) {
                is Outcome.Success -> {
                    dispatchResult(SpotsResult.RestoreFinished(spot.id, outcome.value))
                }

                is Outcome.Failure -> {
                    dispatchResult(SpotsResult.RestoreFailed(spot.id))
                    postEffect(SpotsEffect.SaveFailed(outcome.error.message()))
                }
            }
        }
    }

    /** The values [spotId] has now: the rider's unsaved edit, or as cached; null for a spot that is not followed. */
    private fun editOf(
        inputs: SpotsInputs,
        spotId: SpotId,
    ): SpotEdit? =
        inputs.edits[spotId] ?: inputs.cached
            ?.value
            ?.firstOrNull { it.spot.id == spotId }
            ?.let(SpotEdit::of)
}
