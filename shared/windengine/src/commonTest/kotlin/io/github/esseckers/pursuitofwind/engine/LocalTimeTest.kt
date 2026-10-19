package io.github.esseckers.pursuitofwind.engine

import io.github.esseckers.pursuitofwind.engine.internal.groupByLocalDate
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** Vectors H: daylight saving time in Europe/Berlin, plus zones with half-hour offsets. */
class LocalTimeTest {
    private fun hourlySlots(
        first: String,
        last: String,
    ): List<Instant> = generateSequence(Instant.parse(first)) { it + 1.hours }.takeWhile { it <= Instant.parse(last) }.toList()

    private val springForward = LocalDate(2026, 3, 29)
    private val fallBack = LocalDate(2026, 10, 25)

    private fun slotsOn(
        date: LocalDate,
        first: String,
        last: String,
    ): List<Instant> = groupByLocalDate(hourlySlots(first, last), BERLIN) { it }.getValue(date)

    @Test
    fun `H1 the spring-forward day has 23 hourly slots`() {
        val slots = slotsOn(springForward, "2026-03-28T22:00:00Z", "2026-03-29T23:00:00Z")

        assertEquals(23, slots.size)
        assertEquals(Instant.parse("2026-03-28T23:00:00Z"), slots.first())
        assertEquals(Instant.parse("2026-03-29T21:00:00Z"), slots.last())
    }

    @Test
    fun `H2 the fall-back day has 25 hourly slots`() {
        val slots = slotsOn(fallBack, "2026-10-24T20:00:00Z", "2026-10-26T00:00:00Z")

        assertEquals(25, slots.size)
        assertEquals(Instant.parse("2026-10-24T22:00:00Z"), slots.first())
        assertEquals(Instant.parse("2026-10-25T22:00:00Z"), slots.last())
    }

    @Test
    fun `H3 the repeated hour shows its UTC offset so labels stay distinct`() {
        val labels = slotsOn(fallBack, "2026-10-24T20:00:00Z", "2026-10-26T00:00:00Z").map { localHourLabel(it, BERLIN) }

        assertEquals(listOf("02:00 UTC+2", "02:00 UTC+1"), labels.filter { it.startsWith("02:00") })
        assertEquals(labels.size, labels.toSet().size)
        assertEquals("01:00", labels[1])
        assertEquals("03:00", labels[4])
    }

    @Test
    fun `H4 the skipped hour has no slot`() {
        val labels = slotsOn(springForward, "2026-03-28T22:00:00Z", "2026-03-29T23:00:00Z").map { localHourLabel(it, BERLIN) }

        assertTrue(labels.none { it.startsWith("02:00") }, "labels: $labels")
        assertEquals(listOf("00:00", "01:00", "03:00"), labels.take(3))
    }

    @Test
    fun `ordinary hours are plain wall-clock times`() {
        assertEquals("13:00", localHourLabel(Instant.parse("2026-07-18T11:00:00Z"), BERLIN))
        assertEquals("11:00", localHourLabel(Instant.parse("2026-07-18T11:00:00Z"), TimeZone.UTC))
    }

    @Test
    fun `half-hour offsets are formatted with minutes`() {
        val stJohns = TimeZone.of("America/St_Johns")

        assertEquals("01:30 UTC-2:30", localHourLabel(Instant.parse("2026-11-01T04:00:00Z"), stJohns))
        assertEquals("01:30 UTC-3:30", localHourLabel(Instant.parse("2026-11-01T05:00:00Z"), stJohns))
        assertEquals("01:30 UTC+10:30", localHourLabel(Instant.parse("2026-04-04T15:00:00Z"), TimeZone.of("Australia/Lord_Howe")))
    }
}
