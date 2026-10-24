package io.github.esseckers.pursuitofwind.feature.today.presentation.model

import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.today.domain.entity.RefreshResultEntity
import io.github.esseckers.pursuitofwind.feature.today.domain.entity.TodaySnapshotEntity
import kotlin.time.Instant

/**
 * What [TodayState] is computed from: the cached [snapshot], the time [now], and how the refresh is going. [snapshot]
 * and [now] are null until they first arrive.
 */
internal data class TodayInputs(
    val snapshot: TodaySnapshotEntity? = null,
    val now: Instant? = null,
    val refresh: RefreshStatus = RefreshStatus(),
)

/**
 * Where the refresh is: [refreshing] now, and how the last one ended ([last] is null before the first one ends).
 * [startedAt] is when the latest refresh started, so the state can tell whether the cache already shows it, and
 * [favorites] the favorite spots it was for, so a spot chosen since then is fetched at once.
 */
internal data class RefreshStatus(
    val refreshing: Boolean = false,
    val last: RefreshResultEntity? = null,
    val startedAt: Instant? = null,
    val favorites: Set<SpotId> = emptySet(),
)
