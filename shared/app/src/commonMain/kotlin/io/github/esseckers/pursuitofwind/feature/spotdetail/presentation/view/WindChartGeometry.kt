package io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.view

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.WindChartUi

/** Heights of the chart's lanes, top to bottom: day names, bars, direction arrows, times. */
internal data class ChartLayout(
    val dayLane: Dp,
    val plot: Dp,
    val arrowLane: Dp,
    val timeLane: Dp,
) {
    val total: Dp get() = dayLane + plot + arrowLane + timeLane
}

/**
 * Where everything on the chart goes, in pixels, computed once per chart and size so drawing allocates nothing.
 *
 * @property hourWidth the width of one hour; hour `i` spans `i * hourWidth` to `(i + 1) * hourWidth`.
 * @property plotTop the y of the chart's top, the wind speed [WindChartUi.scaleMaxMs].
 * @property plotBottom the y of zero wind, where the lane of arrows starts.
 * @property arrowBottom the y where the lane of arrows ends and the times start.
 * @property spread the area between the lowest and the highest weather model, one closed shape per run of hours that
 *   has more than one model.
 */
internal class ChartGeometry(
    val chart: WindChartUi,
    val hourWidth: Float,
    val plotTop: Float,
    val plotBottom: Float,
    val arrowBottom: Float,
    val barInset: Float,
) {
    val bars: List<Rect> =
        chart.hours.mapIndexed { i, hour ->
            Rect(left = i * hourWidth + barInset, top = y(hour.meanMs), right = (i + 1) * hourWidth - barInset, bottom = plotBottom)
        }

    val spread: List<Path> = spreadShapes()

    fun y(ms: Double): Float = plotBottom - ((ms / chart.scaleMaxMs).toFloat().coerceIn(0f, 1f) * (plotBottom - plotTop))

    fun center(index: Int): Float = (index + CENTER) * hourWidth

    fun arrowCenter(index: Int): Offset = Offset(center(index), (plotBottom + arrowBottom) / 2)

    private fun spreadShapes(): List<Path> {
        val runs = mutableListOf<IntRange>()
        var start = -1
        chart.hours.forEachIndexed { i, hour ->
            val has = hour.spreadLowMs != null && hour.spreadHighMs != null
            if (has && start < 0) start = i
            if (!has && start >= 0) {
                runs += start until i
                start = -1
            }
        }
        if (start >= 0) runs += start until chart.hours.size
        return runs.map(::spreadShape)
    }

    /** The band over [run]: along the highest model's wind, then back along the lowest. */
    private fun spreadShape(run: IntRange): Path {
        val points =
            if (run.first == run.last) {
                // A lone hour spans the width of its bar, so it is still visible.
                val hour = chart.hours[run.first]
                listOf(run.first * hourWidth + barInset to hour, (run.first + 1) * hourWidth - barInset to hour)
            } else {
                run.map { center(it) to chart.hours[it] }
            }
        return Path().apply {
            points.forEachIndexed { i, (x, hour) ->
                val top = y(hour.spreadHighMs ?: 0.0)
                if (i == 0) moveTo(x, top) else lineTo(x, top)
            }
            points.asReversed().forEach { (x, hour) -> lineTo(x, y(hour.spreadLowMs ?: 0.0)) }
            close()
        }
    }

    private companion object {
        const val CENTER = 0.5f
    }
}
