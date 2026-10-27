package io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model

import io.github.esseckers.pursuitofwind.core.refresh.RefreshNotice
import io.github.esseckers.pursuitofwind.feature.spotdetail.domain.entity.SpotEvaluationEntity
import org.jetbrains.compose.resources.StringResource
import kotlin.time.Duration

/**
 * The MVI state of the spot detail screen: what it shows, and the [inputs] it was computed from.
 *
 * @property header the spot's name and facts; null until the spot is known.
 * @property updatedAgo age of the forecast behind the chart; null while no chart is shown.
 * @property notice why the chart may not be current, shown above it.
 * @property inputs what the reducer computes the rest from. The view does not read it.
 * @property evaluation the engine's answer for the chart on screen, kept so that choosing an hour only works out that
 *   hour; null while no chart is shown. The view does not read it.
 */
internal data class SpotDetailState(
    val inputs: SpotDetailInputs,
    val content: Content = Content.Loading,
    val header: SpotHeaderUi? = null,
    val isRefreshing: Boolean = false,
    val updatedAgo: Duration? = null,
    val notice: RefreshNotice? = null,
    val evaluation: SpotEvaluationEntity? = null,
) {
    sealed interface Content {
        data object Loading : Content

        /** Nothing to show until the rider adds what is [missing]. */
        data class Missing(
            val missing: SpotDetailState.Missing,
        ) : Content

        /** Nothing cached to show: the last refresh failed, or brought no forecast. */
        data class Error(
            val message: StringResource,
        ) : Content

        /**
         * The forecast: the [chart], the [days] it covers, the [windows] the rider can still reach and the [selected]
         * hour; [nowOffset] is how far into the chart's first hour the current time is, from 0 to 1. Each part keeps
         * its instance while it does not change, so Compose redraws only what did.
         */
        data class Ready(
            val chart: WindChartUi,
            val days: List<ChartDayUi>,
            val windows: List<WindowUi>,
            val selected: HourDetailsUi,
            val nowOffset: Float,
        ) : Content
    }

    enum class Missing {
        /** The rider's profile is not in the cache. */
        PROFILE,

        /** The rider does not follow this spot (any more). */
        SPOT,
    }
}
