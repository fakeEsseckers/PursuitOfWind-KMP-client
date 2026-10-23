package io.github.esseckers.pursuitofwind.feature.today.presentation.mapper

import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.error_network
import io.github.esseckers.pursuitofwind.core.resources.error_no_forecast
import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.Cached
import io.github.esseckers.pursuitofwind.data.model.MySpot
import io.github.esseckers.pursuitofwind.data.model.Profile
import io.github.esseckers.pursuitofwind.engine.model.ForecastHour
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.today.BERLIN
import io.github.esseckers.pursuitofwind.feature.today.SUNDAY
import io.github.esseckers.pursuitofwind.feature.today.THURSDAY_8AM
import io.github.esseckers.pursuitofwind.feature.today.alex
import io.github.esseckers.pursuitofwind.feature.today.at
import io.github.esseckers.pursuitofwind.feature.today.domain.model.RefreshResult
import io.github.esseckers.pursuitofwind.feature.today.domain.model.TodaySnapshot
import io.github.esseckers.pursuitofwind.feature.today.mySpot
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.RefreshStatus
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.TodayInputs
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.TodayState
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.TodayState.Content
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.TodayState.Missing
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.TodayState.Notice
import io.github.esseckers.pursuitofwind.feature.today.saturdayOn
import io.github.esseckers.pursuitofwind.feature.today.todaySnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class TodayStateMapperTest {
    private val fetched = THURSDAY_8AM - 20.minutes
    private val nordstrand = mySpot("nordstrand-bay")
    private val refreshed = RefreshStatus(last = RefreshResult(), startedAt = fetched)

    private fun snapshot(
        profile: Cached<Profile?> = Cached(alex(), fetched),
        spots: Cached<List<MySpot>> = Cached(listOf(nordstrand), fetched),
        forecasts: Map<SpotId, Cached<List<ForecastHour>>> = mapOf(nordstrand.spot.id to Cached(saturdayOn(), fetched)),
    ) = todaySnapshot(fetched, profile, spots, forecasts)

    private fun state(
        snapshot: TodaySnapshot = snapshot(),
        now: Instant = THURSDAY_8AM,
        refresh: RefreshStatus = refreshed,
    ) = TodayStateMapper.map(TodayInputs(snapshot, now, refresh), BERLIN)

    private fun failed(
        forecastError: AppError? = null,
        crewError: AppError? = null,
    ) = RefreshStatus(last = RefreshResult(forecastError, crewError), startedAt = fetched)

    private val sundayNoon = at(SUNDAY, 12)

    private val neverFetched = snapshot(profile = Cached(null, null), spots = Cached(emptyList(), null), forecasts = emptyMap())

    @Test
    fun `before the data and the clock arrive the screen loads`() {
        val inputs = TodayInputs(refresh = RefreshStatus(refreshing = true))

        assertEquals(TodayState(Content.Loading, isRefreshing = true, inputs = inputs), TodayStateMapper.map(inputs, BERLIN))
        assertEquals(Content.Loading, TodayStateMapper.map(TodayInputs(snapshot = snapshot()), BERLIN).content)
    }

    @Test
    fun `before the first fetch the screen loads`() {
        val loading = state(neverFetched, refresh = RefreshStatus(refreshing = true))

        assertEquals(Content.Loading, loading.content)
        assertTrue(loading.isRefreshing)
        assertEquals(Content.Loading, state(neverFetched, refresh = RefreshStatus()).content)
    }

    @Test
    fun `the state keeps the inputs it was computed from`() {
        val inputs = TodayInputs(snapshot(), THURSDAY_8AM, refreshed)

        assertEquals(inputs, TodayStateMapper.map(inputs, BERLIN).inputs)
    }

    @Test
    fun `a failed first fetch shows the error and a new attempt shows loading again`() {
        val failure = failed(forecastError = AppError.Network)

        assertEquals(Content.Error(Res.string.error_network), state(neverFetched, refresh = failure).content)
        assertEquals(Content.Loading, state(neverFetched, refresh = failure.copy(refreshing = true)).content)
    }

    @Test
    fun `a refresh that brings no forecast says so instead of loading forever`() {
        val noHours = mapOf(nordstrand.spot.id to Cached(emptyList<ForecastHour>(), fetched))
        val onlyPastHours = mapOf(nordstrand.spot.id to Cached(saturdayOn(), fetched))

        assertEquals(Content.Error(Res.string.error_no_forecast), state(snapshot(forecasts = noHours)).content)
        assertEquals(Content.Error(Res.string.error_no_forecast), state(snapshot(forecasts = onlyPastHours), now = sundayNoon).content)
    }

    @Test
    fun `a finished refresh still loads until the cache shows it`() {
        val older = mapOf(nordstrand.spot.id to Cached(emptyList<ForecastHour>(), fetched - 1.hours))

        assertEquals(Content.Loading, state(snapshot(forecasts = emptyMap())).content)
        assertEquals(Content.Loading, state(snapshot(forecasts = older)).content)
    }

    @Test
    fun `a rider without a profile is asked for one`() {
        assertEquals(Content.Empty(Missing.PROFILE), state(snapshot(profile = Cached(null, fetched))).content)
    }

    @Test
    fun `a rider without a favorite spot is asked to pick one`() {
        val followedOnly = Cached(listOf(mySpot("nordstrand-bay", favorite = false)), fetched)

        assertEquals(Content.Empty(Missing.FAVORITE_SPOTS), state(snapshot(spots = followedOnly)).content)
    }

    @Test
    fun `cached decisions show how old the oldest forecast is`() {
        val salt = mySpot("salt-lagoon")
        val older = THURSDAY_8AM - 50.minutes
        val ready =
            state(
                snapshot(
                    spots = Cached(listOf(nordstrand, salt), fetched),
                    forecasts =
                        mapOf(
                            nordstrand.spot.id to Cached(saturdayOn(), fetched),
                            salt.spot.id to Cached(saturdayOn(), older),
                        ),
                ),
            )

        assertIs<Content.Ready>(ready.content)
        assertEquals(50.minutes, ready.updatedAgo)
        assertNull(ready.notice)
    }

    @Test
    fun `cached decisions stay when a refresh fails and a notice says why`() {
        assertEquals(Notice.OFFLINE, state(refresh = failed(forecastError = AppError.Network)).notice)
        assertEquals(Notice.REFRESH_FAILED, state(refresh = failed(forecastError = AppError.Server(503))).notice)
        assertIs<Content.Ready>(state(refresh = failed(forecastError = AppError.Network)).content)
    }

    @Test
    fun `a crew problem only says that the rides could not be updated`() {
        assertEquals(Notice.CREW_FAILED, state(refresh = failed(crewError = AppError.Network)).notice)
        assertEquals(Notice.OFFLINE, state(refresh = failed(AppError.Network, crewError = AppError.Network)).notice)
    }

    @Test
    fun `a forecast older than three hours is marked as getting old`() {
        assertNull(state(now = fetched + 3.hours).notice)
        assertEquals(Notice.STALE, state(now = fetched + 3.hours + 1.minutes).notice)
        assertEquals(Notice.STALE, state(now = fetched + 4.hours, refresh = failed(crewError = AppError.Network)).notice)
    }
}
