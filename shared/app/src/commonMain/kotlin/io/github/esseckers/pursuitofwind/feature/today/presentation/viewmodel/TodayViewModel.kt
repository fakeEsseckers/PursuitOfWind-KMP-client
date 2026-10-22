package io.github.esseckers.pursuitofwind.feature.today.presentation.viewmodel

import androidx.lifecycle.viewModelScope
import io.github.esseckers.pursuitofwind.core.mvi.MviViewModel
import io.github.esseckers.pursuitofwind.feature.today.domain.repository.TodayRepository
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.TodayIntent
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.TodayState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Today's decisions in MVI. While the screen is shown, the cached data and the ticking [clock] come in as results;
 * the screen refreshes as it appears when [TodayRefreshPolicy] says so, and on [TodayIntent.Refresh]. Decisions are
 * recomputed every [tick], so "leave by" and "updated 5 min ago" stay true; demo mode passes a controllable clock.
 * Today has no one-off effects: a failed refresh is part of the state.
 */
internal class TodayViewModel(
    private val repository: TodayRepository,
    private val clock: Clock,
    riderZone: TimeZone,
    private val tick: Duration = 1.minutes,
) : MviViewModel<TodayState, TodayIntent, TodayResult, Nothing>(TodayState(), TodayReducer(riderZone)) {
    override fun onIntent(intent: TodayIntent) {
        when (intent) {
            TodayIntent.Refresh -> refresh()
        }
    }

    override fun bind(): Flow<TodayResult> =
        merge(
            repository.snapshot.map { TodayResult.DataChanged(it) },
            ticks().map { TodayResult.ClockTicked(it) },
        ).onStart { refreshIfDue() }

    private suspend fun refreshIfDue() {
        if (TodayRefreshPolicy.isDue(repository.snapshot.first(), currentState.inputs.refresh, clock.now())) refresh()
    }

    /** One refresh at a time; a pull while one runs joins it. */
    private fun refresh() {
        if (currentState.inputs.refresh.refreshing) return
        // Cache stamps are stored in milliseconds; a finer start would never count as reflected in them.
        dispatch(TodayResult.RefreshStarted(at = Instant.fromEpochMilliseconds(clock.now().toEpochMilliseconds())))
        viewModelScope.launch { dispatch(TodayResult.RefreshFinished(repository.refresh())) }
    }

    /** The clock's time now and after every [tick]. Repeated readings are dropped, so a stopped clock costs nothing. */
    private fun ticks(): Flow<Instant> =
        flow {
            while (true) {
                emit(clock.now())
                delay(tick)
            }
        }.distinctUntilChanged()
}
