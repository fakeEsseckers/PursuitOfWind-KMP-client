package io.github.esseckers.pursuitofwind.engine.internal

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Calendar date of this instant in [zone]. */
internal fun Instant.localDate(zone: TimeZone): LocalDate = toLocalDateTime(zone).date

/**
 * Groups [items] by the local date of their start in [zone], keeping order. Around DST changes a local day has
 * 23 or 25 hourly slots; grouping by instant keeps every slot exactly once.
 */
internal fun <T> groupByLocalDate(
    items: List<T>,
    zone: TimeZone,
    start: (T) -> Instant,
): Map<LocalDate, List<T>> = items.groupBy { start(it).localDate(zone) }
