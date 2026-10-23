package io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.mapper

import io.github.esseckers.pursuitofwind.data.model.WindUnit
import io.github.esseckers.pursuitofwind.engine.model.WindSpeed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChartScaleTest {
    @Test
    fun `the top of the chart is the first round value above the strongest wind`() {
        val scale = chartScale(WindSpeed.knots(26.0).metersPerSecond, WindUnit.KNOTS)

        assertEquals(listOf(10, 20, 30), scale.grid.map { it.value })
        assertEquals(WindSpeed.knots(30.0).metersPerSecond, scale.maxMs, 1e-9)
    }

    @Test
    fun `a calm forecast is not stretched to fill the chart`() {
        assertEquals(listOf(5, 10), chartScale(1.0, WindUnit.METERS_PER_SECOND).grid.map { it.value })
        assertEquals(listOf(20, 40), chartScale(1.0, WindUnit.KILOMETERS_PER_HOUR).grid.map { it.value })
    }

    @Test
    fun `beaufort lines sit where every second force begins`() {
        val scale = chartScale(WindSpeed.knots(26.0).metersPerSecond, WindUnit.BEAUFORT)

        assertEquals(listOf(2, 4, 6, 8), scale.grid.map { it.value })
        scale.grid.forEach { line ->
            assertEquals(line.value, WindSpeed(line.ms).toBeaufort())
            assertEquals(line.value - 1, WindSpeed(line.ms - 0.05).toBeaufort())
        }
    }

    @Test
    fun `wind beyond the last beaufort line still fits`() {
        val scale = chartScale(45.0, WindUnit.BEAUFORT)

        assertEquals(12, scale.grid.last().value)
        assertTrue(scale.maxMs >= 45.0)
    }
}
