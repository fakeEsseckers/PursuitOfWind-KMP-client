package io.github.esseckers.pursuitofwind.feature.today.presentation.model

import io.github.esseckers.pursuitofwind.core.refresh.RefreshStatus
import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.today.domain.entity.TodaySnapshotEntity
import kotlin.time.Instant

/**
 * What [TodayState] is computed from: the cached [snapshot], the time [now], and how the refresh is going. [snapshot]
 * and [now] are null until they first arrive. The refresh's error is the forecast's; [crewError] is why the crew's rides
 * could not be updated, and [refreshedFavorites] are the favorite spots the latest refresh was for, so a spot chosen
 * since then is fetched at once.
 */
internal data class TodayInputs(
    val snapshot: TodaySnapshotEntity? = null,
    val now: Instant? = null,
    val refresh: RefreshStatus = RefreshStatus(),
    val crewError: AppError? = null,
    val refreshedFavorites: Set<SpotId> = emptySet(),
)
