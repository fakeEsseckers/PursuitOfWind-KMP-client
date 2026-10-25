package io.github.esseckers.pursuitofwind.data.internal

import kotlin.time.Instant

/** The start of the hour this instant falls in (UTC; every zone the app knows has whole-hour offsets for this). */
internal fun Instant.startOfHour(): Instant = Instant.fromEpochSeconds(epochSeconds.floorDiv(SECONDS_PER_HOUR) * SECONDS_PER_HOUR)

private const val SECONDS_PER_HOUR = 3_600L
