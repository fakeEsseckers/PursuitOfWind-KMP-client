package io.github.esseckers.pursuitofwind.data.fake

import io.github.esseckers.pursuitofwind.contract.ApiRoute
import io.github.esseckers.pursuitofwind.contract.SpotForecastDto
import kotlinx.coroutines.test.runTest
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
    fun `an hour looks the same whatever the request window`() =
        runTest {
            val driver = fakeDriver()
            val token = driver.signIn()

            val wide = driver.forecast(token, mapOf("hours" to "48"))
            val narrow = driver.forecast(token, mapOf("from" to "2026-07-17T12:30:00Z", "hours" to "3"))

            assertEquals(Instant.parse("2026-07-17T12:00:00Z"), narrow.hours.first().start)
            assertEquals(wide.hours.first { it.start == narrow.hours.first().start }, narrow.hours.first())
        }

    @Test
    fun `two servers with the same seed forecast the same wind`() =
        runTest {
            val first = fakeDriver()
            val second = fakeDriver()

            assertEquals(first.forecast(first.signIn()).hours, second.forecast(second.signIn()).hours)
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
}
