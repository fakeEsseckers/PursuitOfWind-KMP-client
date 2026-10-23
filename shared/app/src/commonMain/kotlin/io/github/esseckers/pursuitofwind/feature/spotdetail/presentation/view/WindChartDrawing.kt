package io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.view

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import io.github.esseckers.pursuitofwind.core.designsystem.theme.WindChartColors
import io.github.esseckers.pursuitofwind.engine.model.Verdict

/** Text the chart draws, measured once per chart: day names at the first hour of each day, times under their hour. */
internal class ChartText(
    val days: List<Pair<Int, TextLayoutResult>>,
    val times: List<Pair<Int, TextLayoutResult>>,
    val padding: Float,
)

/** Line widths, strokes and the arrow shape, in pixels, made once so drawing allocates nothing. */
internal class ChartStrokes(
    val hairline: Float,
    val line: Float,
    val bold: Float,
    val corner: Float,
    val arrow: Path,
) {
    val edge: Stroke = Stroke(width = hairline)
    val arrowStroke: Stroke = Stroke(width = line, cap = StrokeCap.Round, join = StrokeJoin.Round)
}

/** Draws the chart but the current time and the selection, in this order so later layers stay readable on earlier ones. */
internal fun DrawScope.drawChart(
    geometry: ChartGeometry,
    colors: WindChartColors,
    text: ChartText,
    strokes: ChartStrokes,
) {
    drawBackground(geometry, colors, strokes)
    drawBars(geometry, colors, strokes)
    geometry.spread.forEach { shape ->
        drawPath(shape, colors.spreadFill)
        drawPath(shape, colors.spreadEdge, style = strokes.edge)
    }
    drawArrows(geometry, colors, strokes)
    drawLabels(geometry, colors, text)
}

/** Night, windows, grid lines and day separators. */
private fun DrawScope.drawBackground(
    geometry: ChartGeometry,
    colors: WindChartColors,
    strokes: ChartStrokes,
) {
    val w = geometry.hourWidth
    geometry.chart.hours.forEachIndexed { i, hour ->
        if (!hour.daylight) {
            drawRect(colors.night, topLeft = Offset(i * w, geometry.plotTop), size = Size(w, geometry.arrowBottom - geometry.plotTop))
        }
    }
    geometry.chart.windows.forEach { window ->
        drawRoundRect(
            color = if (window.qualifies) colors.windowGo else colors.windowShort,
            topLeft = Offset(window.first * w, geometry.plotTop),
            size = Size((window.last - window.first + 1) * w, geometry.plotBottom - geometry.plotTop),
            cornerRadius = CornerRadius(strokes.corner),
        )
    }
    geometry.chart.grid.forEach { line ->
        val y = geometry.y(line.ms)
        drawLine(colors.grid, Offset(0f, y), Offset(size.width, y), strokeWidth = strokes.hairline)
    }
    drawLine(colors.daySeparator, Offset(0f, geometry.plotBottom), Offset(size.width, geometry.plotBottom), strokeWidth = strokes.hairline)
    geometry.chart.hours.forEachIndexed { i, hour ->
        if (hour.firstOfDay && i > 0) {
            drawLine(colors.daySeparator, Offset(i * w, 0f), Offset(i * w, size.height), strokeWidth = strokes.line)
        }
    }
}

/** A bar of mean wind in the color of the hour's verdict, with a stem and a tick at the gusts. */
private fun DrawScope.drawBars(
    geometry: ChartGeometry,
    colors: WindChartColors,
    strokes: ChartStrokes,
) {
    geometry.chart.hours.forEachIndexed { i, hour ->
        val bar = geometry.bars[i]
        val color =
            when (hour.verdict) {
                Verdict.GO -> colors.go
                Verdict.MARGINAL -> colors.marginal
                Verdict.NO_GO -> colors.noGo
            }
        drawRoundRect(color, topLeft = bar.topLeft, size = bar.size, cornerRadius = CornerRadius(strokes.corner))
        val x = geometry.center(i)
        val gust = geometry.y(hour.gustMs)
        if (gust < bar.top) drawLine(colors.gust, Offset(x, bar.top), Offset(x, gust), strokeWidth = strokes.hairline)
        drawLine(colors.gust, Offset(bar.left, gust), Offset(bar.right, gust), strokeWidth = strokes.bold, cap = StrokeCap.Round)
    }
}

/** An arrow per hour pointing where the wind blows to. */
private fun DrawScope.drawArrows(
    geometry: ChartGeometry,
    colors: WindChartColors,
    strokes: ChartStrokes,
) {
    geometry.chart.hours.forEachIndexed { i, hour ->
        val center = geometry.arrowCenter(i)
        translate(center.x, center.y) {
            // The bearing is where the wind comes from; the arrow, drawn pointing up, turns to where it goes.
            rotate(degrees = (hour.bearingDeg + HALF_TURN_DEG).toFloat(), pivot = Offset.Zero) {
                drawPath(strokes.arrow, colors.arrow, style = strokes.arrowStroke)
            }
        }
    }
}

/** Day names at the start of each day, clipped to the day so a short first day does not run into the next; times. */
private fun DrawScope.drawLabels(
    geometry: ChartGeometry,
    colors: WindChartColors,
    text: ChartText,
) {
    val w = geometry.hourWidth
    text.days.forEachIndexed { d, (first, layout) ->
        val right =
            text.days
                .getOrNull(d + 1)
                ?.first
                ?.let { it * w } ?: size.width
        clipRect(left = first * w, top = 0f, right = right, bottom = geometry.plotTop) {
            drawText(layout, color = colors.text, topLeft = Offset(first * w + text.padding, text.padding))
        }
    }
    text.times.forEach { (i, layout) ->
        drawText(layout, color = colors.text, topLeft = Offset(timeLabelLeft(geometry, i, layout), geometry.arrowBottom + text.padding))
    }
}

/**
 * The time [labels] that fit: each is centered under its hour, and one that would touch the label before it, with at
 * least [gap] between them, is left out. "Now" on the first hour wins over the time next to it, and at large font sizes
 * fewer times are shown instead of overlapping ones.
 */
internal fun withoutOverlaps(
    labels: List<Pair<Int, TextLayoutResult>>,
    geometry: ChartGeometry,
    gap: Float,
): List<Pair<Int, TextLayoutResult>> {
    var right = Float.NEGATIVE_INFINITY
    return labels.filter { (i, layout) ->
        val left = timeLabelLeft(geometry, i, layout)
        (left >= right + gap).also { fits -> if (fits) right = left + layout.size.width }
    }
}

private fun timeLabelLeft(
    geometry: ChartGeometry,
    index: Int,
    layout: TextLayoutResult,
): Float = (geometry.center(index) - layout.size.width / 2f).coerceAtLeast(0f)

/** An arrow pointing up, centered on the origin, [length] long. */
internal fun arrowPath(length: Float): Path =
    Path().apply {
        val half = length / 2
        val head = length / ARROW_HEAD_DIVISOR
        moveTo(0f, half)
        lineTo(0f, -half)
        moveTo(-head, -half + head)
        lineTo(0f, -half)
        lineTo(head, -half + head)
    }

private const val HALF_TURN_DEG = 180.0
private const val ARROW_HEAD_DIVISOR = 3f
