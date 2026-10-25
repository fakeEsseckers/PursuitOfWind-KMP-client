package io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.viewmodel

import io.github.esseckers.pursuitofwind.core.refresh.RefreshNotice
import io.github.esseckers.pursuitofwind.core.refresh.RefreshStatus
import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.feature.spotdetail.BERLIN
import io.github.esseckers.pursuitofwind.feature.spotdetail.NORDSTRAND
import io.github.esseckers.pursuitofwind.feature.spotdetail.SATURDAY
import io.github.esseckers.pursuitofwind.feature.spotdetail.SUNDAY
import io.github.esseckers.pursuitofwind.feature.spotdetail.at
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.Selection
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.SpotDetailInputs
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.SpotDetailState
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.SpotDetailState.Content
import io.github.esseckers.pursuitofwind.feature.spotdetail.snapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class SpotDetailReducerTest {
    private val reducer = SpotDetailReducer(BERLIN)
    private val now = at(SATURDAY, 8, 20)

    private val opened = SpotDetailState(SpotDetailInputs(NORDSTRAND))
    private val ready = opened.after(SpotDetailResult.DataChanged(snapshot()), SpotDetailResult.ClockTicked(now))

    private fun SpotDetailState.after(vararg results: SpotDetailResult): SpotDetailState = results.fold(this, reducer::reduce)

    @Test
    fun `the screen loads until both the data and the clock have arrived`() {
        assertEquals(Content.Loading, opened.after(SpotDetailResult.DataChanged(snapshot())).content)
        assertIs<Content.Ready>(ready.content)
    }

    @Test
    fun `a refresh is shown from its start to its end and its error stays until the next one ends`() {
        val started = ready.after(SpotDetailResult.RefreshStarted(at = now))
        val failed = started.after(SpotDetailResult.RefreshFinished(AppError.Network))
        val retrying = failed.after(SpotDetailResult.RefreshStarted(at = now + 1.minutes))
        val worked = retrying.after(SpotDetailResult.RefreshFinished(null))

        assertTrue(started.isRefreshing)
        assertEquals(RefreshStatus(finished = true, error = AppError.Network, startedAt = now), failed.inputs.refresh)
        assertEquals(RefreshNotice.OFFLINE, retrying.notice)
        assertFalse(worked.isRefreshing)
        assertEquals(null, worked.notice)
    }

    @Test
    fun `selecting an hour changes the details and keeps the chart as it was`() {
        val before = assertIs<Content.Ready>(ready.content)
        val after = assertIs<Content.Ready>(ready.after(SpotDetailResult.Selected(Selection.Hour(at(SATURDAY, 10)))).content)

        assertEquals(at(SATURDAY, 10), after.selected.start)
        assertSame(before.chart, after.chart)
        assertSame(before.windows, after.windows)
        assertSame(before.days, after.days)
    }

    @Test
    fun `selecting a day shows its best window or its first daylight hour`() {
        val sunday = assertIs<Content.Ready>(ready.after(SpotDetailResult.Selected(Selection.Day(SUNDAY))).content)

        assertEquals(at(SUNDAY, 8), sunday.selected.start)
    }

    @Test
    fun `results that change nothing on screen keep everything as it was`() {
        val again = ready.after(SpotDetailResult.DataChanged(snapshot()))

        assertSame(ready.content, again.content)
    }

    @Test
    fun `a minute moves the line of the current time and leaves the chart as it was`() {
        val before = assertIs<Content.Ready>(ready.content)
        val after = assertIs<Content.Ready>(ready.after(SpotDetailResult.ClockTicked(now + 1.minutes)).content)

        assertSame(before.chart, after.chart)
        assertTrue(after.nowOffset > before.nowOffset)
    }

    @Test
    fun `an hour that has passed leaves the chart`() {
        val later = ready.after(SpotDetailResult.ClockTicked(now + 1.hours))
        val before = assertIs<Content.Ready>(ready.content)
        val after = assertIs<Content.Ready>(later.content)

        assertNotSame(before.chart, after.chart)
        assertEquals(before.chart.hours.size - 1, after.chart.hours.size)
        assertEquals(
            at(SATURDAY, 9),
            after.chart.hours
                .first()
                .start,
        )
    }
}
