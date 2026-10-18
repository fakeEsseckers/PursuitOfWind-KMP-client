package io.github.esseckers.pursuitofwind.engine.internal

import io.github.esseckers.pursuitofwind.engine.model.Verdict
import kotlin.test.Test
import kotlin.test.assertEquals

class WindowRunsTest {
    private data class Vector(
        val id: String,
        val sequence: String,
        val windows: List<IntRange>,
        val marginalHours: List<Int>,
    )

    private val vectors =
        listOf(
            Vector("D1", "N G G M G G N", windows = listOf(1..5), marginalHours = listOf(1)),
            Vector("D2", "N M G G N", windows = listOf(2..3), marginalHours = listOf(0)),
            Vector("D3", "G M M G", windows = listOf(0..0, 3..3), marginalHours = listOf(0, 0)),
            Vector("D4", "G M G M G", windows = listOf(0..4), marginalHours = listOf(2)),
            Vector("D5", "M G G M", windows = listOf(1..2), marginalHours = listOf(0)),
            Vector("D6", "N N N", windows = emptyList(), marginalHours = emptyList()),
        )

    @Test
    fun `D1 to D6 windows bridge isolated marginal hours only`() {
        vectors.forEach { vector ->
            val verdicts = verdicts(vector.sequence)

            val runs = windowRuns(verdicts)

            assertEquals(vector.windows, runs, vector.id)
            assertEquals(vector.marginalHours, runs.map { run -> verdicts.slice(run).count { it == Verdict.MARGINAL } }, vector.id)
        }
    }

    @Test
    fun `no hours means no windows`() {
        assertEquals(emptyList(), windowRuns(emptyList()))
    }

    private fun verdicts(sequence: String): List<Verdict> =
        sequence.split(' ').map { letter ->
            when (letter) {
                "G" -> Verdict.GO
                "M" -> Verdict.MARGINAL
                else -> Verdict.NO_GO
            }
        }
}
