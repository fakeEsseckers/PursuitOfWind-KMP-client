package io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.viewmodel

import androidx.lifecycle.viewModelScope
import io.github.esseckers.pursuitofwind.core.launchFinishing
import io.github.esseckers.pursuitofwind.core.mvi.MviViewModel
import io.github.esseckers.pursuitofwind.core.ticks
import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.spotdetail.domain.repository.SpotDetailRepository
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.Selection
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.SpotDetailInputs
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.SpotDetailIntent
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.SpotDetailState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * One spot's hourly forecast in MVI. Opened at [date] (local to the spot), it shows that day's best window first;
 * without a date, the first window worth riding. While the screen is shown, the cached data and the ticking [clock]
 * come in as results, so passed hours leave the chart. The forecast is refreshed as the screen appears when
 * [SpotDetailRefreshPolicy] says so, and on [SpotDetailIntent.Refresh]. No one-off effects: going back is the view's.
 */
internal class SpotDetailViewModel(
    private val repository: SpotDetailRepository,
    private val spotId: SpotId,
    private val clock: Clock,
    date: LocalDate?,
    riderZone: TimeZone,
) : MviViewModel<SpotDetailState, SpotDetailIntent, SpotDetailResult, Nothing>(
        initialState = SpotDetailState(SpotDetailInputs(spotId, selection = date?.let(Selection::Day) ?: Selection.First)),
        reducer = SpotDetailReducer(riderZone),
    ) {
    override fun onIntent(intent: SpotDetailIntent) {
        when (intent) {
            SpotDetailIntent.Refresh -> refresh()
            is SpotDetailIntent.SelectHour -> dispatchResult(SpotDetailResult.Selected(Selection.Hour(intent.start)))
            is SpotDetailIntent.SelectDay -> dispatchResult(SpotDetailResult.Selected(Selection.Day(intent.date)))
        }
    }

    override fun bind(): Flow<SpotDetailResult> =
        merge(
            repository.snapshot(spotId).map { SpotDetailResult.DataChanged(it) },
            clock.ticks(TICK).map { SpotDetailResult.ClockTicked(it) },
        ).onStart { refreshIfDue() }

    private suspend fun refreshIfDue() {
        if (SpotDetailRefreshPolicy.isDue(repository.snapshot(spotId).first(), currentState.inputs.refresh, clock.now())) refresh()
    }

    /** One refresh at a time; a pull while one runs joins it. */
    private fun refresh() {
        if (currentState.inputs.refresh.refreshing) return
        // Cache stamps are stored in milliseconds; a finer start would never count as reflected in them.
        dispatchResult(SpotDetailResult.RefreshStarted(at = Instant.fromEpochMilliseconds(clock.now().toEpochMilliseconds())))
        viewModelScope.launchFinishing(
            unfinished = AppError.Unknown("Refresh did not finish"),
            work = { repository.refresh(spotId) },
        ) { error -> dispatchResult(SpotDetailResult.RefreshFinished(error)) }
    }

    private companion object {
        /** How often the hours are worked out again, so the window and "now" on the chart stay true. */
        val TICK = 1.minutes
    }
}
