package io.github.esseckers.pursuitofwind.engine

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private const val SECONDS_PER_HOUR = 3_600
private const val SECONDS_PER_MINUTE = 60

/** Offset changes are looked for this far around a slot; real zones never change twice within it. */
private val TRANSITION_SEARCH = 12.hours

/**
 * Local wall-clock label of the hour that starts at [start], such as `"14:00"`.
 *
 * When clocks go back, one local hour happens twice. Both slots then carry their UTC offset (`"02:00 UTC+2"`,
 * `"02:00 UTC+1"`) so a chart never shows two identical labels. When clocks go forward, the skipped hour simply has
 * no slot.
 */
public fun localHourLabel(
    start: Instant,
    zone: TimeZone,
): String {
    val local = start.toLocalDateTime(zone)
    val time = local.time.toString()
    return if (occursTwice(start, local, zone)) "$time ${formatOffset(zone.offsetAt(start))}" else time
}

/** True if another instant has the same [local] date and time in [zone], i.e. [start] falls into a repeated hour. */
private fun occursTwice(
    start: Instant,
    local: LocalDateTime,
    zone: TimeZone,
): Boolean {
    val offset = zone.offsetAt(start)
    return listOf(start - TRANSITION_SEARCH, start + TRANSITION_SEARCH)
        .map { zone.offsetAt(it) }
        .filter { it != offset }
        .any { other ->
            val twin = start + (offset.totalSeconds - other.totalSeconds).seconds
            twin.toLocalDateTime(zone) == local
        }
}

/** `UTC+2`, `UTC-3:30`, `UTC+0`. */
private fun formatOffset(offset: UtcOffset): String {
    val total = offset.totalSeconds
    val sign = if (total < 0) "-" else "+"
    val hours = abs(total) / SECONDS_PER_HOUR
    val minutes = abs(total) % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    return if (minutes == 0) "UTC$sign$hours" else "UTC$sign$hours:${minutes.toString().padStart(2, '0')}"
}
