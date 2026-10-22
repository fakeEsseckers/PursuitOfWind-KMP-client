package io.github.esseckers.pursuitofwind.feature.today.presentation.model

import io.github.esseckers.pursuitofwind.feature.today.domain.model.RefreshResult
import io.github.esseckers.pursuitofwind.feature.today.domain.model.TodaySnapshot
import kotlin.time.Instant

/**
 * What [TodayState] is computed from: the cached [snapshot], the time [now], and how the refresh is going. [snapshot]
 * and [now] are null until they first arrive.
 */
internal data class TodayInputs(
    val snapshot: TodaySnapshot? = null,
    val now: Instant? = null,
    val refresh: RefreshStatus = RefreshStatus(),
)

/**
 * Where the refresh is: [refreshing] now, and how the last one ended ([last] is null before the first one ends).
 * [startedAt] is when the latest refresh started, so the state can tell whether the cache already shows it.
 */
internal data class RefreshStatus(
    val refreshing: Boolean = false,
    val last: RefreshResult? = null,
    val startedAt: Instant? = null,
)
