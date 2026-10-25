package io.github.esseckers.pursuitofwind.feature.rideoffer.domain.mapper

import kotlin.time.Instant

/** When an offered ride leaves. */
internal object DepartureMapper {
    private const val QUARTER_HOUR_SECONDS = 15 * 60L

    /**
     * When a ride drafted to leave at [planned], for a window that starts at [windowStart], leaves if offered at [now]:
     * at [planned] while it is ahead, else at the next quarter hour, which gives the crew a moment to join; and never
     * after the window starts.
     */
    fun departure(
        planned: Instant,
        windowStart: Instant,
        now: Instant,
    ): Instant = minOf(if (planned > now) planned else nextQuarterHour(now), windowStart)

    private fun nextQuarterHour(now: Instant): Instant =
        Instant.fromEpochSeconds((now.epochSeconds / QUARTER_HOUR_SECONDS + 1) * QUARTER_HOUR_SECONDS)
}
