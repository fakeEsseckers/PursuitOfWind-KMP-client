package io.github.esseckers.pursuitofwind.feature.today.presentation.viewmodel

import androidx.lifecycle.viewModelScope
import io.github.esseckers.pursuitofwind.core.launchFinishing
import io.github.esseckers.pursuitofwind.core.mvi.MviViewModel
import io.github.esseckers.pursuitofwind.core.ticks
import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.feature.today.domain.entity.RefreshResultEntity
import io.github.esseckers.pursuitofwind.feature.today.domain.entity.TodaySnapshotEntity
import io.github.esseckers.pursuitofwind.feature.today.domain.repository.TodayRepository
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.TodayEffect
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.TodayIntent
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.TodayState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Today's decisions in MVI. While the screen is shown, the cached data and the ticking [clock] come in as results;
 * the screen refreshes as it appears when [TodayRefreshPolicy] says so, and on [TodayIntent.Refresh]. Decisions are
 * recomputed every [TICK], so "leave by" and "updated 5 min ago" stay true; tests pass a controllable clock.
 * Navigating (a spot, the profile, the spot picker) is a [TodayEffect]; a failed refresh is part of the state, not an effect.
 */
internal class TodayViewModel(
    private val repository: TodayRepository,
    private val clock: Clock,
    riderZone: TimeZone,
) : MviViewModel<TodayState, TodayIntent, TodayResult, TodayEffect>(TodayState(), TodayReducer(riderZone)) {
    override fun onIntent(intent: TodayIntent) {
        when (intent) {
            TodayIntent.Refresh -> refresh()
            is TodayIntent.OpenSpot -> postEffect(TodayEffect.OpenSpot(intent.spotId, intent.date))
            TodayIntent.SetUpProfile -> postEffect(TodayEffect.OpenProfile)
            TodayIntent.PickSpots -> postEffect(TodayEffect.PickSpots)
            is TodayIntent.OfferRide -> postEffect(TodayEffect.OfferRide(intent.offer))
        }
    }

    override fun bind(): Flow<TodayResult> =
        merge(
            repository.snapshot
                // A spot chosen in the spot picker while this screen stayed below it has no forecast yet.
                .onEach { if (TodayRefreshPolicy.hasNewFavorite(it, currentState.inputs.refresh)) refresh(it) }
                .map { TodayResult.DataChanged(it) },
            clock.ticks(TICK).map { TodayResult.ClockTicked(it) },
        ).onStart { refreshIfDue() }

    private suspend fun refreshIfDue() {
        val snapshot = repository.snapshot.first()
        if (TodayRefreshPolicy.isDue(snapshot, currentState.inputs.refresh, clock.now())) refresh(snapshot)
    }

    /** One refresh at a time; a pull while one runs joins it. */
    private fun refresh(snapshot: TodaySnapshotEntity? = currentState.inputs.snapshot) {
        if (currentState.inputs.refresh.refreshing) return
        // Cache stamps are stored in milliseconds; a finer start would never count as reflected in them.
        val at = Instant.fromEpochMilliseconds(clock.now().toEpochMilliseconds())
        dispatchResult(TodayResult.RefreshStarted(at = at, favorites = TodayRefreshPolicy.favorites(snapshot)))
        viewModelScope.launchFinishing(
            unfinished = RefreshResultEntity(forecastError = AppError.Unknown("Refresh did not finish")),
            work = { repository.refresh() },
        ) { result -> dispatchResult(TodayResult.RefreshFinished(result)) }
    }

    private companion object {
        /** How often the decisions are worked out again, so "leave by" and "updated 5 min ago" stay true. */
        val TICK = 1.minutes
    }
}
