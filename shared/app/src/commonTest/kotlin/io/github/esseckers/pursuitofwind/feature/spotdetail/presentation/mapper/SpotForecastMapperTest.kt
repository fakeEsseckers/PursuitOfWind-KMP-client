package io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.mapper

import io.github.esseckers.pursuitofwind.core.ui.Compass
import io.github.esseckers.pursuitofwind.core.ui.DayLabel
import io.github.esseckers.pursuitofwind.core.ui.TimeRange
import io.github.esseckers.pursuitofwind.data.model.WindUnit
import io.github.esseckers.pursuitofwind.engine.model.ForecastHour
import io.github.esseckers.pursuitofwind.engine.model.Reason
import io.github.esseckers.pursuitofwind.engine.model.Verdict
import io.github.esseckers.pursuitofwind.engine.model.WindSpeed
import io.github.esseckers.pursuitofwind.feature.spotdetail.BERLIN
import io.github.esseckers.pursuitofwind.feature.spotdetail.FRIDAY
import io.github.esseckers.pursuitofwind.feature.spotdetail.LONDON
import io.github.esseckers.pursuitofwind.feature.spotdetail.MONDAY
import io.github.esseckers.pursuitofwind.feature.spotdetail.SATURDAY
import io.github.esseckers.pursuitofwind.feature.spotdetail.SUNDAY
import io.github.esseckers.pursuitofwind.feature.spotdetail.THREE_DAYS_OF_HOURS
import io.github.esseckers.pursuitofwind.feature.spotdetail.alex
import io.github.esseckers.pursuitofwind.feature.spotdetail.at
import io.github.esseckers.pursuitofwind.feature.spotdetail.domain.mapper.SpotEvaluationMapper
import io.github.esseckers.pursuitofwind.feature.spotdetail.forecast
import io.github.esseckers.pursuitofwind.feature.spotdetail.nordstrand
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.ChartWindowUi
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.ModelWindUi
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.Selection
import io.github.esseckers.pursuitofwind.feature.spotdetail.saturdayOn
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class SpotForecastMapperTest {
    private val saturday820 = at(SATURDAY, 8, 20)

    private fun mapper(
        hours: List<ForecastHour> = saturdayOn(),
        now: Instant = saturday820,
        unit: WindUnit = WindUnit.KNOTS,
        riderZone: TimeZone = BERLIN,
    ) = SpotForecastMapper(alex(unit), SpotEvaluationMapper.evaluate(alex(unit), nordstrand(), hours, now), now, riderZone)

    private fun SpotForecastMapper.indexAt(start: Instant): Int = chart().hours.indexOfFirst { it.start == start }

    @Test
    fun `the chart starts at the current hour and shows today and the next two days`() {
        val forecast = mapper()
        val longer = mapper(hours = forecast(from = at(SATURDAY, 12), count = 72) { 10.0 }, now = at(SATURDAY, 12))

        assertEquals(
            at(SATURDAY, 8),
            forecast
                .chart()
                .hours
                .first()
                .start,
        )
        assertEquals(THREE_DAYS_OF_HOURS - 8, forecast.chart().hours.size)
        assertEquals(1f / 3, forecast.nowOffset, 0.001f)
        // A refresh at noon reaches into Tuesday; the chart stops at the end of Monday.
        assertEquals(listOf(SATURDAY, SUNDAY, MONDAY), longer.days().map { it.date })
        assertEquals(
            at(MONDAY, 23),
            longer
                .chart()
                .hours
                .last()
                .start,
        )
    }

    @Test
    fun `a last day the cache does not cover until midnight is left out`() {
        val untilMondayNoon = mapper(hours = forecast(count = 60) { 18.0 })

        assertEquals(listOf(SATURDAY, SUNDAY), untilMondayNoon.days().map { it.date })
        assertTrue(untilMondayNoon.windows().none { it.date == MONDAY })
    }

    @Test
    fun `bars carry the verdict of their hour and the window worth riding is marked`() {
        val forecast = mapper()
        val chart = forecast.chart()

        assertEquals(
            listOf("13:00", "14:00", "15:00", "16:00"),
            chart.hours.filter { it.verdict == Verdict.GO }.map { it.label },
        )
        assertEquals(listOf(ChartWindowUi(forecast.indexAt(at(SATURDAY, 13)), forecast.indexAt(at(SATURDAY, 16)), true)), chart.windows)
        assertEquals(20, chart.hours[forecast.indexAt(at(SATURDAY, 14))].mean)
        assertEquals(24, chart.hours[forecast.indexAt(at(SATURDAY, 14))].gust)
    }

    @Test
    fun `the band spans the lowest and the highest model and needs two models`() {
        val one = mapper(hours = saturdayOn(withGfs = true)).chart().hours.first()
        val agreeing = mapper().chart().hours.first()

        assertEquals(WindSpeed.knots(6.0).metersPerSecond, one.spreadLowMs!!, 1e-9)
        assertEquals(WindSpeed.knots(10.0).metersPerSecond, one.spreadHighMs!!, 1e-9)
        // ECMWF and ICON alone agree exactly: a band of zero height, but still two models.
        assertEquals(agreeing.spreadLowMs, agreeing.spreadHighMs)
        val single = forecast { 6.0 }.map { it.copy(byModel = it.byModel.filterKeys { model -> model.value == "ecmwf" }) }
        assertNull(
            mapper(hours = single)
                .chart()
                .hours
                .first()
                .spreadLowMs,
        )
    }

    @Test
    fun `days start at local midnight and the repeated hour of the clock change keeps both labels apart`() {
        val forecast = mapper()
        val hours = forecast.chart().hours

        assertTrue("02:00 UTC+2" in hours.map { it.label } && "02:00 UTC+1" in hours.map { it.label }, hours.map { it.label }.toString())
        assertEquals(hours.size, hours.map { it.label to it.date }.toSet().size)
        assertEquals(listOf(SATURDAY, SUNDAY, MONDAY), hours.filter { it.firstOfDay }.map { it.date })
        assertEquals(
            listOf(DayLabel.Today, DayLabel.Tomorrow, DayLabel.Weekday(DayOfWeek.MONDAY)),
            forecast.days().map { it.label },
        )
        assertEquals(forecast.indexAt(at(SUNDAY, 0)), forecast.days()[1].firstHour)
    }

    @Test
    fun `the time axis has a label every third hour`() {
        val labeled =
            mapper()
                .chart()
                .hours
                .filter { it.showLabel }
                .map { it.label }

        assertEquals(listOf("09:00", "12:00", "15:00", "18:00", "21:00", "00:00", "03:00"), labeled.take(7))
    }

    @Test
    fun `the first window worth riding is shown first and a day shows its best window`() {
        val forecast = mapper()
        val windowStart = forecast.indexAt(at(SATURDAY, 13))

        assertEquals(windowStart, forecast.indexOf(Selection.First))
        assertEquals(windowStart, forecast.indexOf(Selection.Day(SATURDAY)))
        // No window on Sunday: its first daylight hour within the riding hours (daylight from 08:00, riding from 09:00).
        assertEquals(forecast.indexAt(at(SUNDAY, 9)), forecast.indexOf(Selection.Day(SUNDAY)))
        assertEquals(windowStart, forecast.indexOf(Selection.Day(FRIDAY)))
        assertEquals(forecast.indexAt(at(SATURDAY, 15)), forecast.indexOf(Selection.Hour(at(SATURDAY, 15))))
        // An hour that has passed: the current hour.
        assertEquals(0, forecast.indexOf(Selection.Hour(at(SATURDAY, 7))))
        assertEquals(0, mapper(hours = forecast { 6.0 }).indexOf(Selection.First))
    }

    @Test
    fun `the details of an hour say why it is not a GO`() {
        val forecast = mapper(hours = saturdayOn(withGfs = true))
        val light = forecast.details(0)
        val good = forecast.details(forecast.indexAt(at(SATURDAY, 14)))

        assertEquals(Verdict.NO_GO, light.verdict)
        assertTrue(Reason.TOO_LIGHT in light.reasons)
        assertEquals("08:00", light.time)
        assertEquals(DayLabel.Today, light.day)
        assertEquals(Compass.SW, light.compass)
        assertEquals(240, light.bearingDeg)
        assertEquals(listOf(ModelWindUi("ECMWF", 6), ModelWindUi("GFS", 10), ModelWindUi("ICON", 6)), light.models)
        assertEquals(Verdict.GO, good.verdict)
        assertEquals(emptyList(), good.reasons)
        assertNotNull(good.kiteM2)
    }

    @Test
    fun `windows give the riding time at the spot and the departure where the rider is`() {
        val inBerlin = mapper().windows().single()
        val inLondon = mapper(riderZone = LONDON).windows().single()

        assertEquals(TimeRange("13:00", "17:00"), inBerlin.time)
        assertEquals("10:30", inBerlin.leaveBy)
        assertFalse(inBerlin.leaveNow)
        assertTrue(inBerlin.qualifies)
        assertEquals(18 to 22, inBerlin.windLow to inBerlin.windHigh)
        assertEquals(DayLabel.Today, inBerlin.day)
        assertEquals("09:30", inLondon.leaveBy)
        assertEquals(TimeRange("13:00", "17:00"), inLondon.time)
    }

    @Test
    fun `a window that starts too late to be worth the drive is listed but does not qualify`() {
        val late = mapper(now = at(SATURDAY, 13)).windows().single()

        assertFalse(late.qualifies)
        assertTrue(late.leaveNow)
        // Riding could start only after the drive and rigging, at 15:30: 90 minutes, less than the two-hour minimum.
        assertEquals(TimeRange("15:30", "17:00"), late.time)
        assertEquals(0, mapper(now = at(SATURDAY, 13)).indexOf(Selection.First))
    }

    @Test
    fun `a window under way starts at the current hour`() {
        val forecast = mapper(now = at(SATURDAY, 13, 30))

        assertEquals(0, forecast.windows().single().firstHour)
        assertEquals(
            0,
            forecast
                .chart()
                .windows
                .single()
                .first,
        )
    }

    @Test
    fun `a window that can no longer be reached stays on the chart but is not listed`() {
        // Leaving at 15:00, the rider would arrive and be rigged at 17:30, after the window has ended.
        val forecast = mapper(now = at(SATURDAY, 15))

        assertEquals(emptyList(), forecast.windows())
        assertFalse(
            forecast
                .chart()
                .windows
                .single()
                .qualifies,
        )
    }

    @Test
    fun `wind is rounded in the unit of the rider`() {
        val forecast = mapper(unit = WindUnit.METERS_PER_SECOND)
        val details = forecast.details(forecast.indexAt(at(SATURDAY, 14)))

        assertEquals(10, details.mean)
        assertEquals(WindUnit.METERS_PER_SECOND, details.unit)
        assertEquals(listOf(5, 10, 15), forecast.chart().grid.map { it.value })
    }
}
