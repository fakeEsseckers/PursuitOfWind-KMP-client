package io.github.esseckers.pursuitofwind.feature.today.presentation.viewmodel

import io.github.esseckers.pursuitofwind.feature.today.domain.model.RefreshResult
import io.github.esseckers.pursuitofwind.feature.today.domain.model.TodaySnapshot
import kotlin.time.Instant

/** What happened, as the reducer sees it: new cached data, a tick of the clock, a refresh starting or ending. */
internal sealed interface TodayResult {
    data class DataChanged(
        val snapshot: TodaySnapshot,
    ) : TodayResult

    data class ClockTicked(
        val now: Instant,
    ) : TodayResult

    data class RefreshStarted(
        val at: Instant,
    ) : TodayResult

    data class RefreshFinished(
        val result: RefreshResult,
    ) : TodayResult
}
