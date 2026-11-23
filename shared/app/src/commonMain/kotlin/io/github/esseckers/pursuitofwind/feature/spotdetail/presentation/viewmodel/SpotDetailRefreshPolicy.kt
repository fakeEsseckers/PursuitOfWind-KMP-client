package io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.viewmodel

import io.github.esseckers.pursuitofwind.core.refresh.RefreshStatus
import io.github.esseckers.pursuitofwind.core.ui.Freshness
import io.github.esseckers.pursuitofwind.feature.spotdetail.domain.entity.SpotDetailSnapshotEntity
import kotlin.time.Instant

/** When the spot detail screen refreshes the forecast as it appears. */
internal object SpotDetailRefreshPolicy {
    /**
     * True when the cached forecast is missing or older than [Freshness.REFRESH_AFTER], or the last refresh here failed.
     * A forecast stamped later than [now] (the device clock was set back) counts as old. Opening the screen right
     * after Today refreshed fetches nothing. Nor does a spot the rider does not follow: the screen has nothing to show
     * for it, and a link from outside the app can name any spot.
     */
    fun isDue(
        snapshot: SpotDetailSnapshotEntity,
        refresh: RefreshStatus,
        now: Instant,
    ): Boolean {
        val fetchedAt = snapshot.forecast.updatedAt
        val stale = fetchedAt == null || now < fetchedAt || now - fetchedAt >= Freshness.REFRESH_AFTER
        return snapshot.spot.value != null && (stale || refresh.error != null || snapshot.forecast.value.isEmpty())
    }
}
