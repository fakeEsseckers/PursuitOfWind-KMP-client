package io.github.esseckers.pursuitofwind.feature.today.presentation.model

import io.github.esseckers.pursuitofwind.core.refresh.RefreshNotice
import org.jetbrains.compose.resources.StringResource
import kotlin.time.Duration

/**
 * The MVI state of the Today screen: what it shows, and the [inputs] it was computed from.
 *
 * @property updatedAgo age of the oldest forecast behind the cards; null while no cards are shown.
 * @property notice why the cards may not be current, shown above them.
 * @property crewFailed the forecast is current but the crew's rides could not be updated; shown when [notice] is not.
 * @property inputs what the reducer computes the rest from. The view does not read it.
 */
internal data class TodayState(
    val content: Content = Content.Loading,
    val isRefreshing: Boolean = false,
    val updatedAgo: Duration? = null,
    val notice: RefreshNotice? = null,
    val crewFailed: Boolean = false,
    val inputs: TodayInputs = TodayInputs(),
) {
    sealed interface Content {
        data object Loading : Content

        /** Nothing to decide on until the rider adds what is [missing]. */
        data class Empty(
            val missing: Missing,
        ) : Content

        /** Today and the next days, in date order. */
        data class Ready(
            val days: List<DayUi>,
        ) : Content

        /** Nothing cached to show: the last refresh failed, or brought no forecast for the favorite spots. */
        data class Error(
            val message: StringResource,
        ) : Content
    }

    enum class Missing {
        PROFILE,
        FAVORITE_SPOTS,
    }
}
