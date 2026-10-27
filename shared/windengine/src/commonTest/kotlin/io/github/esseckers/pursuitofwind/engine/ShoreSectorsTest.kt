package io.github.esseckers.pursuitofwind.engine

import io.github.esseckers.pursuitofwind.engine.model.Bearing
import io.github.esseckers.pursuitofwind.engine.model.Sector
import io.github.esseckers.pursuitofwind.engine.model.ShoreSectors
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals

/** Rule 8: a spot's sectors from the direction of its water (vectors I1–I5). */
@OptIn(ExperimentalKotest::class)
class ShoreSectorsTest {
    private val config = PropTestConfig(seed = 20_261_027L)

    @Test
    fun `I1 water to the west`() {
        assertEquals(ShoreSectors(rideable = Sector.of(170.0, 10.0), danger = Sector.of(30.0, 150.0)), sectorsFacing(270.0))
    }

    @Test
    fun `I2 water to the north`() {
        assertEquals(ShoreSectors(rideable = Sector.of(260.0, 100.0), danger = Sector.of(120.0, 240.0)), sectorsFacing(0.0))
    }

    @Test
    fun `I3 wind from the water and along the shore is rideable and from the land dangerous`() {
        val east = sectorsFacing(90.0)

        assertEquals(Side.RIDEABLE, east.sideOf(90.0))
        assertEquals(Side.RIDEABLE, east.sideOf(190.0))
        assertEquals(Side.NEITHER, east.sideOf(200.0))
        assertEquals(Side.DANGER, east.sideOf(210.0))
        assertEquals(Side.DANGER, east.sideOf(270.0))
    }

    @Test
    fun `I4 water just west of north wraps both sectors the right way`() {
        assertEquals(ShoreSectors(rideable = Sector.of(250.0, 90.0), danger = Sector.of(110.0, 230.0)), sectorsFacing(350.0))
    }

    @Test
    fun `I5 for any water the wind's side depends only on how far it is from the water`() =
        runTest {
            checkAll(ITERATIONS, config, Arb.double(0.0, 360.0, includeNaNs = false), Arb.int(-180..180)) { water, offset ->
                val expected =
                    when {
                        abs(offset) <= RIDEABLE_DEGREES -> Side.RIDEABLE
                        abs(offset) >= DANGER_DEGREES -> Side.DANGER
                        else -> Side.NEITHER
                    }

                assertEquals(expected, sectorsFacing(water).sideOf(water + offset), "water $water°, wind $offset° off it")
            }
        }

    private fun sectorsFacing(water: Double): ShoreSectors = DecisionEngine.sectorsFacing(Bearing(water))

    private fun ShoreSectors.sideOf(wind: Double): Side {
        val bearing = Bearing(wind)
        val inRideable = bearing in rideable
        val inDanger = bearing in danger
        return when {
            inRideable && inDanger -> error("$bearing is in both sectors of $this")
            inRideable -> Side.RIDEABLE
            inDanger -> Side.DANGER
            else -> Side.NEITHER
        }
    }

    private enum class Side { RIDEABLE, DANGER, NEITHER }

    private companion object {
        const val ITERATIONS = 1_000
        const val RIDEABLE_DEGREES = 100
        const val DANGER_DEGREES = 120
    }
}
