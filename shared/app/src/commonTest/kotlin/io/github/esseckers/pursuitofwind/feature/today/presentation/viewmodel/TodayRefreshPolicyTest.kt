package io.github.esseckers.pursuitofwind.feature.today.presentation.viewmodel

import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.today.THURSDAY_8AM
import io.github.esseckers.pursuitofwind.feature.today.domain.model.RefreshResult
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.RefreshStatus
import io.github.esseckers.pursuitofwind.feature.today.todaySnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class TodayRefreshPolicyTest {
    private val now = THURSDAY_8AM

    private fun refreshedAgo(minutes: Int) = RefreshStatus(last = RefreshResult(), startedAt = now - minutes.minutes)

    @Test
    fun `the first appearance refreshes`() {
        assertTrue(TodayRefreshPolicy.isDue(todaySnapshot(), RefreshStatus(), now))
    }

    @Test
    fun `coming back refreshes after half an hour but not before`() {
        assertFalse(TodayRefreshPolicy.isDue(todaySnapshot(), refreshedAgo(29), now))
        assertTrue(TodayRefreshPolicy.isDue(todaySnapshot(), refreshedAgo(30), now))
    }

    @Test
    fun `coming back refreshes after a failed refresh or for a favorite without a forecast`() {
        val failed = refreshedAgo(1).copy(last = RefreshResult(forecastError = AppError.Network))

        assertTrue(TodayRefreshPolicy.isDue(todaySnapshot(), failed, now))
        assertTrue(TodayRefreshPolicy.isDue(todaySnapshot(forecasts = emptyMap()), refreshedAgo(1), now))
    }

    @Test
    fun `a clock set back before the last refresh refreshes again`() {
        assertTrue(TodayRefreshPolicy.isDue(todaySnapshot(), refreshedAgo(-60), now))
    }

    @Test
    fun `a favorite chosen since the last refresh is fetched at once`() {
        val chosen = todaySnapshot(forecasts = emptyMap())
        val nordstrand = SpotId("nordstrand-bay")

        assertTrue(TodayRefreshPolicy.hasNewFavorite(chosen, refreshedAgo(1)))
        assertFalse(TodayRefreshPolicy.hasNewFavorite(chosen, refreshedAgo(1).copy(refreshing = true)))
        assertFalse(TodayRefreshPolicy.hasNewFavorite(todaySnapshot(), refreshedAgo(1)))
        assertEquals(setOf(nordstrand), TodayRefreshPolicy.favorites(chosen))
    }

    @Test
    fun `a favorite the last refresh could not fetch waits so offline does not refresh in a loop`() {
        val failed =
            refreshedAgo(
                1,
            ).copy(favorites = setOf(SpotId("nordstrand-bay")), last = RefreshResult(forecastError = AppError.Network))

        assertFalse(TodayRefreshPolicy.hasNewFavorite(todaySnapshot(forecasts = emptyMap()), failed))
    }
}
