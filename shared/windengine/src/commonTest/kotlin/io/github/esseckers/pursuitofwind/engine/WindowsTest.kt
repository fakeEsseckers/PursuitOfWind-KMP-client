package io.github.esseckers.pursuitofwind.engine

import io.github.esseckers.pursuitofwind.engine.model.Confidence
import io.github.esseckers.pursuitofwind.engine.model.HourVerdict
import io.github.esseckers.pursuitofwind.engine.model.ModelId
import io.github.esseckers.pursuitofwind.engine.model.SpotConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Vectors E: a 13:00–17:00 window on 2026-07-18 in Berlin (11:00Z–15:00Z), 2 hour drive, 2 hour minimum session. */
class WindowsTest {
    private val firstHour = Instant.parse("2026-07-18T10:00:00Z")

    private fun verdicts(
        pattern: String,
        spot: SpotConfig = SPOT,
        base: ForecastHourBuilder.() -> Unit = {},
    ): List<HourVerdict> = hoursFromPattern(firstHour, pattern, base).map { DecisionEngine.evaluateHour(it, RIDER, spot) }

    private fun windowsAt(
        now: String,
        spot: SpotConfig = SPOT,
    ) = DecisionEngine.findWindows(verdicts("N G G G G N", spot), spot, RIDER, Instant.parse(now))

    @Test
    fun `E1 before leave-by the whole window is usable`() {
        val window = windowsAt(now = "2026-07-18T07:00:00Z").single()

        assertEquals(Instant.parse("2026-07-18T11:00:00Z"), window.start)
        assertEquals(Instant.parse("2026-07-18T15:00:00Z"), window.end)
        assertEquals(Instant.parse("2026-07-18T08:30:00Z"), window.leaveBy)
        assertEquals(window.start, window.usableStart)
        assertEquals(4.hours, window.usable)
        assertTrue(window.qualifies)
    }

    @Test
    fun `E2 after leave-by riding starts after the drive and rigging`() {
        val window = windowsAt(now = "2026-07-18T09:30:00Z").single()

        assertEquals(Instant.parse("2026-07-18T08:30:00Z"), window.leaveBy)
        assertEquals(Instant.parse("2026-07-18T12:00:00Z"), window.usableStart)
        assertEquals(3.hours, window.usable)
        assertTrue(window.qualifies)
    }

    @Test
    fun `E3 too little left after the drive does not qualify`() {
        val window = windowsAt(now = "2026-07-18T11:30:00Z").single()

        assertEquals(Instant.parse("2026-07-18T14:00:00Z"), window.usableStart)
        assertEquals(1.hours, window.usable)
        assertFalse(window.qualifies)
    }

    @Test
    fun `E4 a spot beyond the maximum drive has no windows`() {
        assertEquals(emptyList(), windowsAt(now = "2026-07-18T07:00:00Z", spot = SPOT.copy(drive = 200.minutes)))
    }

    @Test
    fun `a window that is over has no usable time`() {
        val window = windowsAt(now = "2026-07-18T16:00:00Z").single()

        assertEquals(Duration.ZERO, window.usable)
        assertEquals(0.0, window.score)
    }

    @Test
    fun `a missing forecast hour splits a window`() {
        val hours = verdicts("G G G G G G")
        val withGap = hours.filterIndexed { index, _ -> index != 3 }

        val windows = DecisionEngine.findWindows(withGap, SPOT, RIDER, Instant.parse("2026-07-18T05:00:00Z"))

        assertEquals(listOf(3, 2), windows.map { it.hours })
    }

    @Test
    fun `hours are sorted before windows are built`() {
        val windows = DecisionEngine.findWindows(verdicts("G G G").reversed(), SPOT, RIDER, Instant.parse("2026-07-18T05:00:00Z"))

        assertEquals(listOf(firstHour), windows.map { it.start })
    }

    @Test
    fun `the window kite is the most frequent hour kite and the smaller one on a tie`() {
        val nine = verdicts("G G G") { wind(18.kn, 24.kn) }
        val seven = verdicts("G G G") { wind(26.kn, 30.kn) }.map { it.copy(start = it.start + 3.hours) }

        val window = DecisionEngine.findWindows(nine + seven, SPOT, RIDER, Instant.parse("2026-07-18T05:00:00Z")).single()

        assertEquals(6, window.hours)
        assertEquals(7.0, window.recommendedKiteM2)
    }

    @Test
    fun `the window kite follows the majority of hours`() {
        val nine = verdicts("G G") { wind(18.kn, 24.kn) }
        val seven = verdicts("G") { wind(26.kn, 30.kn) }.map { it.copy(start = it.start + 2.hours) }

        val window = DecisionEngine.findWindows(nine + seven, SPOT, RIDER, Instant.parse("2026-07-18T05:00:00Z")).single()

        assertEquals(9.0, window.recommendedKiteM2)
    }

    @Test
    fun `boundaries are inclusive for session length and drive and leave-by`() {
        val exactlyMinimum = DecisionEngine.findWindows(verdicts("N G G N"), SPOT, RIDER, Instant.parse("2026-07-18T05:00:00Z")).single()
        val leavingNow = windowsAt(now = "2026-07-18T08:30:00Z").single()
        val maxDriveSpot = SPOT.copy(drive = RIDER.maxDrive)

        assertEquals(RIDER.minSession, exactlyMinimum.usable)
        assertTrue(exactlyMinimum.qualifies)
        assertEquals(leavingNow.start, leavingNow.usableStart)
        assertEquals(1, windowsAt(now = "2026-07-18T05:00:00Z", spot = maxDriveSpot).size)
    }

    @Test
    fun `repeated hour starts are rejected`() {
        val hours = verdicts("G G")

        assertFailsWith<IllegalArgumentException> {
            DecisionEngine.findWindows(hours + hours.first(), SPOT, RIDER, Instant.parse("2026-07-18T05:00:00Z"))
        }
    }

    @Test
    fun `F4 a single model gives UNKNOWN confidence`() {
        val window =
            DecisionEngine
                .findWindows(
                    verdicts("G G G G") { models = listOf(ModelId.ICON) },
                    SPOT,
                    RIDER,
                    Instant.parse("2026-07-18T05:00:00Z"),
                ).single()

        assertEquals(Confidence.UNKNOWN, window.confidence)
        assertEquals(4.0 * 0.8, window.score, 1e-9)
    }
}
