package io.github.esseckers.pursuitofwind.feature.spotmap.presentation.viewmodel

import androidx.lifecycle.viewModelScope
import io.github.esseckers.pursuitofwind.core.launchFinishing
import io.github.esseckers.pursuitofwind.core.mvi.MviViewModel
import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.spotmap.domain.entity.AddedSpotEntity
import io.github.esseckers.pursuitofwind.feature.spotmap.domain.entity.NewSpotEntity
import io.github.esseckers.pursuitofwind.feature.spotmap.domain.entity.SpotEditEntity
import io.github.esseckers.pursuitofwind.feature.spotmap.domain.repository.SpotMapRepository
import io.github.esseckers.pursuitofwind.feature.spotmap.presentation.model.SpotMapEffect
import io.github.esseckers.pursuitofwind.feature.spotmap.presentation.model.SpotMapInputs
import io.github.esseckers.pursuitofwind.feature.spotmap.presentation.model.SpotMapIntent
import io.github.esseckers.pursuitofwind.feature.spotmap.presentation.model.SpotMapPanel
import io.github.esseckers.pursuitofwind.feature.spotmap.presentation.model.SpotMapState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.datetime.TimeZone
import kotlin.time.Duration.Companion.minutes

/**
 * The map of spots in MVI: every spot riders added, and the rider's favorites on top. A long press places a new spot's
 * pin and a tap shows where its water lies; it is saved with a name and followed as a favorite. A tap on a marker
 * selects that spot to follow, and one the rider added can be changed ([editing] opens the map on it). While the map is
 * shown the cached spots come in as results; the first time it shows, it fetches them. A spot saved, followed or
 * changed closes the map ([SpotMapEffect.Finished]); a failed one stays on it with the reason.
 *
 * @param deviceZone the device's time zone, the server's first guess at a new spot's.
 */
internal class SpotMapViewModel(
    private val repository: SpotMapRepository,
    private val deviceZone: TimeZone,
    editing: SpotId?,
) : MviViewModel<SpotMapState, SpotMapIntent, SpotMapResult, SpotMapEffect>(
        initialState = SpotMapState(inputs = SpotMapInputs(editing = editing, editOnly = editing != null)),
        reducer = SpotMapReducer,
    ) {
    override fun onIntent(intent: SpotMapIntent) {
        when (intent) {
            SpotMapIntent.Follow -> follow()
            SpotMapIntent.Save -> save()
            SpotMapIntent.Refresh -> refreshAll()
            else -> resultOf(intent)?.let(::dispatchResult)
        }
    }

    /** What an intent that only changes the state comes to; null for the ones that do work ([onIntent]). */
    private fun resultOf(intent: SpotMapIntent): SpotMapResult? =
        when (intent) {
            is SpotMapIntent.LongPress -> SpotMapResult.Placing.LongPressed(intent.point)
            is SpotMapIntent.Tap -> SpotMapResult.Placing.Tapped(intent.point)
            is SpotMapIntent.Rename -> SpotMapResult.Placing.Renamed(intent.name)
            is SpotMapIntent.ChangeDrive -> SpotMapResult.Placing.DriveChanged(intent.step)
            SpotMapIntent.AddHere -> SpotMapResult.Placing.AddedHere
            SpotMapIntent.Cancel -> SpotMapResult.Placing.Cancelled
            is SpotMapIntent.SelectSpot -> SpotMapResult.Choosing.Selected(intent.spotId)
            SpotMapIntent.UseNearby -> SpotMapResult.Choosing.UsedNearby
            SpotMapIntent.EditSelected -> SpotMapResult.Choosing.EditStarted
            is SpotMapIntent.BaseMap -> SpotMapResult.BaseMap.Loaded(intent.loaded)
            SpotMapIntent.RetryBaseMap -> SpotMapResult.BaseMap.Retried
            SpotMapIntent.Follow, SpotMapIntent.Save, SpotMapIntent.Refresh -> null
        }

    override fun bind(): Flow<SpotMapResult> =
        repository.snapshot
            .map<_, SpotMapResult> { SpotMapResult.DataChanged(it) }
            .onStart { if (!currentState.inputs.refresh.finished) refresh() }

    /** The spots, and the base map if it did not load. */
    private fun refreshAll() {
        refresh()
        if (currentState.baseMapFailed) dispatchResult(SpotMapResult.BaseMap.Retried)
    }

    /** One refresh at a time; a tap while one runs joins it. */
    private fun refresh() {
        if (currentState.inputs.refresh.refreshing) return
        dispatchResult(SpotMapResult.RefreshStarted)
        viewModelScope.launchFinishing(
            unfinished = AppError.Unknown("Refresh did not finish"),
            work = { repository.refresh() },
        ) { error -> dispatchResult(SpotMapResult.RefreshFinished(error)) }
    }

    /** Follows the selected spot as a favorite, unless it is one already. */
    private fun follow() {
        val inputs = currentState.inputs
        val spotId = inputs.selected ?: return
        val favorite =
            inputs.snapshot
                ?.mySpots
                .orEmpty()
                .any { it.spot.id == spotId && it.favorite }
        if (inputs.saving || favorite) return
        dispatchResult(SpotMapResult.Saving.Started)
        viewModelScope.launchFinishing(
            unfinished = AppError.Unknown("Following did not finish"),
            work = { repository.follow(spotId, inputs.driveMinutes.minutes) },
        ) { error -> finish(error) }
    }

    /** Saves the spot being placed or changed, once it has water and a name. */
    private fun save() {
        val inputs = currentState.inputs
        // The panel says when a spot can be saved: with water and a name, and no save running.
        val draft = inputs.draft?.takeIf { (currentState.panel as? SpotMapPanel.Draft)?.canSave == true }
        val water = draft?.water ?: return
        val name = draft.name.trim()
        dispatchResult(SpotMapResult.Saving.Started)
        val editing = inputs.editing
        if (editing != null) {
            viewModelScope.launchFinishing(
                unfinished = AppError.Unknown("Saving did not finish"),
                work = { repository.change(SpotEditEntity(editing, name, draft.pin, water)) },
            ) { error -> finish(error) }
        } else {
            viewModelScope.launchFinishing(
                unfinished = AddedSpotEntity(spotId = null, error = AppError.Unknown("Saving did not finish")),
                work = { repository.add(NewSpotEntity(name, draft.pin, water, inputs.driveMinutes.minutes), deviceZone) },
            ) { added ->
                // In the catalog but not followed: what is left is to follow it.
                if (added.error != null) added.spotId?.let { dispatchResult(SpotMapResult.Saving.Added(it)) }
                finish(added.error)
            }
        }
    }

    private fun finish(error: AppError?) {
        if (error == null) postEffect(SpotMapEffect.Finished) else dispatchResult(SpotMapResult.Saving.Failed(error))
    }
}
