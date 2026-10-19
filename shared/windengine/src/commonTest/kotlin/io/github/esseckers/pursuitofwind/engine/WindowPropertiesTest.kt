package io.github.esseckers.pursuitofwind.engine

import io.github.esseckers.pursuitofwind.engine.internal.windowRuns
import io.github.esseckers.pursuitofwind.engine.model.Verdict
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Seeded like [HourPropertiesTest]. */
@OptIn(ExperimentalKotest::class)
class WindowPropertiesTest {
    private val config = PropTestConfig(seed = 20_260_718L)

    @Test
    fun `windows never overlap and always start and end on GO`() =
        runTest {
            checkAll(ITERATIONS, config, Arb.list(Arb.enum<Verdict>(), 0..48)) { verdicts ->
                val runs = windowRuns(verdicts)

                runs.zipWithNext().forEach { (earlier, later) -> assertTrue(earlier.last + 1 < later.first, "$runs touch or overlap") }
                runs.forEach { run ->
                    assertEquals(Verdict.GO, verdicts[run.first], "$verdicts → $runs")
                    assertEquals(Verdict.GO, verdicts[run.last], "$verdicts → $runs")
                    assertTrue(verdicts.slice(run).none { it == Verdict.NO_GO }, "$verdicts → $runs")
                }
            }
        }

    @Test
    fun `every GO hour belongs to exactly one window`() =
        runTest {
            checkAll(ITERATIONS, config, Arb.list(Arb.enum<Verdict>(), 0..48)) { verdicts ->
                val covered = windowRuns(verdicts).flatMap { it.toList() }

                assertEquals(covered.size, covered.toSet().size)
                assertEquals(
                    verdicts.indices.filter { verdicts[it] == Verdict.GO }.toSet(),
                    covered.filter { verdicts[it] == Verdict.GO }.toSet(),
                )
            }
        }
}

private const val ITERATIONS = 1_000
