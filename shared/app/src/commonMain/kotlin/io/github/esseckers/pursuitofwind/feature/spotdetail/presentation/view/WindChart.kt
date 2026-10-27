package io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.view

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Sizes
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Spacing
import io.github.esseckers.pursuitofwind.core.designsystem.theme.WindChartColors
import io.github.esseckers.pursuitofwind.core.designsystem.theme.windChartColors
import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.chart_now
import io.github.esseckers.pursuitofwind.core.resources.chart_show_details
import io.github.esseckers.pursuitofwind.core.ui.dayLabel
import io.github.esseckers.pursuitofwind.core.ui.windUnitLabel
import io.github.esseckers.pursuitofwind.data.model.WindUnit
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.ChartDayUi
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.ChartHourUi
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.WindChartUi
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * The hourly wind chart, drawn on a canvas: a bar of mean wind per hour in the color of its verdict, gust ticks, the
 * weather models' range as a band, direction arrows, windows and night as tinted backgrounds, and the time in the
 * spot's zone. It scrolls sideways; the wind scale on the left stays. Time runs left to right in every language.
 *
 * The drawing is for the eye only. On top of it lies one invisible cell per hour that screen readers read as text
 * ("Saturday 14:00: 18 kn, gusts 24 kn, …") and that takes taps, so every hour can be reached without seeing the chart.
 * The cells are 28 dp wide, under Android's 48 dp guideline but above WCAG's 24 px minimum: 72 hours at 48 dp would show
 * a third of a day per screen. The day chips and the window list are the large targets for the same moves.
 *
 * The [selected] hour is framed and scrolled into view, again whenever [jumps] changes (a tap on a day or a window that
 * is already selected). The line at the current time is a layer of its own, [nowOffset] into the first hour, so the
 * minute ticking does not redraw the chart.
 */
@Composable
internal fun WindChart(
    chart: WindChartUi,
    days: List<ChartDayUi>,
    selected: Int,
    nowOffset: Float,
    jumps: Int,
    onSelectHour: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labelStyle = MaterialTheme.typography.labelSmall
    val layout = rememberChartLayout(labelStyle)
    val colors = windChartColors()
    val hourPx = with(LocalDensity.current) { Sizes.chartHour.toPx() }
    // The window's width stands in for the chart's until the chart is measured, so the first frame already shows the
    // selected hour (a spot opened for tomorrow does not start at today and jump). It is read once and not followed:
    // after the first frame the effect below scrolls. A window of unknown width leaves that to the effect too.
    val windowInfo = LocalWindowInfo.current
    val initial =
        remember {
            val window = Snapshot.withoutReadObservation { windowInfo.containerSize.width }
            if (window > 0) scrollTarget(selected, hourPx, viewport = window, current = 0) ?: 0 else 0
        }
    val scroll = rememberScrollState(initial = initial)
    // Written when the chart is measured and read only by the effect, so measuring it does not compose it again.
    val viewport = remember { mutableIntStateOf(0) }
    val placed = remember { mutableStateOf(false) }
    LaunchedEffect(selected, jumps, hourPx) {
        snapshotFlow { viewport.intValue }.filter { it > 0 }.collectLatest { width ->
            val target = scrollTarget(selected, hourPx, width, scroll.value)
            // The first placement jumps; later ones, after a tap on a day or a window, glide.
            if (target != null) {
                if (placed.value) scroll.animateScrollTo(target) else scroll.scrollTo(target)
            }
            placed.value = true
        }
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(modifier = modifier.fillMaxWidth().testTag("spot.chart")) {
            WindScale(chart, layout, colors, labelStyle)
            Box(modifier = Modifier.weight(1f).onSizeChanged { viewport.intValue = it.width }.horizontalScroll(scroll)) {
                val size = Modifier.size(Sizes.chartHour * chart.hours.size, layout.total)
                ChartCanvas(chart, days, layout, colors, labelStyle, size)
                NowLine(nowOffset, layout, colors, size)
                SelectionFrame(selected, layout, colors)
                Row(modifier = size.selectableGroup()) {
                    val label = stringResource(Res.string.chart_show_details)
                    chart.hours.forEachIndexed { i, hour ->
                        HourCell(i, hour, chart.unit, isSelected = i == selected, onClickLabel = label, onClick = { onSelectHour(i) })
                    }
                }
            }
        }
    }
}

/**
 * Where to scroll so the [selected] hour shows a third of the way into a [viewport] scrolled to [current]; null while it
 * is in view already.
 */
private fun scrollTarget(
    selected: Int,
    hourPx: Float,
    viewport: Int,
    current: Int,
): Int? {
    val left = selected * hourPx
    if (left >= current && left + hourPx <= current + viewport) return null
    return (left - viewport / VIEWPORT_LEAD).roundToInt().coerceAtLeast(0)
}

@Composable
private fun rememberChartLayout(labelStyle: TextStyle): ChartLayout {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // Lanes with text grow with the font size, so labels are never cut at 200%. Measured once per style and density,
    // not on every tick or selection.
    return remember(measurer, labelStyle, density) {
        val textHeight =
            with(density) {
                measurer
                    .measure("00:00", labelStyle)
                    .size.height
                    .toDp()
            }
        val textLane = textHeight + Spacing.xs * 2
        ChartLayout(dayLane = textLane, plot = Sizes.chartPlot, arrowLane = Sizes.chartArrowLane, timeLane = textLane)
    }
}

/**
 * Everything but the current time and the selection. Built once per chart, size and theme, then only drawn, in a layer
 * of its own: a ripple on an hour, a new selection or the line at the current time do not record it again.
 */
@Composable
private fun ChartCanvas(
    chart: WindChartUi,
    days: List<ChartDayUi>,
    layout: ChartLayout,
    colors: WindChartColors,
    labelStyle: TextStyle,
    modifier: Modifier,
) {
    val measurer = rememberTextMeasurer()
    val dayNames = days.map { dayLabel(it.label) }
    val now = stringResource(Res.string.chart_now)
    Spacer(
        modifier =
            modifier.graphicsLayer().drawWithCache {
                val plotTop = layout.dayLane.toPx()
                val plotBottom = plotTop + layout.plot.toPx()
                val geometry =
                    ChartGeometry(
                        chart = chart,
                        hourWidth = Sizes.chartHour.toPx(),
                        plotTop = plotTop,
                        plotBottom = plotBottom,
                        arrowBottom = plotBottom + layout.arrowLane.toPx(),
                        barInset = Sizes.chartHour.toPx() * BAR_INSET,
                    )
                val padding = Spacing.xs.toPx()
                val times =
                    chart.hours.indices
                        .filter { it == 0 || chart.hours[it].showLabel }
                        .map { i -> i to measurer.measure(if (i == 0) now else chart.hours[i].label, labelStyle) }
                val text =
                    ChartText(
                        days = days.mapIndexed { i, day -> day.firstHour to measurer.measure(dayNames[i], labelStyle) },
                        times = withoutOverlaps(times, geometry, gap = padding),
                        padding = padding,
                    )
                val line = Sizes.border.toPx() * LINE_STROKE
                val strokes =
                    ChartStrokes(
                        hairline = Sizes.border.toPx(),
                        line = line,
                        bold = Sizes.border.toPx() * BOLD_STROKE,
                        corner = Sizes.border.toPx() * CORNER,
                        arrow = arrowPath(layout.arrowLane.toPx() * ARROW_LENGTH),
                    )
                onDrawBehind { drawChart(geometry, colors, text, strokes) }
            },
    )
}

/** A dashed line at the current time, across the bars. */
@Composable
private fun NowLine(
    nowOffset: Float,
    layout: ChartLayout,
    colors: WindChartColors,
    modifier: Modifier,
) {
    Spacer(
        modifier =
            modifier.drawWithCache {
                val dash = Sizes.border.toPx() * BOLD_STROKE * DASH
                val effect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))
                val x = nowOffset * Sizes.chartHour.toPx()
                val top = layout.dayLane.toPx()
                val bottom = top + layout.plot.toPx()
                val width = Sizes.border.toPx() * LINE_STROKE
                onDrawBehind { drawLine(colors.now, Offset(x, top), Offset(x, bottom), strokeWidth = width, pathEffect = effect) }
            },
    )
}

/** The wind values of the grid lines, next to the chart, and the unit above them. */
@Composable
private fun WindScale(
    chart: WindChartUi,
    layout: ChartLayout,
    colors: WindChartColors,
    labelStyle: TextStyle,
) {
    val measurer = rememberTextMeasurer()
    val unit = stringResource(windUnitLabel(chart.unit))
    val widest = (chart.grid.map { it.value.toString() } + unit).maxOf { measurer.measure(it, labelStyle).size.width }
    val width = with(LocalDensity.current) { widest.toDp() } + Spacing.s
    Spacer(
        modifier =
            Modifier.width(width).height(layout.total).drawWithCache {
                val plotTop = layout.dayLane.toPx()
                val plotHeight = layout.plot.toPx()
                val padding = Spacing.xs.toPx()
                val unitText = measurer.measure(unit, labelStyle)
                val labels =
                    chart.grid.map { line ->
                        val y = plotTop + plotHeight * (1 - (line.ms / chart.scaleMaxMs).toFloat().coerceIn(0f, 1f))
                        y to measurer.measure(line.value.toString(), labelStyle)
                    }
                onDrawBehind {
                    drawText(unitText, color = colors.text, topLeft = Offset(size.width - unitText.size.width - padding, padding))
                    labels.forEach { (y, label) ->
                        drawText(
                            label,
                            color = colors.text,
                            topLeft =
                                Offset(
                                    size.width - label.size.width - padding,
                                    y - label.size.height / 2f,
                                ),
                        )
                    }
                }
            },
    )
}

/** A frame around the selected hour, from the top of the bars to the arrows. */
@Composable
private fun SelectionFrame(
    selected: Int,
    layout: ChartLayout,
    colors: WindChartColors,
) {
    Box(
        modifier =
            Modifier
                .offset(x = Sizes.chartHour * selected, y = layout.dayLane)
                .size(Sizes.chartHour, layout.plot + layout.arrowLane)
                .border(Sizes.border * SELECTION_STROKE, colors.selection, MaterialTheme.shapes.extraSmall),
    )
}

/**
 * One invisible, tappable hour, read by screen readers. A composable of its own, so a new selection recomposes the two
 * cells whose state changed, not all 72.
 */
@Composable
private fun HourCell(
    index: Int,
    hour: ChartHourUi,
    unit: WindUnit,
    isSelected: Boolean,
    onClickLabel: String,
    onClick: () -> Unit,
) {
    val description = hourDescription(hour, unit)
    Box(
        modifier =
            Modifier
                .width(Sizes.chartHour)
                .fillMaxHeight()
                .clickable(onClickLabel = onClickLabel, role = Role.Tab, onClick = onClick)
                .semantics {
                    contentDescription = description
                    selected = isSelected
                }.testTag("spot.hour.$index"),
    )
}

/** How far into the view a hour scrolled into view lands: a third of the way, so the hours after it show too. */
private const val VIEWPORT_LEAD = 3f
private const val BAR_INSET = 0.18f
private const val LINE_STROKE = 1.5f
private const val BOLD_STROKE = 2.5f
private const val CORNER = 2f
private const val ARROW_LENGTH = 0.6f
private const val DASH = 1.5f
private const val SELECTION_STROKE = 2
