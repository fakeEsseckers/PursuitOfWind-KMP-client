package io.github.esseckers.pursuitofwind.data.fake

import io.github.esseckers.pursuitofwind.contract.ApiRoute
import io.github.esseckers.pursuitofwind.contract.GeoPointDto
import io.github.esseckers.pursuitofwind.contract.JoinCrewRequestDto
import io.github.esseckers.pursuitofwind.contract.RideListDto
import io.github.esseckers.pursuitofwind.contract.SectorDto
import io.github.esseckers.pursuitofwind.contract.SpotDto
import io.github.esseckers.pursuitofwind.contract.SpotForecastDto
import io.github.esseckers.pursuitofwind.contract.SpotPageDto
import io.github.esseckers.pursuitofwind.contract.SpotUpdateDto
import io.github.esseckers.pursuitofwind.data.fake.internal.PlaceForecasts
import io.github.esseckers.pursuitofwind.engine.DecisionEngine
import io.github.esseckers.pursuitofwind.engine.model.Bearing
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

/** Spots riders add: in the catalog for everyone, with weather of their own place. */
class RiderSpotsTest {
    @Test
    fun `a spot a rider adds is in everyone's catalog and has a forecast`() =
        runTest {
            val driver = fakeDriver()
            val alex = driver.signIn(installation = 1)
            val jonas = driver.signIn(installation = 2)

            val added = driver.send(ApiRoute.CREATE_SPOT, token = alex, value = SANDBANK).decode(SpotDto.serializer())
            val catalog = driver.call(ApiRoute.LIST_SPOT_CATALOG, token = jonas).decode(SpotPageDto.serializer())
            val forecast =
                driver
                    .get(ApiRoute.GET_SPOT_FORECAST, added.id, token = jonas, query = mapOf("hours" to "24"))
                    .decode(SpotForecastDto.serializer())

            assertEquals(added, catalog.items.single { it.id == added.id })
            assertEquals("Europe/Berlin", added.timeZone)
            assertEquals(24, forecast.hours.size)
        }

    @Test
    fun `a moved spot keeps its id and gets the sectors of its new water and the weather of its new place`() =
        runTest {
            val driver = fakeDriver()
            val alex = driver.signIn()
            val added = driver.send(ApiRoute.CREATE_SPOT, token = alex, value = SANDBANK).decode(SpotDto.serializer())
            val northern = driver.meanTemperature(added.id, alex)

            val update = SpotUpdateDto("Mistral Beach", MEDITERRANEAN, EAST)
            val moved = driver.send(ApiRoute.UPDATE_SPOT, added.id, token = alex, value = update).decode(SpotDto.serializer())
            val southern = driver.meanTemperature(added.id, alex)

            val sectors = DecisionEngine.sectorsFacing(Bearing(EAST))
            assertEquals(added.id, moved.id)
            assertEquals(added.timeZone, moved.timeZone)
            assertEquals(EAST, moved.waterBearingDeg)
            assertEquals(listOf(SectorDto(sectors.rideable.from.degrees, sectors.rideable.to.degrees)), moved.rideableSectors)
            assertEquals(listOf(SectorDto(sectors.danger.from.degrees, sectors.danger.to.degrees)), moved.dangerSectors)
            assertTrue(southern > northern + 2.0, "$northern °C in the north, $southern °C in the south")
        }

    @Test
    fun `crew members never ride to a spot a rider added`() =
        runTest {
            val clock = ControllableClock(THURSDAY_MORNING)
            val seed = Seed.default(clock.now()).let { it.copy(spots = it.spots.map { spot -> spot.copy(forecast = CALM) }) }
            val driver = fakeDriver(clock, seed)
            var token = driver.signIn()
            driver.send(ApiRoute.JOIN_CREW, token = token, value = JoinCrewRequestDto("WIND-4KQ7"))
            // Its water where its wind comes from, so it blows onshore there: the only spot with rideable wind.
            val onshore = PlaceForecasts.profile(SANDBANK.location, seed.randomSeed).bearingDeg
            val added =
                driver
                    .send(
                        ApiRoute.CREATE_SPOT,
                        token = token,
                        value = SANDBANK.copy(waterBearingDeg = onshore),
                    ).decode(SpotDto.serializer())

            repeat(LIVE_HOURS) {
                clock.advanceBy(1.hours)
                token = driver.signIn()
                driver.call(ApiRoute.LIST_MY_SPOTS, token = token)
            }
            val rides = driver.call(ApiRoute.LIST_CREW_RIDES, "crw_weekend", token = token).decode(RideListDto.serializer()).items

            assertTrue(rides.none { it.spotId == added.id }, rides.toString())
        }

    @Test
    fun `the same place always gets the same weather and the south is warmer`() {
        val sandbank = SANDBANK.location
        val fourMetresOff = GeoPointDto(sandbank.lat + 0.00004, sandbank.lon)

        assertEquals(PlaceForecasts.profile(sandbank, seed = 1), PlaceForecasts.profile(sandbank, seed = 1))
        assertEquals(PlaceForecasts.profile(sandbank, seed = 1), PlaceForecasts.profile(fourMetresOff, seed = 1))
        assertNotEquals(PlaceForecasts.profile(sandbank, seed = 1), PlaceForecasts.profile(MEDITERRANEAN, seed = 1))
        assertTrue(PlaceForecasts.profile(MEDITERRANEAN, seed = 1).temperatureC > PlaceForecasts.profile(sandbank, seed = 1).temperatureC)
    }

    /** The mean temperature of the next day at [spotId], as the fake forecasts it. */
    private suspend fun FakeApiDriver.meanTemperature(
        spotId: String,
        token: String,
    ): Double =
        get(ApiRoute.GET_SPOT_FORECAST, spotId, token = token, query = mapOf("hours" to "24"))
            .decode(SpotForecastDto.serializer())
            .hours
            .map { it.temperatureC }
            .average()

    private companion object {
        val MEDITERRANEAN = GeoPointDto(43.3, 5.4)
        const val EAST = 70.0
        const val LIVE_HOURS = 12

        /** No wind at all: nobody rides to a seeded spot. */
        val CALM =
            ForecastProfile(
                meanKn = 0.0,
                swingKn = 0.0,
                swingPeriodHours = 24,
                seaBreezeKn = 0.0,
                bearingDeg = 240.0,
                bearingSwingDeg = 0.0,
                gustFactor = 0.0,
                modelSpreadKn = 0.0,
                noiseKn = 0.0,
                rainyShare = 0.0,
                temperatureC = 18.0,
            )
    }
}
