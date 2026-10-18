package io.github.esseckers.pursuitofwind.engine.model

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Vectors A1–A3 from `.claude/skills/wind-domain-rules/reference.md`. */
class WindSpeedTest {
    @Test
    fun `A1 10 m per s is 19_43844 knots`() {
        assertClose(expected = 19.43844, actual = WindSpeed(10.0).toKnots(), tolerance = 1e-4)
    }

    @Test
    fun `A2 1 knot is 0_514444 m per s`() {
        assertClose(expected = 0.514444, actual = WindSpeed.knots(1.0).metersPerSecond, tolerance = 1e-6)
    }

    @Test
    fun `A3 Beaufort upper bounds are exclusive`() {
        assertEquals(0, WindSpeed(0.0).toBeaufort())
        assertEquals(4, WindSpeed(7.9).toBeaufort())
        assertEquals(5, WindSpeed(8.0).toBeaufort())
        assertEquals(11, WindSpeed(32.6).toBeaufort())
        assertEquals(12, WindSpeed(32.7).toBeaufort())
    }

    @Test
    fun `km per h round-trips through m per s`() {
        assertClose(expected = 36.0, actual = WindSpeed(10.0).toKmh(), tolerance = 1e-9)
        assertClose(expected = 10.0, actual = WindSpeed.kmh(36.0).metersPerSecond, tolerance = 1e-9)
    }

    @Test
    fun `speeds compare and sort by magnitude`() {
        assertTrue(WindSpeed.knots(18.0) < WindSpeed.knots(22.0))
        assertEquals(
            listOf(WindSpeed(2.0), WindSpeed(5.0), WindSpeed(9.0)),
            listOf(WindSpeed(9.0), WindSpeed(2.0), WindSpeed(5.0)).sorted(),
        )
    }

    @Test
    fun `string form states the unit`() {
        assertEquals("10.0 m/s", WindSpeed(10.0).toString())
    }

    @Test
    fun `negative or non-finite speeds are rejected`() {
        assertFailsWith<IllegalArgumentException> { WindSpeed(-0.1) }
        assertFailsWith<IllegalArgumentException> { WindSpeed(Double.NaN) }
        assertFailsWith<IllegalArgumentException> { WindSpeed(Double.POSITIVE_INFINITY) }
    }

    private fun assertClose(
        expected: Double,
        actual: Double,
        tolerance: Double,
    ) {
        assertTrue(abs(expected - actual) <= tolerance, "expected $expected ± $tolerance but was $actual")
    }
}
