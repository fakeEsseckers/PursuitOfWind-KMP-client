package io.github.esseckers.pursuitofwind.engine

import io.github.esseckers.pursuitofwind.engine.internal.idealKiteM2
import io.github.esseckers.pursuitofwind.engine.model.Bearing
import io.github.esseckers.pursuitofwind.engine.model.Sector
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Property tests with a fixed seed, so every run and every platform checks the same cases.
 * Setting the seed through [PropTestConfig] is still marked experimental in Kotest.
 */
@OptIn(ExperimentalKotest::class)
class HourPropertiesTest {
    private val config = PropTestConfig(seed = 20_260_718L)

    private fun finite(
        min: Double,
        max: Double,
    ) = Arb.double(min, max, includeNaNs = false)

    @Test
    fun `bearing normalization stays within 0 until 360 and is idempotent`() =
        runTest {
            checkAll(ITERATIONS, config, Arb.double(-1e6, 1e6, includeNaNs = false)) { degrees ->
                val bearing = Bearing(degrees)

                assertTrue(bearing.degrees >= 0.0 && bearing.degrees < 360.0, "$degrees normalized to $bearing")
                assertEquals(bearing, Bearing(bearing.degrees))
            }
        }

    @Test
    fun `sector containment agrees with walking the arc degree by degree`() =
        runTest {
            checkAll(ITERATIONS, config, Arb.int(0..359), Arb.int(0..359), Arb.int(0..359)) { from, to, probe ->
                if (from == to) return@checkAll
                val walked = generateSequence(from) { (it + 1) % 360 }.takeWhile { it != to }.toSet() + to

                assertEquals(
                    probe in walked,
                    Bearing(probe.toDouble()) in Sector.of(from.toDouble(), to.toDouble()),
                    "Sector $from..$to, probe $probe",
                )
            }
        }

    @Test
    fun `more wind with the same gust spread never asks for a bigger kite`() =
        runTest {
            checkAll(ITERATIONS, config, finite(1.0, 45.0), finite(0.1, 10.0), finite(0.0, 15.0)) { meanKn, extraKn, spreadKn ->
                val lighter = idealKiteM2(80.0, meanKn.kn, (meanKn + spreadKn).kn)
                val stronger = idealKiteM2(80.0, (meanKn + extraKn).kn, (meanKn + extraKn + spreadKn).kn)

                assertTrue(stronger < lighter, "$meanKn kn → $lighter m², ${meanKn + extraKn} kn → $stronger m²")
            }
        }
}

private const val ITERATIONS = 1_000
