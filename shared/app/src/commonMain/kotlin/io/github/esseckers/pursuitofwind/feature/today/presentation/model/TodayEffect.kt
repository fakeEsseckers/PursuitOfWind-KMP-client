package io.github.esseckers.pursuitofwind.feature.today.presentation.model

import io.github.esseckers.pursuitofwind.engine.model.SpotId
import kotlinx.datetime.LocalDate

/** One-off events of the Today screen, handled by the app outside the screen. */
internal sealed interface TodayEffect {
    /** Navigate to the forecast of [spotId] at [date]. */
    data class OpenSpot(
        val spotId: SpotId,
        val date: LocalDate,
    ) : TodayEffect

    /** Navigate to the profile. */
    data object OpenProfile : TodayEffect

    /** Navigate to the spot picker. */
    data object PickSpots : TodayEffect
}
