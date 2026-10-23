package io.github.esseckers.pursuitofwind.feature.today.presentation.model

import io.github.esseckers.pursuitofwind.engine.model.SpotId
import kotlinx.datetime.LocalDate

/** What the rider can ask of the Today screen. */
internal sealed interface TodayIntent {
    /** Pull to refresh, or retry after an error. */
    data object Refresh : TodayIntent

    /** Show the hourly forecast of [spotId], at [date] (local to the spot). */
    data class OpenSpot(
        val spotId: SpotId,
        val date: LocalDate,
    ) : TodayIntent

    /** Set up the profile that is still missing. */
    data object SetUpProfile : TodayIntent

    /** Choose favorite spots, none of which is set yet. */
    data object PickSpots : TodayIntent
}
