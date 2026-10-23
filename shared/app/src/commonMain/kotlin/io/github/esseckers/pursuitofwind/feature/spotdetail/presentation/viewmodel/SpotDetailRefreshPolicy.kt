package io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.viewmodel

import io.github.esseckers.pursuitofwind.core.ui.Freshness
import io.github.esseckers.pursuitofwind.feature.spotdetail.domain.model.SpotDetailSnapshot
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.RefreshStatus
import kotlin.time.Instant

/** When the spot detail screen refreshes the forecast as it appears. */
internal object SpotDetailRefreshPolicy {
    /**
     * True when the cached forecast is missing or older than [Freshness.REFRESH_AFTER], or the last refresh here failed.
     * A forecast stamped later than [now] (the device clock was set back) counts as old. Opening the screen right
     * after Today refreshed fetches nothing.
     */
    fun isDue(
        snapshot: SpotDetailSnapshot,
        refresh: RefreshStatus,
        now: Instant,
    ): Boolean {
        val fetchedAt = snapshot.forecast.updatedAt
        return fetchedAt == null ||
            refresh.error != null ||
            now < fetchedAt ||
            now - fetchedAt >= Freshness.REFRESH_AFTER ||
            snapshot.forecast.value.isEmpty()
    }
}
