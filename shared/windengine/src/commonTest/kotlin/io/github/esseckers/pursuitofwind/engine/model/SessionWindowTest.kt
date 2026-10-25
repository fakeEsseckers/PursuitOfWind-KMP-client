package io.github.esseckers.pursuitofwind.engine.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** The questions callers ask of a window, at their boundaries. */
class SessionWindowTest {
    private val start = Instant.parse("2026-07-18T11:00:00Z")

    /** 11:00–15:00, leave by 09:00; riding from [usableStart]. */
    private fun window(usableStart: Instant = start): SessionWindow {
        val end = start + 4.hours
        return SessionWindow(
            spotId = SpotId("nordstrand-bay"),
            start = start,
            end = end,
            hours = 4,
            marginalHours = 0,
            leaveBy = start - 2.hours,
            usableStart = usableStart,
            usable = (end - usableStart).coerceAtLeast(Duration.ZERO),
            confidence = Confidence.HIGH,
            score = 4.0,
            recommendedKiteM2 = 9.0,
            qualifies = true,
        )
    }

    @Test
    fun `a window is reachable while some riding time is left`() {
        assertTrue(window().isReachable)
        assertTrue(window(usableStart = start + 4.hours - 1.minutes).isReachable)
        assertFalse(window(usableStart = start + 4.hours).isReachable)
        assertFalse(window(usableStart = start + 5.hours).isReachable)
    }

    @Test
    fun `it is time to leave from the leave-by time on`() {
        val leaveBy = window().leaveBy

        assertFalse(window().mustLeaveNow(leaveBy - 1.milliseconds))
        assertTrue(window().mustLeaveNow(leaveBy))
        assertTrue(window().mustLeaveNow(leaveBy + 1.hours))
    }

    @Test
    fun `an hour has riding in it when it overlaps the time from the usable start to the end`() {
        val late = window(usableStart = start + 90.minutes)
        val hours = (0..5).map { start + it.hours - 1.hours }

        assertEquals(listOf(false, true, true, true, true, false), hours.map { window().hasRidingIn(it) })
        assertEquals(listOf(false, false, true, true, true, false), hours.map { late.hasRidingIn(it) })
    }
}
