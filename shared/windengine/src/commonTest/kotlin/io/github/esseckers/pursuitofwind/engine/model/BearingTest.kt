package io.github.esseckers.pursuitofwind.engine.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BearingTest {
    @Test
    fun `A4 to A6 angles are normalized to 0 until 360`() {
        assertEquals(350.0, Bearing(-10.0).degrees)
        assertEquals(10.0, Bearing(370.0).degrees)
        assertEquals(0.0, Bearing(360.0).degrees)
    }

    @Test
    fun `whole turns in either direction land on the same bearing`() {
        assertEquals(Bearing(45.0), Bearing(45.0 + 360.0 * 3))
        assertEquals(Bearing(45.0), Bearing(45.0 - 360.0 * 5))
    }

    @Test
    fun `values that round to a full turn and negative zero are north`() {
        assertEquals(0.0, Bearing(-1e-15).degrees)
        assertEquals("0.0°", Bearing(-0.0).toString())
    }

    @Test
    fun `non-finite angles are rejected`() {
        assertFailsWith<IllegalArgumentException> { Bearing(Double.NaN) }
        assertFailsWith<IllegalArgumentException> { Bearing(Double.NEGATIVE_INFINITY) }
    }
}
