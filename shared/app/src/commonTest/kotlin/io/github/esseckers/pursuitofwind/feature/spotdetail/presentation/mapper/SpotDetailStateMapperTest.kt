package io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.mapper

import io.github.esseckers.pursuitofwind.core.refresh.RefreshNotice
import io.github.esseckers.pursuitofwind.core.refresh.RefreshStatus
import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.error_network
import io.github.esseckers.pursuitofwind.core.resources.spot_no_forecast
import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.feature.spotdetail.BERLIN
import io.github.esseckers.pursuitofwind.feature.spotdetail.NORDSTRAND
import io.github.esseckers.pursuitofwind.feature.spotdetail.SATURDAY
import io.github.esseckers.pursuitofwind.feature.spotdetail.at
import io.github.esseckers.pursuitofwind.feature.spotdetail.domain.entity.SpotDetailSnapshotEntity
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.Selection
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.SpotDetailInputs
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.SpotDetailState.Content
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.SpotDetailState.Missing
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.SpotHeaderUi
import io.github.esseckers.pursuitofwind.feature.spotdetail.snapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class SpotDetailStateMapperTest {
    private val now = at(SATURDAY, 8, 20)
    private val fetched = now - 25.minutes
    private val done = RefreshStatus(finished = true, startedAt = fetched)

    private fun state(
        snapshot: SpotDetailSnapshotEntity? = snapshot(fetched),
        refresh: RefreshStatus = done,
        now: Instant? = this.now,
        selection: Selection = Selection.First,
    ) = SpotDetailStateMapper.map(SpotDetailInputs(NORDSTRAND, selection, snapshot, now, refresh), BERLIN)

    @Test
    fun `the screen loads until the data and the clock arrive`() {
        assertEquals(Content.Loading, state(snapshot = null).content)
        assertEquals(Content.Loading, state(now = null).content)
        assertTrue(state(snapshot = null, refresh = RefreshStatus(refreshing = true)).isRefreshing)
    }

    @Test
    fun `the forecast shows with the spot and how old it is`() {
        val ready = state()

        val content = assertIs<Content.Ready>(ready.content)
        assertEquals(SpotHeaderUi("Nordstrand Bay", 2.hours), ready.header)
        assertEquals(25.minutes, ready.updatedAgo)
        assertNull(ready.notice)
        assertEquals(content.chart.hours.indexOfFirst { it.start == at(SATURDAY, 13) }, content.selected.index)
    }

    @Test
    fun `a spot the rider does not follow and a missing profile say so`() {
        assertEquals(Content.Missing(Missing.SPOT), state(snapshot(fetched, spot = null)).content)
        assertNull(state(snapshot(fetched, spot = null)).header)
        assertEquals(Content.Missing(Missing.PROFILE), state(snapshot(fetched, profile = null)).content)
    }

    @Test
    fun `without hours the screen loads while a refresh runs and then shows why`() {
        val empty = snapshot(fetched, hours = emptyList())

        assertEquals(Content.Loading, state(empty, RefreshStatus()).content)
        assertEquals(Content.Loading, state(empty, RefreshStatus(refreshing = true, startedAt = now)).content)
        assertEquals(
            Content.Error(Res.string.error_network),
            state(empty, RefreshStatus(finished = true, error = AppError.Network)).content,
        )
        assertEquals(Content.Error(Res.string.spot_no_forecast), state(empty, done).content)
    }

    @Test
    fun `a refresh that worked still loads until the cache shows it`() {
        val older = snapshot(fetched - 1.hours, hours = emptyList())

        assertEquals(Content.Loading, state(older, done).content)
    }

    @Test
    fun `cached hours stay when a refresh fails and a notice says why`() {
        val offline = state(refresh = RefreshStatus(finished = true, error = AppError.Network))
        val failed = state(refresh = RefreshStatus(finished = true, error = AppError.Server(503)))

        assertIs<Content.Ready>(offline.content)
        assertEquals(RefreshNotice.OFFLINE, offline.notice)
        assertEquals(RefreshNotice.REFRESH_FAILED, failed.notice)
    }

    @Test
    fun `a forecast older than three hours is marked as getting old`() {
        assertNull(state(now = fetched + 3.hours).notice)
        assertEquals(RefreshNotice.STALE, state(now = fetched + 3.hours + 1.minutes).notice)
    }

    @Test
    fun `the selection picks the hour whose details are shown`() {
        val content = assertIs<Content.Ready>(state(selection = Selection.Hour(at(SATURDAY, 10))).content)

        assertEquals(at(SATURDAY, 10), content.selected.start)
    }
}
