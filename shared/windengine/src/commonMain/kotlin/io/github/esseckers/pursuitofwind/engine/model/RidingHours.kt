package io.github.esseckers.pursuitofwind.engine.model

import kotlinx.datetime.LocalDateTime

/**
 * When the rider rides, in whole hours of the spot's local time: from [fromHour] (0–23) until [toHour] (1–24). An hour
 * counts when it starts within them: with 9–21 the first starts at 09:00 and the last at 20:00, and 0–24 is the whole
 * day. In a time zone half an hour off UTC forecast hours start at half past, so 9–21 counts from 09:30 to 21:30.
 */
public data class RidingHours(
    public val fromHour: Int,
    public val toHour: Int,
) {
    init {
        require(fromHour in 0 until HOURS_PER_DAY) { "Riding hours start at a whole hour from 0 to 23, not $fromHour" }
        require(toHour in 1..HOURS_PER_DAY) { "Riding hours end at a whole hour from 1 to 24, not $toHour" }
        require(fromHour < toHour) { "Riding hours must start before they end, were $fromHour–$toHour" }
    }

    /** True when the hour that starts at [localStart] starts within these hours: at [fromHour] or later, before [toHour]. */
    public operator fun contains(localStart: LocalDateTime): Boolean {
        val startMinute = localStart.hour * MINUTES_PER_HOUR + localStart.minute
        return startMinute >= fromHour * MINUTES_PER_HOUR && startMinute < toHour * MINUTES_PER_HOUR
    }

    public companion object {
        /** From 09:00 to 21:00: what most riders call a riding day. */
        public val DEFAULT: RidingHours = RidingHours(fromHour = 9, toHour = 21)

        private const val HOURS_PER_DAY = 24
        private const val MINUTES_PER_HOUR = 60
    }
}
