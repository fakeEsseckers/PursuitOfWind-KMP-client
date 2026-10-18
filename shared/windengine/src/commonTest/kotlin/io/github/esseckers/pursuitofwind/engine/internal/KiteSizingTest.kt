package io.github.esseckers.pursuitofwind.engine.internal

import io.github.esseckers.pursuitofwind.engine.kn
import io.github.esseckers.pursuitofwind.engine.model.Reason
import io.github.esseckers.pursuitofwind.engine.model.Verdict
import io.github.esseckers.pursuitofwind.engine.model.WindSpeed
import kotlin.test.Test
import kotlin.test.assertEquals

class KiteSizingTest {
    private data class Vector(
        val id: String,
        val weightKg: Double,
        val meanKn: Int,
        val gustKn: Int,
        val quiver: List<Double>,
        val ideal: Double,
        val expected: KiteSizing,
    )

    private val standardQuiver = listOf(7.0, 9.0, 12.0)

    private fun go(kite: Double) = KiteSizing(kiteM2 = kite, verdict = Verdict.GO, reason = null)

    private val vectors =
        listOf(
            Vector("B1", 80.0, 18, 24, standardQuiver, ideal = 8.8889, expected = go(9.0)),
            Vector("B2", 80.0, 14, 18, standardQuiver, ideal = 11.5789, expected = go(12.0)),
            Vector("B3", 80.0, 26, 30, standardQuiver, ideal = 6.4706, expected = go(7.0)),
            Vector(
                "B4",
                80.0,
                11,
                13,
                standardQuiver,
                ideal = 15.1724,
                expected = KiteSizing(null, Verdict.NO_GO, Reason.UNDERPOWERED_FOR_QUIVER),
            ),
            Vector(
                "B5",
                60.0,
                30,
                34,
                standardQuiver,
                ideal = 4.2308,
                expected = KiteSizing(null, Verdict.NO_GO, Reason.OVERPOWERED_FOR_QUIVER),
            ),
            Vector("B6", 84.0, 17, 19, listOf(9.0, 12.0), ideal = 10.5, expected = go(9.0)),
            Vector(
                "B7",
                80.0,
                18,
                24,
                listOf(7.0, 12.0),
                ideal = 8.8889,
                expected = KiteSizing(7.0, Verdict.MARGINAL, Reason.UNDERPOWERED_FOR_QUIVER),
            ),
            Vector("B8", 80.0, 20, 31, standardQuiver, ideal = 7.5536, expected = go(7.0)),
        )

    @Test
    fun `B1 to B8 ideal size and the kite picked from the quiver`() {
        vectors.forEach { vector ->
            val mean = vector.meanKn.kn
            val gust = vector.gustKn.kn

            assertEquals(vector.ideal, idealKiteM2(vector.weightKg, mean, gust), 1e-4, "${vector.id} ideal size")
            assertEquals(vector.expected, sizeKite(vector.weightKg, vector.quiver, mean, gust), vector.id)
        }
    }

    @Test
    fun `B9 an empty quiver skips sizing`() {
        assertEquals(KiteSizing(null, Verdict.GO, null), sizeKite(80.0, emptyList(), 18.kn, 24.kn))
    }

    @Test
    fun `quiver order does not matter`() {
        assertEquals(go(9.0), sizeKite(80.0, listOf(12.0, 9.0, 7.0), 18.kn, 24.kn))
    }

    @Test
    fun `no wind at all needs an infinitely large kite`() {
        val calm = WindSpeed(0.0)

        assertEquals(Double.POSITIVE_INFINITY, idealKiteM2(80.0, calm, calm))
        assertEquals(KiteSizing(null, Verdict.NO_GO, Reason.UNDERPOWERED_FOR_QUIVER), sizeKite(80.0, listOf(9.0), calm, calm))
    }
}
