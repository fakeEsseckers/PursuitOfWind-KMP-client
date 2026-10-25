package io.github.esseckers.pursuitofwind.feature.spots.presentation.viewmodel

import androidx.lifecycle.viewModelScope
import io.github.esseckers.pursuitofwind.core.mvi.MviViewModel
import io.github.esseckers.pursuitofwind.core.mvi.launchFinishing
import io.github.esseckers.pursuitofwind.core.ui.message
import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.Cached
import io.github.esseckers.pursuitofwind.data.model.MySpot
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.spots.domain.repository.SpotsRepository
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.RemovedSpot
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.SpotDrive
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.SpotEdit
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.SpotsEffect
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.SpotsInputs
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.SpotsIntent
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.SpotsState
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.minutes

/**
 * The Spots tab in MVI. While it is shown, the cached spots come in as results; the first time it shows, it fetches
 * them. Changes are saved as the rider makes them and show at once:
 *
 * - One save at a time per spot. Taps while it runs only update what the rider wants, and the running save sends the
 *   latest value when it is done, so quick taps cost a request or two, not one each.
 * - The writes of a spot (saves, a removal, its undo) run one after another, and finish even when the rider leaves the
 *   tab meanwhile. The shared repository runs a refresh and writes from any screen one at a time, so a refresh never
 *   brings back the spots from before a write.
 * - A failed save puts the spot back as cached and says why ([SpotsEffect.SaveFailed]); a removal offers to undo
 *   ([SpotsEffect.Removed]).
 */
internal class SpotsViewModel(
    private val repository: SpotsRepository,
) : MviViewModel<SpotsState, SpotsIntent, SpotsResult, SpotsEffect>(SpotsState(), SpotsReducer) {
    /** The rider's latest choice per spot, until a save sends it. */
    private val wanted = mutableMapOf<SpotId, SpotEdit>()

    /** The last write started per spot; the next one starts after it. */
    private val writes = mutableMapOf<SpotId, Job>()

    override fun onIntent(intent: SpotsIntent) {
        when (intent) {
            SpotsIntent.Refresh -> {
                refresh()
            }

            is SpotsIntent.ToggleFavorite -> {
                edit(intent.spotId) { it.copy(favorite = !it.favorite) }
            }

            is SpotsIntent.ChangeDrive -> {
                edit(
                    intent.spotId,
                ) { it.copy(driveMinutes = intent.minutes.coerceIn(0, SpotDrive.MAX_MINUTES)) }
            }

            is SpotsIntent.Remove -> {
                remove(intent.spotId)
            }

            is SpotsIntent.Undo -> {
                undo(intent.spot)
            }

            is SpotsIntent.OpenSpot -> {
                postEffect(SpotsEffect.OpenSpot(intent.spotId))
            }

            SpotsIntent.AddSpots -> {
                postEffect(SpotsEffect.AddSpots)
            }
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
        wanted[spotId] = edit
        dispatchResult(SpotsResult.Edited(spotId, edit))
        if (spotId !in inputs.saving) launchWrite(spotId) { save(spotId) }
    }

    /** Sends what the rider wants for [spotId] until the last value is saved, or one fails. */
    private suspend fun save(spotId: SpotId) {
        var sent: SpotEdit? = null
        var next = wanted[spotId]
        var error: AppError? = null
        var saved: Cached<List<MySpot>>? = null
        while (next != null && next != sent && error == null) {
            error = repository.update(spotId, next.driveMinutes.minutes, next.favorite)
            sent = next
            // Read before looking for newer taps, so a tap made while reading is still sent.
            if (error == null) saved = repository.current()
            next = wanted[spotId]
        }
        // Here what is wanted was sent, or the spot is being removed, or the save failed: nothing is left to send.
        wanted.remove(spotId)
        if (error == null) {
            dispatchResult(SpotsResult.SaveFinished(spotId, saved))
        } else {
            dispatchResult(SpotsResult.SaveFailed(spotId))
            postEffect(SpotsEffect.SaveFailed(error.message()))
        }
    }

    private fun remove(spotId: SpotId) {
        val inputs = currentState.inputs
        if (spotId in inputs.removing) return
        val edit = editOf(inputs, spotId) ?: return
        val name =
            inputs.cached
                ?.value
                ?.firstOrNull { it.spot.id == spotId }
                ?.spot
                ?.name
                .orEmpty()
        val removed = RemovedSpot(spotId, name, edit.driveMinutes.minutes, edit.favorite)
        // A save still running stops after its current request instead of following the spot again.
        wanted.remove(spotId)
        dispatchResult(SpotsResult.RemoveStarted(spotId))
        launchWrite(spotId) {
            val error = repository.remove(spotId)
            if (error == null) {
                dispatchResult(SpotsResult.RemoveFinished(spotId, repository.current()))
                postEffect(SpotsEffect.Removed(removed))
            } else {
                dispatchResult(SpotsResult.RemoveFailed(spotId))
                postEffect(SpotsEffect.SaveFailed(error.message()))
            }
        }
    }

    private fun undo(spot: RemovedSpot) {
        launchWrite(spot.id) {
            // Followed again since, from the picker for example: what the rider chose there wins over the old values.
            val followed =
                repository
                    .current()
                    ?.value
                    .orEmpty()
                    .any { it.spot.id == spot.id }
            if (!followed) repository.update(spot.id, spot.drive, spot.favorite)?.let { postEffect(SpotsEffect.SaveFailed(it.message())) }
        }
    }

    /**
     * A write the rider asked for on [spotId]. It starts after the spot's previous write and finishes even when the rider
     * leaves the tab and this ViewModel is cleared.
     */
    private fun launchWrite(
        spotId: SpotId,
        block: suspend () -> Unit,
    ) {
        val previous = writes[spotId]
        writes[spotId] =
            viewModelScope.launch {
                withContext(NonCancellable) {
                    previous?.join()
                    block()
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
