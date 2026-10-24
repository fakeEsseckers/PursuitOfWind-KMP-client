package io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.viewmodel

import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.feature.spotdetail.domain.entity.SpotDetailSnapshotEntity
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.Selection
import kotlin.time.Instant

/** What happened, as the reducer sees it. */
internal sealed interface SpotDetailResult {
    data class DataChanged(
        val snapshot: SpotDetailSnapshotEntity,
    ) : SpotDetailResult

    data class ClockTicked(
        val now: Instant,
    ) : SpotDetailResult

    data class RefreshStarted(
        val at: Instant,
    ) : SpotDetailResult

    /** A refresh ended; [error] is null when it worked. */
    data class RefreshFinished(
        val error: AppError?,
    ) : SpotDetailResult

    data class Selected(
        val selection: Selection,
    ) : SpotDetailResult
}
