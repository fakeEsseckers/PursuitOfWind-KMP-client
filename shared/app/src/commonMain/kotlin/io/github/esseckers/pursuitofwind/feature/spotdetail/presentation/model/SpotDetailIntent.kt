package io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** What the rider can ask of the spot detail screen. */
internal sealed interface SpotDetailIntent {
    /** Pull to refresh, the refresh button, or retry after an error. */
    data object Refresh : SpotDetailIntent

    /** Show the details of the hour that starts at [start]: a tap on the chart or on a window. */
    data class SelectHour(
        val start: Instant,
    ) : SpotDetailIntent

    /** Jump to [date] (local to the spot): a tap on a day chip. */
    data class SelectDay(
        val date: LocalDate,
    ) : SpotDetailIntent
}
