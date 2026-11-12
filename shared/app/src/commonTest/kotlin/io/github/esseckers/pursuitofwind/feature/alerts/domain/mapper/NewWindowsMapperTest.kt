package io.github.esseckers.pursuitofwind.feature.alerts.domain.mapper

import io.github.esseckers.pursuitofwind.data.model.NotifiedWindow
import io.github.esseckers.pursuitofwind.engine.model.Confidence
import io.github.esseckers.pursuitofwind.engine.model.DayDecision
import io.github.esseckers.pursuitofwind.engine.model.Reason
import io.github.esseckers.pursuitofwind.engine.model.SessionWindow
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** Which windows are news to the rider: known by spot, day and shared time, not by their exact start (ADR 0011). */
class NewWindowsMapperTest {
    private val bay = SpotId("nordstrand-bay")
    private val lagoon = SpotId("salt-lagoon")
    private val saturday = LocalDate(2026, 10, 24)
    private val sunday = LocalDate(2026, 10, 25)
    private val thursdayMorning = at(LocalDate(2026, 10, 22), 8)

    private fun at(
        date: LocalDate,
        hour: Int,
    ): Instant = date.atTime(LocalTime(hour, 0)).toInstant(TimeZone.UTC)

    /** A GO day whose best window runs from [from] to [to] o'clock. */
    private fun go(
        date: LocalDate,
        from: Int,
        to: Int,
        spot: SpotId = bay,
    ): DayDecision.Go {
        val window =
            SessionWindow(
                spotId = spot,
                start = at(date, from),
                end = at(date, to),
                hours = to - from,
                marginalHours = 0,
                leaveBy = at(date, from) - 2.hours,
                usableStart = at(date, from),
                usable = (to - from).hours,
                confidence = Confidence.HIGH,
                recommendedKiteM2 = 9.0,
                qualifies = true,
            )
        return DayDecision.Go(date, window, alternatives = emptyList())
    }

    private fun noGo(date: LocalDate): DayDecision.NoGo = DayDecision.NoGo(date, Reason.TOO_LIGHT, details = emptyList(), nextGoDay = null)

    private fun told(
        date: LocalDate,
        from: Int,
        to: Int,
        spot: SpotId = bay,
    ): NotifiedWindow = NotifiedWindow(spot, date, at(date, from), at(date, to))

    @Test
    fun `the first check announces every GO day and remembers each window`() {
        val days = listOf(noGo(LocalDate(2026, 10, 22)), go(saturday, 13, 17), go(sunday, 11, 15, lagoon))

        val found = NewWindowsMapper.newWindows(days, notified = emptyList(), now = thursdayMorning)

        assertEquals(listOf(saturday, sunday), found.fresh.map { it.date })
        assertEquals(listOf(told(saturday, 13, 17), told(sunday, 11, 15, lagoon)), found.notified)
    }

    @Test
    fun `a window that was announced is not announced again`() {
        val announced = listOf(told(saturday, 13, 17))

        val found = NewWindowsMapper.newWindows(listOf(go(saturday, 13, 17)), announced, thursdayMorning)

        assertEquals(emptyList(), found.fresh)
        assertEquals(announced, found.notified)
    }

    @Test
    fun `a window the next model run moved by an hour is the same window and is remembered where it is now`() {
        val found = NewWindowsMapper.newWindows(listOf(go(saturday, 14, 18)), listOf(told(saturday, 13, 17)), thursdayMorning)

        assertEquals(emptyList(), found.fresh)
        assertEquals(listOf(told(saturday, 14, 18)), found.notified)
    }

    @Test
    fun `a window that keeps drifting stays the same window run after run`() {
        val afterFirstRun = NewWindowsMapper.newWindows(listOf(go(saturday, 14, 18)), listOf(told(saturday, 13, 17)), thursdayMorning)

        // 17:00 to 19:00 shares no time with the 13:00 to 17:00 first announced, only with where the window moved since.
        val found = NewWindowsMapper.newWindows(listOf(go(saturday, 17, 19)), afterFirstRun.notified, thursdayMorning)

        assertEquals(emptyList(), found.fresh)
    }

    @Test
    fun `a window that only touches the announced one is another window`() {
        val found = NewWindowsMapper.newWindows(listOf(go(saturday, 17, 20)), listOf(told(saturday, 13, 17)), thursdayMorning)

        assertEquals(listOf(saturday), found.fresh.map { it.date })
        assertEquals(listOf(told(saturday, 13, 17), told(saturday, 17, 20)), found.notified)
    }

    @Test
    fun `the same hours at another spot are news`() {
        val found = NewWindowsMapper.newWindows(listOf(go(saturday, 13, 17, lagoon)), listOf(told(saturday, 13, 17)), thursdayMorning)

        assertEquals(listOf(lagoon), found.fresh.map { it.spotId })
    }

    @Test
    fun `the same hours on another day are news`() {
        val found = NewWindowsMapper.newWindows(listOf(go(sunday, 13, 17)), listOf(told(saturday, 13, 17)), thursdayMorning)

        assertEquals(listOf(sunday), found.fresh.map { it.date })
    }

    @Test
    fun `a window the forecast dropped stays remembered and is not announced when it comes back`() {
        val announced = listOf(told(saturday, 13, 17))
        val dropped = NewWindowsMapper.newWindows(listOf(noGo(saturday)), announced, thursdayMorning)

        val back = NewWindowsMapper.newWindows(listOf(go(saturday, 13, 17)), dropped.notified, thursdayMorning)

        assertEquals(announced, dropped.notified)
        assertEquals(emptyList(), back.fresh)
    }

    @Test
    fun `a window that is over is forgotten`() {
        val found = NewWindowsMapper.newWindows(listOf(noGo(sunday)), listOf(told(saturday, 13, 17)), now = at(saturday, 17))

        assertEquals(emptyList(), found.notified)
    }

    @Test
    fun `a day without a GO announces nothing`() {
        val found = NewWindowsMapper.newWindows(listOf(noGo(saturday), noGo(sunday)), emptyList(), thursdayMorning)

        assertEquals(emptyList(), found.fresh)
        assertEquals(emptyList(), found.notified)
    }
}
