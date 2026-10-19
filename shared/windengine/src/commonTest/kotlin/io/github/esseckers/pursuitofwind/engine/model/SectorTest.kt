package io.github.esseckers.pursuitofwind.engine.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SectorTest {
    private val northern = Sector.of(300.0, 60.0)

    @Test
    fun `A7 a sector wrapping past north contains north and both bounds`() {
        listOf(0.0, 330.0, 300.0, 60.0).forEach { degrees ->
            assertTrue(Bearing(degrees) in northern, "$degrees° should be inside $northern")
        }
    }

    @Test
    fun `A8 a sector wrapping past north excludes the rest of the circle`() {
        listOf(61.0, 299.0, 180.0).forEach { degrees ->
            assertFalse(Bearing(degrees) in northern, "$degrees° should be outside $northern")
        }
    }

    @Test
    fun `A9 a sector that does not wrap`() {
        val southEast = Sector.of(90.0, 180.0)

        assertTrue(Bearing(135.0) in southEast)
        assertFalse(Bearing(200.0) in southEast)
    }

    @Test
    fun `A10 equal bounds are rejected`() {
        assertFailsWith<IllegalArgumentException> { Sector.of(45.0, 45.0) }
        assertFailsWith<IllegalArgumentException> { Sector.of(0.0, 360.0) }
    }

    @Test
    fun `directions a rounding error away from a bound still count`() {
        val southEast = Sector.of(60.0, 150.0)

        assertTrue(Bearing(59.99999999999999) in southEast)
        assertTrue(Bearing(150.00000000000003) in southEast)
        assertFalse(Bearing(59.999) in southEast)
        assertTrue(Bearing(359.99999999999994) in Sector.of(0.0, 90.0))
    }

    @Test
    fun `width is measured clockwise`() {
        assertEquals(120.0, northern.widthDegrees)
        assertEquals(240.0, Sector.of(60.0, 300.0).widthDegrees)
    }
}
