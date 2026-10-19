package io.github.esseckers.pursuitofwind.engine.model

import io.github.esseckers.pursuitofwind.engine.RIDER
import io.github.esseckers.pursuitofwind.engine.SPOT
import io.github.esseckers.pursuitofwind.engine.forecastHour
import io.github.esseckers.pursuitofwind.engine.kn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class ModelValidationTest {
    @Test
    fun `rider profile defaults to a 2 hour session and a 3 hour drive`() {
        assertEquals(2.hours, RIDER.minSession)
        assertEquals(180.minutes, RIDER.maxDrive)
    }

    @Test
    fun `rider profile rejects impossible values`() {
        assertFailsWith<IllegalArgumentException> { RIDER.copy(weightKg = 0.0) }
        assertFailsWith<IllegalArgumentException> { RIDER.copy(weightKg = Double.NaN) }
        assertFailsWith<IllegalArgumentException> { RIDER.copy(quiverM2 = listOf(9.0, -1.0)) }
        assertFailsWith<IllegalArgumentException> { RIDER.copy(minWind = 20.kn, maxWind = 20.kn) }
        assertFailsWith<IllegalArgumentException> { RIDER.copy(minSession = (-1).hours) }
        assertFailsWith<IllegalArgumentException> { RIDER.copy(maxDrive = (-1).minutes) }
    }

    @Test
    fun `spot config and spot id reject invalid values`() {
        assertFailsWith<IllegalArgumentException> { SPOT.copy(rideableSectors = emptyList()) }
        assertFailsWith<IllegalArgumentException> { SPOT.copy(drive = (-5).minutes) }
        assertFailsWith<IllegalArgumentException> { SpotId(" ") }
        assertEquals("north-beach", SPOT.id.toString())
    }

    @Test
    fun `forecast hour needs model data and sane weather`() {
        assertFailsWith<IllegalArgumentException> { forecastHour { models = emptyList() } }
        assertFailsWith<IllegalArgumentException> { forecastHour { precipMm = -0.1 } }
        assertFailsWith<IllegalArgumentException> { forecastHour { tempC = Double.NaN } }
        assertFailsWith<IllegalArgumentException> { ModelId("") }
        assertEquals("ecmwf", ModelId.ECMWF.toString())
    }

    @Test
    fun `spot ids order by value`() {
        assertEquals(listOf(SpotId("a"), SpotId("b")), listOf(SpotId("b"), SpotId("a")).sorted())
    }
}
