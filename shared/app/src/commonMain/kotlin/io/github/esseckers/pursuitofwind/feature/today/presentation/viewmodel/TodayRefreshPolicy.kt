package io.github.esseckers.pursuitofwind.feature.today.presentation.viewmodel

import io.github.esseckers.pursuitofwind.feature.today.domain.model.TodaySnapshot
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.RefreshStatus
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** When Today refreshes as it appears again. */
internal object TodayRefreshPolicy {
    /** Coming back to the screen refreshes it when the last refresh is older than this. */
    val REFRESH_AFTER: Duration = 30.minutes

    /**
     * True when the screen should refresh as it (re)appears: never refreshed, the last refresh failed, is older than
     * [REFRESH_AFTER] or later than [now], or a favorite spot has no cached forecast yet.
     */
    fun isDue(
        snapshot: TodaySnapshot,
        refresh: RefreshStatus,
        now: Instant,
    ): Boolean {
        val startedAt = refresh.startedAt
        return startedAt == null ||
            refresh.last?.forecastError != null ||
            // A clock set back (demo time travel) makes the last refresh look like it is from the future.
            now < startedAt ||
            now - startedAt >= REFRESH_AFTER ||
            snapshot.spots.value.any { it.favorite && snapshot.forecasts[it.spot.id]?.value.isNullOrEmpty() }
    }
}
