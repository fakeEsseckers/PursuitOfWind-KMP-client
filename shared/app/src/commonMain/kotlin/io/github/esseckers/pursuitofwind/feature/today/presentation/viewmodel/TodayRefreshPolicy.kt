package io.github.esseckers.pursuitofwind.feature.today.presentation.viewmodel

import io.github.esseckers.pursuitofwind.core.ui.Freshness
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.today.domain.model.TodaySnapshot
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.RefreshStatus
import kotlin.time.Instant

/** When Today refreshes on its own: as it appears again, and when a favorite spot is new. */
internal object TodayRefreshPolicy {
    /**
     * True when the screen should refresh as it (re)appears: never refreshed, the last refresh failed, is older than
     * [Freshness.REFRESH_AFTER] or later than [now], or a favorite spot has no cached forecast yet.
     */
    fun isDue(
        snapshot: TodaySnapshot,
        refresh: RefreshStatus,
        now: Instant,
    ): Boolean {
        val startedAt = refresh.startedAt
        return startedAt == null ||
            refresh.last?.forecastError != null ||
            // A device clock set back makes the last refresh look like it is from the future.
            now < startedAt ||
            now - startedAt >= Freshness.REFRESH_AFTER ||
            snapshot.spots.value.any { it.favorite && snapshot.forecasts[it.spot.id]?.value.isNullOrEmpty() }
    }

    /**
     * True while the screen is shown when a favorite spot has no cached forecast and the last refresh was not for it:
     * the rider just chose it (in the spot picker). A favorite the last refresh failed to fetch waits for a pull or
     * for [isDue], so an offline device does not refresh in a loop.
     */
    fun hasNewFavorite(
        snapshot: TodaySnapshot,
        refresh: RefreshStatus,
    ): Boolean =
        !refresh.refreshing &&
            snapshot.spots.value.any {
                it.favorite && it.spot.id !in refresh.favorites && snapshot.forecasts[it.spot.id]?.value.isNullOrEmpty()
            }

    /** The favorite spots of [snapshot], which a refresh started now fetches. */
    fun favorites(snapshot: TodaySnapshot?): Set<SpotId> =
        snapshot
            ?.spots
            ?.value
            .orEmpty()
            .filter { it.favorite }
            .map { it.spot.id }
            .toSet()
}
