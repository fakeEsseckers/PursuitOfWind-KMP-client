package io.github.esseckers.pursuitofwind.feature.today.presentation.model

import org.jetbrains.compose.resources.StringResource
import kotlin.time.Duration

/**
 * The MVI state of the Today screen: what it shows, and the [inputs] it was computed from.
 *
 * @property updatedAgo age of the oldest forecast behind the cards; null while no cards are shown.
 * @property notice why the cards may not be current, shown above them.
 * @property inputs what the reducer computes the rest from. The view does not read it.
 */
internal data class TodayState(
    val content: Content = Content.Loading,
    val isRefreshing: Boolean = false,
    val updatedAgo: Duration? = null,
    val notice: Notice? = null,
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

    enum class Notice {
        /** The last refresh failed because the device is offline. */
        OFFLINE,

        /** The last refresh failed for another reason. */
        REFRESH_FAILED,

        /** The forecast is current, but the crew's rides could not be updated. */
        CREW_FAILED,

        /** The refresh worked earlier, but the forecast is now old. */
        STALE,
    }
}
