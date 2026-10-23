package io.github.esseckers.pursuitofwind.feature.today.presentation.viewmodel

import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.feature.today.BERLIN
import io.github.esseckers.pursuitofwind.feature.today.THURSDAY_8AM
import io.github.esseckers.pursuitofwind.feature.today.domain.model.RefreshResult
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.RefreshStatus
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.TodayState
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.TodayState.Content
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.TodayState.Notice
import io.github.esseckers.pursuitofwind.feature.today.todaySnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class TodayReducerTest {
    private val reducer = TodayReducer(BERLIN)

    private fun TodayState.after(vararg results: TodayResult): TodayState = results.fold(this, reducer::reduce)

    @Test
    fun `the screen loads until both the data and the clock have arrived`() {
        val dataOnly = TodayState().after(TodayResult.DataChanged(todaySnapshot()))
        val both = dataOnly.after(TodayResult.ClockTicked(THURSDAY_8AM))

        assertEquals(Content.Loading, dataOnly.content)
        assertIs<Content.Ready>(both.content)
    }

    @Test
    fun `a refresh is shown from its start to its end`() {
        val started = TodayState().after(TodayResult.RefreshStarted(at = THURSDAY_8AM))
        val finished = started.after(TodayResult.RefreshFinished(RefreshResult()))

        assertTrue(started.isRefreshing)
        assertEquals(RefreshStatus(refreshing = true, startedAt = THURSDAY_8AM), started.inputs.refresh)
        assertFalse(finished.isRefreshing)
        assertEquals(RefreshStatus(last = RefreshResult(), startedAt = THURSDAY_8AM), finished.inputs.refresh)
    }

    @Test
    fun `a failed refresh over cached answers becomes a notice`() {
        val state =
            TodayState().after(
                TodayResult.DataChanged(todaySnapshot()),
                TodayResult.ClockTicked(THURSDAY_8AM),
                TodayResult.RefreshStarted(at = THURSDAY_8AM),
                TodayResult.RefreshFinished(RefreshResult(forecastError = AppError.Network)),
            )

        assertIs<Content.Ready>(state.content)
        assertEquals(Notice.OFFLINE, state.notice)
    }

    @Test
    fun `a tick of the clock updates the age of the forecast`() {
        val ready = TodayState().after(TodayResult.DataChanged(todaySnapshot()), TodayResult.ClockTicked(THURSDAY_8AM))
        val later = ready.after(TodayResult.ClockTicked(THURSDAY_8AM + 10.minutes))

        assertEquals(20.minutes, ready.updatedAgo)
        assertEquals(30.minutes, later.updatedAgo)
    }

    @Test
    fun `a new refresh keeps the result of the last one until it finishes`() {
        val offline =
            TodayState().after(
                TodayResult.DataChanged(todaySnapshot()),
                TodayResult.ClockTicked(THURSDAY_8AM),
                TodayResult.RefreshFinished(RefreshResult(forecastError = AppError.Network)),
            )
        val retrying = offline.after(TodayResult.RefreshStarted(at = THURSDAY_8AM + 1.minutes))

        assertEquals(RefreshResult(forecastError = AppError.Network), retrying.inputs.refresh.last)
        assertEquals(Notice.OFFLINE, retrying.notice)
    }

    @Test
    fun `results that change nothing on screen keep the cards as they were`() {
        val ready = TodayState().after(TodayResult.DataChanged(todaySnapshot()), TodayResult.ClockTicked(THURSDAY_8AM))
        val later = ready.after(TodayResult.DataChanged(todaySnapshot()), TodayResult.ClockTicked(THURSDAY_8AM + 1.minutes))

        assertNotEquals(ready, later)
        assertSame(ready.content, later.content)
    }
}
