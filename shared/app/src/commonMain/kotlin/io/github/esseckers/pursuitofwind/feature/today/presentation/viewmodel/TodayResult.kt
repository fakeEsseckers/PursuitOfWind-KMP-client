package io.github.esseckers.pursuitofwind.feature.today.presentation.viewmodel

import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.today.domain.entity.RefreshResultEntity
import io.github.esseckers.pursuitofwind.feature.today.domain.entity.TodaySnapshotEntity
import kotlin.time.Instant

/** What happened, as the reducer sees it: new cached data, a tick of the clock, a refresh starting or ending. */
internal sealed interface TodayResult {
    data class DataChanged(
        val snapshot: TodaySnapshotEntity,
    ) : TodayResult

    data class ClockTicked(
        val now: Instant,
    ) : TodayResult

    /** A refresh started [at], for the [favorites] cached at that moment. */
    data class RefreshStarted(
        val at: Instant,
        val favorites: Set<SpotId> = emptySet(),
    ) : TodayResult

    data class RefreshFinished(
        val result: RefreshResultEntity,
    ) : TodayResult
}
