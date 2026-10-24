package io.github.esseckers.pursuitofwind.core.ui

import kotlin.time.Instant

/**
 * When a ride leaves that leaves as soon as it can: at the next quarter hour after [now], which gives the crew a moment
 * to join. Today's "I'm going" and the ride offer form use it once the time to leave has passed.
 */
internal fun nextQuarterHour(now: Instant): Instant =
    Instant.fromEpochSeconds((now.epochSeconds / QUARTER_HOUR_SECONDS + 1) * QUARTER_HOUR_SECONDS)

private const val QUARTER_HOUR_SECONDS = 15 * 60L
