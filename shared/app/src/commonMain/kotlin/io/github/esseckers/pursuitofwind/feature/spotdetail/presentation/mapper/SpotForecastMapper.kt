package io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.mapper

import io.github.esseckers.pursuitofwind.core.ui.TimeRange
import io.github.esseckers.pursuitofwind.core.ui.clockTime
import io.github.esseckers.pursuitofwind.core.ui.dayLabelOf
import io.github.esseckers.pursuitofwind.core.ui.rounded
import io.github.esseckers.pursuitofwind.data.model.MySpot
import io.github.esseckers.pursuitofwind.data.model.Profile
import io.github.esseckers.pursuitofwind.engine.DecisionEngine
import io.github.esseckers.pursuitofwind.engine.localHourLabel
import io.github.esseckers.pursuitofwind.engine.model.ForecastHour
import io.github.esseckers.pursuitofwind.engine.model.HourVerdict
import io.github.esseckers.pursuitofwind.engine.model.SessionWindow
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.ChartDayUi
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.ChartHourUi
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.ChartWindowUi
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.Compass
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.HourDetailsUi
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.ModelWindUi
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.Selection
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.WindChartUi
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.WindowUi
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** Today and the next two days at the spot, as on Today. */
internal const val CHART_DAYS = 3

/** The time axis has a label every this many hours. */
private const val LABEL_EVERY_HOURS = 3

/**
 * Runs the decision engine over one spot's cached forecast and formats it for the chart, the window list and the
 * details of an hour. It shows the hours that have not ended by [now], over at most [CHART_DAYS] days at the spot; a
 * last day the cache does not cover until midnight is left out, because its windows would end where the data ends.
 * Hours and windows are shown in the spot's time zone, departures and day names as the rider in [riderZone] sees them.
 */
internal class SpotForecastMapper(
    private val profile: Profile,
    spot: MySpot,
    hours: List<ForecastHour>,
    private val now: Instant,
    private val riderZone: TimeZone,
) {
    private val config = spot.toSpotConfig()
    private val zone = config.timeZone
    private val riderToday = now.toLocalDateTime(riderZone).date

    private val forecast: List<ForecastHour> = shownHours(hours, now, zone)
    private val verdicts: List<HourVerdict> = forecast.map { DecisionEngine.evaluateHour(it, profile.rider, config) }
    private val dates: List<LocalDate> = forecast.map { it.start.toLocalDateTime(zone).date }
    private val sessions: List<SessionWindow> = DecisionEngine.findWindows(verdicts, config, profile.rider, now)

    /** True when no hour is left to show. */
    val isEmpty: Boolean get() = forecast.isEmpty()

    /** How far into the first hour the current time is, from 0 to 1. Apart from the chart, so a tick does not redraw it. */
    val nowOffset: Float get() = ((now - forecast.first().start) / 1.hours).toFloat().coerceIn(0f, 1f)

    fun chart(): WindChartUi {
        val highest = forecast.indices.maxOf { i -> maxOf(verdicts[i].wind.gust.metersPerSecond, forecast[i].modelRange()?.second ?: 0.0) }
        val scale = chartScale(highest, profile.windUnit)
        return WindChartUi(
            hours = forecast.indices.map(::chartHour),
            windows =
                sessions.map { window ->
                    ChartWindowUi(firstHourOf(window), forecast.indexOfLast { it.start < window.end }, window.qualifies)
                },
            grid = scale.grid,
            scaleMaxMs = scale.maxMs,
            unit = profile.windUnit,
        )
    }

    fun days(): List<ChartDayUi> =
        dates.indices
            .filter { it == 0 || dates[it] != dates[it - 1] }
            .map { ChartDayUi(dates[it], dayLabelOf(dates[it], riderToday), firstHour = it) }

    /** The windows the rider can still reach; one whose riding time has run out before the arrival is left out. */
    fun windows(): List<WindowUi> = sessions.filter { it.usable > Duration.ZERO }.map(::windowUi)

    /** The index of the hour [selection] points to. */
    fun indexOf(selection: Selection): Int =
        when (selection) {
            Selection.First -> firstWindowHour() ?: 0
            is Selection.Day -> dayHour(selection.date) ?: firstWindowHour() ?: 0
            is Selection.Hour -> forecast.indexOfFirst { it.start == selection.start }.takeIf { it >= 0 } ?: 0
        }

    fun details(index: Int): HourDetailsUi {
        val hour = forecast[index]
        val verdict = verdicts[index]
        val unit = profile.windUnit
        return HourDetailsUi(
            index = index,
            start = hour.start,
            day = dayLabelOf(dates[index], riderToday),
            time = localHourLabel(hour.start, zone),
            verdict = verdict.verdict,
            reasons = verdict.reasons,
            mean = verdict.wind.mean.rounded(unit),
            gust = verdict.wind.gust.rounded(unit),
            unit = unit,
            bearingDeg =
                verdict.wind.bearing.degrees
                    .roundToInt(),
            compass = Compass.of(verdict.wind.bearing.degrees),
            models =
                hour.byModel.entries
                    .sortedBy { it.key.value }
                    .map { (model, wind) -> ModelWindUi(model.value.uppercase(), wind.mean.rounded(unit)) },
            kiteM2 = verdict.recommendedKiteM2,
            tempC = hour.tempC.roundToInt(),
            daylight = hour.isDaylight,
        )
    }

    private fun chartHour(index: Int): ChartHourUi {
        val hour = forecast[index]
        val wind = verdicts[index].wind
        val spread = hour.modelRange()
        return ChartHourUi(
            start = hour.start,
            date = dates[index],
            day = dayLabelOf(dates[index], riderToday),
            label = localHourLabel(hour.start, zone),
            showLabel = hour.start.toLocalDateTime(zone).hour % LABEL_EVERY_HOURS == 0,
            firstOfDay = index == 0 || dates[index] != dates[index - 1],
            verdict = verdicts[index].verdict,
            meanMs = wind.mean.metersPerSecond,
            gustMs = wind.gust.metersPerSecond,
            spreadLowMs = spread?.first,
            spreadHighMs = spread?.second,
            bearingDeg = wind.bearing.degrees,
            daylight = hour.isDaylight,
            mean = wind.mean.rounded(profile.windUnit),
            gust = wind.gust.rounded(profile.windUnit),
        )
    }

    private fun windowUi(window: SessionWindow): WindowUi {
        val first = firstHourOf(window)
        val riding = forecast.indices.filter { forecast[it].start < window.end && forecast[it].start + 1.hours > window.usableStart }
        val speeds = riding.map { verdicts[it].wind.mean }.ifEmpty { listOf(verdicts[first].wind.mean) }
        return WindowUi(
            start = window.start,
            firstHour = first,
            date = window.start.toLocalDateTime(zone).date,
            day = dayLabelOf(window.start.toLocalDateTime(zone).date, riderToday),
            time = TimeRange(window.usableStart.clockTime(zone), window.end.clockTime(zone)),
            windLow = speeds.min().rounded(profile.windUnit),
            windHigh = speeds.max().rounded(profile.windUnit),
            unit = profile.windUnit,
            kiteM2 = window.recommendedKiteM2,
            leaveBy = window.leaveBy.clockTime(riderZone),
            leaveNow = now >= window.leaveBy,
            confidence = window.confidence,
            qualifies = window.qualifies,
        )
    }

    /** The first hour of [window] still on the chart: a window under way starts at the current hour. */
    private fun firstHourOf(window: SessionWindow): Int = forecast.indexOfFirst { it.start + 1.hours > window.start }.coerceAtLeast(0)

    private fun firstWindowHour(): Int? = sessions.firstOrNull { it.qualifies }?.let(::firstHourOf)

    /** The best window worth riding on [date], or that day's first daylight hour, or its first hour. */
    private fun dayHour(date: LocalDate): Int? {
        val best = sessions.filter { it.qualifies && it.start.toLocalDateTime(zone).date == date }.maxByOrNull { it.score }
        return best?.let(::firstHourOf)
            ?: dates.indices.firstOrNull { dates[it] == date && forecast[it].isDaylight }
            ?: dates.indexOf(date).takeIf { it >= 0 }
    }
}

/** The lowest and highest model mean wind of the hour, in m/s; null with one model. */
private fun ForecastHour.modelRange(): Pair<Double, Double>? {
    val means = byModel.values.map { it.mean.metersPerSecond }
    return if (means.size < 2) null else means.min() to means.max()
}

/** The hours of [hours] that have not ended by [now], over at most [CHART_DAYS] days at the spot in [zone]. */
private fun shownHours(
    hours: List<ForecastHour>,
    now: Instant,
    zone: TimeZone,
): List<ForecastHour> {
    val upcoming = hours.sortedBy { it.start }.filter { it.start + 1.hours > now }
    val dates = upcoming.map { it.start.toLocalDateTime(zone).date }.distinct()
    val days = dates.take(CHART_DAYS)
    val coveredUntilMidnight =
        dates.size > CHART_DAYS || upcoming.lastOrNull()?.let { (it.start + 1.hours).toLocalDateTime(zone).date > days.last() } == true
    val shown = if (days.size > 1 && !coveredUntilMidnight) days.dropLast(1) else days
    return upcoming.filter { it.start.toLocalDateTime(zone).date in shown }
}
