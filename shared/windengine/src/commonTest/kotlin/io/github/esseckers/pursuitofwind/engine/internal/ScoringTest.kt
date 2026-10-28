package io.github.esseckers.pursuitofwind.engine.internal

import io.github.esseckers.pursuitofwind.engine.DecisionEngine
import io.github.esseckers.pursuitofwind.engine.model.Confidence
import io.github.esseckers.pursuitofwind.engine.model.SessionWindow
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration
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
    fun `F5 the longest window ranks first whatever its confidence`() {
        val fourHigh = window("a-beach", start = "2026-07-18T10:00:00Z", usable = 4.hours, confidence = Confidence.HIGH)
        val fiveMedium =
            window("b-bay", start = "2026-07-18T10:00:00Z", usable = 5.hours, confidence = Confidence.MEDIUM, marginalHours = 1)

        assertEquals(listOf(fiveMedium, fourHigh), listOf(fourHigh, fiveMedium).sortedWith(windowRanking { 60.minutes }))
    }

    @Test
    fun `F6 equally long windows rank by confidence with unknown between medium and low`() {
        val windows = Confidence.entries.map { window(it.name.lowercase(), start = "2026-07-18T10:00:00Z", confidence = it) }

        val ranked = windows.shuffled(Random(7)).sortedWith(windowRanking { 60.minutes })

        assertEquals(listOf(Confidence.HIGH, Confidence.MEDIUM, Confidence.UNKNOWN, Confidence.LOW), ranked.map { it.confidence })
    }

    @Test
    fun `F7 and F8 equally long and sure windows rank the shorter drive then the earlier start then the spot id`() {
        val drives = mapOf(SpotId("far") to 90.minutes, SpotId("near") to 60.minutes, SpotId("also-near") to 60.minutes)
        val far = window("far", start = "2026-07-18T10:00:00Z")
        val near = window("near", start = "2026-07-18T12:00:00Z")
        val nearEarlier = window("near", start = "2026-07-18T11:00:00Z")
        val alsoNear = window("also-near", start = "2026-07-18T11:00:00Z")

        val ranked = listOf(far, near, nearEarlier, alsoNear).sortedWith(windowRanking { drives.getValue(it) })

        assertEquals(listOf(alsoNear, nearEarlier, near, far), ranked)
    }

    @Test
    fun `a longer window beats a shorter drive`() {
        val drives = mapOf(SpotId("far") to 90.minutes, SpotId("near") to 60.minutes)
        val far = window("far", start = "2026-07-18T10:00:00Z", usable = 5.hours)
        val near = window("near", start = "2026-07-18T10:00:00Z", usable = 4.hours)

        assertEquals(listOf(far, near), DecisionEngine.bestFirst(listOf(near, far)) { drives.getValue(it) })
    }

    private fun window(
        spot: String,
        start: String,
        usable: Duration = 4.hours,
        confidence: Confidence = Confidence.HIGH,
        marginalHours: Int = 0,
    ): SessionWindow {
        val startInstant = Instant.parse(start)
        return SessionWindow(
            spotId = SpotId(spot),
            start = startInstant,
            end = startInstant + usable,
            hours = usable.inWholeHours.toInt(),
            marginalHours = marginalHours,
            leaveBy = startInstant - 2.hours,
            usableStart = startInstant,
            usable = usable,
            confidence = confidence,
            recommendedKiteM2 = 9.0,
            qualifies = true,
        )
    }
}
