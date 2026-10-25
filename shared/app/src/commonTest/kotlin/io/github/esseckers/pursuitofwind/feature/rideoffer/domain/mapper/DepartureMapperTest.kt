package io.github.esseckers.pursuitofwind.feature.rideoffer.domain.mapper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class DepartureMapperTest {
    private val windowStart = Instant.parse("2026-10-24T11:00:00Z")

    private fun at(time: String) = Instant.parse("2026-10-24T${time}Z")

    @Test
    fun `a ride leaves at the drafted time while it is ahead`() {
        assertEquals(at("08:30:00"), DepartureMapper.departure(at("08:30:00"), windowStart, now = at("08:29:59")))
    }

    @Test
    fun `once the drafted time has come the ride leaves at the next quarter hour`() {
        assertEquals(at("08:45:00"), DepartureMapper.departure(at("08:30:00"), windowStart, now = at("08:30:00")))
        assertEquals(at("09:00:00"), DepartureMapper.departure(at("08:30:00"), windowStart, now = at("08:52:10")))
    }

    @Test
    fun `a ride never leaves after its window starts`() {
        assertEquals(windowStart, DepartureMapper.departure(at("08:30:00"), windowStart, now = at("10:50:00")))
        assertEquals(windowStart, DepartureMapper.departure(at("11:30:00"), windowStart, now = at("08:00:00")))
    }
}
