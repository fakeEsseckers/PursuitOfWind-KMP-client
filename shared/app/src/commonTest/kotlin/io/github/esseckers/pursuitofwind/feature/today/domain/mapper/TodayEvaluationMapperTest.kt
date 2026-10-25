package io.github.esseckers.pursuitofwind.feature.today.domain.mapper

import io.github.esseckers.pursuitofwind.core.ui.rounded
import io.github.esseckers.pursuitofwind.data.model.WindUnit
import io.github.esseckers.pursuitofwind.engine.model.DayDecision
import io.github.esseckers.pursuitofwind.engine.model.ForecastHour
import io.github.esseckers.pursuitofwind.feature.today.BERLIN
import io.github.esseckers.pursuitofwind.feature.today.FRIDAY
import io.github.esseckers.pursuitofwind.feature.today.SATURDAY
import io.github.esseckers.pursuitofwind.feature.today.SUNDAY
import io.github.esseckers.pursuitofwind.feature.today.THURSDAY
import io.github.esseckers.pursuitofwind.feature.today.THURSDAY_8AM
import io.github.esseckers.pursuitofwind.feature.today.alex
import io.github.esseckers.pursuitofwind.feature.today.at
import io.github.esseckers.pursuitofwind.feature.today.day
import io.github.esseckers.pursuitofwind.feature.today.domain.entity.DayEvaluationEntity
import io.github.esseckers.pursuitofwind.feature.today.domain.entity.SpotForecastEntity
import io.github.esseckers.pursuitofwind.feature.today.mySpot
import io.github.esseckers.pursuitofwind.feature.today.saturdayOn
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Instant

class TodayEvaluationMapperTest {
    private fun evaluate(
        hours: List<ForecastHour> = saturdayOn(),
        now: Instant = THURSDAY_8AM,
        zone: TimeZone = BERLIN,
    ): List<DayEvaluationEntity> =
        TodayEvaluationMapper.evaluate(alex(), listOf(SpotForecastEntity(mySpot("nordstrand-bay", zone = zone), hours)), now, BERLIN)

    private fun DayEvaluationEntity.windKnots(): Pair<Int?, Int?> = windLow?.rounded(WindUnit.KNOTS) to windHigh?.rounded(WindUnit.KNOTS)

    @Test
    fun `a GO day carries the wind of the hours that can be ridden`() {
        val saturday = evaluate()[2]

        assertIs<DayDecision.Go>(saturday.decision)
        assertEquals(18 to 22, saturday.windKnots())
    }

    @Test
    fun `once the time to leave has passed the wind counts only the hours left to ride`() {
        // 2 h drive and 30 min to rig: leaving at 08:00 means riding from 10:30. The morning is lighter.
        val windyToday = day(THURSDAY) { hour -> if (hour < 10) 12.0 else 18.0 }

        assertEquals(18 to 18, evaluate(hours = windyToday).single().windKnots())
    }

    @Test
    fun `a NO-GO day has no wind range`() {
        val thursday = evaluate().first()

        assertIs<DayDecision.NoGo>(thursday.decision)
        assertNull(thursday.windLow)
        assertNull(thursday.windHigh)
    }

    @Test
    fun `at most today and the next two days are decided`() {
        val fourDays = saturdayOn() + day(SUNDAY) { 18.0 }

        assertEquals(listOf(THURSDAY, FRIDAY, SATURDAY), evaluate(hours = fourDays).map { it.decision.date })
        assertEquals(DAYS_SHOWN, evaluate(hours = fourDays).size)
    }

    @Test
    fun `a spot behind the time zone of the rider does not bring back yesterday`() {
        // 00:30 on Friday in Berlin, where the rider is, is still Thursday 23:30 in London, where the spot is.
        val days = evaluate(now = at(FRIDAY, 0, 30), zone = TimeZone.of("Europe/London"))

        assertEquals(listOf(FRIDAY, SATURDAY), days.map { it.decision.date })
    }
}
