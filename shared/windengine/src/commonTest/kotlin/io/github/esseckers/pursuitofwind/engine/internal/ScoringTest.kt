package io.github.esseckers.pursuitofwind.engine.internal

import io.github.esseckers.pursuitofwind.engine.model.Confidence
import io.github.esseckers.pursuitofwind.engine.model.SessionWindow
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class ScoringTest {
    @Test
    fun `F1 to F3 confidence from the median model spread`() {
        assertEquals(Confidence.HIGH, confidenceForSpreads(listOf(2.0, 3.0, 4.0, 2.0)), "F1 median 2.5")
        assertEquals(Confidence.LOW, confidenceForSpreads(listOf(5.0, 7.0, 8.0)), "F2 median 7")
        assertEquals(Confidence.MEDIUM, confidenceForSpreads(listOf(4.0, 6.0)), "F3 median 5")
    }

    @Test
    fun `confidence bucket limits are inclusive`() {
        assertEquals(Confidence.HIGH, confidenceForSpreads(listOf(3.0)))
        assertEquals(Confidence.MEDIUM, confidenceForSpreads(listOf(6.0)))
        assertEquals(Confidence.UNKNOWN, confidenceForSpreads(emptyList()))
    }

    @Test
    fun `median of odd and even counts`() {
        assertEquals(2.5, median(listOf(4.0, 2.0, 3.0, 2.0)))
        assertEquals(7.0, median(listOf(8.0, 5.0, 7.0)))
        assertFailsWith<IllegalArgumentException> { median(emptyList()) }
    }

    @Test
    fun `F5 and F6 window scores`() {
        assertEquals(4.0, windowScore(4.hours, marginalHours = 0, confidence = Confidence.HIGH), 1e-9, "F5")
        assertEquals(3.6125, windowScore(5.hours, marginalHours = 1, confidence = Confidence.MEDIUM), 1e-9, "F6")
    }

    @Test
    fun `quality has a floor of one tenth`() {
        assertEquals(1.0, windowScore(10.hours, marginalHours = 9, confidence = Confidence.HIGH), 1e-9)
        assertEquals(1.2, windowScore(2.hours, marginalHours = 0, confidence = Confidence.LOW), 1e-9)
    }

    @Test
    fun `F7 equal scores rank the shorter drive first then the earlier start then the spot id`() {
        val drives = mapOf(SpotId("far") to 90.minutes, SpotId("near") to 60.minutes, SpotId("also-near") to 60.minutes)
        val far = window("far", start = "2026-07-18T10:00:00Z")
        val near = window("near", start = "2026-07-18T12:00:00Z")
        val nearEarlier = window("near", start = "2026-07-18T11:00:00Z")
        val alsoNear = window("also-near", start = "2026-07-18T11:00:00Z")

        val ranked = listOf(far, near, nearEarlier, alsoNear).sortedWith(windowRanking { drives.getValue(it) })

        assertEquals(listOf(alsoNear, nearEarlier, near, far), ranked)
    }

    @Test
    fun `a higher score beats a shorter drive`() {
        val drives = mapOf(SpotId("far") to 90.minutes, SpotId("near") to 60.minutes)
        val far = window("far", start = "2026-07-18T10:00:00Z", score = 4.0)
        val near = window("near", start = "2026-07-18T10:00:00Z", score = 3.6125)

        assertEquals(listOf(far, near), listOf(near, far).sortedWith(windowRanking { drives.getValue(it) }))
    }

    private fun window(
        spot: String,
        start: String,
        score: Double = 4.0,
    ): SessionWindow {
        val startInstant = Instant.parse(start)
        return SessionWindow(
            spotId = SpotId(spot),
            start = startInstant,
            end = startInstant + 4.hours,
            hours = 4,
            marginalHours = 0,
            leaveBy = startInstant - 2.hours,
            usableStart = startInstant,
            usable = 4.hours,
            confidence = Confidence.HIGH,
            score = score,
            recommendedKiteM2 = 9.0,
            qualifies = true,
        )
    }
}
