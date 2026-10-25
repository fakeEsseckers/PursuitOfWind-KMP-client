package io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.viewmodel

import io.github.esseckers.pursuitofwind.core.refresh.RefreshStatus
import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.feature.spotdetail.SATURDAY
import io.github.esseckers.pursuitofwind.feature.spotdetail.at
import io.github.esseckers.pursuitofwind.feature.spotdetail.domain.entity.SpotDetailSnapshotEntity
import io.github.esseckers.pursuitofwind.feature.spotdetail.snapshot
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class SpotDetailRefreshPolicyTest {
    private val now = at(SATURDAY, 8, 20)

    private fun due(
        snapshot: SpotDetailSnapshotEntity = snapshot(fetched = now - 10.minutes),
        refresh: RefreshStatus = RefreshStatus(),
    ) = SpotDetailRefreshPolicy.isDue(snapshot, refresh, now)

    @Test
    fun `a fresh forecast is not fetched again`() {
        assertFalse(due())
        assertFalse(due(snapshot(fetched = now - 29.minutes)))
    }

    @Test
    fun `a missing old or failed forecast is fetched`() {
        assertTrue(due(snapshot(fetched = now - 30.minutes)))
        assertTrue(due(snapshot(fetched = now - 10.minutes, hours = emptyList())))
        assertTrue(due(refresh = RefreshStatus(finished = true, error = AppError.Network)))
        val neverFetched = snapshot().let { it.copy(forecast = it.forecast.copy(updatedAt = null)) }
        assertTrue(due(neverFetched))
    }

    @Test
    fun `a forecast from the future after the clock was set back is fetched`() {
        assertTrue(due(snapshot(fetched = now + 5.minutes)))
    }
}
