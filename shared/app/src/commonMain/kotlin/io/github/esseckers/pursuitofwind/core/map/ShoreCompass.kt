package io.github.esseckers.pursuitofwind.core.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.esseckers.pursuitofwind.engine.model.Bearing
import io.github.esseckers.pursuitofwind.engine.model.Sector
import io.github.esseckers.pursuitofwind.engine.model.ShoreSectors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A spot's shore around its pin, the same size at any zoom: an arrow towards the [water], labelled [waterLabel], in a
 * ring whose [ShoreSectors.rideable] arc is [rideable] and [ShoreSectors.danger] arc is [danger] (wind from that side).
 * [mapBearing] turns it with the map, so north on the ring is north on the map. Drawn over the map, it takes no touches.
 */
@Composable
internal fun ShoreCompass(
    water: Bearing,
    sectors: ShoreSectors,
    mapBearing: Double,
    waterLabel: String,
    rideable: Color,
    danger: Color,
    arrow: Color,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.size(COMPASS_SIZE), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(RING_SIZE)) {
            val stroke = RING_WIDTH.toPx()
            // The ring's outline, so the gaps between the arcs read as part of it.
            drawCircle(color = Color.White, radius = size.minDimension / 2 - stroke / 2, style = Stroke(stroke + OUTLINE.toPx()))
            arc(sectors.rideable, mapBearing, rideable, stroke)
            arc(sectors.danger, mapBearing, danger, stroke)
            waterArrow(water.degrees - mapBearing, arrow, length = size.minDimension / 2 - stroke)
        }
        val label = labelOffset(water.degrees - mapBearing)
        Text(
            text = waterLabel,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            modifier =
                Modifier
                    .offset { label }
                    .background(arrow, MaterialTheme.shapes.small)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** Where the label sits: just outside the ring, towards the water. */
@Composable
private fun labelOffset(screenDegrees: Double): IntOffset {
    val radius = with(LocalDensity.current) { (RING_SIZE / 2 + LABEL_GAP).toPx() }
    val (x, y) = towards(screenDegrees, radius)
    return IntOffset(x.roundToInt(), y.roundToInt())
}

/** [sector] as an arc of the ring, [stroke] wide, inside the canvas. */
private fun DrawScope.arc(
    sector: Sector,
    mapBearing: Double,
    color: Color,
    stroke: Float,
) {
    drawArc(
        color = color,
        startAngle = canvasAngle(sector.from.degrees - mapBearing),
        sweepAngle = sector.widthDegrees.toFloat(),
        useCenter = false,
        topLeft = Offset(stroke / 2, stroke / 2),
        size = Size(size.width - stroke, size.height - stroke),
        style = Stroke(width = stroke, cap = StrokeCap.Butt),
    )
}

private fun DrawScope.waterArrow(
    screenDegrees: Double,
    color: Color,
    length: Float,
) {
    val tip = center + towards(screenDegrees, length).let { (x, y) -> Offset(x, y) }
    val head = ARROW_HEAD.toPx()
    val width = ARROW_WIDTH.toPx()
    drawLine(color = color, start = center, end = tip, strokeWidth = width, cap = StrokeCap.Round)
    listOf(HEAD_ANGLE_DEG, -HEAD_ANGLE_DEG).forEach { turn ->
        val (x, y) = towards(screenDegrees + turn, head)
        drawLine(color = color, start = tip, end = tip + Offset(x, y), strokeWidth = width, cap = StrokeCap.Round)
    }
}

/** A compass direction (0 north, clockwise) as an angle for `drawArc` (0 east, clockwise). */
private fun canvasAngle(compassDegrees: Double): Float = (compassDegrees - QUARTER_TURN_DEG).toFloat()

/** The point [distance] away from the centre in a compass direction, as x to the right and y down. */
private fun towards(
    compassDegrees: Double,
    distance: Float,
): Pair<Float, Float> {
    val radians = compassDegrees * PI / HALF_TURN_DEG
    return (sin(radians) * distance).toFloat() to (-cos(radians) * distance).toFloat()
}

private val COMPASS_SIZE: Dp = 180.dp
private val RING_SIZE: Dp = 112.dp
private val RING_WIDTH: Dp = 14.dp
private val OUTLINE: Dp = 3.dp
private val LABEL_GAP: Dp = 22.dp
private val ARROW_HEAD: Dp = 14.dp
private val ARROW_WIDTH: Dp = 4.dp

/** The head's sides, turned back from the arrow. */
private const val HEAD_ANGLE_DEG = 150.0
private const val QUARTER_TURN_DEG = 90.0
private const val HALF_TURN_DEG = 180.0
