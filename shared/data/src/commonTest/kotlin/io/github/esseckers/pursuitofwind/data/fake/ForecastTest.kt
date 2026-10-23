package io.github.esseckers.pursuitofwind.data.fake

import io.github.esseckers.pursuitofwind.contract.ApiRoute
import io.github.esseckers.pursuitofwind.contract.SpotForecastDto
import io.github.esseckers.pursuitofwind.engine.model.WindSpeed
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class ForecastTest {
    private suspend fun FakeApiDriver.forecast(
        token: String,
        query: Map<String, String> = emptyMap(),
    ): SpotForecastDto =
        get(ApiRoute.GET_SPOT_FORECAST, "nordstrand-bay", token = token, query = query).decode(SpotForecastDto.serializer())

    @Test
    fun `72 hourly slots by default with three models each starting on the current hour`() =
        runTest {
            val clock = ControllableClock(THURSDAY_MORNING + 25.minutes)
            val driver = fakeDriver(clock)

            val forecast = driver.forecast(driver.signIn())

            assertEquals(72, forecast.hours.size)
            assertEquals(THURSDAY_MORNING, forecast.hours.first().start)
            assertTrue(forecast.hours.zipWithNext().all { (a, b) -> b.start - a.start == 1.hours })
            assertTrue(forecast.hours.all { hour -> hour.models.map { it.model } == listOf("ecmwf", "icon", "gfs") })
        }

    @Test
    fun `without run drift an hour looks the same whatever the request window`() =
        runTest {
            val clock = ControllableClock(THURSDAY_MORNING)
            val driver = fakeDriver(clock, seed = steadyRuns(Seed.default(clock.now())))
            val token = driver.signIn()

            val wide = driver.forecast(token, mapOf("hours" to "48"))
            val narrow = driver.forecast(token, mapOf("from" to "2026-07-17T12:30:00Z", "hours" to "3"))

            assertEquals(Instant.parse("2026-07-17T12:00:00Z"), narrow.hours.first().start)
            assertEquals(wide.hours.first { it.start == narrow.hours.first().start }, narrow.hours.first())
        }

    @Test
    fun `two servers with the same seed answer the same runs`() =
        runTest {
            val first = fakeDriver()
            val second = fakeDriver()
            val firstToken = first.signIn()
            val secondToken = second.signIn()

            assertEquals(first.forecast(firstToken).hours, second.forecast(secondToken).hours)
            assertEquals(first.forecast(firstToken).hours, second.forecast(secondToken).hours)
        }

    @Test
    fun `every request is a new model run that moves the wind a little and the next hours least`() =
        runTest {
            val driver = fakeDriver()
            val token = driver.signIn()

            val before = driver.forecast(token).hours
            val after = driver.forecast(token).hours
            val changes =
                before.zip(after).map { (old, new) ->
                    old.models.zip(new.models).maxOf { (a, b) -> WindSpeed(abs(a.meanMps - b.meanMps)).toKnots() }
                }

            assertTrue(changes.any { it > 0.0 }, "A new run should change the forecast")
            changes.forEachIndexed { lead, change ->
                // Two runs each move the wind by at most 4 kn, scaled from a quarter now to all of it three days out.
                val limit = 2 * LIVE_DRIFT_KN * (0.25 + 0.75 * minOf(1.0, lead / 72.0)) + ROUNDING_KN
                assertTrue(change <= limit, "Hour $lead moved $change kn, more than $limit")
            }
            assertTrue(changes.take(6).average() < changes.takeLast(24).average(), "Hours far ahead should move more than the next ones")
        }

    @Test
    fun `a run is stamped with the time of the request`() =
        runTest {
            val clock = ControllableClock(THURSDAY_MORNING + 25.minutes)
            val driver = fakeDriver(clock)

            assertEquals(clock.now(), driver.forecast(driver.signIn()).generatedAt)
        }

    @Test
    fun `nights are not daylight and summer afternoons are`() =
        runTest {
            val driver = fakeDriver()
            val hours = driver.forecast(driver.signIn(), mapOf("from" to "2026-07-17T00:00:00Z", "hours" to "24")).hours

            assertTrue(!hours.single { it.start == Instant.parse("2026-07-17T01:00:00Z") }.isDaylight)
            assertTrue(hours.single { it.start == Instant.parse("2026-07-17T12:00:00Z") }.isDaylight)
            assertTrue(
                hours.all { hour ->
                    hour.models.all { it.meanMps >= 0 && it.gustMps >= it.meanMps && it.bearingDeg in 0.0..359.99 }
                },
            )
        }

    @Test
    fun `the forecast ends ten days out`() =
        runTest {
            val driver = fakeDriver()
            val hours = driver.forecast(driver.signIn(), mapOf("from" to "2026-07-25T00:00:00Z", "hours" to "168")).hours

            assertEquals(Instant.parse("2026-07-26T05:00:00Z"), hours.last().start)
        }

    private companion object {
        /** The default spots' run drift three days out. */
        const val LIVE_DRIFT_KN = 4.0

        /** Speeds travel as m/s rounded to hundredths. */
        const val ROUNDING_KN = 0.05

        fun steadyRuns(seed: Seed): Seed =
            seed.copy(spots = seed.spots.map { it.copy(forecast = it.forecast.copy(runDriftKn = 0.0, runDriftDeg = 0.0)) })
    }
}
