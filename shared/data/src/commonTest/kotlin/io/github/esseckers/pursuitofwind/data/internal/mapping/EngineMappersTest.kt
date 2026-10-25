package io.github.esseckers.pursuitofwind.data.internal.mapping

import io.github.esseckers.pursuitofwind.contract.ForecastHourDto
import io.github.esseckers.pursuitofwind.contract.ModelWindDto
import io.github.esseckers.pursuitofwind.contract.SectorDto
import io.github.esseckers.pursuitofwind.contract.ThunderRiskDto
import io.github.esseckers.pursuitofwind.data.fake.Seed
import io.github.esseckers.pursuitofwind.data.fake.THURSDAY_MORNING
import io.github.esseckers.pursuitofwind.engine.model.Sector
import kotlin.test.Test
import kotlin.test.assertEquals

/** The engine sees the same spot and hour whether it reads them from the API or from the cache. */
class EngineMappersTest {
    private val hour =
        ForecastHourDto(
            start = THURSDAY_MORNING,
            models =
                listOf(
                    ModelWindDto("icon-d2", meanMps = 8.2, gustMps = 10.5, bearingDeg = 245.0),
                    ModelWindDto("ecmwf", meanMps = 7.4, gustMps = 9.8, bearingDeg = 250.0),
                ),
            precipitationMm = 0.2,
            temperatureC = 17.5,
            thunderRisk = ThunderRiskDto.MEDIUM,
            isDaylight = true,
        )

    @Test
    fun `an hour reads the same straight from the API and through the cache`() {
        assertEquals(hour.toForecastHour(), hour.toEntity("nordstrand-bay").toForecastHour())
    }

    @Test
    fun `every seeded spot reads the same straight from the API and through the cache`() {
        Seed.default(THURSDAY_MORNING).spots.forEach { seeded ->
            assertEquals(seeded.spot.toSpot(), seeded.spot.toEntity().toSpot(), seeded.spot.id)
        }
    }

    @Test
    fun `a sector across north keeps its ends`() {
        assertEquals(Sector.of(300.0, 60.0), SectorDto(300.0, 60.0).toSector())
    }
}
