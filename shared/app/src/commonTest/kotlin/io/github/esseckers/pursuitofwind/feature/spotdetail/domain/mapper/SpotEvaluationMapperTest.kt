package io.github.esseckers.pursuitofwind.feature.spotdetail.domain.mapper

import io.github.esseckers.pursuitofwind.engine.model.ForecastHour
import io.github.esseckers.pursuitofwind.feature.spotdetail.MONDAY
import io.github.esseckers.pursuitofwind.feature.spotdetail.SATURDAY
import io.github.esseckers.pursuitofwind.feature.spotdetail.SUNDAY
import io.github.esseckers.pursuitofwind.feature.spotdetail.alex
import io.github.esseckers.pursuitofwind.feature.spotdetail.at
import io.github.esseckers.pursuitofwind.feature.spotdetail.domain.entity.SpotEvaluationEntity
import io.github.esseckers.pursuitofwind.feature.spotdetail.forecast
import io.github.esseckers.pursuitofwind.feature.spotdetail.nordstrand
import io.github.esseckers.pursuitofwind.feature.spotdetail.saturdayOn
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class SpotEvaluationMapperTest {
    private fun evaluate(
        hours: List<ForecastHour> = saturdayOn(),
        now: Instant = at(SATURDAY, 8, 20),
    ): SpotEvaluationEntity = SpotEvaluationMapper.evaluate(alex(), nordstrand(), hours, now)

    private fun SpotEvaluationEntity.dates() = hours.map { it.start.toLocalDateTime(zone).date }.distinct()

    @Test
    fun `the hours start at the current one and cover today and the next two days at the spot`() {
        val longer = evaluate(hours = forecast(from = at(SATURDAY, 12), count = 72) { 10.0 }, now = at(SATURDAY, 12))

        assertEquals(at(SATURDAY, 8), evaluate().hours.first().start)
        // A refresh at noon reaches into Tuesday; the hours stop at the end of Monday.
        assertEquals(listOf(SATURDAY, SUNDAY, MONDAY), longer.dates())
        assertEquals(at(MONDAY, 23), longer.hours.last().start)
    }

    @Test
    fun `a last day the cache does not cover until midnight is left out`() {
        val untilMondayNoon = evaluate(hours = forecast(count = 60) { 18.0 })

        assertEquals(listOf(SATURDAY, SUNDAY), untilMondayNoon.dates())
        assertTrue(untilMondayNoon.windows.none { it.start.toLocalDateTime(untilMondayNoon.zone).date == MONDAY })
    }

    @Test
    fun `every hour shown has its verdict`() {
        val evaluation = evaluate()

        assertEquals(evaluation.hours.size, evaluation.verdicts.size)
        assertEquals(evaluation.hours.map { it.start }, evaluation.verdicts.map { it.start })
    }

    @Test
    fun `the first window worth riding is also the best one on its day`() {
        val evaluation = evaluate()
        val first = SpotEvaluationMapper.firstWorthRiding(evaluation)

        assertEquals(at(SATURDAY, 13), first?.start)
        assertEquals(first, SpotEvaluationMapper.bestWindowOn(evaluation, SATURDAY))
        assertNull(SpotEvaluationMapper.bestWindowOn(evaluation, SUNDAY))
        assertNull(SpotEvaluationMapper.firstWorthRiding(evaluate(hours = forecast { 6.0 })))
    }

    @Test
    fun `a window that can no longer be reached is found but not reachable`() {
        // Leaving at 15:00, the rider would arrive and be rigged at 17:30, after the window has ended.
        val evaluation = evaluate(now = at(SATURDAY, 15))

        assertEquals(1, evaluation.windows.size)
        assertFalse(evaluation.windows.single().qualifies)
        assertEquals(emptyList(), SpotEvaluationMapper.reachableWindows(evaluation))
    }
}
