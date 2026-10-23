package io.github.esseckers.pursuitofwind.data.repository

import io.github.esseckers.pursuitofwind.data.fake.THURSDAY_MORNING
import io.github.esseckers.pursuitofwind.engine.DecisionEngine
import io.github.esseckers.pursuitofwind.engine.model.ModelId
import io.github.esseckers.pursuitofwind.engine.model.SpotForecast
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class ForecastRepositoryTest {
    @Test
    fun `a refresh caches 72 hours from the start of the current hour`() =
        runTest {
            world {
                val alex = device()
                clock.advanceBy(20.minutes)

                alex.forecasts.refresh(SpotId("nordstrand-bay")).value()
                val cached = alex.forecasts.forecast(SpotId("nordstrand-bay")).first()

                assertEquals(72, cached.value.size)
                assertEquals(THURSDAY_MORNING, cached.value.first().start)
                assertEquals(THURSDAY_MORNING + 71.hours, cached.value.last().start)
                assertEquals(
                    setOf(ModelId.ECMWF, ModelId.ICON, ModelId.GFS),
                    cached.value
                        .first()
                        .byModel.keys,
                )
                assertEquals(THURSDAY_MORNING + 20.minutes, cached.updatedAt)
            }
        }

    @Test
    fun `pulling to refresh again brings the next model run`() =
        runTest {
            world {
                val alex = device()
                val nordstrand = SpotId("nordstrand-bay")

                alex.forecasts.refresh(nordstrand).value()
                val first = alex.forecasts.forecast(nordstrand).first()
                clock.advanceBy(1.minutes)
                alex.forecasts.refresh(nordstrand).value()
                val second = alex.forecasts.forecast(nordstrand).first()

                assertEquals(first.value.map { it.start }, second.value.map { it.start })
                assertNotEquals(first.value, second.value)
                assertEquals(THURSDAY_MORNING + 1.minutes, second.updatedAt)
            }
        }

    @Test
    fun `cached profile spots and forecasts go into the decision engine unchanged`() =
        runTest {
            world {
                val alex = device()
                alex.profiles.save(ALEX).value()
                listOf("nordstrand-bay", "salt-lagoon").forEach { id ->
                    alex.spots.follow(SpotId(id), drive = 90.minutes, favorite = true).value()
                    alex.forecasts.refresh(SpotId(id)).value()
                }

                val rider =
                    requireNotNull(
                        alex.profiles.profile
                            .first()
                            .value,
                    ).rider
                val forecasts =
                    alex.spots.mySpots.first().value.map { mySpot ->
                        SpotForecast(
                            mySpot.toSpotConfig(),
                            alex.forecasts
                                .forecast(mySpot.spot.id)
                                .first()
                                .value,
                        )
                    }
                val days = DecisionEngine.decideDays(forecasts, rider, clock.now())

                assertTrue(days.size in 3..4, "one decision per local day of the forecast, was ${days.size}")
                assertEquals(days.map { it.date }.sorted(), days.map { it.date })
            }
        }
}
